package fyi.blep.wear

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import fyi.blep.core.ble.createBleScanner

class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // `--ez demo true` swaps in the scripted hunt (store screenshots/previews);
        // off in normal use, where it requests BLE permissions and scans for real.
        val demo = intent?.getBooleanExtra("demo", false) == true
        if (!demo) {
            requestBlePermissions()
            // Light, foreground-service-free anti-tracking watch: a periodic (~30 min)
            // check that notifies if a tracker seems to be following you.
            WearSafetyScan.enqueue(applicationContext)
        }
        setContent {
            val scope = rememberCoroutineScope()
            val controller = remember(scope) {
                if (demo) {
                    WearController(DemoWearScanner(), scope, motionProvider = DemoWearMotion())
                } else {
                    WearController(createBleScanner(), scope)
                }
            }
            WearApp(controller)
        }
    }

    private fun requestBlePermissions() {
        val needed = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN); add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            // So the periodic safety check's "tracker following you" alert can show.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
        permissionLauncher.launch(needed)
    }
}
