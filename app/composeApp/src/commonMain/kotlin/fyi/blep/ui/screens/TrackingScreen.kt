package fyi.blep.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fyi.blep.core.spatial.CueKind
import fyi.blep.core.spatial.GuidanceLine
import fyi.blep.core.spatial.SpatialSnapshot
import fyi.blep.core.tracking.Guidance
import fyi.blep.core.tracking.GuidanceCue
import fyi.blep.core.tracking.TrackingStatus
import fyi.blep.resources.Res
import fyi.blep.resources.action_cancel
import fyi.blep.resources.cue_calibrate_detail
import fyi.blep.resources.cue_calibrate_title
import fyi.blep.resources.cue_complete_detail
import fyi.blep.resources.cue_complete_title
import fyi.blep.resources.cue_pinpoint_detail
import fyi.blep.resources.cue_pinpoint_lost_detail
import fyi.blep.resources.cue_pinpoint_lost_title
import fyi.blep.resources.cue_pinpoint_title
import fyi.blep.resources.cue_reorient_detail
import fyi.blep.resources.cue_reorient_title
import fyi.blep.resources.cue_sweep_colder_detail
import fyi.blep.resources.cue_sweep_colder_title
import fyi.blep.resources.cue_sweep_flat_detail
import fyi.blep.resources.cue_sweep_flat_title
import fyi.blep.resources.cue_sweep_start_detail
import fyi.blep.resources.cue_sweep_start_title
import fyi.blep.resources.cue_sweep_warmer_detail
import fyi.blep.resources.cue_sweep_warmer_title
import fyi.blep.resources.cue_walk_colder_detail
import fyi.blep.resources.cue_walk_colder_title
import fyi.blep.resources.cue_walk_flat_detail
import fyi.blep.resources.cue_walk_flat_title
import fyi.blep.resources.cue_walk_found_detail
import fyi.blep.resources.cue_walk_found_title
import fyi.blep.resources.cue_walk_overshoot_detail
import fyi.blep.resources.cue_walk_overshoot_title
import fyi.blep.resources.cue_walk_warmer_detail
import fyi.blep.resources.cue_walk_warmer_title
import fyi.blep.resources.dbm
import fyi.blep.resources.distance_almost_on_it
import fyi.blep.resources.distance_away
import fyi.blep.resources.floor_down_many
import fyi.blep.resources.floor_down_one
import fyi.blep.resources.floor_up_many
import fyi.blep.resources.floor_up_one
import fyi.blep.resources.line_ahead_warmer
import fyi.blep.resources.line_distance_almost
import fyi.blep.resources.line_distance_m
import fyi.blep.resources.line_facing_signal
import fyi.blep.resources.line_target
import fyi.blep.resources.line_turn_to_signal
import fyi.blep.resources.line_turn_warmer
import fyi.blep.resources.sound_off
import fyi.blep.resources.sound_on
import fyi.blep.resources.tracking_db_hint
import fyi.blep.resources.tracking_field_clean
import fyi.blep.resources.tracking_field_noisy
import fyi.blep.resources.tracking_field_suffix
import fyi.blep.resources.tracking_last_heard
import fyi.blep.resources.tracking_no_signal
import fyi.blep.resources.tracking_no_signal_lc
import fyi.blep.resources.tracking_out_of_range
import fyi.blep.resources.tracking_right_here
import fyi.blep.resources.tracking_right_here_detail
import fyi.blep.resources.tracking_scanning
import fyi.blep.resources.turn_ahead
import fyi.blep.resources.turn_left
import fyi.blep.resources.turn_right
import fyi.blep.ui.KeepScreenOn
import fyi.blep.ui.components.RadarView
import fyi.blep.ui.components.VectorArrow
import fyi.blep.ui.theme.BlepColors
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@Composable
fun TrackingScreen(
    deviceName: String,
    status: TrackingStatus,
    rssi: Int?,
    spatial: SpatialSnapshot?,
    guidanceLine: GuidanceLine?,
    signalLost: Boolean,
    signalAgeSec: Int,
    soundOn: Boolean,
    onToggleSound: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KeepScreenOn() // don't let the display sleep mid-hunt
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // Foreground "ink" + its contrast halo flip with the theme so the radar/text
    // aren't black-on-bright in dark mode.
    val ink = if (dark) Color(0xFFE7EBEF) else BlepColors.Ink
    val halo = if (dark) Color(0xFF14181D) else BlepColors.Cream
    val background by animateColorAsState(
        // Blend the warm/cold proximity cue toward a calm neutral so the full-bleed
        // background isn't harsh — a light neutral in light mode, a dark one in dark
        // mode (using the muted ramp). Either way it still shifts cool→warm as you
        // close in; the radar/arrow keep the vivid signal.
        targetValue = if (dark) {
            lerp(BlepColors.proximity(status.proximity, dark = true), Color(0xFF14181D), 0.62f)
        } else {
            lerp(BlepColors.proximity(status.proximity), Color(0xFFEFF2F6), 0.32f)
        },
        animationSpec = tween(durationMillis = 800),
        label = "trackingBackground",
    )
    val arrowTint = ink.copy(alpha = 0.82f)
    val pulse by rememberInfiniteTransition(label = "radar").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse), label = "pulse",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(background)                       // full-bleed colour
            .windowInsetsPadding(WindowInsets.safeDrawing) // keep content off the bars
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(
                text = deviceName,
                style = MaterialTheme.typography.titleMedium,
                color = ink.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.Center),
            )
            MuteToggle(
                soundOn = soundOn,
                onToggle = onToggleSound,
                ink = ink,
                bg = background,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }

        // Point-blank: a strong live signal means it's basically on you. Computed up
        // here because both the radar (drop the cue ray) and the headline use it.
        val onIt = !signalLost && rssi != null && rssi >= POINT_BLANK_DBM

        // The spatial map is the hero; a compact arrow keeps the immediate cue.
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            RadarView(
                snapshot = spatial,
                pulse = pulse,
                // The ray mirrors the headline cue; at point-blank the headline switches
                // to "right here", so the ray and the metres label go too — picture and
                // words stay one.
                line = guidanceLine,
                pointBlank = onIt,
                signalLost = signalLost,
                ink = ink,
                halo = halo,
                modifier = Modifier.fillMaxSize(),
            )
            if (spatial == null) {
                VectorArrow(curl = status.arrow.curl, scale = status.arrow.scale, tint = arrowTint)
            }
        }

        // ONE authoritative cue, never two that disagree: the precise compass /
        // turn-by-turn instruction when we have it (it already folds in warmer /
        // distance), otherwise the coarser RSSI phase guidance.
        val lineText: String? = guidanceLine?.let { guidanceLineText(it) }
        val fallback: String? = if (lineText == null) {
            spatial?.target?.takeIf { it.confidence >= 0.35f && it.distanceM != null }
                ?.let { distanceLabel(it.distanceM!!) }
        } else {
            null
        }
        val instruction = lineText ?: fallback
        // Point-blank (`onIt`, computed above the radar): trust the strong live signal
        // over the spatial distance, which can stick far (seeded from an early weak
        // sample) and read e.g. "8 m" while you're standing on it. Show "right here"
        // and drop the misleading turn/distance cue.
        // No fresh signal trumps everything — don't guide on a stale reading.
        val headline = when {
            signalLost -> stringResource(Res.string.tracking_no_signal)
            onIt -> stringResource(Res.string.tracking_right_here)
            else -> instruction ?: phaseTitle(status.guidance)
        }
        val detail = when {
            signalLost -> stringResource(Res.string.tracking_out_of_range) +
                (if (signalAgeSec > 0) " " + stringResource(Res.string.tracking_last_heard, signalAgeSec) else "")
            onIt -> stringResource(Res.string.tracking_right_here_detail)
            instruction != null -> null
            else -> phaseDetail(status.guidance)
        }
        AnimatedContent(
            targetState = headline,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
            label = "guidance",
        ) { text ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text,
                    style = MaterialTheme.typography.displaySmall,
                    color = ink,
                    textAlign = TextAlign.Center,
                    // announce each new turn-by-turn cue to screen readers
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                if (detail != null) {
                    Text(detail, style = MaterialTheme.typography.bodyLarge, color = ink.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                }
            }
        }

        val floor = spatial?.floorDelta?.let { floorHint(it) }
        if (floor != null) {
            Text(
                text = floor,
                style = MaterialTheme.typography.bodyLarge,
                color = ink.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        // RSSI + which environment the tracker thinks it's in (from signal jitter).
        val rssiText = rssi?.let { stringResource(Res.string.dbm, it) } ?: stringResource(Res.string.tracking_scanning)
        val fieldSuffix = spatial?.signalVolatilityDb?.takeIf { it > 0.0 }?.let { v ->
            val tag = stringResource(if (v >= NOISY_FIELD_DB) Res.string.tracking_field_noisy else Res.string.tracking_field_clean)
            "  " + stringResource(Res.string.tracking_field_suffix, tag, (v * 10).roundToInt() / 10.0)
        }
        val noSignalLc = stringResource(Res.string.tracking_no_signal_lc)
        // At point-blank the radar is useless but the live dB still pinpoints — bring
        // it into focus (it scales up + brightens) with a quiet explainer, so you can
        // sweep the phone over the exact spot and watch it peak. Otherwise the dB sits
        // quietly in the footer with the field-quality note.
        val dbFocus by animateFloatAsState(if (onIt) 1f else 0f, tween(450), label = "dbFocus")
        if (onIt && rssi != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(top = 10.dp),
            ) {
                Text(
                    stringResource(Res.string.dbm, rssi),
                    style = MaterialTheme.typography.displaySmall,
                    color = ink,
                    modifier = Modifier.graphicsLayer {
                        val s = 0.8f + 0.2f * dbFocus
                        scaleX = s; scaleY = s
                        alpha = 0.35f + 0.65f * dbFocus
                    },
                )
                Text(
                    stringResource(Res.string.tracking_db_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = ink.copy(alpha = 0.5f * dbFocus),
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            Text(
                text = if (signalLost) noSignalLc else rssiText + (fieldSuffix ?: ""),
                style = MaterialTheme.typography.labelLarge,
                color = ink.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        Text(
            text = stringResource(Res.string.action_cancel),
            style = MaterialTheme.typography.labelLarge,
            color = ink.copy(alpha = 0.55f),
            modifier = Modifier
                .padding(top = 10.dp, bottom = 6.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onCancel)
                .padding(horizontal = 24.dp, vertical = 10.dp),
        )
    }
}

// ── Localized formatters for the core-generated guidance ────────────────────

@Composable
private fun phaseTitle(g: Guidance): String = cueTitle(g.cue)?.let { stringResource(it) } ?: g.title

@Composable
private fun phaseDetail(g: Guidance): String = cueDetail(g.cue)?.let { stringResource(it) } ?: g.detail

private fun cueTitle(cue: GuidanceCue) = when (cue) {
    GuidanceCue.NONE -> null
    GuidanceCue.CALIBRATE -> Res.string.cue_calibrate_title
    GuidanceCue.SWEEP_START -> Res.string.cue_sweep_start_title
    GuidanceCue.SWEEP_WARMER -> Res.string.cue_sweep_warmer_title
    GuidanceCue.SWEEP_COLDER -> Res.string.cue_sweep_colder_title
    GuidanceCue.SWEEP_FLAT -> Res.string.cue_sweep_flat_title
    GuidanceCue.WALK_WARMER -> Res.string.cue_walk_warmer_title
    GuidanceCue.WALK_COLDER -> Res.string.cue_walk_colder_title
    GuidanceCue.WALK_FLAT -> Res.string.cue_walk_flat_title
    GuidanceCue.WALK_OVERSHOOT -> Res.string.cue_walk_overshoot_title
    GuidanceCue.WALK_FOUND -> Res.string.cue_walk_found_title
    GuidanceCue.REORIENT -> Res.string.cue_reorient_title
    GuidanceCue.PINPOINT -> Res.string.cue_pinpoint_title
    GuidanceCue.PINPOINT_LOST -> Res.string.cue_pinpoint_lost_title
    GuidanceCue.COMPLETE -> Res.string.cue_complete_title
}

private fun cueDetail(cue: GuidanceCue) = when (cue) {
    GuidanceCue.NONE -> null
    GuidanceCue.CALIBRATE -> Res.string.cue_calibrate_detail
    GuidanceCue.SWEEP_START -> Res.string.cue_sweep_start_detail
    GuidanceCue.SWEEP_WARMER -> Res.string.cue_sweep_warmer_detail
    GuidanceCue.SWEEP_COLDER -> Res.string.cue_sweep_colder_detail
    GuidanceCue.SWEEP_FLAT -> Res.string.cue_sweep_flat_detail
    GuidanceCue.WALK_WARMER -> Res.string.cue_walk_warmer_detail
    GuidanceCue.WALK_COLDER -> Res.string.cue_walk_colder_detail
    GuidanceCue.WALK_FLAT -> Res.string.cue_walk_flat_detail
    GuidanceCue.WALK_OVERSHOOT -> Res.string.cue_walk_overshoot_detail
    GuidanceCue.WALK_FOUND -> Res.string.cue_walk_found_detail
    GuidanceCue.REORIENT -> Res.string.cue_reorient_detail
    GuidanceCue.PINPOINT -> Res.string.cue_pinpoint_detail
    GuidanceCue.PINPOINT_LOST -> Res.string.cue_pinpoint_lost_detail
    GuidanceCue.COMPLETE -> Res.string.cue_complete_detail
}

@Composable
private fun guidanceLineText(line: GuidanceLine): String {
    val turn = turnText(line.ahead, line.turnDeg)
    return when (line.kind) {
        CueKind.SIGNAL ->
            if (line.ahead) stringResource(Res.string.line_facing_signal) else stringResource(Res.string.line_turn_to_signal, turn)
        CueKind.RECOVER ->
            if (line.ahead) stringResource(Res.string.line_ahead_warmer) else stringResource(Res.string.line_turn_warmer, turn)
        CueKind.TARGET -> stringResource(Res.string.line_target, turn, distanceWord(line.distanceM ?: 0.0))
    }
}

@Composable
private fun turnText(ahead: Boolean, turnDeg: Int): String = when {
    ahead -> stringResource(Res.string.turn_ahead)
    turnDeg > 0 -> stringResource(Res.string.turn_right, turnDeg)
    else -> stringResource(Res.string.turn_left, -turnDeg)
}

@Composable
private fun distanceWord(m: Double): String =
    if (m < 1.5) stringResource(Res.string.line_distance_almost) else stringResource(Res.string.line_distance_m, m.roundToInt())

/** Human label for an estimated target distance (the coarse fallback cue). */
@Composable
private fun distanceLabel(meters: Double): String =
    if (meters < 1.5) stringResource(Res.string.distance_almost_on_it) else stringResource(Res.string.distance_away, meters.roundToInt())

@Composable
private fun floorHint(delta: Int): String? = when {
    delta > 0 -> stringResource(if (delta == 1) Res.string.floor_up_one else Res.string.floor_up_many, delta)
    delta < 0 -> stringResource(if (delta == -1) Res.string.floor_down_one else Res.string.floor_down_many, -delta)
    else -> null
}

// Above this much signal jitter (dB) the field reads as "noisy" — mirrors
// GuidanceStabilizer.noisyVolatilityDb, which gates directional commitment.
private const val NOISY_FIELD_DB = 2.2

// At/above this raw RSSI you're genuinely on top of it (within ~a metre):
// directional guidance is moot and the spatial distance is unreliable, so we
// switch to a plain "it's right here". Raw (not the proximity curve, which
// saturates at -58 dBm ≈ 1-2 m) so it only fires when you're truly on it.
private const val POINT_BLANK_DBM = -50

/** A flat 2-D speaker glyph (no system emoji) that toggles the tracking tone:
 *  sound-wave arcs when on, a slash when muted. */
@Composable
private fun MuteToggle(soundOn: Boolean, onToggle: () -> Unit, ink: Color, bg: Color, modifier: Modifier = Modifier) {
    val tint = ink.copy(alpha = 0.72f)
    val desc = stringResource(if (soundOn) Res.string.sound_on else Res.string.sound_off)
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onToggle)
            .semantics { contentDescription = desc; role = Role.Button }
            .padding(10.dp)
            .size(26.dp),
    ) {
        val w = size.width; val h = size.height
        // Speaker = back block + cone, one filled polygon.
        drawPath(
            Path().apply {
                moveTo(w * 0.08f, h * 0.38f)
                lineTo(w * 0.28f, h * 0.38f)
                lineTo(w * 0.50f, h * 0.16f)
                lineTo(w * 0.50f, h * 0.84f)
                lineTo(w * 0.28f, h * 0.62f)
                lineTo(w * 0.08f, h * 0.62f)
                close()
            },
            tint,
        )
        if (soundOn) {
            val cx = w * 0.45f; val cy = h * 0.5f
            listOf(w * 0.22f, w * 0.34f).forEach { r ->
                drawArc(
                    color = tint,
                    startAngle = -55f, sweepAngle = 110f, useCenter = false,
                    topLeft = Offset(cx - r, cy - r), size = Size(r * 2, r * 2),
                    style = Stroke(width = w * 0.07f, cap = StrokeCap.Round),
                )
            }
        } else {
            // Muted: one diagonal strike across the whole glyph (the universal mute
            // symbol). A wider background-coloured "cut" sits under the strike so it
            // reads cleanly where it crosses the speaker cone.
            val a = Offset(w * 0.14f, h * 0.14f)
            val z = Offset(w * 0.86f, h * 0.86f)
            drawLine(bg, a, z, strokeWidth = w * 0.20f, cap = StrokeCap.Round)
            drawLine(tint, a, z, strokeWidth = w * 0.09f, cap = StrokeCap.Round)
        }
    }
}
