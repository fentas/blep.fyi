package fyi.blep

// iOS background BLE is a different model (CoreBluetooth state preservation); not
// wired yet, so these are no-ops.
actual object BackgroundScan {
    actual fun applyPeriodic(enabled: Boolean, intervalMinutes: Int) {}
    actual fun setForeground(active: Boolean) {}
}
