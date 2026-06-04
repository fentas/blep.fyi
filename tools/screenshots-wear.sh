#!/usr/bin/env bash
# Wear OS Play-listing screenshots from the in-app demo mode.
#
# Boots a Wear OS round AVD (creating it if needed), installs the wear demo build,
# runs the scripted "hunt", captures each watch screen, and composites the round
# watch face onto the brand Mist canvas (1080×1080, 1:1 — valid for the Play
# "Wear OS screenshots" slot, which wants a square image 384–3840 px per side).
#
#   tools/screenshots-wear.sh             # boot + capture + compose
#   tools/screenshots-wear.sh --compose   # re-compose from existing raw captures
#
# Output: screenshots/store/wear/{01-discovery,02-tracking,03-found}.png
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RAW="$ROOT/screenshots/raw/wear"
OUT="$ROOT/screenshots/store/wear"
APP="$ROOT/app"
AVD="blepwear"; PKG="fyi.blep"
WEAR_IMG="system-images;android-34;android-wear;x86_64"
WEAR_DEVICE="wearos_large_round"
mkdir -p "$RAW" "$OUT"

ANDROID_HOME="${ANDROID_HOME:-$(sed -n 's/^sdk.dir=//p' "$APP/local.properties" 2>/dev/null)}"
export ANDROID_HOME ANDROID_SDK_ROOT="$ANDROID_HOME"
for d in "$HOME/.config/.android/avd" "$HOME/.android/avd"; do [ -d "$d" ] && export ANDROID_AVD_HOME="$d"; done
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

FONT_B="$(fc-match -f '%{file}' 'DejaVu Sans:bold' 2>/dev/null || true)"; FONT_B="${FONT_B:-DejaVu-Sans-Bold}"
FONT_R="$(fc-match -f '%{file}' 'DejaVu Sans' 2>/dev/null || true)"; FONT_R="${FONT_R:-DejaVu-Sans}"

# Resolve which attached emulator is our Wear AVD (a phone AVD may also be running).
wear_serial() {
  for s in $(adb devices | awk '/emulator/{print $1}'); do
    [ "$(adb -s "$s" emu avd name 2>/dev/null | head -1 | tr -d '\r')" = "$AVD" ] && { echo "$s"; return; }
  done
}

if [ "${1:-}" != "--compose" ]; then
  if ! emulator -list-avds 2>/dev/null | grep -qx "$AVD"; then
    echo "› creating Wear OS AVD ($AVD)…"
    sdkmanager "$WEAR_IMG" >/dev/null
    echo "no" | avdmanager create avd -n "$AVD" -k "$WEAR_IMG" -d "$WEAR_DEVICE" --force >/dev/null
  fi
  WS="$(wear_serial)"
  if [ -z "$WS" ]; then
    echo "› booting Wear OS emulator…"
    nohup emulator @"$AVD" -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -no-snapshot -no-metrics >/tmp/emulator-wear.log 2>&1 &
    until [ -n "$(wear_serial)" ]; do sleep 2; done
    WS="$(wear_serial)"
    until [ "$(adb -s "$WS" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do sleep 3; done
  fi
  echo "› building + installing wear demo build…"
  ( cd "$APP" && ./gradlew :wearApp:assembleDebug -q )
  adb -s "$WS" install -r "$APP/wearApp/build/outputs/apk/debug/wearApp-debug.apk" >/dev/null

  echo "› running scripted hunt + capturing…"
  adb -s "$WS" shell am force-stop "$PKG"
  adb -s "$WS" shell am start -n "$PKG/fyi.blep.wear.MainActivity" --ez demo true >/dev/null
  sleep 6;  adb -s "$WS" exec-out screencap -p > "$RAW/01-discovery.png"
  adb -s "$WS" shell input tap 227 225      # tap the first chip (Keys)
  sleep 7;  adb -s "$WS" exec-out screencap -p > "$RAW/02-tracking.png"   # mid-walk, on course
  sleep 6;  adb -s "$WS" exec-out screencap -p > "$RAW/03-found.png"      # "on it"
fi

# Circle-crop the watch face and drop it on the brand canvas with a soft shadow.
compose() { # src out
  local tmp; tmp="$(mktemp -d)"
  local w; w=$(identify -format %w "$1")            # native watch is square (e.g. 454)
  magick -size ${w}x${w} xc:none -fill white -draw "circle $((w/2)),$((w/2)) $((w/2)),2" "$tmp/m.png"
  magick "$1" "$tmp/m.png" -alpha set -compose DstIn -composite "$tmp/watch.png"
  magick "$tmp/watch.png" -resize 860x860 "$tmp/w.png"
  magick -size 1080x1080 xc:'#EEF2F6' \
    \( "$tmp/w.png" \( +clone -background black -shadow 60x30+0+12 \) +swap -background none -layers merge +repage \) \
    -gravity center -composite "$2"
  rm -rf "$tmp"
}

echo "› composing branded 1080×1080 wear set…"
for f in 01-discovery 02-tracking 03-found; do
  [ -f "$RAW/$f.png" ] && compose "$RAW/$f.png" "$OUT/$f.png"
done

echo "✓ Wear OS screenshots → $OUT/{01-discovery,02-tracking,03-found}.png  (1080×1080, 1:1)"
