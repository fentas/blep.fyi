package fyi.blep.core.ble

import com.juul.kable.Bluetooth
import com.juul.kable.Scanner
import fyi.blep.core.model.BleDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlin.time.TimeSource

/**
 * [BleScanner] backed by Kable, shared by Android and all Apple targets
 * (iOS + watchOS) — both use the same advertisement scanning API.
 *
 * Targets the Kable 0.35 API. Fine-grained permission/adapter prompting is
 * handled by each app shell; here availability collapses to ready/not-ready.
 *
 * Note: only [com.juul.kable.Advertisement.name] is read here because it is the
 * one name field common to every platform — `peripheralName` is Apple-only and
 * must not appear in this shared source set.
 */
internal class KableScanner(
    /** Devices unseen for this long are dropped from the discovery list. */
    private val staleAfterMs: Long = 10_000,
) : BleScanner {

    private val scanner = Scanner { }

    override val availability: Flow<ScanAvailability> =
        Bluetooth.availability.map { state ->
            if (state is Bluetooth.Availability.Available) ScanAvailability.READY
            else ScanAvailability.BLUETOOTH_OFF
        }

    override fun devices(includeUnnamed: Boolean): Flow<List<BleDevice>> = flow {
        val table = DeviceTable()
        val clock = TimeSource.Monotonic.markNow()
        scanner.advertisements.collect { adv ->
            val now = clock.elapsedNow().inWholeMilliseconds
            table.upsert(
                id = adv.identifier.toString(),
                name = adv.name,
                rssi = adv.rssi,
                // Scan results don't expose OS connection state; the app shell
                // overlays that from platform connection APIs where available.
                isConnected = false,
                seenAtMs = now,
            )
            table.prune(nowMs = now, ttlMs = staleAfterMs)
            emit(table.snapshot(includeUnnamed))
        }
    }

    // No distinctUntilChanged: the tracking state machine is sample-driven and
    // needs a fresh reading per advertisement, even when the value is unchanged.
    override fun rssi(deviceId: String): Flow<Int> =
        scanner.advertisements
            .mapNotNull { adv -> if (adv.identifier.toString() == deviceId) adv.rssi else null }
}

actual fun createBleScanner(): BleScanner = KableScanner()
