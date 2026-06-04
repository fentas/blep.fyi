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
        if (!demo) requestBlePermissions()
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
        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        permissionLauncher.launch(needed)
    }
}
