#!/usr/bin/env bash
# Regenerate Play/App-Store screenshots from the in-app demo mode.
#
# Boots the `blep` emulator (if needed), installs the debug build, runs the
# scripted demo "hunt", captures each screen, then composites them into a
# consistent captioned set (the "continuous thread") for the store listing.
#
#   tools/screenshots.sh             # capture + compose
#   tools/screenshots.sh --compose   # re-compose from existing raw captures
#
# Output: screenshots/raw/*.png (device frames) and screenshots/store/*.png.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RAW="$ROOT/screenshots/raw"
STORE="$ROOT/screenshots/store"
APP="$ROOT/app"
AVD="blep"
PKG="fyi.blep"
mkdir -p "$RAW" "$STORE"

# ── Android SDK env (from app/local.properties) ──────────────────────────────
ANDROID_HOME="${ANDROID_HOME:-$(sed -n 's/^sdk.dir=//p' "$APP/local.properties" 2>/dev/null)}"
export ANDROID_HOME ANDROID_SDK_ROOT="$ANDROID_HOME"
for d in "$HOME/.config/.android/avd" "$HOME/.android/avd"; do [ -d "$d/$AVD.avd" ] && export ANDROID_AVD_HOME="$d"; done
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

# fonts (fall back gracefully)
FONT_B="$(fc-match -f '%{file}' 'DejaVu Sans:bold' 2>/dev/null || true)"; FONT_B="${FONT_B:-DejaVu-Sans-Bold}"
FONT_R="$(fc-match -f '%{file}' 'DejaVu Sans' 2>/dev/null || true)"; FONT_R="${FONT_R:-DejaVu-Sans}"

caption() { # raw.png "Headline" "subtext" out.png
  local src="$1" head="$2" sub="$3" out="$4"
  local W=1080 H=2400 SHOT_W=840 R=40
  local tmp; tmp="$(mktemp -d)"
  # resize, round the corners with a generated mask, then drop a soft shadow
  magick "$src" -resize ${SHOT_W}x "$tmp/s.png"
  local w h; w=$(identify -format %w "$tmp/s.png"); h=$(identify -format %h "$tmp/s.png")
  magick -size ${w}x${h} xc:none -fill white -draw "roundrectangle 0,0,$((w-1)),$((h-1)),$R,$R" "$tmp/m.png"
  magick "$tmp/s.png" "$tmp/m.png" -alpha set -compose DstIn -composite "$tmp/r.png"
  magick "$tmp/r.png" \( +clone -background black -shadow 55x28+0+16 \) +swap -background none -layers merge +repage "$tmp/sh.png"
  # canvas + caption + screenshot
  magick -size ${W}x${H} xc:'#EEF2F6' \
    -font "$FONT_B" -pointsize 64 -fill '#27313B' -gravity north -annotate +0+150 "$head" \
    -font "$FONT_R" -pointsize 38 -fill '#566472' -gravity north -annotate +0+255 "$sub" \
    "$tmp/sh.png" -gravity center -geometry +0+180 -composite \
    "$out"
  rm -rf "$tmp"
}

if [ "${1:-}" != "--compose" ]; then
  echo "› ensuring emulator is up…"
  if ! adb devices | grep -q emulator; then
    nohup emulator @"$AVD" -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -no-snapshot -no-metrics >/tmp/emulator.log 2>&1 &
    adb wait-for-device
    until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do sleep 2; done
  fi
  echo "› building + installing demo build…"
  ( cd "$APP" && ./gradlew :composeApp:assembleDebug -q )
  adb install -r "$APP/composeApp/build/outputs/apk/debug/composeApp-debug.apk" >/dev/null

  echo "› running scripted demo + capturing…"
  adb shell am force-stop "$PKG"
  adb shell am start -n "$PKG/.MainActivity" --ez demo true >/dev/null
  sleep 4
  adb exec-out screencap -p > "$RAW/01-discovery.png"
  adb shell input tap 540 547                 # tap the first device (Ford Kuga)
  sleep 9;  adb exec-out screencap -p > "$RAW/02-tracking.png"  # mid-walk, on course
  sleep 5;  adb exec-out screencap -p > "$RAW/03-found.png"     # "Right here?"
  adb shell input tap 540 2024                # "Got it" → celebration
  sleep 2;  adb exec-out screencap -p > "$RAW/04-celebrate.png"
fi

echo "› composing captioned store set…"
caption "$RAW/01-discovery.png"  "Find what you lost"      "Every nearby Bluetooth thing, ranked by signal." "$STORE/01.png"
caption "$RAW/02-tracking.png"   "Walk right to it"        "A warm/cold pointer guides every step — no map."  "$STORE/02.png"
caption "$RAW/03-found.png"      "You're on top of it"     "Calibrate, sweep, walk, done."                    "$STORE/03.png"
caption "$RAW/04-celebrate.png"  "Found it"                "Free & open source. No ads, no tracking."         "$STORE/04.png"

echo "› brand assets (Play hi-res icon + feature graphic)…"
LOGO="$ROOT/logo.svg"
# 512² listing icon: the dog on brand Mist, opaque
rsvg-convert -w 360 -h 360 "$LOGO" -o "$STORE/.dog.png"
magick -size 512x512 xc:'#EEF2F6' "$STORE/.dog.png" -gravity center -composite -alpha off -depth 8 "$STORE/icon-512.png"
# 1024×500 feature graphic: dog + wordmark + tagline
magick -size 1024x500 xc:'#EEF2F6' \
  "$STORE/.dog.png" -gravity west -geometry +90+0 -composite \
  -font "$FONT_B" -pointsize 132 -fill '#27313B' -gravity west -annotate +500-36 "blep" \
  -font "$FONT_R" -pointsize 34  -fill '#566472' -gravity west -annotate +505+66 "Find lost Bluetooth things" \
  "$STORE/feature-1024x500.png"
rm -f "$STORE/.dog.png"
echo "✓ $STORE/{01..04}.png · icon-512.png · feature-1024x500.png"
