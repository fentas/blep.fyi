package fyi.blep.core.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.SystemClock
import fyi.blep.core.model.BleDevice
import fyi.blep.core.safety.AddressType
import fyi.blep.core.safety.RawAdvert
import fyi.blep.core.safety.shortServiceUuid
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Raw-Android BLE scanner. Chosen over the shared Kable path on Android so we
 * can (a) scan at [ScanSettings.SCAN_MODE_LOW_LATENCY] for fast RSSI updates,
 * (b) surface bonded/connected devices that don't advertise (e.g. a watch),
 * and (c) range those via a GATT `readRemoteRssi` poll.
 *
 * BLE permissions are requested by the app shell; calls here throw if missing,
 * which the controller catches and surfaces as a banner.
 */
internal class AndroidBleScanner : BleScanner {

    private val context: Context? get() = BlepContext.app
    private val manager: BluetoothManager?
        get() = context?.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? get() = manager?.adapter

    private fun now() = SystemClock.elapsedRealtime()

    override val availability: Flow<ScanAvailability> = flow {
        while (true) {
            val a = adapter
            emit(
                when {
                    a == null -> ScanAvailability.UNSUPPORTED
                    !a.isEnabled -> ScanAvailability.BLUETOOTH_OFF
                    else -> ScanAvailability.READY
                },
            )
            delay(1500)
        }
    }

    @SuppressLint("MissingPermission")
    override fun devices(includeUnnamed: Boolean): Flow<List<BleDevice>> = channelFlow {
        val adapter = adapter ?: throw IllegalStateException("Bluetooth unavailable")
        val scanner = adapter.bluetoothLeScanner ?: throw IllegalStateException("Bluetooth permission/adapter")
        val table = DeviceTable()

        // Bonded/connected devices don't advertise — seed them in on a ticker.
        val seed = launch {
            while (isActive) {
                val connected = runCatching { manager?.getConnectedDevices(BluetoothProfile.GATT) }
                    .getOrNull().orEmpty().map { it.address }.toSet()
                runCatching { adapter.bondedDevices }.getOrNull().orEmpty().forEach { d ->
                    table.upsert(
                        id = d.address, name = d.name, rssi = BleDevice.RSSI_UNKNOWN,
                        isConnected = d.address in connected, seenAtMs = now(), isPaired = true,
                    )
                }
                table.prune(nowMs = now(), ttlMs = 12_000)
                trySend(table.snapshot(includeUnnamed))
                delay(1500)
            }
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val d = result.device
                table.upsert(
                    id = d.address,
                    name = result.scanRecord?.deviceName ?: runCatching { d.name }.getOrNull(),
                    rssi = result.rssi,
                    isConnected = false,
                    seenAtMs = now(),
                    isPaired = runCatching { d.bondState == BluetoothDevice.BOND_BONDED }.getOrDefault(false),
                )
                trySend(table.snapshot(includeUnnamed))
            }

            override fun onScanFailed(errorCode: Int) {
                close(IllegalStateException("BLE scan failed: $errorCode"))
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()
        scanner.startScan(emptyList<ScanFilter>(), settings, callback)
        awaitClose {
            runCatching { scanner.stopScan(callback) }
            seed.cancel()
        }
    }

    @SuppressLint("MissingPermission")
    override fun rssi(deviceId: String): Flow<Int> {
        val adapter = adapter ?: return flow { throw IllegalStateException("Bluetooth unavailable") }
        val bonded = runCatching { adapter.bondedDevices?.any { it.address == deviceId } == true }.getOrDefault(false)
        return if (bonded) gattRssi(deviceId) else advertisementRssi(deviceId)
    }

    /** Fast advertisement RSSI for a single device (low-latency, no batching). */
    @SuppressLint("MissingPermission")
    private fun advertisementRssi(deviceId: String): Flow<Int> = callbackFlow {
        val scanner = adapter?.bluetoothLeScanner ?: throw IllegalStateException("Bluetooth permission/adapter")
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (result.device.address == deviceId) trySend(result.rssi)
            }
            override fun onScanFailed(errorCode: Int) {
                close(IllegalStateException("BLE scan failed: $errorCode"))
            }
        }
        val filters = listOf(ScanFilter.Builder().setDeviceAddress(deviceId).build())
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()
        scanner.startScan(filters, settings, callback)
        awaitClose { runCatching { scanner.stopScan(callback) } }
    }

    /**
     * Raw advertisements for the safety scan — every nearby device, unfiltered, with
     * the manufacturer data / service UUIDs / address-type the tracker classifier needs.
     * Runs on its own (the safety screen isn't scanning for a pointer target at the
     * same time), so it can take the whole low-latency radio for itself.
     */
    @SuppressLint("MissingPermission")
    override fun advertisements(): Flow<RawAdvert> = callbackFlow {
        val scanner = adapter?.bluetoothLeScanner ?: throw IllegalStateException("Bluetooth permission/adapter")
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                trySend(result.toRawAdvert(now()))
            }
            override fun onScanFailed(errorCode: Int) {
                close(IllegalStateException("BLE scan failed: $errorCode"))
            }
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()
        scanner.startScan(emptyList<ScanFilter>(), settings, callback)
        awaitClose { runCatching { scanner.stopScan(callback) } }
    }

    /** RSSI for a connected/bonded device via our own GATT connection + polling. */
    @SuppressLint("MissingPermission")
    private fun gattRssi(deviceId: String): Flow<Int> = callbackFlow {
        val ctx = context ?: throw IllegalStateException("No context")
        val device = adapter?.getRemoteDevice(deviceId) ?: throw IllegalStateException("Unknown device")
        var poller: kotlinx.coroutines.Job? = null
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    poller = launch {
                        while (isActive) {
                            runCatching { gatt.readRemoteRssi() }
                            delay(350)
                        }
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    poller?.cancel()
                }
            }

            override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) trySend(rssi)
            }
        }
        val gatt = device.connectGatt(ctx, /* autoConnect = */ false, callback, BluetoothDevice.TRANSPORT_LE)
        awaitClose {
            poller?.cancel()
            runCatching { gatt.disconnect() }
            runCatching { gatt.close() }
        }
    }
}

