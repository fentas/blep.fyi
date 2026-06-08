package fyi.blep.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp

/** Identity of the running build, supplied by the platform launcher (Android's
 *  MainActivity). null on iOS/Wear and in demo/screenshot mode, where the stamp
 *  is hidden. Carries the device string too, since that's platform-specific. */
data class BuildInfo(
    val versionName: String,
    val versionCode: Int,
    val gitSha: String,
    val device: String,
)

private const val ISSUES_URL = "https://github.com/fentas/blep.fyi/issues/new"

/** A quiet "v1.4.1 · a1b2c3d" line that sits on the background (no card/box) and,
 *  when tapped, opens a new GitHub issue pre-filled with the app + device info so a
 *  bug report always says exactly which build it came from. */
@Composable
fun VersionStamp(info: BuildInfo, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Text(
        text = "v${info.versionName} · ${info.gitSha}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
        modifier = modifier
            // clip bounds the tap ripple but adds no fill, so it stays on the background
            .clip(RoundedCornerShape(6.dp))
            .clickable { uriHandler.openUri(issueUrl(info)) }
            .padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

/** New-issue URL with a body the reporter can describe over, followed by an
 *  auto-stamped footer (version, build, commit, device) — so reports are traceable. */
private fun issueUrl(info: BuildInfo): String {
    val body = """
        |
        |
        |
        |---
        |blep ${info.versionName} (${info.versionCode}) · ${info.gitSha}
        |${info.device}
    """.trimMargin()
    return "$ISSUES_URL?body=${percentEncode(body)}"
}

/** Percent-encode a string for a URL query component (RFC 3986 unreserved set kept,
 *  everything else as UTF-8 %XX). Pure Kotlin — no java.net, so it works on iOS too. */
private fun percentEncode(s: String): String {
    val unreserved = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~"
    val sb = StringBuilder()
    for (byte in s.encodeToByteArray()) {
        val c = byte.toInt() and 0xFF
        if (c.toChar() in unreserved) {
            sb.append(c.toChar())
        } else {
            sb.append('%')
            sb.append(((c shr 4) and 0xF).toString(16).uppercase())
            sb.append((c and 0xF).toString(16).uppercase())
        }
    }
    return sb.toString()
}
