package fyi.blep.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import fyi.blep.core.ble.RotationStats
import fyi.blep.core.ble.ScanAvailability
import fyi.blep.core.model.BleDevice
import fyi.blep.core.platform.epochMillis
import fyi.blep.AppSettings
import fyi.blep.ScanMode
import fyi.blep.resources.Res
import fyi.blep.resources.app_tagline
import fyi.blep.resources.action_cancel
import fyi.blep.resources.action_clear
import fyi.blep.resources.action_save
import fyi.blep.resources.donate_dialog_action
import fyi.blep.resources.donate_dialog_body
import fyi.blep.resources.donate_dialog_title
import fyi.blep.resources.avail_bluetooth_off
import fyi.blep.resources.avail_location_off
import fyi.blep.resources.avail_permission
import fyi.blep.resources.avail_permission_blocked
import fyi.blep.resources.avail_unsupported
import fyi.blep.resources.dbm
import fyi.blep.resources.device_left_behind_badge
import fyi.blep.resources.action_ok
import fyi.blep.resources.fg_status_title
import fyi.blep.resources.fg_status_body
import fyi.blep.resources.fg_status_watching
import fyi.blep.resources.fg_status_disable
import fyi.blep.resources.watch_status_title
import fyi.blep.resources.watch_status_body
import fyi.blep.resources.discovery_empty_title
import fyi.blep.resources.discovery_hint
import fyi.blep.resources.hide_unnamed
import fyi.blep.resources.nearby_count
import fyi.blep.resources.paired_button
import fyi.blep.resources.paired_sheet_empty
import fyi.blep.resources.paired_sheet_hint
import fyi.blep.resources.paired_sheet_title
import fyi.blep.resources.rename_label
import fyi.blep.resources.rename_title
import fyi.blep.resources.a11y_donate
import fyi.blep.resources.a11y_details
import fyi.blep.resources.a11y_favorite
import fyi.blep.resources.safety_entry_subtitle
import fyi.blep.resources.safety_entry_title
import fyi.blep.resources.settings_title
import fyi.blep.resources.section_frozen
import fyi.blep.resources.section_frozen_new
import fyi.blep.resources.section_nearby
import fyi.blep.resources.suspect_button
import fyi.blep.resources.show_unnamed_many
import fyi.blep.resources.show_unnamed_one
import fyi.blep.resources.status_connected
import fyi.blep.resources.status_paired
import fyi.blep.resources.status_no_signal
import fyi.blep.resources.status_via_watch
import fyi.blep.resources.status_lost
import fyi.blep.resources.settings_scan_off
import fyi.blep.resources.settings_scan_interval
import fyi.blep.resources.settings_scan_continuous
import fyi.blep.resources.settings_interval_min
import fyi.blep.resources.settings_interval_h
import fyi.blep.resources.settings_interval_hm
import fyi.blep.ui.rememberAvailabilityAction
import fyi.blep.ui.rememberBlePermissionRecovery
import fyi.blep.ui.theme.BlepColors
import fyi.blep.ui.theme.ExitIcon
import fyi.blep.ui.theme.GiftIcon
import fyi.blep.ui.theme.PencilIcon
import fyi.blep.ui.theme.StarFilledIcon
import fyi.blep.ui.theme.StarOutlineIcon
import fyi.blep.ui.theme.BlepLogo
import fyi.blep.ui.theme.HeartIcon
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun DiscoveryScreen(
    devices: List<BleDevice>,
    pairedDevices: List<BleDevice>,
    nearbyCount: Int,
    unnamedCount: Int,
    availability: ScanAvailability,
    includeUnnamed: Boolean,
    onToggleUnnamed: () -> Unit,
    onSelect: (BleDevice) -> Unit,
    onDetails: (BleDevice) -> Unit,
    onToggleFavorite: (BleDevice) -> Unit,
    onSafetyScan: () -> Unit,
    onSettings: () -> Unit,
    onDonate: () -> Unit,
    watchConnected: Boolean = false,
    watchedCount: Int = 0,
    onDisableForeground: () -> Unit = {},
    scanMode: ScanMode = ScanMode.OFF,
    onSetScanMode: (ScanMode) -> Unit = {},
    intervalMinutes: Int = 30,
    onIntervalChange: (Int) -> Unit = {},
    suspectIds: Set<String> = emptySet(),
    modifier: Modifier = Modifier,
    rotationOf: (String) -> RotationStats? = { null },
    frozen: Boolean = false,
    newSinceFreeze: Int = 0,
    onFreeze: () -> Unit = {},
    onUnfreeze: () -> Unit = {},
) {
    var showPaired by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var onlySuspects by remember { mutableStateOf(false) }
    var showDonate by remember { mutableStateOf(false) }
    var showFgInfo by remember { mutableStateOf(false) }
    var showWatchInfo by remember { mutableStateOf(false) }
    // A coarse clock (1 min) so a pinned device's "lost for >1h" state updates over time.
    val nowMs by produceState(epochMillis()) { while (true) { delay(60_000); value = epochMillis() } }

    // Counted off the visible list, not the raw id set, so the number always matches what
    // the filter would actually show.
    val suspectCount = devices.count { it.id in suspectIds }
    // The chip disappears once nothing is suspected, so drop the filter with it — otherwise
    // the user is left staring at an empty list with no control to undo it.
    if (suspectCount == 0 && onlySuspects) onlySuspects = false
    val shownDevices = if (onlySuspects) devices.filter { it.id in suspectIds } else devices

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Header(deviceCount = nearbyCount, watchConnected = watchConnected, onWatchClick = { showWatchInfo = true })
        Spacer(Modifier.height(14.dp))
        SafetyEntry(onSafetyScan)
        Spacer(Modifier.height(16.dp))

        AnimatedVisibility(availability != ScanAvailability.READY) {
            AvailabilityBanner(availability)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // The label carries the pinned state: a list that has quietly stopped
            // updating is indistinguishable from a list with nothing to say, so it has to
            // announce itself — with the count of what's queued up behind the pin.
            if (frozen) {
                FrozenLabel(newCount = newSinceFreeze, modifier = Modifier.weight(1f))
            } else {
                SectionLabel(stringResource(Res.string.section_nearby), Modifier.weight(1f))
            }
            // Background-activity chip, coloured by what's running: continuous tracker scan
            // (blue), interval scan (amber), or only watching your things (gray).
            val bgChipColor = when {
                scanMode == ScanMode.CONTINUOUS -> BlepColors.Blue
                scanMode == ScanMode.INTERVAL -> Color(0xFFE8A33D)
                watchedCount > 0 -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                else -> null
            }
            if (bgChipColor != null) {
                BackgroundStatusChip(bgChipColor) { showFgInfo = true }
                Spacer(Modifier.width(8.dp))
            }
            // Suspects the safety layer is currently watching. Doubles as a filter: in a
            // busy street the one device that matters is otherwise buried in the churn.
            if (suspectCount > 0) {
                SuspectPill(
                    count = suspectCount,
                    active = onlySuspects,
                    onClick = { onlySuspects = !onlySuspects },
                )
                Spacer(Modifier.width(8.dp))
            }
            if (pairedDevices.isNotEmpty()) {
                PairedPill(count = pairedDevices.size, onClick = { showPaired = true })
                Spacer(Modifier.width(8.dp))
            }
            SettingsButton(onClick = onSettings)
        }
        Spacer(Modifier.height(10.dp))

        // Any scroll pins the list. Fires on the *gesture*, not on settled position, so
        // the order is already fixed by the time the finger has moved a few pixels.
        LaunchedEffect(listState.isScrollInProgress) {
            if (listState.isScrollInProgress) onFreeze()
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                // A press pins it too, before any scroll starts — reaching for a row is
                // the moment it must stop moving, and a tap that lands on a row that
                // slid away is the exact failure this prevents. Initial pass and no
                // consumption, so the rows still get the tap.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial)
                                .changes.firstOrNull { it.pressed }?.let { onFreeze() }
                        }
                    }
                },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (shownDevices.isEmpty() && availability == ScanAvailability.READY) {
                item { EmptyState() }
            }
            items(shownDevices, key = { it.id }) { device ->
                DeviceCard(
                    device = device,
                    onClick = { onSelect(device) },
                    onDetails = { onDetails(device) },
                    onToggleFavorite = { onToggleFavorite(device) },
                    rotation = rotationOf(device.id),
                    nowMs = nowMs,
                    suspect = device.id in suspectIds,
                    modifier = Modifier.animateItem(),
                )
            }
            // Hidden while filtering — "show N unnamed" would widen a list the user just
            // narrowed on purpose.
            if (!onlySuspects && (unnamedCount > 0 || includeUnnamed)) {
                item(key = "show-more") {
                    ShowMoreRow(
                        unnamedCount = unnamedCount,
                        expanded = includeUnnamed,
                        onClick = onToggleUnnamed,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

        DonateHeart(
            onClick = { showDonate = true },
            frozen = frozen,
            onRefresh = onUnfreeze,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(20.dp),
        )
    }

    if (showPaired) {
        PairedSheet(
            devices = pairedDevices,
            onDismiss = { showPaired = false },
            onSelect = { showPaired = false; onSelect(it) },
            onToggleFavorite = onToggleFavorite,
        )
    }

    if (showDonate) {
        AlertDialog(
            onDismissRequest = { showDonate = false },
            icon = { Icon(rememberVectorPainter(GiftIcon), contentDescription = null, tint = BlepColors.Blue, modifier = Modifier.size(28.dp)) },
            title = { Text(stringResource(Res.string.donate_dialog_title), color = MaterialTheme.colorScheme.onBackground) },
            text = {
                Text(
                    stringResource(Res.string.donate_dialog_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            },
            confirmButton = {
                TextButton(onClick = { showDonate = false; onDonate() }) {
                    Icon(rememberVectorPainter(HeartIcon), contentDescription = null, tint = BlepColors.Pink, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.donate_dialog_action), color = BlepColors.Blue)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDonate = false }) {
                    Text(stringResource(Res.string.action_cancel), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                }
            },
        )
    }

    if (showFgInfo) {
        AlertDialog(
            onDismissRequest = { showFgInfo = false },
            title = { Text(stringResource(Res.string.fg_status_title)) },
            text = {
                Column {
                    Text(stringResource(Res.string.fg_status_body))
                    Spacer(Modifier.height(12.dp))
                    // Switch the background tracker scan right here.
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f))
                            .padding(2.dp),
                    ) {
                        ModeChip(stringResource(Res.string.settings_scan_off), scanMode == ScanMode.OFF) { onSetScanMode(ScanMode.OFF) }
                        ModeChip(stringResource(Res.string.settings_scan_interval), scanMode == ScanMode.INTERVAL) { onSetScanMode(ScanMode.INTERVAL) }
                        ModeChip(stringResource(Res.string.settings_scan_continuous), scanMode == ScanMode.CONTINUOUS) { onSetScanMode(ScanMode.CONTINUOUS) }
                    }
                    if (scanMode == ScanMode.INTERVAL) {
                        val min = AppSettings.INTERVAL_MIN
                        val max = AppSettings.INTERVAL_MAX
                        Slider(
                            value = intervalMinutes.toFloat(),
                            onValueChange = { v -> onIntervalChange((v / 15f).roundToInt() * 15) },
                            valueRange = min.toFloat()..max.toFloat(),
                            steps = (max - min) / 15 - 1,
                            colors = SliderDefaults.colors(thumbColor = BlepColors.Blue, activeTrackColor = BlepColors.Blue),
                        )
                        Text(
                            intervalChipLabel(intervalMinutes),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }
                    if (watchedCount > 0) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            stringResource(Res.string.fg_status_watching, watchedCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = BlepColors.Pink,
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFgInfo = false }) { Text(stringResource(Res.string.action_ok)) } },
            // Only offer the destructive "stop watching all" when there's something to stop.
            dismissButton = if (watchedCount > 0) {
                { TextButton(onClick = { showFgInfo = false; onDisableForeground() }) { Text(stringResource(Res.string.fg_status_disable), color = BlepColors.Pink) } }
            } else null,
        )
    }

    if (showWatchInfo) {
        AlertDialog(
            onDismissRequest = { showWatchInfo = false },
            title = { Text(stringResource(Res.string.watch_status_title)) },
            text = { Text(stringResource(Res.string.watch_status_body)) },
            confirmButton = { TextButton(onClick = { showWatchInfo = false }) { Text(stringResource(Res.string.action_ok)) } },
        )
    }
}

