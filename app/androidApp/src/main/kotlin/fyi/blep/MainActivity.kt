package fyi.blep

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import fyi.blep.ui.BuildInfo

class MainActivity : ComponentActivity() {

    /** A tapped tracker-alert notification carries the suspect's device id — hand it to
     *  the shared controller (cold start via onCreate, warm via onNewIntent/singleTop). */
    private fun consumeDeepLink(intent: Intent?) {
        intent?.getStringExtra(EXTRA_SUSPECT_ID)?.let { DeepLink.suspectId.value = it }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeDeepLink(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeDeepLink(intent)
        // BLE permissions are requested by the in-app flow — onboarding's last card,
        // then the tappable availability banner for recovery — not blindly on launch,
        // so the OS prompt never pops over the first-run intro.
        // `--ez demo true` → scripted data for screenshots; no scan/permission needed.
        val demo = intent?.getBooleanExtra("demo", false) == true
        // Real builds carry their identity for the on-screen version stamp; demo
        // (screenshot) runs pass null so the stamp stays out of the store images.
        val buildInfo = if (demo) null else BuildInfo(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            gitSha = BuildConfig.GIT_SHA,
            device = "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        )
        setContent { App(demo = demo, buildInfo = buildInfo) }
    }
}
