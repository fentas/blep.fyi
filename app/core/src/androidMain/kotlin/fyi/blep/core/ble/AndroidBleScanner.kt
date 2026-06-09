package fyi.blep.core.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import fyi.blep.core.model.BleDevice
import fyi.blep.core.safety.AddressType
import fyi.blep.core.safety.RawAdvert
import fyi.blep.core.safety.shortServiceUuid
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.coroutines.resume

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
                    !hasScanPermission() -> ScanAvailability.PERMISSION_REQUIRED
                    !a.isEnabled -> ScanAvailability.BLUETOOTH_OFF
                    else -> ScanAvailability.READY
                },
            )
            delay(1500)
        }
    }

    /** True once the user has granted the runtime scan permission (BLUETOOTH_SCAN on
     *  Android 12+, else legacy ACCESS_FINE_LOCATION). Drives PERMISSION_REQUIRED so
     *  the banner reflects the real grant state, not a string-matched exception. */
    private fun hasScanPermission(): Boolean {
        val ctx = context ?: return true
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            Manifest.permission.BLUETOOTH_SCAN else Manifest.permission.ACCESS_FINE_LOCATION
        return ctx.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    override fun devices(includeUnnamed: Boolean, measureConnectedSignal: Boolean): Flow<List<BleDevice>> = channelFlow {
        val adapter = adapter ?: throw IllegalStateException("Bluetooth unavailable")
        val scanner = adapter.bluetoothLeScanner ?: throw IllegalStateException("Bluetooth permission/adapter")
        val table = DeviceTable()

        // Optional: range *connected* devices (which don't advertise) by holding a
        // GATT connection and polling readRemoteRssi. One connection per connected
        // device, started/stopped from the seed ticker; all closed on cancel.
        val rssiPolls = mutableMapOf<String, BluetoothGatt>()
        fun startRssiPoll(address: String) {
            val ctx = context ?: return
            if (address in rssiPolls) return
            val device = runCatching { adapter.getRemoteDevice(address) }.getOrNull() ?: return
            var poller: Job? = null
            val cb = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        poller = launch { while (isActive) { runCatching { g.readRemoteRssi() }; delay(2000) } }
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        poller?.cancel()
                    }
                }
                override fun onReadRemoteRssi(g: BluetoothGatt, rssi: Int, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        table.upsert(
                            id = address, name = runCatching { device.name }.getOrNull(),
                            rssi = rssi, isConnected = true, seenAtMs = now(), isPaired = true,
                        )
                        trySend(table.snapshot(includeUnnamed))
                    }
                }
            }
            rssiPolls[address] = device.connectGatt(ctx, /* autoConnect = */ false, cb, BluetoothDevice.TRANSPORT_LE)
        }
        fun stopRssiPoll(address: String) {
            rssiPolls.remove(address)?.let { runCatching { it.disconnect() }; runCatching { it.close() } }
        }

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
                if (measureConnectedSignal) {
                    connected.forEach { startRssiPoll(it) }                       // range newly-connected
                    rssiPolls.keys.toList().forEach { if (it !in connected) stopRssiPoll(it) } // drop gone
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
            rssiPolls.values.forEach { runCatching { it.disconnect() }; runCatching { it.close() } }
            rssiPolls.clear()
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

    /**
     * One-shot GATT interrogation: connect, discover, read the GAP name + Device
     * Information Service, disconnect. Sequential reads (GATT allows one at a time).
     * Bounded by [PROBE_TIMEOUT_MS]; any failure/refusal resolves to a non-connectable
     * result. We never bond, so anything that needs pairing simply reads back null.
     */
    @SuppressLint("MissingPermission")
    override suspend fun probe(deviceId: String): ProbeResult {
        val ctx = context ?: return ProbeResult(connectable = false)
        val device = adapter?.let { runCatching { it.getRemoteDevice(deviceId) }.getOrNull() }
            ?: return ProbeResult(connectable = false)
        var gatt: BluetoothGatt? = null
        val result = withTimeoutOrNull(PROBE_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val fields = HashMap<String, String>()        // char-uuid (16-bit) → text value
                var services: List<String> = emptyList()
                var structure: String? = null
                var serviceCount = 0
                var battery: Int? = null
                var needsPairing = false
                val toRead = ArrayDeque<BluetoothGattCharacteristic>()

                fun finish(connectable: Boolean) {
                    if (!cont.isActive) return
                    cont.resume(
                        ProbeResult(
                            connectable = connectable,
                            name = fields[CHAR_GAP_NAME] ?: runCatching { device.name }.getOrNull()?.takeIf { it.isNotBlank() },
                            manufacturer = fields[CHAR_DIS_MANUFACTURER],
                            model = fields[CHAR_DIS_MODEL],
                            firmware = fields[CHAR_DIS_FIRMWARE],
                            hardware = fields[CHAR_DIS_HARDWARE],
                            serial = fields[CHAR_DIS_SERIAL],
                            serviceUuids = services,
                            structure = structure,
                            serviceCount = serviceCount,
                            batteryPct = battery,
                            needsPairing = needsPairing,
                        ),
                    )
                }

                val cb = object : BluetoothGattCallback() {
                    fun readNext(g: BluetoothGatt) {
                        val c = toRead.removeFirstOrNull() ?: return finish(connectable = true)
                        if (!runCatching { g.readCharacteristic(c) }.getOrDefault(false)) readNext(g) // skip unreadable
                    }

                    override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                        when (newState) {
                            BluetoothProfile.STATE_CONNECTED -> if (!runCatching { g.discoverServices() }.getOrDefault(false)) finish(true)
                            BluetoothProfile.STATE_DISCONNECTED -> finish(connectable = false) // refused / dropped before we read
                        }
                    }

                    override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                        // The GATT skeleton — services (incl. 128-bit custom) + each
                        // characteristic's property bitmask — is a model-level fingerprint
                        // that never changes with the MAC. Hash it.
                        val svcs = g.services.orEmpty()
                        serviceCount = svcs.size
                        services = svcs.map { shortServiceUuid(it.uuid.toString()) }
                        structure = structureFingerprint(svcs)
                        for ((svc, ch) in WANTED_CHARS) {
                            runCatching { g.getService(uuid16(svc))?.getCharacteristic(uuid16(ch)) }.getOrNull()?.let { toRead.addLast(it) }
                        }
                        readNext(g)
                    }

                    @Suppress("DEPRECATION") // 3-arg form works across API levels; .value is fine
                    override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
                        val key = c.uuid.toString().substring(4, 8)
                        when (status) {
                            BluetoothGatt.GATT_SUCCESS -> {
                                val bytes = c.value
                                if (key == CHAR_BATTERY) {
                                    battery = bytes?.takeIf { it.isNotEmpty() }?.let { it[0].toInt() and 0xFF }
                                } else {
                                    bytes?.toString(Charsets.UTF_8)?.trim { ch -> ch <= ' ' }?.takeIf { it.isNotEmpty() }
                                        ?.let { fields[key] = it }
                                }
                            }
                            // The *way* it refuses is itself a fingerprint: a protected read
                            // demanding pairing means a locked-down (often higher-value) device.
                            GATT_INSUFFICIENT_AUTHENTICATION, GATT_INSUFFICIENT_ENCRYPTION -> needsPairing = true
                        }
                        readNext(g)
                    }
                }
                gatt = device.connectGatt(ctx, /* autoConnect = */ false, cb, BluetoothDevice.TRANSPORT_LE)
                if (gatt == null) finish(connectable = false)
                cont.invokeOnCancellation { runCatching { gatt?.close() } }
            }
        }
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        return result ?: ProbeResult(connectable = false)
    }
}