/** Small top-bar status chip: a tappable glyph, sized like the settings cog. */
@Composable
private fun StatusChipGlyph(glyph: String, color: Color, label: String, onClick: () -> Unit) {
    Text(
        glyph,
        style = MaterialTheme.typography.titleMedium,
        color = color,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(6.dp)
            .semantics { contentDescription = label },
    )
}

/** Background-activity indicator — colour says which mode (blue=continuous, amber=interval,
 *  gray=watch-only). */
@Composable
private fun BackgroundStatusChip(color: Color, onClick: () -> Unit) =
    StatusChipGlyph("◉", color, stringResource(Res.string.fg_status_title), onClick)

/** "Connected to your watch" indicator. */
@Composable
private fun WatchChip(onClick: () -> Unit) =
    StatusChipGlyph("⌚", BlepColors.Blue, stringResource(Res.string.watch_status_title), onClick)

/** A segmented chip in the scan-mode switcher (mirrors Settings' ThemeChip). */
@Composable
internal fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (selected) BlepColors.Blue else Color.Transparent,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) BlepColors.Cream else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun intervalChipLabel(minutes: Int): String = when {
    minutes < 60 -> stringResource(Res.string.settings_interval_min, minutes)
    minutes % 60 == 0 -> stringResource(Res.string.settings_interval_h, minutes / 60)
    else -> stringResource(Res.string.settings_interval_hm, minutes / 60, minutes % 60)
}

