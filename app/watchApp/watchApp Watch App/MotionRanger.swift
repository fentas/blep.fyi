import CoreLocation
import CoreMotion
import Foundation

/// Gathers watchOS motion + position natively and exposes the latest values as
/// plain Swift primitives for the shared Kotlin `SpatialTracker.updateGeo(...)`.
///
/// Heading comes from CoreMotion device-motion attitude taken in the magnetic-
/// north reference frame (when the watch has a magnetometer), so `-yaw` is a
/// compass heading; otherwise `hasHeading` stays false and the track is GPS-only.
final class MotionRanger: NSObject, CLLocationManagerDelegate {
    private let motion = CMMotionManager()
    private let location = CLLocationManager()

    private(set) var headingRad: Double = 0
    private(set) var hasHeading = false
    private(set) var lat: Double = 0
    private(set) var lon: Double = 0
    private(set) var hasFix = false
    private(set) var accuracyM: Double = -1
    private(set) var speedMps: Double = 0
    private(set) var moving = false
    private(set) var reorienting = false

    override init() {
        super.init()
        location.delegate = self
        location.desiredAccuracy = kCLLocationAccuracyBest
    }

    func start() {
        location.requestWhenInUseAuthorization()
        location.startUpdatingLocation()
        guard motion.isDeviceMotionAvailable else { return }
        motion.deviceMotionUpdateInterval = 0.1
        if CMMotionManager.availableAttitudeReferenceFrames().contains(.xMagneticNorthZVertical) {
            hasHeading = true
            motion.startDeviceMotionUpdates(using: .xMagneticNorthZVertical, to: .main) { [weak self] dm, _ in
                self?.consume(dm)
            }
        } else {
            motion.startDeviceMotionUpdates(to: .main) { [weak self] dm, _ in
                self?.consume(dm)
            }
        }
    }

    func stop() {
        location.stopUpdatingLocation()
        motion.stopDeviceMotionUpdates()
    }

    private func consume(_ dm: CMDeviceMotion?) {
        guard let dm = dm else { return }
        let ua = dm.userAcceleration
        moving = sqrt(ua.x * ua.x + ua.y * ua.y + ua.z * ua.z) > 0.08
        let rr = dm.rotationRate
        reorienting = sqrt(rr.x * rr.x + rr.y * rr.y + rr.z * rr.z) > 1.2
        if hasHeading { headingRad = -dm.attitude.yaw }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let loc = locations.last else { return }
        lat = loc.coordinate.latitude
        lon = loc.coordinate.longitude
        hasFix = true
        accuracyM = loc.horizontalAccuracy
        speedMps = loc.speed >= 0 ? loc.speed : 0
    }
}
