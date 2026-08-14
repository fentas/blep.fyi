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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import fyi.blep.core.model.BleDevice
import fyi.blep.core.ble.ProbeResult
import fyi.blep.core.ble.RotationStats
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
    // Held by id, not by value: the scan re-emits a fresh BleDevice every tick, so a
    // captured copy would freeze the detail page's signal at whatever it was on open.
    var detailId by remember { mutableStateOf<String?>(null) }
    // ...but the last copy is kept too, because the scanner drops a device the moment it
    // goes quiet. Resolving the page purely from the live list meant losing the signal
    // threw the user back to the list, and regaining it re-opened the page under them.
    // The page belongs to the id, and only the user closes it.
    var detailLast by remember { mutableStateOf<fyi.blep.core.model.BleDevice?>(null) }
    val tracked = controller.tracking
    val live = detailId?.let { id -> controller.visibleDevices.firstOrNull { it.id == id } }
    LaunchedEffect(live) { if (live != null) detailLast = live }
    val detail = live
        ?: detailLast?.takeIf { it.id == detailId }
            // Absent: keep the row we last saw, with the signal cleared so the gauge
            // empties and the readout says so.
            ?.copy(rssi = fyi.blep.core.model.BleDevice.RSSI_UNKNOWN, isConnected = false)
    when {
        showSettings -> WearSettings(onBack = { showSettings = false })
        tracked == null && detail != null -> {
            // Correlation only runs while the page is open — see watchRotation.
            LaunchedEffect(detail.id) { controller.watchRotation(detail.id) }
            DisposableEffect(detail.id) { onDispose { controller.stopWatchingRotation() } }
            DeviceDetail(
                device = detail,
                tethered = controller.isTethered(detail.id),
                rotation = controller.detailRotation,
                probe = controller.detailProbe,
                probing = controller.probing,
                onIdentify = { controller.probeDevice(detail.id) },
                onFind = { detailId = null; controller.track(detail) },
                onToggleFavorite = { controller.toggleFavorite(detail) },
                onToggleTether = { controller.toggleTether(detail) },
                onBack = { detailId = null },
            )
        }
        tracked == null -> DiscoveryList(
            controller,
            onSettings = { showSettings = true },
            onOpenDetail = { detailId = it.id },
        )
        else -> controller.status?.let {
            TrackingView(
                tracked.displayName, it, controller.spatial, controller.guidance,
                rssi = controller.lastRssi, signalLost = controller.signalLost,
                onCancel = controller::startDiscovery,
            )
        }
    }
}

/** The watch frame: scroll arc on the bezel, edges faded so a scrolling list doesn't
 *  collide with the curve. No clock — the watch face is a swipe away and this screen
 *  would rather spend that arc on content. */
