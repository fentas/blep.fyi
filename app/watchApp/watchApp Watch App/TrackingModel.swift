import Foundation
import BlepCore

/// Wraps the shared Kotlin `TrackingSession` so SwiftUI can observe its output.
final class TrackingModel: ObservableObject {
    @Published var status: TrackingStatus

    private var session: TrackingSession
    private var start = Date()

    init() {
        let s = TrackingSession(tuning: TrackingTuning())
        self.session = s
        self.status = s.status
    }

    /// Start a fresh tracking session (call when a new device is selected).
    func reset() {
        let s = TrackingSession(tuning: TrackingTuning())
        session = s
        start = Date()
        status = s.status
    }

    /// Feed one RSSI sample (called from `BleRanger.onRssi`).
    func onRssi(_ rssi: Int) {
        let ms = Int64(Date().timeIntervalSince(start) * 1000)
        status = session.onSample(rssi: Int32(rssi), timeMs: ms)
    }

    var isComplete: Bool { status.phase == .complete }
}
