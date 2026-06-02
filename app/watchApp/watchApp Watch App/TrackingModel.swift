import Foundation
import BlepCore

/// Wraps the shared Kotlin `TrackingSession` (RSSI guidance) and `SpatialTracker`
/// (sensor-fused path + target estimate) so SwiftUI can observe their output.
final class TrackingModel: ObservableObject {
    @Published var status: TrackingStatus
    @Published var spatial: SpatialSnapshot?

    private var session: TrackingSession
    private let spatialTracker = SpatialTracker(tuning: SpatialTuning())
    private let motion = MotionRanger()
    private var start = Date()

    init() {
        let s = TrackingSession(tuning: TrackingTuning())
        self.session = s
        self.status = s.status
        motion.start()
    }

    /// Start a fresh tracking session (call when a new device is selected).
    func reset() {
        let s = TrackingSession(tuning: TrackingTuning())
        session = s
        start = Date()
        status = s.status
        spatialTracker.reset()
        spatial = nil
    }

    /// Feed one RSSI sample (called from `BleRanger.onRssi`).
    func onRssi(_ rssi: Int) {
        let ms = Int64(Date().timeIntervalSince(start) * 1000)
        status = session.onSample(rssi: Int32(rssi), timeMs: ms)
        spatial = spatialTracker.updateGeo(
            rssi: Double(rssi),
            timeMs: ms,
            headingRad: motion.headingRad,
            hasHeading: motion.hasHeading,
            lat: motion.lat,
            lon: motion.lon,
            hasFix: motion.hasFix,
            positionAccuracyM: motion.accuracyM,
            speedMps: motion.speedMps,
            moving: motion.moving,
            reorienting: motion.reorienting,
            relativeAltitudeM: motion.relativeAltitude
        )
    }

    var isComplete: Bool { status.phase == .complete }
}