@Composable
private fun WearScreen(
    listState: ScalingLazyListState,
    content: @Composable () -> Unit,
) {
    Scaffold(
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
private fun DiscoveryList(
    controller: WearController,
    onSettings: () -> Unit,
    onOpenDetail: (fyi.blep.core.model.BleDevice) -> Unit,
) {
    val listState = rememberScalingLazyListState()
    var filter by remember { mutableStateOf(DeviceFilter.ALL) }
    val starredCount = controller.visibleDevices.count { it.isFavorite }
    val watchedCount = controller.visibleDevices.count { controller.isTethered(it.id) }
    // A filter whose last member disappears would strand the user on an empty list with
    // no obvious way back, so it releases itself.
    if ((filter == DeviceFilter.STARRED && starredCount == 0) ||
        (filter == DeviceFilter.WATCHED && watchedCount == 0)
    ) {
        filter = DeviceFilter.ALL
    }
    val shown = when (filter) {
        DeviceFilter.ALL -> controller.visibleDevices
        DeviceFilter.STARRED -> controller.visibleDevices.filter { it.isFavorite }
        DeviceFilter.WATCHED -> controller.visibleDevices.filter { controller.isTethered(it.id) }
    }
    WearScreen(listState) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background),
        ) {
            // Filters, not a title: the count was decoration, and this row is the only
            // horizontal space the screen has. Icon chips rather than labelled ones —
            // two words plus two counts do not fit across a 450 px circle.
            item {
                FilterRow(
                    filter = filter,
                    all = controller.visibleDevices.size,
                    starred = starredCount,
                    watched = watchedCount,
                    onPick = { filter = it },
                )
            }
            if (shown.isEmpty()) {
                // Otherwise the screen is a filter row above nothing, which reads as a
                // hang rather than "nothing is in range yet".
                item {
                    Text(
                        stringResource(R.string.discovery_empty_title),
                        color = MaterialTheme.colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.caption1,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
                    )
                }
            }
            items(shown, key = { it.id }) { device ->
                DeviceRow(
                    device = device,
                    tethered = controller.isTethered(device.id),
                    // Tap opens the device rather than hunting it, so everything the
                    // watch can do to a device has a visible home; holding still starts
                    // the hunt directly for anyone who knows what they're after.
                    onClick = { onOpenDetail(device) },
                    onLongClick = { controller.track(device) },
                )
            }
            // Settings closes the list rather than competing with it — as a full-width
            // chip it read as another device.
            item {
                FootButton(onClick = onSettings, label = stringResource(R.string.settings_title)) {
                    GearGlyph(it)
                }
            }
            // The long press is otherwise invisible. It lives at the very foot, which is
            // where someone exploring ends up, and costs nothing to anyone who doesn't
            // scroll this far.
            item {
                Text(
                    stringResource(R.string.wear_hold_to_hunt),
                    color = MaterialTheme.colors.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.caption3,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/**
 * What the watch can say about one device, and everything it can do to it.
 *
 * Deliberately thinner than the phone's detail page: the watch runs no GATT probe and
 * no rotation correlator, so there is no identity history or device-info card to show
 * — inventing empty sections would be worse than leaving them out. What it does have is
 * the live signal and the two toggles, which until now were only reachable by a hidden
 * long press.
 */
@Composable
private fun DeviceDetail(
    device: fyi.blep.core.model.BleDevice,
    tethered: Boolean,
    rotation: RotationStats?,
    probe: ProbeResult?,
    probing: Boolean,
    onIdentify: () -> Unit,
    onFind: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleTether: () -> Unit,
    onBack: () -> Unit,
) {
    val listState = rememberScalingLazyListState()
    // No PositionIndicator here: the signal arc already owns the bezel, and Wear draws
    // the scroll arc in the same place — the two would sit on top of each other.
    Scaffold(
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        modifier = Modifier.background(MaterialTheme.colors.background),
    ) {
        ScalingLazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 28.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                ListHeader {
                    Text(
                        device.displayName,
                        color = MaterialTheme.colors.onBackground,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // The identifier is normally a MAC and worth showing, but an unnamed device
            // falls back to its id as a name — then this line just repeats the header.
            if (!device.id.equals(device.displayName, ignoreCase = true)) {
                item {
                    Text(
                        device.id,
                        color = MaterialTheme.colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.caption3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }
            }
            item {
                ToggleChip(
                    checked = device.isFavorite,
                    onCheckedChange = { onToggleFavorite() },
                    label = { Text(stringResource(R.string.settings_sync_favorites), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    toggleControl = { Switch(checked = device.isFavorite) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
            }
            item {
                ToggleChip(
                    checked = tethered,
                    onCheckedChange = { onToggleTether() },
                    label = {
                        Text(
                            stringResource(if (tethered) R.string.detail_tethered else R.string.detail_tether),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    toggleControl = { Switch(checked = tethered) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
            }
            // History — only once the correlator has actually linked two addresses.
            // "No id change seen yet" is the honest state for a device that simply
            // hasn't rotated while you were looking at it.
            if (rotation != null) {
                item {
                    SectionLabel(
                        if (rotation.rotations > 0) {
                            stringResource(R.string.detail_history_rotations, rotation.rotations)
                        } else {
                            stringResource(R.string.detail_history)
                        },
                    )
                }
                item {
                    Text(
                        if (rotation.rotations > 0) {
                            stringResource(R.string.detail_confidence, "${(rotation.confidence * 100).roundToInt()}%")
                        } else {
                            stringResource(R.string.detail_no_rotation)
                        },
                        color = MaterialTheme.colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.caption2,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                }
                // Worn ids, oldest first — the same lineage the phone lists.
                items(rotation.history.takeLast(4)) { worn ->
                    Text(
                        worn.address,
                        color = MaterialTheme.colors.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.caption3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                }
            }

            // Device info — behind an explicit button, never automatic. A probe is a GATT
            // connect, and spending the watch's radio on one the moment a page opens is
            // not a cost the user asked for.
            item { SectionLabel(stringResource(R.string.detail_identity)) }
            if (probe?.connectable == true) {
                probe.manufacturer?.let { item { InfoLine(stringResource(R.string.detail_identity), it) } }
                probe.model?.let { item { InfoLine(stringResource(R.string.detail_identifier), it) } }
                probe.firmware?.let { item { InfoLine("Firmware", it) } }
                probe.batteryPct?.let { item { InfoLine("Battery", "$it%") } }
            } else {
                item {
                    Chip(
                        onClick = onIdentify,
                        enabled = !probing,
                        colors = ChipDefaults.secondaryChipColors(),
                        label = {
                            Text(
                                if (probing) stringResource(R.string.detail_identifying)
                                else stringResource(R.string.detail_identify),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    )
                }
                // A probe that came back with nothing has to say so. Without this the
                // button just sat there unchanged, so a refusal looked identical to
                // never having pressed it — and most trackers do refuse.
                if (probe != null && !probing) {
                    item {
                        Text(
                            stringResource(R.string.detail_identify_failed),
                            color = MaterialTheme.colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.caption3,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            // The reason you opened this page, so it gets the accent and sits last where
            // the thumb already is.
            item {
                Chip(
                    onClick = onFind,
                    colors = ChipDefaults.primaryChipColors(),
                    label = {
                        Text(
                            stringResource(R.string.detail_find),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
            }
            // Full width, like every other control on this page — as a narrow footer pill
            // it was the one button whose edges didn't line up with the rest.
            item {
                Chip(
                    onClick = onBack,
                    colors = ChipDefaults.secondaryChipColors(),
                    label = {
                        Text(
                            stringResource(R.string.action_done),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
            }
        }
        // Drawn last, so it sits above the list. The arc traces a circle while the rows
        // are rectangles, so near the top and bottom of the screen a full-width row will
        // always cross it — better that content slides under the gauge than that the
        // gauge gets chopped into pieces by whatever happens to be scrolled past it.
        Box(Modifier.fillMaxSize()) {
            SignalArc(rssi = device.bestRssi, modifier = Modifier.fillMaxSize())
            // The reading sits in the arc's own gap, where it labels the gauge instead of
            // taking a row from the list — and it's tinted to match, so the number and
            // the ring read as one measurement rather than two facts about the device.
            val f = ((device.bestRssi - ARC_RSSI_FLOOR) / (ARC_RSSI_CEIL - ARC_RSSI_FLOOR)).coerceIn(0f, 1f)
            Text(
                when {
                    device.isConnected -> stringResource(R.string.status_connected)
                    device.bestRssi == BleDevice.RSSI_UNKNOWN -> stringResource(R.string.status_no_signal)
                    else -> stringResource(R.string.dbm, device.bestRssi)
                },
                // Dimmed when there's nothing to read, so an empty gauge and a grey
                // label say the same thing.
                color = if (device.bestRssi == BleDevice.RSSI_UNKNOWN) MaterialTheme.colors.onSurfaceVariant else proximityColor(f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                style = MaterialTheme.typography.button,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp),
            )
        }
    }
}

// Signal arc range: empty at RSSI_FLOOR, closed at RSSI_CEIL. The ceiling is the
// point where you're standing over the thing rather than near it, so the ring
// completing means "you have arrived", not "the signal is unusually good".
private const val ARC_RSSI_FLOOR = -100f
private const val ARC_RSSI_CEIL = -30f
// A 60° gap centred on the top, so the gauge has a visible start and finish.
private const val ARC_START = -60f
private const val ARC_SWEEP = 300f

/**
 * Signal strength as a ring around the bezel — the whole edge of the watch becomes the
 * readout, which is legible at a glance in a way a dBm figure never is.
 *
 * Animated, because raw RSSI jitters several dB between adverts and an unsmoothed ring
 * would flicker constantly; and coloured by the same warm/cold ramp as the hunt, so the
 * arc and the signal bars in the list agree about what "close" looks like.
 */
@Composable
private fun SignalArc(rssi: Int, modifier: Modifier = Modifier) {
    val target = ((rssi - ARC_RSSI_FLOOR) / (ARC_RSSI_CEIL - ARC_RSSI_FLOOR)).coerceIn(0f, 1f)
    val f by animateFloatAsState(target, tween(600), label = "signalArc")
    val color = proximityColor(f)
    Canvas(modifier) {
        val stroke = 8.dp.toPx()
        val inset = stroke / 2f + 1.dp.toPx()
        val d = size.minDimension - inset * 2f
        val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)
        val arcSize = androidx.compose.ui.geometry.Size(d, d)
        // The unfilled remainder still reads as a track, so the ring is always a whole
        // shape rather than a fragment floating at the top of the screen. The gap at the
        // top gives the arc two visible ends — a closed ring reads as decoration, an arc
        // with a start and a finish reads as a gauge.
        drawArc(
            color = BlepWear.Surface,
            startAngle = ARC_START, sweepAngle = ARC_SWEEP, useCenter = false,
            topLeft = topLeft, size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        if (f > 0.001f) {
            drawArc(
                color = color,
                startAngle = ARC_START, sweepAngle = ARC_SWEEP * f, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}

/** Which slice of the list is showing. Single-select: two independent toggles would let
 *  the user ask for "starred and watched" and get an empty list with no explanation. */
private enum class DeviceFilter { ALL, STARRED, WATCHED }

/**
 * A three-up segmented control: all, starred, watched — each with its tally.
 *
 * Segments span the row rather than floating as two small pills, which left the widest
 * part of the screen looking unfinished. "All" is an explicit segment, not the absence
 * of a selection: with two chips the unfiltered state was invisible, and there was no
 * obvious control to press to get back to it.
 *
 * A filter with nothing in it stays visible but inert, so the row doesn't reflow under
 * the user's thumb as devices come and go.
 */
@Composable
private fun FilterRow(
    filter: DeviceFilter,
    all: Int,
    starred: Int,
    watched: Int,
    onPick: (DeviceFilter) -> Unit,
) {
    // Outer edges fully rounded, inner edges softened rather than square: squaring the
    // joins made the middle and right segments read as sharp-cornered rectangles. Every
    // corner stays rounded, the outer ones just more so, which is what makes the three
    // read as one control instead of three loose pills.
    val end = 999.dp
    val join = 10.dp
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(0.92f).padding(vertical = 2.dp),
    ) {
        FilterSegment(
            selected = filter == DeviceFilter.ALL,
            count = all,
            label = stringResource(R.string.section_nearby),
            onClick = { onPick(DeviceFilter.ALL) },
            shape = RoundedCornerShape(topStart = end, bottomStart = end, topEnd = join, bottomEnd = join),
            modifier = Modifier.weight(1f),
        ) { ListGlyph(it) }
        FilterSegment(
            selected = filter == DeviceFilter.STARRED,
            count = starred,
            label = stringResource(R.string.settings_sync_favorites),
            onClick = { onPick(DeviceFilter.STARRED) },
            shape = RoundedCornerShape(join),
            modifier = Modifier.weight(1f),
        ) { StarGlyph(it) }
        FilterSegment(
            selected = filter == DeviceFilter.WATCHED,
            count = watched,
            label = stringResource(R.string.settings_tether_title),
            onClick = { onPick(DeviceFilter.WATCHED) },
            shape = RoundedCornerShape(topStart = join, bottomStart = join, topEnd = end, bottomEnd = end),
            modifier = Modifier.weight(1f),
        ) { WatchedGlyph(it) }
    }
}

@Composable
private fun FilterSegment(
    selected: Boolean,
    count: Int,
    label: String,
    onClick: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier,
    glyph: @Composable (Color) -> Unit,
) {
    val enabled = count > 0
    val fg = when {
        selected -> BlepWear.Ink
        enabled -> MaterialTheme.colors.onSurface
        else -> MaterialTheme.colors.onSurfaceVariant.copy(alpha = 0.4f)
    }
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(46.dp)
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colors.primary
                else MaterialTheme.colors.surface.copy(alpha = if (enabled) 1f else 0.5f),
            )
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            // The glyph carries no text, so spell the filter out for TalkBack.
            .semantics { contentDescription = "$label, $count" },
    ) {
        glyph(fg)
        Spacer(Modifier.size(4.dp))
        Text("$count", color = fg, style = MaterialTheme.typography.caption2)
    }
}

/** A quiet divider-by-typography, so the detail page reads as sections without spending
 *  vertical space on rules the watch can't afford. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = MaterialTheme.colors.onSurfaceVariant.copy(alpha = 0.7f),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.caption3,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = MaterialTheme.colors.onSurfaceVariant,
            style = MaterialTheme.typography.caption3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.size(6.dp))
        Text(
            value,
            color = MaterialTheme.colors.onSurface,
            style = MaterialTheme.typography.caption2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** "Everything" — a stack of rows, i.e. the list itself. */
@Composable
private fun ListGlyph(tint: Color) {
    Canvas(modifier = Modifier.size(13.dp)) {
        val h = size.height * 0.16f
        for (i in 0 until 3) {
            drawRoundRect(
                color = tint,
                topLeft = Offset(0f, i * size.height * 0.42f),
                size = androidx.compose.ui.geometry.Size(size.width, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2f),
            )
        }
    }
}

/** Favourite — the same five-point star the phone uses for the same idea. */
@Composable
private fun StarGlyph(tint: Color) {
    Canvas(modifier = Modifier.size(14.dp)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val rOuter = size.minDimension * 0.5f
        val rInner = rOuter * 0.44f
        val path = Path()
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) rOuter else rInner
            val a = (-90f + i * 36f) * (PI.toFloat() / 180f)
            val p = Offset(c.x + cos(a) * r, c.y + sin(a) * r)
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        path.close()
        drawPath(path, tint)
    }
}

/** Watched (left-behind alert armed) — a ring around a dot, i.e. something being kept
 *  an eye on. Deliberately not a bell: this is presence, not a reminder. */
@Composable
private fun WatchedGlyph(tint: Color) {
    Canvas(modifier = Modifier.size(14.dp)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(tint, radius = size.minDimension * 0.46f, center = c, style = Stroke(width = size.minDimension * 0.13f))
        drawCircle(tint, radius = size.minDimension * 0.17f, center = c)
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
    // State goes in the trailing markers, never in this line. "Left-behind on" fits in
    // English and truncates to "Mahajätmise hoiatus s…" in Estonian, and a row that
    // trades its signal reading for a clipped sentence is worse in every language.
    val sub = when {
        // RSSI_UNKNOWN is a sentinel, not a reading — printing it gave paired devices a
        // permanent, meaningless "-127 dBm".
        device.isConnected -> stringResource(R.string.status_connected)
        // bestRssi: the strongest of our own reading and the phone's. A device only the
        // phone hears is still worth a number, and it is the phone's number.
        device.bestRssi == BleDevice.RSSI_UNKNOWN -> stringResource(R.string.status_no_signal)
        else -> stringResource(R.string.dbm, device.bestRssi)
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
            rssi = device.bestRssi,
            // On the filled row the ramp would fight the accent, so the glyph goes flat.
            tint = if (tethered) BlepWear.Ink else null,
        )
        Spacer(Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
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
        // State has to be identifiable *in* the list, not only by filtering to it —
        // otherwise the chips are the only way to answer "which one is it?". Glyphs
        // rather than words, so nothing clips at any string length.
        if (device.isFavorite) {
            Spacer(Modifier.size(6.dp))
            StarGlyph(if (tethered) BlepWear.Ink else BlepWear.Blue)
        }
        if (tethered) {
            Spacer(Modifier.size(6.dp))
            WatchedGlyph(BlepWear.Ink)
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

/**
 * The action that closes a list — a wide stadium pill sitting under the last row, the
 * shape Wear uses for "More" / "+" / "Track" at the foot of a screen.
 *
 * Narrower than a device row and a shade lighter, so it reads as a control rather than
 * one more thing in the list; a full-width accent pill here would compete with the
 * accent-filled watched row directly above it.
 */
@Composable
private fun FootButton(onClick: () -> Unit, label: String, glyph: @Composable (Color) -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp), contentAlignment = Alignment.Center) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            // A plain stadium. An earlier version bowed the bottom edge to follow the
            // bezel, but hand-rolling that shape put a visible seam where the corner
            // arcs met the bow and it read as a lump rather than a curve. Wear Material 3
            // has a real EdgeButton for this; Material 2, which this app is on, does not,
            // and a clean pill beats a bad approximation of a nicer one.
            modifier = Modifier
                .fillMaxWidth(0.62f)
                .height(46.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colors.secondaryVariant)
                .clickable(onClick = onClick)
                .semantics { contentDescription = label },
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
