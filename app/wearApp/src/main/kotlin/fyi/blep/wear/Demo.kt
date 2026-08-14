package fyi.blep.wear

import fyi.blep.core.ble.BleScanner
import fyi.blep.core.ble.ProbeResult
import fyi.blep.core.ble.ScanAvailability
import fyi.blep.core.model.BleDevice
import fyi.blep.core.spatial.MotionProvider
import fyi.blep.core.spatial.MotionSample
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.sin
import kotlin.time.TimeSource

/**
 * Scripted demo sources for the watch — the same "hunt" the phone uses, so store
 * screenshots fill with realistic content without any BLE/motion hardware. Enabled
 * with the `demo` launch flag (`adb shell am start … --ez demo true`); never in
 * normal use. Mirrors `composeApp`'s DemoProviders, trimmed to what Wear consumes
 * (discovery list + RSSI + motion; no safety-scan adverts on the watch).
 */
private val DEMO_DEVICES = listOf(
    BleDevice(id = "keys", name = "Keys", rssi = -58),
    BleDevice(id = "buds", name = "AirPods Pro", rssi = -71),
    BleDevice(id = "watch", name = "Galaxy Watch", rssi = -64, isPaired = true),
    BleDevice(id = "wallet", name = "Wallet", rssi = -83),
)

class DemoWearScanner : BleScanner {
    override val availability: Flow<ScanAvailability> = flow { emit(ScanAvailability.READY) }

    override fun devices(includeUnnamed: Boolean, measureConnectedSignal: Boolean): Flow<List<BleDevice>> = flow {
        var i = 0
        while (true) {
            emit(DEMO_DEVICES.map { it.copy(rssi = it.rssi + ((i + it.id.length) % 3 - 1)) })
            i++
            delay(900)
        }
    }

    override fun rssi(deviceId: String): Flow<Int> = flow {
        val t0 = TimeSource.Monotonic.markNow()
        while (true) {
            emit(scriptedRssi(t0.elapsedNow().inWholeMilliseconds / 1000.0))
            delay(300)
        }
    }

    /** A rich result so the detail page's device-info section has something to render.
     *  The real probe is a GATT connect; here it just pauses long enough to feel like one. */
    override suspend fun probe(deviceId: String): ProbeResult {
        delay(800)
        return ProbeResult(
            connectable = true, name = "Pixel Buds Pro", manufacturer = "Google",
            model = "GA03201", firmware = "4.0.1", hardware = "1.2", batteryPct = 82,
        )
    }
}

class DemoWearMotion : MotionProvider {
    override fun motion(): Flow<MotionSample> = flow {
        val t0 = TimeSource.Monotonic.markNow()
        while (true) {
            val t = t0.elapsedNow().inWholeMilliseconds / 1000.0
            val step = when {
                t < 5.5 -> 0.0           // calibrate + turn in place
                t < 10.5 -> 0.45         // walk toward it
                else -> 0.0              // arrived — stand on it
            }
            emit(
                MotionSample(
                    timeMs = (t * 1000).toLong(),
                    headingRad = scriptedHeading(t),
                    stepDistanceM = step,
                    speedMps = step / 0.3,
                    moving = step > 0.0,
                ),
            )
            delay(300)
        }
    }
}

private fun scriptedRssi(t: Double): Int {
    val base = when {
        t < 2.5 -> -80.0
        t < 3.8 -> -80.0 + (t - 2.5) / 1.3 * 7.0
        t < 5.5 -> -73.0 - (t - 3.8) / 1.7 * 5.0
        t < 11.0 -> -78.0 + (t - 5.5) / 5.5 * 35.0
        else -> -43.0
    }
    return (base + 0.5 * sin(t * 5.1)).toInt()
}

private const val PEAK_HEADING = 2.1

private fun scriptedHeading(t: Double): Double =
    if (t < 5.5) t * 2.2 else PEAK_HEADING + 0.1 * sin(t * 0.9)
