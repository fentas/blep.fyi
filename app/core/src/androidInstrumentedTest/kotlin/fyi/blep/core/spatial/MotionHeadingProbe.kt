package fyi.blep.core.spatial

import android.content.Context
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Instrumentation probe used by `tools/ble-netsim/check_motion_bridge.sh`: reports
 * the current fused heading so the host can inject an orientation (`adb emu sensor
 * set`), read it back here, and assert the heading *tracks the yaw*. Emits the
 * reading via the instrumentation status bundle (key `blep_heading`, radians).
 */
@RunWith(AndroidJUnit4::class)
class MotionHeadingProbe {

    @Test
    fun read() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val provider = AndroidMotionProvider(ctx)
        val headings = runBlocking {
            withTimeoutOrNull(8_000) {
                provider.motion().mapNotNull { it.headingRad }.take(12).toList()
            }
        } ?: error("no heading from the device sensor HAL")

        // Circular mean — robust across the ±π wrap.
        val mean = atan2(headings.sumOf { sin(it) }, headings.sumOf { cos(it) })
        InstrumentationRegistry.getInstrumentation()
            .sendStatus(0, Bundle().apply { putString("blep_heading", mean.toString()) })
    }
}
