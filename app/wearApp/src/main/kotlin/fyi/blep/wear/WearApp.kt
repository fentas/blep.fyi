package fyi.blep.wear

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.CurvedDirection
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.foundation.CurvedTextStyle
import androidx.wear.compose.material.curvedText
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Switch
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.TimeTextDefaults
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.wear.compose.material.scrollAway
import fyi.blep.core.spatial.CueKind
import fyi.blep.core.spatial.GuidanceLine
import fyi.blep.core.spatial.SpatialSnapshot
import fyi.blep.core.spatial.Vec2
import fyi.blep.core.tracking.Guidance
import fyi.blep.core.tracking.GuidanceCue
import fyi.blep.core.tracking.TrackingStatus

private val Ink = Color(0xFF27313B)
// At/above this raw RSSI you're on top of it — show "it's right here" + the dB
// instead of the (then unreliable) spatial distance. Mirrors the phone.
private const val POINT_BLANK_DBM = -50
private val proximityStops = listOf(
    0.00f to Color(0xFF7FA8D4),
    0.40f to Color(0xFF8FD0CB),
    0.70f to Color(0xFFAEDFA6),
    1.00f to Color(0xFFF4D58D),
)

// ── Localized formatters for the core-generated guidance (mirror the phone's
//    TrackingScreen, but against the watch's Android string resources) ─────────

@Composable
private fun phaseTitle(g: Guidance): String = cueTitle(g.cue)?.let { stringResource(it) } ?: g.title

private fun cueTitle(cue: GuidanceCue): Int? = when (cue) {
    GuidanceCue.NONE -> null
    GuidanceCue.CALIBRATE -> R.string.cue_calibrate_title
    GuidanceCue.SWEEP_START -> R.string.cue_sweep_start_title
    GuidanceCue.SWEEP_WARMER -> R.string.cue_sweep_warmer_title
    GuidanceCue.SWEEP_COLDER -> R.string.cue_sweep_colder_title
    GuidanceCue.SWEEP_FLAT -> R.string.cue_sweep_flat_title
    GuidanceCue.WALK_WARMER -> R.string.cue_walk_warmer_title
    GuidanceCue.WALK_COLDER -> R.string.cue_walk_colder_title
    GuidanceCue.WALK_FLAT -> R.string.cue_walk_flat_title
    GuidanceCue.WALK_OVERSHOOT -> R.string.cue_walk_overshoot_title
    GuidanceCue.WALK_FOUND -> R.string.cue_walk_found_title
    GuidanceCue.REORIENT -> R.string.cue_reorient_title
    GuidanceCue.PINPOINT -> R.string.cue_pinpoint_title
    GuidanceCue.PINPOINT_LOST -> R.string.cue_pinpoint_lost_title
    GuidanceCue.COMPLETE -> R.string.cue_complete_title
}

@Composable
private fun guidanceLineText(line: GuidanceLine): String {
    val turn = turnText(line.ahead, line.turnDeg)
    return when (line.kind) {
        CueKind.SIGNAL ->
            if (line.ahead) stringResource(R.string.line_facing_signal) else stringResource(R.string.line_turn_to_signal, turn)
        CueKind.RECOVER ->
            if (line.ahead) stringResource(R.string.line_ahead_warmer) else stringResource(R.string.line_turn_warmer, turn)
        CueKind.TARGET -> stringResource(R.string.line_target, turn, distanceWord(line.distanceM ?: 0.0))
    }
}

@Composable
private fun turnText(ahead: Boolean, turnDeg: Int): String = when {
    ahead -> stringResource(R.string.turn_ahead)
    turnDeg > 0 -> stringResource(R.string.turn_right, turnDeg)
    else -> stringResource(R.string.turn_left, -turnDeg)
}

@Composable
private fun distanceWord(m: Double): String =
    if (m < 1.5) stringResource(R.string.line_distance_almost) else stringResource(R.string.line_distance_m, m.roundToInt())

/** Coarse fallback distance label (when there's no turn-by-turn line yet). */
@Composable
private fun wearDistanceLabel(meters: Double): String =
    if (meters < 1.5) stringResource(R.string.distance_almost_on_it) else stringResource(R.string.distance_away, meters.roundToInt())

