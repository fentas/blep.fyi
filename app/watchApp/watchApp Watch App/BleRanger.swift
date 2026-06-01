import CoreBluetooth
import Foundation

struct DiscoveredDevice: Identifiable {
    let id: String
    let name: String
    let rssi: Int
    let isConnected: Bool
}

/// CoreBluetooth scanning for watchOS. Does the radio work in Swift and forwards
/// raw RSSI of the tracked device to a callback; the shared Kotlin
/// `TrackingSession` turns those into guidance.
final class BleRanger: NSObject, ObservableObject, CBCentralManagerDelegate {
    @Published var devices: [DiscoveredDevice] = []
    @Published var poweredOn = false

    private var manager: CBCentralManager!
    private var seen: [String: DiscoveredDevice] = [:]
    private var trackingId: String?

    /// Called for every advertisement of the currently tracked device.
    var onRssi: ((Int) -> Void)?

    override init() {
        super.init()
        manager = CBCentralManager(delegate: self, queue: nil)
    }

    func startScan() {
        trackingId = nil
        seen.removeAll()
        devices = []
        if manager.state == .poweredOn {
            manager.scanForPeripherals(
                withServices: nil,
                options: [CBCentralManagerScanOptionAllowDuplicatesKey: true]
            )
        }
    }

    func track(_ id: String) { trackingId = id }

    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        poweredOn = central.state == .poweredOn
        if poweredOn { startScan() }
    }

    func centralManager(
        _ central: CBCentralManager,
        didDiscover peripheral: CBPeripheral,
        advertisementData: [String: Any],
        rssi RSSI: NSNumber
    ) {
        let id = peripheral.identifier.uuidString
        let name = peripheral.name
            ?? (advertisementData[CBAdvertisementDataLocalNameKey] as? String)
            ?? ""
        let rssi = RSSI.intValue

        if id == trackingId { onRssi?(rssi) }

        guard !name.isEmpty else { return } // hide unnamed by default
        seen[id] = DiscoveredDevice(id: id, name: name, rssi: rssi, isConnected: false)
        devices = seen.values.sorted { $0.rssi > $1.rssi }
    }
}