/** Builds a 16-bit Bluetooth SIG UUID into its full 128-bit form. */
private fun uuid16(short: String): UUID = UUID.fromString("0000$short-0000-1000-8000-00805f9b34fb")

/** A deterministic hash of the GATT skeleton: every service (sorted) and, under it,
 *  every characteristic's UUID + property bitmask (sorted). Same model ⇒ same hash,
 *  across every MAC rotation; a fundamentally different device ⇒ different hash. */
private fun structureFingerprint(services: List<android.bluetooth.BluetoothGattService>): String? {
    if (services.isEmpty()) return null
    val sb = StringBuilder()
    for (svc in services.sortedBy { it.uuid.toString() }) {
        sb.append(svc.uuid.toString()).append('{')
        for (c in svc.characteristics.sortedBy { it.uuid.toString() }) {
            sb.append(c.uuid.toString()).append(':').append(c.properties).append(',')
        }
        sb.append('}')
    }
    var h = 1125899906842597L
    for (ch in sb) h = 31 * h + ch.code
    return h.toString(36)
}

private const val PROBE_TIMEOUT_MS = 12_000L
private const val GATT_INSUFFICIENT_AUTHENTICATION = 5
private const val GATT_INSUFFICIENT_ENCRYPTION = 15
private const val CHAR_GAP_NAME = "2a00"
private const val CHAR_DIS_MANUFACTURER = "2a29"
private const val CHAR_DIS_MODEL = "2a24"
private const val CHAR_DIS_FIRMWARE = "2a26"
private const val CHAR_DIS_HARDWARE = "2a27"
private const val CHAR_DIS_SERIAL = "2a25"
private const val CHAR_BATTERY = "2a19"
// (service, characteristic) pairs to read: GAP name, Device Information Service, battery.
private val WANTED_CHARS = listOf(
    "1800" to CHAR_GAP_NAME,
    "180a" to CHAR_DIS_MANUFACTURER,
    "180a" to CHAR_DIS_MODEL,
    "180a" to CHAR_DIS_FIRMWARE,
    "180a" to CHAR_DIS_HARDWARE,
    "180a" to CHAR_DIS_SERIAL,
    "180f" to CHAR_BATTERY,
)

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