@Composable
private fun floorHint(delta: Int): String? = when {
    delta > 0 -> stringResource(if (delta == 1) R.string.floor_up_one else R.string.floor_up_many, delta)
    delta < 0 -> stringResource(if (delta == -1) R.string.floor_down_one else R.string.floor_down_many, -delta)
    else -> null
}

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
fun WearApp(controller: WearController) = BlepWearTheme {
    var showSettings by remember { mutableStateOf(false) }
    val tracked = controller.tracking
    when {
        showSettings -> WearSettings(onBack = { showSettings = false })
        tracked == null -> DiscoveryList(controller, onSettings = { showSettings = true })
        else -> controller.status?.let {
            TrackingView(
                tracked.displayName, it, controller.spatial, controller.guidance,
                rssi = controller.lastRssi, signalLost = controller.signalLost,
                onCancel = controller::startDiscovery,
            )
        }
    }
}

/** The standard watch frame: clock arc on top, scroll arc on the bezel, and the edges
 *  faded so a scrolling list doesn't collide with the curve. Every screen wears it —
 *  without it a Wear app reads as a phone app that got shrunk. */
@Composable
private fun WearScreen(
    listState: ScalingLazyListState,
    content: @Composable () -> Unit,
) {
    Scaffold(
        timeText = { TimeText(modifier = Modifier.scrollAway(listState)) },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
        modifier = Modifier.background(MaterialTheme.colors.background),
    ) { content() }
}

