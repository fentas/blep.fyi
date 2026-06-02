import SwiftUI
import BlepCore

// MARK: - Brand colour

private let stops: [(Double, Color)] = [
    (0.00, Color(red: 0.498, green: 0.659, blue: 0.831)),
    (0.40, Color(red: 0.561, green: 0.816, blue: 0.796)),
    (0.70, Color(red: 0.682, green: 0.875, blue: 0.651)),
    (1.00, Color(red: 0.957, green: 0.835, blue: 0.553)),
]

func proximityColor(_ f: Float) -> Color {
    let x = Double(max(0, min(1, f)))
    for i in 0..<(stops.count - 1) {
        let (p0, c0) = stops[i]
        let (p1, c1) = stops[i + 1]
        if x <= p1 {
            let t = p1 == p0 ? 0 : (x - p0) / (p1 - p0)
            return c0.blend(to: c1, t: t)
        }
    }
    return stops.last!.1
}

private extension Color {
    func blend(to: Color, t: Double) -> Color {
        // Approximate linear blend in sRGB.
        let a = UIColor(self); let b = UIColor(to)
        var ar: CGFloat = 0, ag: CGFloat = 0, ab: CGFloat = 0, aa: CGFloat = 0
        var br: CGFloat = 0, bg: CGFloat = 0, bb: CGFloat = 0, ba: CGFloat = 0
        a.getRed(&ar, green: &ag, blue: &ab, alpha: &aa)
        b.getRed(&br, green: &bg, blue: &bb, alpha: &ba)
        let f = CGFloat(t)
        return Color(red: ar + (br - ar) * f, green: ag + (bg - ag) * f, blue: ab + (bb - ab) * f)
    }
}

private let ink = Color(red: 0.153, green: 0.192, blue: 0.231)

// MARK: - Arrow

/// A flexible arrow whose body curls to express the instruction. `curl` is the
/// signed bend from the shared engine (0 = straight, ±large = turn / U-turn).
/// `animatableData` lets SwiftUI morph the shape smoothly between poses.
struct CurlArrow: Shape {
    var curl: CGFloat
    var animatableData: CGFloat {
        get { curl }
        set { curl = newValue }
    }

    func path(in rect: CGRect) -> Path {
        let n = 28
        let len: CGFloat = 1.95, maxAngle: CGFloat = 4.2, head: CGFloat = 0.56, spread: CGFloat = 0.5
        let arc = abs(curl) * maxAngle
        let sign: CGFloat = curl < 0 ? -1 : 1
        let dTheta = arc * sign / CGFloat(n)
        let ds = len / CGFloat(n)
        var x: CGFloat = 0, y: CGFloat = 0.9, a: CGFloat = -.pi / 2
        var xs = [x], ys = [y]
        for _ in 0..<n { x += ds * cos(a); y += ds * sin(a); a += dTheta; xs.append(x); ys.append(y) }
        let back = a + .pi
        xs.append(xs[n] + head * cos(back + spread)); ys.append(ys[n] + head * sin(back + spread))
        xs.append(xs[n] + head * cos(back - spread)); ys.append(ys[n] + head * sin(back - spread))

        let minX = xs.min()!, maxX = xs.max()!, minY = ys.min()!, maxY = ys.max()!
        let bx = (minX + maxX) / 2, by = (minY + maxY) / 2
        let span = min(rect.width, rect.height)
        let sc = span / max(maxX - minX, maxY - minY, 0.0001)
        func p(_ i: Int) -> CGPoint { CGPoint(x: (xs[i] - bx) * sc + rect.midX, y: (ys[i] - by) * sc + rect.midY) }

        var path = Path()
        path.move(to: p(0))
        for k in 1...n { path.addLine(to: p(k)) }
        path.move(to: p(n + 1)); path.addLine(to: p(n)); path.addLine(to: p(n + 2))
        return path
    }
}

// MARK: - Screens

struct ContentView: View {
    @StateObject private var ranger = BleRanger()
    @StateObject private var tracking = TrackingModel()
    @State private var trackingName: String?

    var body: some View {
        if let name = trackingName {
            TrackingScreen(name: name, status: tracking.status, spatial: tracking.spatial) {
                ranger.onRssi = nil
                trackingName = nil
                ranger.startScan()
            }
        } else {
            DiscoveryScreen(devices: ranger.devices) { device in
                // Fresh session + wire RSSI before tracking so no samples are lost.
                tracking.reset()
                ranger.onRssi = { tracking.onRssi(Int($0)) }
                ranger.track(device.id)
                trackingName = device.name
            }
        }
    }
}

struct DiscoveryScreen: View {
    let devices: [DiscoveredDevice]
    let onSelect: (DiscoveredDevice) -> Void

    var body: some View {
        List {
            Text("blep").font(.title3.bold())
            ForEach(devices) { device in
                Button(action: { onSelect(device) }) {
                    HStack {
                        Text(device.name)
                        Spacer()
                        Text("\(device.rssi)").foregroundColor(.secondary).font(.caption2)
                    }
                }
            }
        }
    }
}

struct TrackingScreen: View {
    let name: String
    let status: TrackingStatus
    let spatial: SpatialSnapshot?
    let onCancel: () -> Void

    /// Confident spatial distance estimate, formatted, or nil.
    private var distanceText: String? {
        guard let est = spatial?.target, est.confidence >= 0.35,
              let d = est.distanceM?.doubleValue else { return nil }
        return d < 1.5 ? "almost on it" : "~\(Int(d.rounded())) m away"
    }

    var body: some View {
        ZStack {
            proximityColor(status.proximity).ignoresSafeArea()
                .animation(.easeInOut(duration: 0.8), value: status.proximity)
            VStack(spacing: 6) {
                CurlArrow(curl: CGFloat(status.arrow.curl))
                    .stroke(ink, style: StrokeStyle(lineWidth: 7, lineCap: .round, lineJoin: .round))
                    .frame(width: 92, height: 92)
                    .scaleEffect(CGFloat(status.arrow.scale))
                    .animation(.spring(response: 0.5, dampingFraction: 0.7), value: status.arrow.curl)
                    .animation(.easeInOut(duration: 0.6), value: status.arrow.scale)
                Text(status.guidance.title).font(.headline).foregroundColor(ink)
                if let distanceText {
                    Text(distanceText).font(.caption).foregroundColor(ink.opacity(0.75))
                }
                Text(name).font(.caption2).foregroundColor(ink.opacity(0.6))
            }
            .padding()
        }
        .onTapGesture(perform: onCancel)
    }
}
