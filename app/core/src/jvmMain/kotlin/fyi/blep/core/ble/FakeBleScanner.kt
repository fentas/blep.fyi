package fyi.blep.core.ble

import fyi.blep.core.model.BleDevice
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlin.math.sin

/**
 * Deterministic, dependency-free [BleScanner] for the JVM target.
 *
 * Used by unit tests and any desktop/preview harness — no real radio. It serves
 * a small fixed device list and synthesizes an RSSI curve that rises then falls,
 * which is enough to drive the tracking flow end-to-end in a demo.
 */
class FakeBleScanner : BleScanner {

    private val catalog = listOf(
        BleDevice(id = "AA:BB:CC:DD:EE:01", name = "Keys Tag", rssi = -78, isConnected = false),
        BleDevice(id = "AA:BB:CC:DD:EE:02", name = "Earbuds", rssi = -61, isConnected = true),
        BleDevice(id = "AA:BB:CC:DD:EE:03", name = null, rssi = -88, isConnected = false),
    )

    override val availability: Flow<ScanAvailability> = flowOf(ScanAvailability.READY)

    override fun devices(includeUnnamed: Boolean): Flow<List<BleDevice>> = flow {
        val table = DeviceTable()
        catalog.forEach { table.upsert(it.id, it.name, it.rssi, it.isConnected) }
        emit(table.snapshot(includeUnnamed))
    }

    override fun rssi(deviceId: String): Flow<Int> = flow {
        var t = 0.0
        while (true) {
            // Sweep from far (-90) toward near (-48) and back, with light noise.
            val base = -90 + (42 * (0.5 + 0.5 * sin(t)))
            val noise = ((t * 53).toInt() % 5) - 2
            emit((base + noise).toInt())
            t += 0.15
            delay(250)
        }
    }
}

actual fun createBleScanner(): BleScanner = FakeBleScanner()
