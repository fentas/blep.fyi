package fyi.blep.core.spatial

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import fyi.blep.core.ble.BlepContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Fuses Android's rotation-vector (compass), linear-accelerometer (motion),
 * gyroscope (sharp-reorientation), and GPS into a steady [MotionSample] stream.
 *
 * Heading is taken from the rotation vector **remapped for a vertically-held
 * phone** (the body-shielding posture: screen/back facing forward), so the
 * azimuth reflects the direction the user is facing rather than where the top of
 * the phone points. Indoors, where GPS speed is absent, distance is dead-reckoned
 * at an assumed walking pace whenever the accelerometer says we're moving.
 */
internal class AndroidMotionProvider(private val context: Context) : MotionProvider {

    override fun motion(): Flow<MotionSample> {
        val sensors = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return emptyFlow()

        return callbackFlow {
            // Latest fused sensor state, updated by callbacks, sampled on a tick.
            var heading: Double? = null
            var headingAccuracy = Double.NaN
            var accelEma = 0.0
            var moving = false
            var reorienting = false
            var gpsSpeed = Double.NaN
            val stepCounter = StepCounter()
            var pendingStepDistance = 0.0
            var frame: LocalFrame? = null
            var localPos: Vec2? = null
            var posAccuracy = Double.NaN

            val rotation = FloatArray(9)
            val remapped = FloatArray(9)
            val orientation = FloatArray(3)

            val sensorListener = object : SensorEventListener {
                override fun onSensorChanged(e: SensorEvent) {
                    when (e.sensor.type) {
                        Sensor.TYPE_ROTATION_VECTOR -> {
                            SensorManager.getRotationMatrixFromVector(rotation, e.values)
                            // Hold-vertical remap: device pointing direction → azimuth.
                            SensorManager.remapCoordinateSystem(
                                rotation, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapped,
                            )
                            SensorManager.getOrientation(remapped, orientation)
                            heading = orientation[0].toDouble() // 0 = north, +π/2 = east
                            if (e.values.size >= 5 && e.values[4] >= 0f) headingAccuracy = e.values[4].toDouble()
                        }
                        Sensor.TYPE_LINEAR_ACCELERATION -> {
                            val m = sqrt(
                                e.values[0] * e.values[0] +
                                    e.values[1] * e.values[1] +
                                    e.values[2] * e.values[2],
                            ).toDouble()
                            accelEma = 0.8 * accelEma + 0.2 * m
                            moving = accelEma > MOVE_ACCEL_THRESHOLD
                            pendingStepDistance += stepCounter.onAccel(System.currentTimeMillis(), m)
                        }
                        Sensor.TYPE_GYROSCOPE -> {
                            val rate = sqrt(
                                e.values[0] * e.values[0] +
                                    e.values[1] * e.values[1] +
                                    e.values[2] * e.values[2],
                            ).toDouble()
                            reorienting = rate > REORIENT_RATE_THRESHOLD
                        }
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }

            fun register(type: Int) {
                sensors.getDefaultSensor(type)?.let {
                    sensors.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME)
                }
            }
            register(Sensor.TYPE_ROTATION_VECTOR)
            register(Sensor.TYPE_LINEAR_ACCELERATION)
            register(Sensor.TYPE_GYROSCOPE)

            // GPS (best-effort; needs location permission, already held for BLE).
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val locationListener = LocationListener { fix: Location ->
                val f = frame ?: LocalFrame(fix.latitude, fix.longitude).also { frame = it }
                localPos = f.toLocal(GeoPoint(fix.latitude, fix.longitude))
                posAccuracy = if (fix.hasAccuracy()) fix.accuracy.toDouble() else Double.NaN
                gpsSpeed = if (fix.hasSpeed() && fix.speed > 0f) fix.speed.toDouble() else Double.NaN
            }
            try {
                locationManager?.getProviders(true)?.forEach { provider ->
                    locationManager.requestLocationUpdates(provider, 1000L, 0f, locationListener, Looper.getMainLooper())
                }
            } catch (_: SecurityException) {
                // No location permission → carry on with inertial sensors only.
            }

            // Emit a fused sample at a steady cadence aligned with RSSI sampling.
            val ticker = launch {
                while (isActive) {
                    val stepDistance = pendingStepDistance
                    pendingStepDistance = 0.0
                    trySend(
                        MotionSample(
                            timeMs = System.currentTimeMillis(),
                            position = localPos,
                            positionAccuracyM = posAccuracy,
                            headingRad = heading,
                            headingAccuracyRad = headingAccuracy,
                            stepDistanceM = stepDistance,
                            speedMps = if (!gpsSpeed.isNaN()) gpsSpeed else 0.0,
                            moving = moving,
                            reorienting = reorienting,
                        ),
                    )
                    kotlinx.coroutines.delay(SAMPLE_INTERVAL_MS)
                }
            }

            awaitClose {
                ticker.cancel()
                sensors.unregisterListener(sensorListener)
                runCatching { locationManager?.removeUpdates(locationListener) }
            }
        }
    }

    private companion object {
        const val MOVE_ACCEL_THRESHOLD = 0.6     // m/s² (EMA) above which we count as moving
        const val REORIENT_RATE_THRESHOLD = 1.2  // rad/s gyro magnitude = a deliberate turn/tilt
        const val SAMPLE_INTERVAL_MS = 200L
    }
}

actual fun createMotionProvider(): MotionProvider {
    val app = BlepContext.app
    return if (app != null) AndroidMotionProvider(app) else NoMotionProvider
}

/** Fallback when the application context isn't available yet. */
private object NoMotionProvider : MotionProvider {
    override fun motion(): Flow<MotionSample> = emptyFlow()
}