/** Flat 2-D floating heart (no shadow/elevation) that invites a donation. */
/**
 * One button in the corner, doing whichever job the list currently needs.
 *
 * Live, it's the donate heart. Pinned, it becomes refresh — because that is the moment
 * the user needs a way back to a live list, and putting the release control anywhere
 * else would mean a second permanent affordance for a state that is usually off. The
 * heart returning is itself the signal that the list is live again.
 */
@Composable
private fun DonateHeart(
    onClick: () -> Unit,
    frozen: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(
                if (frozen) BlepColors.Blue.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surfaceVariant, // soft gray, flips with the theme
            )
            .clickable(onClick = if (frozen) onRefresh else onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (frozen) {
            RefreshGlyph(tint = BlepColors.Blue, modifier = Modifier.size(24.dp))
        } else {
            Icon(
                painter = rememberVectorPainter(HeartIcon),
                contentDescription = stringResource(Res.string.a11y_donate),
                tint = BlepColors.Pink, // mild pastel
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

/** A circular arrow — drawn rather than imported, matching the other hand-drawn glyphs
 *  here and avoiding an icon dependency for one shape. */
@Composable
private fun RefreshGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.13f
        val r = size.minDimension * 0.36f
        val c = Offset(size.width / 2f, size.height / 2f)
        // Open arc, so the arrowhead has somewhere to sit.
        drawArc(
            color = tint,
            startAngle = 55f, sweepAngle = 285f, useCenter = false,
            topLeft = Offset(c.x - r, c.y - r),
            size = Size(r * 2f, r * 2f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        val a = 55f * (PI.toFloat() / 180f)
        val tip = Offset(c.x + cos(a) * r, c.y + sin(a) * r)
        val h = size.minDimension * 0.17f
        drawPath(
            Path().apply {
                moveTo(tip.x + h * 0.9f, tip.y - h * 0.1f)
                lineTo(tip.x - h * 0.35f, tip.y - h * 0.75f)
                lineTo(tip.x - h * 0.2f, tip.y + h * 0.8f)
                close()
            },
            tint,
        )
    }
}

/** The NEARBY label while the list is pinned. Tinted rather than merely relabelled, so
 *  a glance is enough to explain why nothing is moving. */
@Composable
private fun FrozenLabel(newCount: Int, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.padding(start = 4.dp)) {
        Text(
            stringResource(Res.string.section_frozen).uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = BlepColors.Blue,
        )
        if (newCount > 0) {
            Spacer(Modifier.width(6.dp))
            Surface(shape = RoundedCornerShape(999.dp), color = BlepColors.Blue.copy(alpha = 0.14f)) {
                Text(
                    stringResource(Res.string.section_frozen_new, newCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = BlepColors.Blue,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** The "all paired" manager: every bonded device, where favourites are curated.
 *  This is the only place silent (non-advertising) paired devices appear, so the
 *  main list can stay genuinely nearby. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PairedSheet(
    devices: List<BleDevice>,
    onDismiss: () -> Unit,
    onSelect: (BleDevice) -> Unit,
    onToggleFavorite: (BleDevice) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(
                stringResource(Res.string.paired_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(Res.string.paired_sheet_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
            )
            Spacer(Modifier.height(14.dp))
            if (devices.isEmpty()) {
                Text(
                    stringResource(Res.string.paired_sheet_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(devices, key = { it.id }) { device ->
                        DeviceCard(
                            device = device,
                            onClick = { onSelect(device) },
                            onToggleFavorite = { onToggleFavorite(device) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(deviceCount: Int, watchConnected: Boolean, onWatchClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = rememberVectorPainter(BlepLogo),
            contentDescription = "blep",
            tint = Color.Unspecified,
            modifier = Modifier.size(52.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("blep", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onBackground)
            Text(
                stringResource(Res.string.app_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
            )
        }
        if (deviceCount > 0) {
            Surface(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f), shape = RoundedCornerShape(999.dp)) {
                Text(
                    stringResource(Res.string.nearby_count, deviceCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        // A persistent "connected to your watch" info indicator, top-right.
        if (watchConnected) {
            Spacer(Modifier.width(8.dp))
            WatchChip(onClick = onWatchClick)
        }
    }
}

/** Gear that opens Settings — sits in the section row, right of the Paired pill. */
@Composable
private fun SettingsButton(onClick: () -> Unit) {
    val label = stringResource(Res.string.settings_title)
    Text(
        "⚙",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(6.dp)
            .semantics { contentDescription = label },
    )
}

@Composable
private fun SafetyEntry(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = BlepColors.Blue.copy(alpha = 0.10f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🛡️", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.safety_entry_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
                Text(stringResource(Res.string.safety_entry_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f))
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = BlepColors.Blue)
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
        modifier = modifier.padding(start = 4.dp),
    )
}

/** Touchable pill beside the NEARBY label that opens the all-paired manager. */
@Composable
private fun PairedPill(count: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = BlepColors.Blue.copy(alpha = 0.10f),
    ) {
        Text(
            stringResource(Res.string.paired_button, count),
            style = MaterialTheme.typography.labelLarge,
            color = BlepColors.Blue,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** Count of currently-suspected trackers, and a toggle that narrows the list to them.
 *  Carries the same pink tint the suspect cards use, deepened while the filter is on so
 *  it's obvious the list is filtered — an unexplained short list reads as a bug. */
@Composable
private fun SuspectPill(count: Int, active: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = BlepColors.Pink.copy(alpha = if (active) 0.38f else 0.16f)
            .compositeOver(MaterialTheme.colorScheme.surface),
    ) {
        Text(
            stringResource(Res.string.suspect_button, count),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun DeviceCard(
    device: BleDevice,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    onDetails: (() -> Unit)? = null,
    rotation: RotationStats? = null,
    nowMs: Long = 0L,
    suspect: Boolean = false,
) {
    // A flagged device gone >1h with a rotating id has likely changed address — mark its
    // border dashed (vs a solid light-red border while it's still findable).
    val gone = device.probablyGone(nowMs)
    val flagBorder = BlepColors.Pink.copy(alpha = 0.45f)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        // A suspected tracker (live safety alert / tapped alert notification) gets a
        // pink-washed background so it stands out in the list at a glance. Composited
        // opaque — a translucent surface lets the elevation shadow bleed through as mud.
        color = if (suspect) BlepColors.Pink.copy(alpha = 0.16f).compositeOver(MaterialTheme.colorScheme.surface)
        else MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        // "Watch this device" (flag) marks the row with a light red border (solid while
        // findable; dashed once it's probably gone).
        border = if (device.isFlagged && !gone) BorderStroke(1.5.dp, flagBorder) else null,
        modifier = modifier.fillMaxWidth()
            .then(if (device.isFlagged && gone) Modifier.dashedBorder(flagBorder, 1.5.dp, 22.dp) else Modifier),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(device)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    device.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (device.isNamed) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                )
                Spacer(Modifier.height(2.dp))
                // Second (and last) text line: the live signal, then the rotation count and a
                // "watched" marker beside it — all on one row to keep the card two lines tall.
                // The status chip stands in only when a bonded device has no live signal.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when {
                        // bestRssi, so a device only the watch can hear still shows a
                        // reading — and shows the watch's.
                        device.bestRssi != BleDevice.RSSI_UNKNOWN -> {
                            if (device.isConnected) {
                                Box(Modifier.size(6.dp).clip(CircleShape).background(BlepColors.Blue))
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                stringResource(Res.string.dbm, device.bestRssi),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                            )
                            // Say whose reading it is when it isn't ours: the row is
                            // reporting something this phone cannot hear on its own.
                            if (device.heardBetterRemotely) {
                                Spacer(Modifier.width(6.dp))
                                StatusChip(stringResource(Res.string.status_via_watch), showDot = false)
                            }
                        }
                        device.isConnected -> StatusChip(stringResource(Res.string.status_connected), showDot = true)
                        device.isPaired -> StatusChip(stringResource(Res.string.status_paired), showDot = false)
                        // A watched (favourite/flag/left-behind) device that's pinned but has
                        // gone silent — it isn't paired, so say so rather than mislabel it.
                        else -> StatusChip(stringResource(if (gone) Res.string.status_lost else Res.string.status_no_signal), showDot = false)
                    }
                    if (rotation != null && rotation.rotations > 0) {
                        Spacer(Modifier.width(8.dp))
                        RotationBadge(rotation)
                    }
                    if (device.isTethered) {
                        Spacer(Modifier.width(8.dp))
                        LeftBehindBadge()
                    }
                }
            }
            if (device.bestRssi != BleDevice.RSSI_UNKNOWN) {
                SignalDots(rssi = device.bestRssi)
                Spacer(Modifier.width(10.dp))
            }
            FavoriteButton(isFavorite = device.isFavorite, onClick = onToggleFavorite)
            if (onDetails != null) DetailsButton(onDetails)
        }
    }
}

/** Compact "this device has changed its id N times" badge — pink when the match is
 *  still contested (an unresolved fork). Only shown once the correlator has evidence. */
@Composable
private fun RotationBadge(rotation: RotationStats) {
    Text(
        "↻ ${rotation.rotations}×",
        style = MaterialTheme.typography.labelMedium,
        color = if (rotation.contested) BlepColors.Pink else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
    )
}

/** "Watched for left-behind" marker: a small eye-like ring in the alert pink. Shown on the
 *  signal line of a device that has a leave/return alert set on it. */
@Composable
private fun LeftBehindBadge() {
    val label = stringResource(Res.string.device_left_behind_badge)
    Icon(
        rememberVectorPainter(ExitIcon),
        contentDescription = label,
        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
        modifier = Modifier.size(15.dp).semantics { contentDescription = label },
    )
}

/** Star toggle: filled gold when starred, hollow otherwise. Starred devices pin to
 *  the top of the main list even when not advertising. */
@Composable
private fun FavoriteButton(isFavorite: Boolean, onClick: () -> Unit) {
    val label = stringResource(Res.string.a11y_favorite)
    Box(
        Modifier.size(30.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            rememberVectorPainter(if (isFavorite) StarFilledIcon else StarOutlineIcon),
            contentDescription = label,
            tint = if (isFavorite) BlepColors.Gold else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun Avatar(device: BleDevice) {
    val bg = when {
        device.isConnected -> BlepColors.Blue
        device.isNamed -> BlepColors.Blue.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)
    }
    val fg = if (device.isConnected) BlepColors.Cream else BlepColors.Blue
    val initial = if (device.isNamed) device.displayName.first().uppercaseChar().toString() else "?"
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initial,
            style = MaterialTheme.typography.titleMedium,
            color = if (device.isNamed || device.isConnected) fg else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun StatusChip(label: String, showDot: Boolean) {
    Surface(color = BlepColors.Blue.copy(alpha = 0.14f), shape = RoundedCornerShape(999.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            if (showDot) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(BlepColors.Blue))
                Spacer(Modifier.width(5.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge, color = BlepColors.Blue)
        }
    }
}

@Composable
private fun DetailsButton(onClick: () -> Unit) {
    val label = stringResource(Res.string.a11y_details)
    Box(
        Modifier.size(30.dp).clip(CircleShape).clickable(onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "›",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
        )
    }
}

/** Four bars that fill based on RSSI strength, coloured by proximity. */
@Composable
private fun SignalDots(rssi: Int) {
    val strength = when {
        rssi >= -55 -> 4
        rssi >= -67 -> 3
        rssi >= -80 -> 2
        rssi >= -92 -> 1
        else -> 0
    }
    // On the dark theme the bright pastels glare, so use the muted ramp.
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (i in 1..4) {
            Box(
                Modifier
                    .width(5.dp)
                    .height((6 + i * 3).dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (i <= strength) BlepColors.proximity(strength / 4f, dark = dark)
                        else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f),
                    ),
            )
        }
    }
}

@Composable
private fun ShowMoreRow(
    unnamedCount: Int,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = if (expanded) {
        stringResource(Res.string.hide_unnamed)
    } else if (unnamedCount == 1) {
        stringResource(Res.string.show_unnamed_one, unnamedCount)
    } else {
        stringResource(Res.string.show_unnamed_many, unnamedCount)
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = BlepColors.Blue.copy(alpha = 0.10f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (expanded) "–" else "+", style = MaterialTheme.typography.titleMedium, color = BlepColors.Blue)
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = BlepColors.Blue)
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(Res.string.discovery_empty_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(Res.string.discovery_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
        )
    }
}

@Composable
private fun AvailabilityBanner(availability: ScanAvailability) {
    // For the permission case, try the OS prompt first; only once it's permanently
    // denied does [blocked] flip and we point the user at app settings instead.
    val permission = rememberBlePermissionRecovery()
    val message = when (availability) {
        ScanAvailability.BLUETOOTH_OFF -> stringResource(Res.string.avail_bluetooth_off)
        ScanAvailability.PERMISSION_REQUIRED ->
            if (permission.blocked) stringResource(Res.string.avail_permission_blocked)
            else stringResource(Res.string.avail_permission)
        ScanAvailability.LOCATION_OFF -> stringResource(Res.string.avail_location_off)
        ScanAvailability.UNSUPPORTED -> stringResource(Res.string.avail_unsupported)
        ScanAvailability.READY -> return
    }
    // Tappable recovery: request the permission / turn on the adapter / open settings
    // (UNSUPPORTED has no fix, so it isn't actionable). The chevron signals tappable.
    val fix = rememberAvailabilityAction()
    val actionable = availability != ScanAvailability.UNSUPPORTED
    val onFix: () -> Unit = {
        if (availability == ScanAvailability.PERMISSION_REQUIRED) permission.request()
        else fix(availability)
    }
    Surface(
        color = BlepColors.Pink.copy(alpha = 0.35f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            .then(if (actionable) Modifier.clickable(onClick = onFix) else Modifier),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            if (actionable) {
                Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f))
            }
        }
    }
}

// (Rename moved to the device detail page — DeviceDetailScreen owns the dialog now.)
