package fyi.blep.core.spatial

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI

/**
 * On-device "bridge" test for the motion glue the JVM sims bypass: drives the real
 * Android [android.hardware.SensorManager] through [AndroidMotionProvider] and
 * checks the fused rotation-vector → heading transform (getRotationMatrixFromVector
 * → remapCoordinateSystem(AXIS_X, AXIS_Z) → getOrientation) actually runs on a real
 * device and yields well-formed samples.
 *
 * This is plumbing coverage, not path-finding: it can't assert heading-vs-true-north
 * (the emulator's default sensor state has no controlled orientation, and netsim has
 * no RSSI gradient anyway). Convergence stays in the JVM TrackingSimulationTest.
 *
 * Run: `:core:connectedDebugAndroidTest` (needs a running emulator/device).
 */
@RunWith(AndroidJUnit4::class)
class AndroidMotionProviderBridgeTest {

    @Test
    fun real_fused_sensors_produce_well_formed_samples_with_a_finite_heading() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val provider = AndroidMotionProvider(ctx)

        // The fused rotation-vector sensor is continuous, so samples should arrive
        // promptly even with the device held still.
        val samples = withTimeoutOrNull(10_000) { provider.motion().take(8).toList() }

        assertNotNull("no motion samples from the device sensor HAL within 10s", samples)
        assertTrue("expected at least one motion sample", samples!!.isNotEmpty())

        val headings = samples.mapNotNull { it.headingRad }
        assertTrue(
            "rotation-vector → heading never produced a value — the SensorManager " +
                "wiring or the matrix transform isn't running on this device",
            headings.isNotEmpty(),
        )
        headings.forEach { h ->
            assertTrue("heading not finite: $h", h.isFinite())
            assertTrue("heading out of [-π, π]: $h", h in -PI..PI)
        }

        // Samples must be monotonically timestamped and never carry NaN motion.
        samples.zipWithNext().forEach { (a, b) ->
            assertTrue("timestamps not monotonic", b.timeMs >= a.timeMs)
        }
        samples.forEach {
            assertTrue("speed is NaN", !it.speedMps.isNaN())
            assertTrue("vertical speed is NaN", !it.verticalMps.isNaN())
        }
    }
}