@Composable
private fun WearSettings(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val listState = rememberScalingLazyListState()
    var phoneTether by remember { mutableStateOf(PhoneTether.enabled(ctx)) }
    WearScreen(listState) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background),
        ) {
            item { ListHeader { Text(stringResource(R.string.settings_title), color = MaterialTheme.colors.onBackground) } }
            item {
                // A real toggle control rather than a chip that merely looks pressed —
                // the switch states what "on" means without the user having to infer it.
                ToggleChip(
                    checked = phoneTether,
                    onCheckedChange = { phoneTether = it; PhoneTether.setEnabled(ctx, it) },
                    label = { Text(stringResource(R.string.settings_phone_tether_title)) },
                    secondaryLabel = {
                        Text(stringResource(if (phoneTether) R.string.detail_tethered else R.string.settings_phone_tether_desc))
                    },
                    toggleControl = { Switch(checked = phoneTether) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
            }
            item {
                Chip(
                    onClick = onBack,
                    colors = ChipDefaults.secondaryChipColors(),
                    label = { Text(stringResource(R.string.action_done)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DiscoveryList(controller: WearController, onSettings: () -> Unit) {
    val listState = rememberScalingLazyListState()
    WearScreen(listState) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background),
        ) {
            // No app title: the launcher already said "blep", and on this screen the
            // count is the only thing worth the top row.
            item {
                ListHeader {
                    Text(
                        stringResource(R.string.nearby_count, controller.devices.size),
                        color = MaterialTheme.colors.onSurfaceVariant,
                    )
                }
            }
            items(controller.devices, key = { it.id }) { device ->
                DeviceRow(
                    device = device,
                    tethered = controller.isTethered(device.id),
                    onClick = { controller.track(device) },
                    onLongClick = { controller.toggleTether(device) },
                )
            }
            // Settings demoted to a round icon button: it competed with the devices as a
            // full-width chip, and devices are what this screen is for.
            item { IconOnlyButton(onClick = onSettings) { GearGlyph(it) } }
        }
    }
}

/**
 * One device, shaped like a Wear chip — stadium fill, leading signal glyph, name over a
 * status line. Hand-built rather than [Chip] because tapping hunts the device while a
 * long press arms its left-behind alert, and Chip exposes no long-press slot.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeviceRow(
    device: fyi.blep.core.model.BleDevice,
    tethered: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val sub = when {
        tethered -> stringResource(R.string.detail_tethered)
        device.isConnected -> stringResource(R.string.status_connected)
        else -> stringResource(R.string.dbm, device.rssi)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(26.dp))
            // A watched device is filled with the accent — the same "this one is
            // selected" language the rest of the system uses.
            .background(if (tethered) MaterialTheme.colors.primary else MaterialTheme.colors.surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        SignalGlyph(
            rssi = device.rssi,
            // On the filled row the ramp would fight the accent, so the glyph goes flat.
            tint = if (tethered) BlepWear.Ink else null,
        )
        Spacer(Modifier.size(10.dp))
        Column {
            Text(
                device.displayName,
                color = if (tethered) BlepWear.Ink else MaterialTheme.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.button,
            )
            Text(
                sub,
                color = if (tethered) BlepWear.Ink.copy(alpha = 0.7f) else MaterialTheme.colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.caption2,
            )
        }
    }
}

/** Four bars, filled by signal and coloured by the same warm/cold ramp the hunt uses,
 *  so "which of these is closest" is answerable without reading a single number. */
@Composable
private fun SignalGlyph(rssi: Int, tint: Color?) {
    val f = ((rssi + 95f) / 45f).coerceIn(0f, 1f)
    val lit = (f * 4f).roundToInt().coerceIn(1, 4)
    val on = tint ?: proximityColor(f)
    val off = (tint ?: MaterialTheme.colors.onSurfaceVariant).copy(alpha = 0.25f)
    Canvas(modifier = Modifier.size(20.dp)) {
        val w = size.width / 7f
        for (i in 0 until 4) {
            val h = size.height * (0.32f + 0.225f * i)
            drawRoundRect(
                color = if (i < lit) on else off,
                topLeft = Offset(i * w * 1.75f, size.height - h),
                size = androidx.compose.ui.geometry.Size(w, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w / 2f),
            )
        }
    }
}

/** A round icon button — the Wear affordance for a secondary action that doesn't
 *  deserve a full-width row. */
@Composable
private fun IconOnlyButton(onClick: () -> Unit, glyph: @Composable (Color) -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.secondaryButtonColors(),
            modifier = Modifier.size(44.dp),
        ) { glyph(MaterialTheme.colors.onSurface) }
    }
}

/** Settings gear, drawn rather than pulled in as an icon dependency (the watch module
 *  ships no icon pack, and this is the only glyph it needs). */
@Composable
private fun GearGlyph(tint: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        // The teeth have to overlap the body — set further out they read as petals and
        // the whole glyph turns into a flower.
        val rOuter = size.minDimension * 0.36f
        val rTooth = size.minDimension * 0.11f
        for (i in 0 until 8) {
            val a = (i * 45f) * (PI.toFloat() / 180f)
            drawCircle(tint, radius = rTooth, center = Offset(c.x + cos(a) * rOuter, c.y + sin(a) * rOuter))
        }
        drawCircle(tint, radius = size.minDimension * 0.31f, center = c)
        drawCircle(BlepWear.Surface, radius = size.minDimension * 0.13f, center = c)
    }
}

@Composable
private fun TrackingView(name: String, status: TrackingStatus, spatial: SpatialSnapshot?, guidanceLine: GuidanceLine?, rssi: Int?, signalLost: Boolean, onCancel: () -> Unit) {
    val bg by animateColorAsState(proximityColor(status.proximity), tween(800), label = "wearBg")
    // Point-blank: a strong live signal means it's on you. The spatial distance can
    // stick far at this range (shared with the phone), so trust the raw reading and
    // show "it's right here" + the live dB to sweep the last few cm. Mirrors phone.
    val onIt = !signalLost && rssi != null && rssi >= POINT_BLANK_DBM
    // Scaffold, so the clock gets the slot that positions it on the top arc — dropped
    // into a centre-aligned Box it just lands behind the guidance text. Inked, because
    // this is the one light background in the app and the default white would vanish.
    Scaffold(
        timeText = { TimeText(timeTextStyle = TimeTextDefaults.timeTextStyle(color = BlepWear.Ink.copy(alpha = 0.6f))) },
    ) {
    Box(
        modifier = Modifier.fillMaxSize().background(bg).clickable(onClick = onCancel),
        contentAlignment = Alignment.Center,
    ) {
        // The device name rides the bottom bezel instead of taking a line in the middle:
        // it's the least urgent thing here, and the curve is otherwise dead space. The
        // hunt keeps its light proximity background — that colour *is* the reading, so it
        // stays out of the dark theme.
        // The direction has to be set on the text itself, not just the layout: at a
        // bottom anchor the glyphs otherwise ride the outside of the arc and the name
        // reads upside down.
        CurvedLayout(anchor = 90f, modifier = Modifier.fillMaxSize()) {
            curvedText(
                text = name,
                color = BlepWear.Ink.copy(alpha = 0.55f),
                style = CurvedTextStyle(fontSize = 13.sp),
                angularDirection = CurvedDirection.Angular.CounterClockwise,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Spatial map when motion sensors feed it; otherwise the shape arrow.
            if (spatial != null) {
                WearRadar(spatial, line = if (onIt) null else guidanceLine, background = bg)
            } else {
                WearArrow(status.arrow.curl, status.arrow.scale)
            }
            Text(
                if (onIt) stringResource(R.string.tracking_right_here) else phaseTitle(status.guidance),
                color = Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp),
            )
            val line = if (onIt) rssi?.let { stringResource(R.string.dbm, it) }
                else guidanceLine?.let { guidanceLineText(it) }
                    ?: spatial?.target?.takeIf { it.confidence >= 0.35f && it.distanceM != null }
                        ?.let { wearDistanceLabel(it.distanceM!!) }
            if (line != null) {
                Text(
                    line,
                    color = Ink.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    // Keep the line off the round screen's curved edge so longer
                    // languages wrap instead of clipping.
                    modifier = Modifier.padding(top = 2.dp, start = 24.dp, end = 24.dp),
                )
            }
            val floor = spatial?.floorDelta?.let { floorHint(it) }
            if (floor != null) {
                Text(
                    floor,
                    color = Ink.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            // The name used to sit here; it now rides the bezel, which buys the guidance
            // a line of breathing room on the smallest screen we ship.
        }
    }
    }
}

/** Compact heads-up map for the watch, mirroring the phone radar: your forward
 *  direction is always up around a fixed chevron; the fog of war (averaged
 *  reveal tint) paints where you've been, the destination pin marks the
 *  estimate, and ONE cue ray shows the same turn the text phrases. */
@Composable
private fun WearRadar(snapshot: SpatialSnapshot, line: GuidanceLine?, background: Color) {
    Canvas(modifier = Modifier.size(108.dp).clipToBounds()) {
        val hub = Offset(size.width / 2f, size.height / 2f)
        val pts = snapshot.path
        val here = snapshot.here
        val heading = snapshot.headingRad
        var maxR = 3.0
        fun include(v: Vec2) { val d = (v - here).length; if (d > maxR) maxR = d }
        pts.forEach { include(it.pos) }; include(Vec2.ZERO); snapshot.target.position?.let(::include)
        val scale = (size.minDimension * 0.44f) / maxR.toFloat()
        val ch = cos(heading).toFloat(); val sh = sin(heading).toFloat()
        fun toScreen(v: Vec2): Offset {
            val x = ((v.x - here.x) * scale).toFloat()
            val y = (-(v.y - here.y) * scale).toFloat()
            return Offset(hub.x + x * ch + y * sh, hub.y - x * sh + y * ch)
        }

        // fog of war: the averaged reveal raster (see the phone RadarView).
        val splatR = ((snapshot.fogCellM * scale).toFloat() * 1.5f).coerceAtLeast(5f)
        for (c in snapshot.fog) {
            if (c.confidence <= 0.04f) continue
            val p = toScreen(c.pos)
            val col = fogColor(c.strength01).copy(alpha = 0.30f + 0.25f * c.confidence)
            drawCircle(
                brush = Brush.radialGradient(0f to col, 0.55f to col, 1f to col.copy(alpha = 0f), center = p, radius = splatR),
                radius = splatR, center = p,
            )
        }
        // Vignette into the page colour — fade completes inside the canvas so the
        // clip edge can't show as a border.
        drawRect(
            brush = Brush.radialGradient(
                0.55f to background.copy(alpha = 0f), 0.96f to background,
                center = hub, radius = size.minDimension * 0.5f,
            ),
        )

        // trail
        for (k in 1 until pts.size) {
            drawLine(
                fogColor((pts[k - 1].strength01 + pts[k].strength01) / 2f),
                toScreen(pts[k - 1].pos), toScreen(pts[k].pos), strokeWidth = 4f, cap = StrokeCap.Round,
            )
        }
        // start
        drawCircle(Ink.copy(alpha = 0.45f), radius = 4f, center = toScreen(Vec2.ZERO))
        // destination pin on the estimate (+ confidence glow)
        val est = snapshot.target
        val target = est.position
        if (target != null && est.confidence > 0.05f) {
            val tc = toScreen(target)
            val r = 14f * (0.6f + est.confidence)
            drawCircle(Brush.radialGradient(listOf(WearRose.copy(alpha = 0.45f * est.confidence), Color.Transparent), center = tc, radius = r), radius = r, center = tc)
            val headC = Offset(tc.x, tc.y - 13f)
            val headR = 7f
            val tail = Path().apply {
                moveTo(tc.x, tc.y)
                lineTo(headC.x - headR * 0.78f, headC.y + headR * 0.55f)
                lineTo(headC.x + headR * 0.78f, headC.y + headR * 0.55f)
                close()
            }
            drawPath(tail, Cream, style = Stroke(width = 4f))
            drawCircle(Cream, radius = headR + 2f, center = headC)
            drawPath(tail, WearRose)
            drawCircle(WearRose, radius = headR, center = headC)
            drawCircle(Cream, radius = 2.5f, center = headC)
        }
        // ONE cue ray — the same stabilized cue the text phrases (rose toward the
        // signal/target, amber back to the warmest spot on recovery).
        val turnDeg: Float? = when {
            line != null -> if (line.ahead) 0f else line.turnDeg.toFloat()
            else -> null
        }
        if (turnDeg != null) {
            val a = (turnDeg - 90f) * (PI.toFloat() / 180f)
            val dir = Offset(cos(a), sin(a))
            val from = hub + Offset(dir.x * 14f, dir.y * 14f)
            val tip = hub + Offset(dir.x * size.minDimension * 0.30f, dir.y * size.minDimension * 0.30f)
            val col = if (line!!.kind == CueKind.RECOVER) WearAmber else WearRose
            drawLine(Cream, from, tip, strokeWidth = 8f, cap = StrokeCap.Round)
            drawLine(col, from, tip, strokeWidth = 5f, cap = StrokeCap.Round)
            val perp = Offset(-dir.y, dir.x)
            val back = Offset(tip.x - dir.x * 11f, tip.y - dir.y * 11f)
            drawPath(
                Path().apply {
                    moveTo(tip.x + dir.x * 7f, tip.y + dir.y * 7f)
                    lineTo(back.x + perp.x * 8f, back.y + perp.y * 8f)
                    lineTo(back.x - perp.x * 8f, back.y - perp.y * 8f)
                    close()
                },
                col,
            )
        }
        // you: a fixed chevron, always up (heads-up frame)
        val wedgeColor = when {
            snapshot.target.bearingRad == null -> Ink
            snapshot.onCourse > 0.25f -> Color(0xFF3E8F4E)
            snapshot.onCourse < -0.25f -> Color(0xFFC85A41)
            else -> Ink
        }
        val tipP = Offset(hub.x, hub.y - 18f)
        val baseY = hub.y + 5f
        val wedge = Path().apply {
            moveTo(tipP.x, tipP.y)
            lineTo(hub.x + 9f, baseY)
            lineTo(hub.x, hub.y)
            lineTo(hub.x - 9f, baseY)
            close()
        }
        drawPath(wedge, Cream, style = Stroke(width = 3.5f))
        drawPath(wedge, wedgeColor)
    }
}

// Map-layer palette (mirrors the phone RadarView): a wide temperature ramp so
// cold and warm are unmistakable at fog alpha, plus the cue/pin colours.
private val WearRose = Color(0xFFD94F70)
private val WearAmber = Color(0xFFE8A33D)
private val Cream = Color(0xFFF4F5F0)
private val fogStops = listOf(
    0.00f to Color(0xFF5E83C9),
    0.35f to Color(0xFF8FD0CB),
    0.60f to Color(0xFFC4E7B6),
    0.80f to Color(0xFFF4D58D),
    1.00f to Color(0xFFE8A33D),
)

private fun fogColor(strength01: Float): Color {
    val f = ((strength01 - 0.15f) / 0.7f).coerceIn(0f, 1f)
    for (i in 0 until fogStops.lastIndex) {
        val (p0, c0) = fogStops[i]
        val (p1, c1) = fogStops[i + 1]
        if (f <= p1) {
            val t = if (p1 == p0) 0f else (f - p0) / (p1 - p0)
            return lerp(c0, c1, t)
        }
    }
    return fogStops.last().second
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
