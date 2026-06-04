#!/usr/bin/env bash
# Dynamic motion-bridge check: inject device yaw through the emulator's sensor HAL
# and assert AndroidMotionProvider's fused heading *tracks* it (~90° of heading per
# 90° of injected yaw, consistent direction). This catches the remap / scale / sign
# regressions the static smoke test (AndroidMotionProviderBridgeTest) can't see.
#
# It can't run from inside an instrumented test (sensor injection is host-side via
# `adb emu`), so the host drives it and reads the heading back through a tiny
# instrumentation probe (MotionHeadingProbe → status bundle `blep_heading`).
#
# Usage: tools/ble-netsim/check_motion_bridge.sh   (needs a running emulator)
set -uo pipefail

ADB="${ANDROID_HOME:-$HOME/android-sdk}/platform-tools/adb"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
RUNNER="fyi.blep.core.test/androidx.test.runner.AndroidJUnitRunner"

$ADB shell pm list instrumentation 2>/dev/null | grep -q fyi.blep.core.test || {
  echo "→ installing instrumented test APK"
  (cd "$ROOT/app" && mise exec -- ./gradlew :core:installDebugAndroidTest -q) || exit 1
}

probe() {
  $ADB shell am instrument -w -r -e class fyi.blep.core.spatial.MotionHeadingProbe#read "$RUNNER" 2>/dev/null \
    | sed -n 's/.*blep_heading=\(-\?[0-9.eE]*\).*/\1/p' | head -1
}

$ADB shell am force-stop fyi.blep 2>/dev/null
$ADB emu sensor set acceleration 0:0:9.81 >/dev/null 2>&1 # hold the device flat

echo "→ injecting four 90° yaw steps and reading the fused heading"
fields=("0:40:0" "40:0:0" "0:-40:0" "-40:0:0" "0:40:0")
headings=()
for f in "${fields[@]}"; do
  $ADB emu sensor set magnetic-field "$f" >/dev/null 2>&1
  sleep 2
  h="$(probe)"
  [ -n "$h" ] || { echo "FAIL: no heading reading from the probe" >&2; exit 1; }
  headings+=("$h")
  printf '  field=%-8s heading=%s rad\n' "$f" "$h"
done

awk -v vals="${headings[*]}" 'BEGIN {
  n = split(vals, h, " "); pi = 3.14159265358979; tol = 0.6; sign = 0; ok = 1
  for (i = 1; i < n; i++) {
    d = h[i+1] - h[i]
    while (d >  pi) d -= 2*pi
    while (d < -pi) d += 2*pi
    mag = (d < 0 ? -d : d)
    printf("  step %d: Δ = %+.3f rad (%+.0f°)\n", i, d, d*180/pi)
    if (mag < pi/2 - tol || mag > pi/2 + tol) { ok = 0; print "    ✗ not a ~90° response" }
    s = (d < 0 ? -1 : 1)
    if (sign == 0) sign = s; else if (s != sign) { ok = 0; print "    ✗ turn direction flipped" }
  }
  if (ok) { print "PASS: fused heading tracks injected yaw (~90°/step, consistent sign)"; exit 0 }
  print "FAIL: motion bridge did not track injected yaw"; exit 1
}'
