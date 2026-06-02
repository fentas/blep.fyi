@file:OptIn(ExperimentalForeignApi::class)

package fyi.blep.core.spatial

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.CoreMotion.CMAltimeter
import platform.CoreMotion.CMAttitudeReferenceFrameXMagneticNorthZVertical
import platform.CoreMotion.CMDeviceMotion
import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSError
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSProcessInfo
import platform.darwin.NSObject
import kotlin.math.sqrt

/**
 * watchOS motion fusion. CoreLocation supplies GPS (position + accuracy + speed)
 * via its delegate. Heading comes from CoreMotion device-motion attitude taken in
 * the magnetic-north reference frame (when the watch has a magnetometer) — its
 * yaw is north-referenced, so −yaw is a compass heading; otherwise heading stays
 * null and the watch dead-reckons only from GPS. userAcceleration/rotationRate
 * drive the moving and reorienting flags.
 */
actual fun createMotionProvider(): MotionProvider = WatchMotionProvider()

private class WatchMotionProvider : MotionProvider {

    override fun motion(): Flow<MotionSample> = callbackFlow {
        var heading: Double? = null
        var headingValid = false
        var localPos: Vec2? = null
        var posAccuracy = Double.NaN
        var gpsSpeed = Double.NaN
        var moving = false
        var reorienting = false
        var frame: LocalFrame? = null
        val stepCounter = StepCounter()
        var pendingStepDistance = 0.0
        var relativeAltitude = 0.0

        val locationManager = CLLocationManager()
        val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
            override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
                val loc = didUpdateLocations.lastOrNull() as? CLLocation ?: return
                loc.coordinate.useContents {
                    val f = frame ?: LocalFrame(latitude, longitude).also { frame = it }
                    localPos = f.toLocal(GeoPoint(latitude, longitude))
                }
                posAccuracy = loc.horizontalAccuracy.let { if (it >= 0.0) it else Double.NaN }
                gpsSpeed = loc.speed.let { if (it >= 0.0) it else Double.NaN }
            }

            override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {}
        }
        locationManager.delegate = delegate
        locationManager.desiredAccuracy = kCLLocationAccuracyBest
        locationManager.requestWhenInUseAuthorization()
        locationManager.startUpdatingLocation()

        val motionManager = CMMotionManager()
        val handler: (CMDeviceMotion?, NSError?) -> Unit = handler@{ dm, _ ->
            if (dm == null) return@handler
            val accelG = dm.userAcceleration.useContents { sqrt(x * x + y * y + z * z) }
            moving = accelG > MOVE_ACCEL_THRESHOLD
            val rate = dm.rotationRate.useContents { sqrt(x * x + y * y + z * z) }
            reorienting = rate > REORIENT_RATE_THRESHOLD
            if (headingValid) heading = -dm.attitude.yaw
            // StepCounter works in m/s²; userAcceleration is in g.
            pendingStepDistance += stepCounter.onAccel(
                (NSProcessInfo.processInfo.systemUptime * 1000.0).toLong(), accelG * 9.81,
            )
        }
        if (motionManager.deviceMotionAvailable) {
            motionManager.deviceMotionUpdateInterval = 0.1
            val frames = CMMotionManager.availableAttitudeReferenceFrames()
            if ((frames and CMAttitudeReferenceFrameXMagneticNorthZVertical) != 0uL) {
                headingValid = true
                motionManager.startDeviceMotionUpdatesUsingReferenceFrame(
                    CMAttitudeReferenceFrameXMagneticNorthZVertical, NSOperationQueue.mainQueue, handler,
                )
            } else {
                // No north reference → attitude yaw isn't a compass; motion only.
                motionManager.startDeviceMotionUpdatesToQueue(NSOperationQueue.mainQueue, handler)
            }
        }

        val altimeter = CMAltimeter()
        if (CMAltimeter.isRelativeAltitudeAvailable()) {
            altimeter.startRelativeAltitudeUpdatesToQueue(NSOperationQueue.mainQueue) { data, _ ->
                if (data != null) relativeAltitude = data.relativeAltitude.doubleValue
            }
        }

        val ticker = launch {
            while (isActive) {
                val stepDistance = pendingStepDistance
                pendingStepDistance = 0.0
                trySend(
                    MotionSample(
                        timeMs = (NSProcessInfo.processInfo.systemUptime * 1000.0).toLong(),
                        position = localPos,
                        positionAccuracyM = posAccuracy,
                        headingRad = heading,
                        stepDistanceM = stepDistance,
                        speedMps = if (!gpsSpeed.isNaN()) gpsSpeed else 0.0,
                        relativeAltitudeM = relativeAltitude,
                        moving = moving,
                        reorienting = reorienting,
                    ),
                )
                delay(SAMPLE_INTERVAL_MS)
            }
        }

        awaitClose {
            ticker.cancel()
            locationManager.stopUpdatingLocation()
            motionManager.stopDeviceMotionUpdates()
            altimeter.stopRelativeAltitudeUpdates()
        }
    }

    private companion object {
        const val MOVE_ACCEL_THRESHOLD = 0.08
        const val REORIENT_RATE_THRESHOLD = 1.2
        const val SAMPLE_INTERVAL_MS = 200L
    }
}
