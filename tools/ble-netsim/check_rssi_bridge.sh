#!/usr/bin/env bash
# RSSI bridge smoke test: prove AndroidBleScanner.rssi() streams a live reading
# all the way to the tracking screen, using a netsim+Bumble-injected peripheral.
#
# This validates the *platform glue* the JVM tracking sim bypasses (the real BLE
# scan → RSSI path). It does NOT test path-finding: netsim assigns a single static
# RSSI, so there's no warmer/colder gradient — that stays in TrackingSimulationTest.
#
# Usage: tools/ble-netsim/check_rssi_bridge.sh
# Needs: a running blep emulator + tools/ble-netsim/venv (bumble).
set -uo pipefail

ADB="${ANDROID_HOME:-$HOME/android-sdk}/platform-tools/adb"
APP=fyi.blep
HERE="$(cd "$(dirname "$0")" && pwd)"
PY="$HERE/venv/bin/python"
PORT="$(sed -n 's/^grpc.port=//p' "${TMPDIR:-/tmp}"/netsim.ini 2>/dev/null)"
PORT="${PORT:-35677}"

fail() { echo "FAIL: $*" >&2; cleanup; exit 1; }
cleanup() { [ -n "${ADV_PID:-}" ] && kill "$ADV_PID" 2>/dev/null; $ADB shell am force-stop "$APP" 2>/dev/null; }
trap cleanup EXIT

dump() { $ADB shell uiautomator dump /sdcard/b.xml >/dev/null 2>&1; $ADB pull /sdcard/b.xml /tmp/bridge.xml >/dev/null 2>&1; }
# Center-tap the element whose text matches $1.
tap_text() {
  local b nums
  b="$(grep -oE "text=\"$1\"[^>]*bounds=\"\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]\"" /tmp/bridge.xml | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | head -1)"
  [ -n "$b" ] || return 1
  nums=($(echo "$b" | grep -oE '[0-9]+'))
  $ADB shell input tap $(( (${nums[0]}+${nums[2]})/2 )) $(( (${nums[1]}+${nums[3]})/2 ))
}

echo "→ advertising fake trackers via netsim :$PORT"
[ -x "$PY" ] || fail "no venv — run: python3 -m venv $HERE/venv && $HERE/venv/bin/pip install bumble"
"$PY" "$HERE/advertise.py" "$PORT" > /tmp/bridge_adv.log 2>&1 &
ADV_PID=$!
sleep 4
kill -0 "$ADV_PID" 2>/dev/null || fail "advertiser died — $(cat /tmp/bridge_adv.log)"

echo "→ launching blep (real mode) and opening discovery"
$ADB shell am force-stop "$APP"
$ADB shell am start -n "$APP/.MainActivity" >/dev/null 2>&1
sleep 6
dump
grep -q 'text="airtag"' /tmp/bridge.xml || fail "injected 'airtag' not in discovery list"
echo "  ✓ scanner sees the injected peripheral"

echo "→ tapping it to start tracking"
tap_text "airtag" || fail "could not tap the airtag row"
sleep 5
dump
# TrackingScreen renders rssi as "<n> dBm" once rssi() streams a reading.
if grep -oE 'text="-?[0-9]+ dBm"' /tmp/bridge.xml | head -1 | grep -q dBm; then
  val="$(grep -oE 'text="-?[0-9]+ dBm"' /tmp/bridge.xml | head -1)"
  echo "  ✓ AndroidBleScanner.rssi() streamed to the tracking screen: $val"
  echo "PASS: RSSI bridge"
else
  fail "tracking screen never showed a dBm reading (rssi() didn't stream)"
fi
