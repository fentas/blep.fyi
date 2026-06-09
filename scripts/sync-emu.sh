#!/usr/bin/env bash
# Reproducible two-emulator test rig for the phone↔watch sync (Wear Data Layer).
#
# Spins up a phone + Wear OS emulator (sharing one netsim radio medium), installs the
# blep debug builds, and — the fiddly part — sideloads the Wear OS companion app and
# drives the pairing wizard headlessly so the Data Layer actually has a transport. Then
# verify-sync.sh proves favourites/names/etc. cross phone↔watch.
#
# Subcommands (also exposed as `make sync-emu-*`):
#   images     install the required system images (phone google_apis + Wear)
#   avds       create the blep (phone) + blepwear (watch) AVDs if missing
#   up         boot both emulators headless on netsim; wait for them
#   apps       build + install the phone & wear debug APKs
#   companion  fetch (cached) + sideload the Wear OS companion app onto the phone
#   pair       drive the pairing wizard (sideloaded companion + uiautomator + netsim BT)
#   verify     run scripts/verify-sync.sh
#   down       kill both emulators
#   all        images → avds → up → apps → companion → pair → verify
#   status     show emulator + netsim + pairing state
#
# Env: SDK (default /home/fentas/android-sdk), PHONE/WEAR serials, AVD_PHONE/AVD_WEAR names,
#      IMG_PHONE/IMG_WEAR system-image ids.
set -uo pipefail

SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/android-sdk}}"
export ANDROID_SDK_ROOT="$SDK" ANDROID_HOME="$SDK"
# AVDs may live under ~/.config/.android/avd (not the default ~/.android/avd); honour an
# explicit ANDROID_AVD_HOME, else auto-detect so `emulator -avd` can find them.
if [ -z "${ANDROID_AVD_HOME:-}" ]; then
  for d in "$HOME/.config/.android/avd" "$HOME/.android/avd"; do
    [ -d "$d" ] && ls "$d"/*.ini >/dev/null 2>&1 && { export ANDROID_AVD_HOME="$d"; break; }
  done
fi
EMU="$SDK/emulator/emulator"
ADB="${ADB:-adb}"
SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
AVDMANAGER="$SDK/cmdline-tools/latest/bin/avdmanager"

PHONE="${PHONE:-emulator-5554}"
WEAR="${WEAR:-emulator-5556}"
AVD_PHONE="${AVD_PHONE:-blep}"
AVD_WEAR="${AVD_WEAR:-blepwear}"
IMG_PHONE="${IMG_PHONE:-system-images;android-35;google_apis;x86_64}"
IMG_WEAR="${IMG_WEAR:-system-images;android-34;android-wear;x86_64}"
PKG="fyi.blep"
COMPANION_PKG="com.google.android.apps.wear.companion"
CACHE="${CACHE:-${TMPDIR:-/tmp}/blep-emu}"
EMU_FLAGS=(-no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -no-snapshot -no-metrics)
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
mkdir -p "$CACHE"

log() { printf '\033[1;34m▶ %s\033[0m\n' "$*"; }
die() { printf '\033[1;31m✗ %s\033[0m\n' "$*" >&2; exit 1; }

uiatap() { # uiatap <serial> id|text|textc <needle> — robust resource-id/text tap
  python3 - "$1" "$2" "$3" <<'PY'
import subprocess, sys, re
dev, mode, needle = sys.argv[1:4]
subprocess.run([__import__("os").environ.get("ADB","adb"),"-s",dev,"shell","uiautomator","dump","/sdcard/u.xml"],capture_output=True)
xml=subprocess.run([__import__("os").environ.get("ADB","adb"),"-s",dev,"shell","cat","/sdcard/u.xml"],capture_output=True,text=True).stdout
for node in re.findall(r'<node[^>]*?/?>', xml):
    if ((mode=="id" and f'resource-id="{needle}"' in node) or (mode=="text" and f'text="{needle}"' in node)
        or (mode=="textc" and re.search(r'text="[^"]*'+re.escape(needle)+r'[^"]*"', node, re.I))):
        m=re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
        if m:
            x=(int(m[1])+int(m[3]))//2; y=(int(m[2])+int(m[4]))//2
            subprocess.run([__import__("os").environ.get("ADB","adb"),"-s",dev,"shell","input","tap",str(x),str(y)])
            print(f"  tapped {mode}={needle} @ {x},{y}"); sys.exit(0)
sys.exit(1)
PY
}

wait_boot() { # wait_boot <serial> [timeout_s]
  local s="$1" t="${2:-240}" i=0
  log "waiting for $s to boot (≤${t}s)"
  while [ "$i" -lt "$t" ]; do
    [ "$($ADB -s "$s" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && { echo "  ✓ $s booted"; sleep 3; return 0; }
    sleep 5; i=$((i+5))
  done
  die "$s did not boot in ${t}s"
}

cmd_images() {
  log "installing system images"
  yes | "$SDKMANAGER" "$IMG_PHONE" "$IMG_WEAR" "emulator" "platform-tools" >/dev/null || die "sdkmanager failed"
}

cmd_avds() {
  local have; have="$("$AVDMANAGER" list avd 2>/dev/null | grep -c "Name: \($AVD_PHONE\|$AVD_WEAR\)")" || true
  if "$AVDMANAGER" list avd 2>/dev/null | grep -q "Name: $AVD_PHONE"; then echo "  ✓ $AVD_PHONE exists"; else
    log "creating $AVD_PHONE"; echo no | "$AVDMANAGER" create avd -n "$AVD_PHONE" -k "$IMG_PHONE" -d pixel_6 --force; fi
  if "$AVDMANAGER" list avd 2>/dev/null | grep -q "Name: $AVD_WEAR"; then echo "  ✓ $AVD_WEAR exists"; else
    log "creating $AVD_WEAR"; echo no | "$AVDMANAGER" create avd -n "$AVD_WEAR" -k "$IMG_WEAR" -d wearos_large_round --force; fi
}

boot_one() { # boot_one <avd> <serial>
  local avd="$1" s="$2"
  if $ADB -s "$s" get-state 2>/dev/null | grep -q device; then echo "  ✓ $s already up"; return; fi
  log "launching $avd → $s"
  nohup "$EMU" -avd "$avd" "${EMU_FLAGS[@]}" >"$CACHE/$avd.log" 2>&1 &
  for i in $(seq 1 60); do $ADB -s "$s" get-state 2>/dev/null | grep -q device && break; sleep 2; done
}

cmd_up()   { boot_one "$AVD_PHONE" "$PHONE"; boot_one "$AVD_WEAR" "$WEAR"; wait_boot "$PHONE"; wait_boot "$WEAR"; }
cmd_down() { for s in "$PHONE" "$WEAR"; do $ADB -s "$s" emu kill 2>/dev/null || true; done; echo "  killed"; }

cmd_apps() {
  log "building + installing debug APKs"
  ( cd "$ROOT/app" && ./gradlew :androidApp:assembleDebug :wearApp:assembleDebug ) || die "gradle build failed"
  $ADB -s "$PHONE" install -r -d "$ROOT/app/androidApp/build/outputs/apk/debug/androidApp-debug.apk"
  $ADB -s "$WEAR"  install -r -d "$ROOT/app/wearApp/build/outputs/apk/debug/wearApp-debug.apk"
  echo "  ✓ installed (phone + wear)"
}

cmd_companion() {
  $ADB -s "$PHONE" shell pm list packages 2>/dev/null | grep -q "$COMPANION_PKG" && { echo "  ✓ companion already installed"; return; }
  local apk="$CACHE/companion.apk"
  if [ ! -s "$apk" ]; then
    log "fetching the Wear OS companion APK (apkcombo)"
    local ua="Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
    local page; page="$(curl -fsSL -m 30 -A "$ua" "https://apkcombo.com/google-pixel-watch/$COMPANION_PKG/download/apk" 2>/dev/null || true)"
    local url; url="$(printf '%s' "$page" | python3 -c 'import sys,re,urllib.parse; m=re.search(r"href=\"/r2\?u=([^\"]+)\"", sys.stdin.read()); print(urllib.parse.unquote(m.group(1)) if m else "")')"
    [ -n "$url" ] || die "could not find a companion download link (apkcombo layout changed?)"
    curl -fsSL -m 180 -A "$ua" -o "$apk" "$url" || die "companion download failed"
  fi
  unzip -l "$apk" 2>/dev/null | grep -q AndroidManifest.xml || die "downloaded companion is not a valid APK"
  log "sideloading companion ($(du -h "$apk" | cut -f1))"
  $ADB -s "$PHONE" install -r --no-incremental "$apk" || die "companion install failed"
  echo "  ✓ companion sideloaded"
}

cmd_pair() {
  # Headless emulator pairing — NOT BLE/netsim. Android Studio's "Pair Wearable" works by
  # bridging the Wear Data Layer over a plain TCP socket on port 5601, bypassing Bluetooth
  # entirely. We reproduce that bridge + drive the companion's hidden emulator-pairing entry:
  #
  #   watch app  ──connects to──▶  watch localhost:5601
  #                  (adb reverse on WEAR)  ▼
  #                              HOST localhost:5601
  #                  (adb forward on PHONE) ▼
  #                              phone localhost:5601  ◀── companion EmulatorActivity binds here
  #
  # The companion entry is AutomatedSetupActivity, an alias for
  # com.google.android.apps.wear.companion.core.application.EmulatorActivity. Launching it
  # kicks off the GMS Wear Terms-of-Service consent (TermsOfServiceActivity); accepting it
  # lets the companion bind :5601 and the watch's Wear_NetworkService sync loop connect
  # (watch logcat flips companionDisconnected → false).
  #
  # CAVEAT: this establishes the companion *link* (good enough for many Data Layer paths),
  # but full DataItem replication between the two sandboxed GMS instances over the loopback
  # bridge is unreliable, so `verify` may still not see a favourite propagate. The reliable
  # e2e proof of the CRDT + transport contract is the cross-wired SyncManagerConvergenceTest
  # (no hardware needed). Treat this rig as a smoke test of the *link*, not a sync guarantee.
  log "pairing (TCP 5601 bridge + companion EmulatorActivity)"
  # watch loopback → host; host → phone loopback. Only ONE device may own host:5601, so the
  # watch uses `reverse` (its own loopback) and the phone uses `forward` (host → phone).
  $ADB -s "$WEAR"  reverse tcp:5601 tcp:5601 >/dev/null 2>&1 || true
  $ADB -s "$PHONE" forward --remove tcp:5601 >/dev/null 2>&1 || true
  $ADB -s "$PHONE" forward tcp:5601 tcp:5601 >/dev/null 2>&1 || true

  # Launch the companion's emulator-pairing entry (the alias resolves to EmulatorActivity).
  $ADB -s "$PHONE" shell am start -n "$COMPANION_PKG/.core.application.EmulatorActivity" >/dev/null 2>&1
  sleep 6

  # GMS Terms-of-Service consent. The accept button id is stable; the first tap can just
  # scroll the long ToS, so tap it up to 3× until the screen leaves TermsOfServiceActivity.
  for i in 1 2 3; do
    $ADB -s "$PHONE" shell dumpsys window 2>/dev/null | grep -q TermsOfServiceActivity || break
    uiatap "$PHONE" id com.google.android.gms:id/terms_of_service_accept_button && sleep 4 || break
  done
  # Runtime permission prompts (location/nearby) the companion may raise.
  for i in 1 2 3 4 5; do uiatap "$PHONE" id com.android.permissioncontroller:id/permission_allow_button && sleep 2 || break; done
  sleep 6

  local state; state="$($ADB -s "$WEAR" logcat -d 2>/dev/null | grep -oE 'companionDisconnected=(true|false)' | tail -1)"
  if [ "$state" = "companionDisconnected=false" ]; then
    echo "  ✓ companion link up ($state). Note: DataItem propagation over the bridge is"
    echo "    best-effort — see SyncManagerConvergenceTest for the authoritative e2e proof."
  else
    echo "  ⚠ companion not connected yet ($state). Re-run, or check the consent screen:"
    echo "    adb -s $PHONE shell dumpsys window | grep mCurrentFocus"
  fi
}

cmd_status() {
  echo "emulators:"; $ADB devices | grep emulator || echo "  (none)"
  echo "netsim radios:"; curl -fsS -m 5 http://localhost:7681/v1/devices 2>/dev/null | python3 -c 'import json,sys; d=json.load(sys.stdin);
[print("  ",x["name"],[c["kind"] for c in x["chips"]]) for x in d["devices"]]' 2>/dev/null || echo "  (netsim not reachable)"
  echo "companion installed on phone: $($ADB -s "$PHONE" shell pm list packages 2>/dev/null | grep -c "$COMPANION_PKG")"
  echo "watch companion connected: $($ADB -s "$WEAR" logcat -d 2>/dev/null | grep -oE 'companionDisconnected=(true|false)' | tail -1)"
}

cmd_verify() { PHONE="$PHONE" WEAR="$WEAR" "$ROOT/scripts/verify-sync.sh"; }
cmd_all() { cmd_images; cmd_avds; cmd_up; cmd_apps; cmd_companion; cmd_pair; cmd_verify; }

case "${1:-}" in
  images|avds|up|down|apps|companion|pair|verify|status|all) "cmd_$1" ;;
  *) grep -E '^#( |$)' "$0" | sed 's/^# \{0,1\}//'; exit 1 ;;
esac
