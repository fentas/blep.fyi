package fyi.blep

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // BLE permissions are requested by the in-app flow — onboarding's last card,
        // then the tappable availability banner for recovery — not blindly on launch,
        // so the OS prompt never pops over the first-run intro.
        // `--ez demo true` → scripted data for screenshots; no scan/permission needed.
        val demo = intent?.getBooleanExtra("demo", false) == true
        setContent { App(demo = demo) }
    }
}
