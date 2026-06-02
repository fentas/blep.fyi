package fyi.blep.wear

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Text
import fyi.blep.core.spatial.SpatialSnapshot
import fyi.blep.core.spatial.Vec2
import fyi.blep.core.tracking.TrackingStatus

private val Ink = Color(0xFF27313B)
private val proximityStops = listOf(
    0.00f to Color(0xFF7FA8D4),
    0.40f to Color(0xFF8FD0CB),
    0.70f to Color(0xFFAEDFA6),
    1.00f to Color(0xFFF4D58D),
)

private fun proximityColor(f: Float): Color {
    val x = f.coerceIn(0f, 1f)
    for (i in 0 until proximityStops.lastIndex) {
        val (p0, c0) = proximityStops[i]
        val (p1, c1) = proximityStops[i + 1]
        if (x <= p1) return lerp(c0, c1, if (p1 == p0) 0f else (x - p0) / (p1 - p0))
    }
    return proximityStops.last().second
}

@Composable
fun WearApp(controller: WearController) {
    val tracked = controller.tracking
    if (tracked == null) {
        DiscoveryList(controller)
    } else {
        controller.status?.let {
            TrackingView(tracked.displayName, it, controller.spatial, onCancel = controller::startDiscovery)
        }
    }
}

@Composable
private fun DiscoveryList(controller: WearController) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize().background(Color(0xFF101418)),
    ) {
        item { Text("blep", textAlign = TextAlign.Center, color = Color(0xFFF4F5F0)) }
        items(controller.devices, key = { it.id }) { device ->
            Chip(
                onClick = { controller.track(device) },
                colors = ChipDefaults.primaryChipColors(backgroundColor = Color(0xFF5F90C3)),
                label = { Text(device.displayName) },
                secondaryLabel = if (device.isConnected) ({ Text("Connected") }) else null,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun TrackingView(name: String, status: TrackingStatus, spatial: SpatialSnapshot?, onCancel: () -> Unit) {
    val bg by animateColorAsState(proximityColor(status.proximity), tween(800), label = "wearBg")
    Box(
        modifier = Modifier.fillMaxSize().background(bg).clickable(onClick = onCancel),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Spatial map when motion sensors feed it; otherwise the shape arrow.
            if (spatial != null) WearRadar(spatial) else WearArrow(status.arrow.curl, status.arrow.scale)
            Text(
                status.guidance.title,
                color = Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp),
            )
            Text(
                name,
                color = Ink.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** Compact map-less radar for the watch: trail (signal-coloured), your dot +
 *  heading wedge (green toward / red away from target), and the target glow. */
@Composable
private fun WearRadar(snapshot: SpatialSnapshot) {
    Canvas(modifier = Modifier.size(108.dp)) {
        val cx = size.width / 2f; val cy = size.height / 2f
        val pts = snapshot.path
        val here = snapshot.here
        val target = snapshot.target.position
        var minX = -3.0; var maxX = 3.0; var minY = -3.0; var maxY = 3.0
        fun include(v: Vec2) {
            if (v.x < minX) minX = v.x; if (v.x > maxX) maxX = v.x
            if (v.y < minY) minY = v.y; if (v.y > maxY) maxY = v.y
        }
        pts.forEach { include(it.pos) }; include(here); target?.let(::include)
        val span = max(max(maxX - minX, maxY - minY), 6.0)
        val midX = (minX + maxX) / 2.0; val midY = (minY + maxY) / 2.0
        val scale = (minOf(size.width, size.height) * 0.82f) / span.toFloat()
        fun toScreen(v: Vec2) = Offset(cx + ((v.x - midX) * scale).toFloat(), cy - ((v.y - midY) * scale).toFloat())

        // trail
        for (k in 1 until pts.size) {
            drawLine(
                proximityColor((pts[k - 1].strength01 + pts[k].strength01) / 2f),
                toScreen(pts[k - 1].pos), toScreen(pts[k].pos), strokeWidth = 4f, cap = StrokeCap.Round,
            )
        }
        // start
        drawCircle(Ink.copy(alpha = 0.45f), radius = 4f, center = toScreen(Vec2.ZERO))
        // target glow
        val est = snapshot.target
        if (target != null && est.confidence > 0.05f) {
            val tc = toScreen(target)
            val r = 14f * (0.6f + est.confidence)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFAB1B7).copy(alpha = 0.5f * est.confidence), Color.Transparent), center = tc, radius = r), radius = r, center = tc)
            drawCircle(Color(0xFFFAB1B7), radius = 4f, center = tc)
        }
        // you + heading wedge
        val hp = toScreen(here)
        val wedge = when {
            snapshot.target.bearingRad == null -> Color(0xFF5F90C3)
            snapshot.onCourse > 0.25f -> Color(0xFF8FCB7A)
            snapshot.onCourse < -0.25f -> Color(0xFFE0907F)
            else -> Color(0xFF5F90C3)
        }
        val dir = Offset(sin(snapshot.headingRad).toFloat(), -cos(snapshot.headingRad).toFloat())
        val perp = Offset(-dir.y, dir.x)
        val tip = Offset(hp.x + dir.x * 18f, hp.y + dir.y * 18f)
        val b1 = Offset(hp.x + perp.x * 7f, hp.y + perp.y * 7f)
        val b2 = Offset(hp.x - perp.x * 7f, hp.y - perp.y * 7f)
        drawPath(Path().apply { moveTo(tip.x, tip.y); lineTo(b1.x, b1.y); lineTo(b2.x, b2.y); close() }, wedge)
        drawCircle(Ink, radius = 5f, center = hp)
        drawCircle(Color(0xFFF4F5F0), radius = 2.5f, center = hp)
    }
}

@Composable
private fun WearArrow(curl: Float, scale: Float) {
    val c by animateFloatAsState(curl, tween(600), label = "wearArrowCurl")
    val s by animateFloatAsState(scale, tween(600), label = "wearArrowScale")
    Canvas(modifier = Modifier.size(96.dp)) {
        if (s <= 0.01f) return@Canvas
        val span = minOf(size.width, size.height) * 0.8f * s
        val stroke = (span * 0.09f).coerceAtLeast(2f)

        val arc = abs(c) * 4.2f
        val sign = if (c < 0f) -1f else 1f
        val dTheta = arc * sign / 28
        val ds = 1.95f / 28
        val xs = FloatArray(31); val ys = FloatArray(31)
        var x = 0f; var y = 0.9f; var a = (-PI / 2).toFloat()
        for (k in 1..28) { x += ds * cos(a); y += ds * sin(a); a += dTheta; xs[k] = x; ys[k] = y }
        val back = a + PI.toFloat()
        xs[29] = xs[28] + 0.56f * cos(back + 0.5f); ys[29] = ys[28] + 0.56f * sin(back + 0.5f)
        xs[30] = xs[28] + 0.56f * cos(back - 0.5f); ys[30] = ys[28] + 0.56f * sin(back - 0.5f)
        var minX = xs[0]; var maxX = xs[0]; var minY = ys[0]; var maxY = ys[0]
        for (i in 0..30) { if (xs[i] < minX) minX = xs[i]; if (xs[i] > maxX) maxX = xs[i]; if (ys[i] < minY) minY = ys[i]; if (ys[i] > maxY) maxY = ys[i] }
        val bx = (minX + maxX) / 2f; val by = (minY + maxY) / 2f
        val sc = span / maxOf(maxX - minX, maxY - minY, 0.0001f)
        val ox = size.width / 2f; val oy = size.height / 2f
        fun px(i: Int) = (xs[i] - bx) * sc + ox
        fun py(i: Int) = (ys[i] - by) * sc + oy

        val path = Path().apply {
            moveTo(px(0), py(0))
            for (k in 1..28) lineTo(px(k), py(k))
            moveTo(px(29), py(29)); lineTo(px(28), py(28)); lineTo(px(30), py(30))
        }
        drawPath(path, Ink, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
