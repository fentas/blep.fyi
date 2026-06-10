package fyi.blep.ui.screens

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import fyi.blep.core.ble.AddressKind
import fyi.blep.core.ble.addressKind
import fyi.blep.core.model.BleDevice

/** How long since this device's signal was last seen, in ms — null when it's present or
 *  was never seen with a live signal. */
internal fun BleDevice.lostForMs(nowMs: Long): Long? =
    if (!isPresent && seenAtMs > 0) nowMs - seenAtMs else null

/** True for a device gone long enough (>1h) that, if its address rotates, it has almost
 *  certainly changed id and is effectively unfindable. A stable/bonded device could still
 *  come back, so it never counts as gone. */
internal fun BleDevice.probablyGone(nowMs: Long): Boolean =
    (lostForMs(nowMs) ?: 0L) > 3_600_000L && addressKind(id) == AddressKind.RANDOM

/** Draws a dashed rounded outline over the content (Compose's BorderStroke can't dash). */
internal fun Modifier.dashedBorder(color: Color, width: Dp, radius: Dp): Modifier = drawWithContent {
    drawContent()
    val w = width.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(w / 2, w / 2),
        size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 6, w * 4))),
    )
}