actual fun createBleScanner(): BleScanner = AndroidBleScanner()

/** Maps a raw [ScanResult] into the platform-neutral [RawAdvert] the classifier reads. */
@SuppressLint("MissingPermission")
private fun ScanResult.toRawAdvert(timeMs: Long): RawAdvert {
    val record = scanRecord
    val mfg = HashMap<Int, ByteArray>()
    record?.manufacturerSpecificData?.let { sa ->
        for (i in 0 until sa.size()) mfg[sa.keyAt(i)] = sa.valueAt(i)
    }
    val uuids = record?.serviceUuids?.map { shortServiceUuid(it.uuid.toString()) }.orEmpty()
    return RawAdvert(
        address = device.address,
        rssi = rssi,
        timeMs = timeMs,
        addressType = addressTypeOf(device.address),
        serviceUuids = uuids,
        manufacturerData = mfg,
    )
}

/**
 * Best-effort BLE address-type from the two most-significant bits of the address —
 * the standard random-address scheme. We only need to know whether it's a *rotating
 * privacy* address (resolvable/non-resolvable private), which is the rotation tell;
 * IEEE-assigned public addresses don't follow this scheme, so they fall through to
 * PUBLIC. (Some OEMs hide the real type; validate against a real tracker on-device.)
 */
private fun addressTypeOf(address: String): AddressType {
    val msb = address.substringBefore(':').toIntOrNull(16) ?: return AddressType.UNKNOWN
    return when (msb and 0xC0) {
        0x40, 0x00, 0xC0 -> AddressType.RANDOM // resolvable / non-resolvable / static random
        else -> AddressType.PUBLIC
    }
}
