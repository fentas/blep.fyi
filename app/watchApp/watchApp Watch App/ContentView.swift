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

struct ChevronArrow: Shape {
    func path(in rect: CGRect) -> Path {
        let cx = rect.midX, cy = rect.midY
        let unit = min(rect.width, rect.height) / 2
        var p = Path()
        p.move(to: CGPoint(x: cx, y: cy + unit * 0.85))
        p.addLine(to: CGPoint(x: cx, y: cy - unit * 0.55))
        p.move(to: CGPoint(x: cx - unit * 0.6, y: cy - unit * 0.15))
        p.addLine(to: CGPoint(x: cx, y: cy - unit * 0.85))
        p.addLine(to: CGPoint(x: cx + unit * 0.6, y: cy - unit * 0.15))
        return p
    }
}

// MARK: - Screens

struct ContentView: View {
    @StateObject private var ranger = BleRanger()
    @StateObject private var tracking = TrackingModel()
    @State private var trackingName: String?

    var body: some View {
        if let name = trackingName {
            TrackingScreen(name: name, status: tracking.status) {
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
    let onCancel: () -> Void

    var body: some View {
        ZStack {
            proximityColor(status.proximity).ignoresSafeArea()
                .animation(.easeInOut(duration: 0.8), value: status.proximity)
            VStack(spacing: 6) {
                ChevronArrow()
                    .stroke(ink, style: StrokeStyle(lineWidth: 8, lineCap: .round, lineJoin: .round))
                    .frame(width: 88, height: 88)
                    .scaleEffect(CGFloat(status.arrow.scale))
                    .rotationEffect(.degrees(Double(status.arrow.rotationDeg)))
                    .animation(.easeInOut(duration: 0.6), value: status.arrow.rotationDeg)
                    .animation(.easeInOut(duration: 0.6), value: status.arrow.scale)
                Text(status.guidance.title).font(.headline).foregroundColor(ink)
                Text(name).font(.caption2).foregroundColor(ink.opacity(0.6))
            }
            .padding()
        }
        .onTapGesture(perform: onCancel)
    }
}
