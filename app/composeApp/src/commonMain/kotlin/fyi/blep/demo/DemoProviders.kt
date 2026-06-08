package fyi.blep.demo

import fyi.blep.core.ble.BleScanner
import fyi.blep.core.ble.ScanAvailability
import fyi.blep.core.model.BleDevice
import fyi.blep.core.safety.AddressType
import fyi.blep.core.safety.RawAdvert
import fyi.blep.core.spatial.MotionProvider
import fyi.blep.core.spatial.MotionSample
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.PI
import kotlin.math.sin
import kotlin.time.TimeSource

/**
 * Demo data sources: a scripted "hunt" so the UI fills with realistic content
 * without any Bluetooth or motion hardware — used for store screenshots and
 * previews. Enabled with the `demo` launch flag (never in normal use).
 *
 * The timeline (seconds from first collection): ~3 s calibrate in place (heading
 * sweeps to build the field), then walk toward the target as the signal
 * strengthens, slow to a pinpoint, and stop on top of it so it reaches "found".
 */
private const val DEMO_TOTAL = 20.0

private val DEMO_DEVICES = listOf(
    BleDevice(id = "keys", name = "Keys", rssi = -58),
    BleDevice(id = "buds", name = "AirPods Pro", rssi = -71),
    BleDevice(id = "watch", name = "Galaxy Watch", rssi = -64, isPaired = true),
    BleDevice(id = "wallet", name = "Wallet", rssi = -83),
)

class DemoBleScanner : BleScanner {
    override val availability: Flow<ScanAvailability> = flow { emit(ScanAvailability.READY) }

    override fun devices(includeUnnamed: Boolean, measureConnectedSignal: Boolean): Flow<List<BleDevice>> = flow {
        var i = 0
        while (true) {
            // gentle live jitter so the list feels alive
            emit(DEMO_DEVICES.map { it.copy(rssi = it.rssi + ((i + it.id.length) % 3 - 1)) })
            i++
            delay(900)
        }
    }

    override fun rssi(deviceId: String): Flow<Int> = flow {
        val t0 = TimeSource.Monotonic.markNow()
        while (true) {
            val t = t0.elapsedNow().inWholeMilliseconds / 1000.0
            emit(scriptedRssi(t))
            delay(300)
        }
    }

    // Scripted raw adverts for the Safety scan: a separated AirTag shadowing you
    // (rotating its id), a churn of anonymous close devices (the rotation pattern),
    // and a benign far device.
    override fun advertisements(): Flow<RawAdvert> = flow {
        val t0 = TimeSource.Monotonic.markNow()
        var i = 0
        while (true) {
            val ms = t0.elapsedNow().inWholeMilliseconds
            emit(RawAdvert("airtag-${ms / 30_000}", rssi = -57 + (i % 3 - 1), timeMs = ms,
                addressType = AddressType.RANDOM, manufacturerData = mapOf(0x004C to byteArrayOf(0x12, 0x19, 0x00))))
            emit(RawAdvert("anon-${ms / 18_000}", rssi = -66, timeMs = ms, addressType = AddressType.RANDOM))
            emit(RawAdvert("speaker", rssi = -84, timeMs = ms, addressType = AddressType.PUBLIC))
            i++
            delay(600)
        }
    }
}

class DemoMotionProvider : MotionProvider {
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

/**
 * Signal over the hunt: a flat baseline, then during the sweep it rises (you turn
 * toward it) and dips (you turn past) — the rise-then-dip is what lets the phase
 * machine lock the bearing — then climbs steadily as you walk in and holds strong.
 */
private fun scriptedRssi(t: Double): Int {
    val base = when {
        t < 2.5 -> -80.0                                  // calibrate
        t < 3.8 -> -80.0 + (t - 2.5) / 1.3 * 7.0          // sweep: rise -80 → -73 (facing it)
        t < 5.5 -> -73.0 - (t - 3.8) / 1.7 * 5.0          // sweep: dip -73 → -78 (turned past → lock)
        t < 12.0 -> -78.0 + (t - 5.5) / 6.5 * 42.0        // walk in: -78 → -36 (turn cues, then closing)
        else -> -36.0                                     // on you: point-blank, "it's right here"
    }
    return (base + 0.5 * sin(t * 5.1)).toInt()
}

/** Rotate on the spot through the sweep (so a real turn is detected), then walk
 *  the heading the signal peaked at — so guidance reads "facing the signal"
 *  (on-course green) rather than a big correction. */
private const val PEAK_HEADING = 2.1 // ≈ heading when RSSI peaks mid-sweep (t≈3.8 · 2.2, mod 2π)

private fun scriptedHeading(t: Double): Double =
    if (t < 5.5) t * 2.2                          // keep turning through the sweep
    else PEAK_HEADING + 0.1 * sin(t * 0.9)        // walk toward where it peaked
