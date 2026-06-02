package fyi.blep.core.spatial

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import fyi.blep.core.ble.BlepContext

/**
 * Android haptics via [Vibrator] plus a short, quiet audible tick via
 * [ToneGenerator] — a metal-detector style cue. All calls are defensively
 * wrapped so a missing vibrator or audio focus can never crash tracking.
 */
internal class AndroidHaptic(context: Context) : Haptic {

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }.getOrNull()

    private val tone: ToneGenerator? =
        runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 35) }.getOrNull()

    override fun pulse(intensity: Float) {
        val amp = (40 + 215 * intensity).toInt().coerceIn(1, 255)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(26, amp))
            } else {
                @Suppress("DEPRECATION") vibrator?.vibrate(26)
            }
        }
        runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 16) }
    }

    override fun success() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 40, 60, 120), -1))
            } else {
                @Suppress("DEPRECATION") vibrator?.vibrate(longArrayOf(0, 40, 60, 120), -1)
            }
        }
        runCatching { tone?.startTone(ToneGenerator.TONE_PROP_ACK, 150) }
    }

    override fun release() {
        runCatching { tone?.release() }
    }
}

actual fun createHaptic(): Haptic =
    BlepContext.app?.let { AndroidHaptic(it) } ?: NoHaptic
