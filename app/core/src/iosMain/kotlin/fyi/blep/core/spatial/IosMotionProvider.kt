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
import platform.CoreLocation.CLHeading
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSError
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSProcessInfo
import platform.darwin.NSObject
import kotlin.math.PI
import kotlin.math.sqrt

/**
 * iOS motion fusion via CoreLocation (heading + GPS) and CoreMotion (device
 * motion). Heading comes from the magnetometer (true, falling back to magnetic),
 * already 0 = north / clockwise — matching the local frame. Indoors, where GPS
 * speed is absent, distance is dead-reckoned at an assumed walking pace whenever
 * the accelerometer reports motion.
 */
actual fun createMotionProvider(): MotionProvider = IosMotionProvider()

private class IosMotionProvider : MotionProvider {

    override fun motion(): Flow<MotionSample> = callbackFlow {
        var heading: Double? = null
        var localPos: Vec2? = null
        var posAccuracy = Double.NaN
        var gpsSpeed = Double.NaN
        var moving = false
        var reorienting = false
        var frame: LocalFrame? = null

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

            override fun locationManager(manager: CLLocationManager, didUpdateHeading: CLHeading) {
                val deg = if (didUpdateHeading.trueHeading >= 0.0) {
                    didUpdateHeading.trueHeading
                } else {
                    didUpdateHeading.magneticHeading
                }
                heading = deg * PI / 180.0
            }

            override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
                // Ignore — keep running on whatever sensors are available.
            }
        }
        locationManager.delegate = delegate
        locationManager.desiredAccuracy = kCLLocationAccuracyBest
        locationManager.requestWhenInUseAuthorization()
        locationManager.startUpdatingLocation()
        locationManager.startUpdatingHeading()

        val motionManager = CMMotionManager()
        if (motionManager.deviceMotionAvailable) {
            motionManager.deviceMotionUpdateInterval = 0.1
            motionManager.startDeviceMotionUpdatesToQueue(NSOperationQueue.mainQueue) { dm, _ ->
                if (dm != null) {
                    val accel = dm.userAcceleration.useContents { sqrt(x * x + y * y + z * z) }
                    moving = accel > MOVE_ACCEL_THRESHOLD
                    val rate = dm.rotationRate.useContents { sqrt(x * x + y * y + z * z) }
                    reorienting = rate > REORIENT_RATE_THRESHOLD
                }
            }
        }

        val ticker = launch {
            while (isActive) {
                val speed = when {
                    !gpsSpeed.isNaN() -> gpsSpeed
                    moving -> ASSUMED_WALK_MPS
                    else -> 0.0
                }
                trySend(
                    MotionSample(
                        timeMs = (NSProcessInfo.processInfo.systemUptime * 1000.0).toLong(),
                        position = localPos,
                        positionAccuracyM = posAccuracy,
                        headingRad = heading,
                        speedMps = speed,
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
            locationManager.stopUpdatingHeading()
            motionManager.stopDeviceMotionUpdates()
        }
    }

    private companion object {
        const val MOVE_ACCEL_THRESHOLD = 0.08     // g (userAcceleration) above which we count as moving
        const val REORIENT_RATE_THRESHOLD = 1.2   // rad/s rotation = a deliberate turn/tilt
        const val ASSUMED_WALK_MPS = 1.2
        const val SAMPLE_INTERVAL_MS = 200L
    }
}
