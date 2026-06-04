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
# Output (under screenshots/):
#   raw/*.png               — device captures
#   store/phone/*.png       — 1440×2560 (9:16) · Phone + 7" + 10" tablet slots
#   store/chromebook/*.png  — 2560×1440 (16:9) · Chromebook slot
#   store/icon-512.png · store/feature-1024x500.png
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

# Round corners (mask) + soft shadow on a pre-sized PNG.
frame() { # in out radius
  local in="$1" out="$2" R="$3" tmp; tmp="$(mktemp -d)"
  local w h; w=$(identify -format %w "$in"); h=$(identify -format %h "$in")
  magick -size ${w}x${h} xc:none -fill white -draw "roundrectangle 0,0,$((w-1)),$((h-1)),$R,$R" "$tmp/m.png"
  magick "$in" "$tmp/m.png" -alpha set -compose DstIn -composite "$tmp/r.png"
  magick "$tmp/r.png" \( +clone -background black -shadow 55x28+0+16 \) +swap -background none -layers merge +repage "$out"
  rm -rf "$tmp"
}

# 9:16 portrait (1440×2560): caption on top, phone mockup below. This single size
# is valid for the Phone, 7-inch tablet AND 10-inch tablet slots (each side is
# 1080–7680 px and the ratio is exactly 9:16, which Play accepts everywhere).
cap_portrait() { # src head sub out
  local tmp; tmp="$(mktemp -d)"
  magick "$1" -resize 980x "$tmp/s.png"
  frame "$tmp/s.png" "$tmp/sh.png" 44
  magick -size 1440x2560 xc:'#EEF2F6' \
    -font "$FONT_B" -pointsize 78 -fill '#27313B' -gravity north -annotate +0+150 "$2" \
    -font "$FONT_R" -pointsize 44 -fill '#566472' -gravity north -annotate +0+270 "$3" \
    "$tmp/sh.png" -gravity north -geometry +0+340 -composite \
    "$4"
  rm -rf "$tmp"
}

# 16:9 landscape (2560×1440): phone mockup on the right, caption beside it. For the
# Chromebook slot (and usable for a tablet-landscape set if you want one).
cap_landscape() { # src head sub out
  local tmp; tmp="$(mktemp -d)"
  magick "$1" -resize x1180 "$tmp/s.png"
  frame "$tmp/s.png" "$tmp/sh.png" 40
  magick -size 2560x1440 xc:'#EEF2F6' \
    "$tmp/sh.png" -gravity east -geometry +240+0 -composite \
    -font "$FONT_B" -pointsize 84 -fill '#27313B' -gravity west -annotate +150-48 "$2" \
    -font "$FONT_R" -pointsize 42 -fill '#566472' -gravity west -annotate +150+56 "$3" \
    "$4"
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
  adb shell input tap 540 721                 # tap the first device (Ford Kuga)
  sleep 9;  adb exec-out screencap -p > "$RAW/02-tracking.png"  # mid-walk, on course
  sleep 5;  adb exec-out screencap -p > "$RAW/03-found.png"     # "Right here?"
  adb shell input tap 540 2024                # "Got it" → celebration
  sleep 2;  adb exec-out screencap -p > "$RAW/04-celebrate.png"

  echo "› capturing the safety scan (anti-stalking)…"
  adb shell pm clear "$PKG" >/dev/null        # fresh state so the demo AirTag alerts (not muted)
  adb shell am start -n "$PKG/.MainActivity" --ez demo true >/dev/null
  sleep 4
  adb shell input tap 420 460                 # open "Is something tracking you?"
  sleep 8;  adb exec-out screencap -p > "$RAW/05-safety.png"    # "Find My tracker may be following you"
fi

PHONE="$STORE/phone"; CHROME="$STORE/chromebook"
mkdir -p "$PHONE" "$CHROME"

# The captioned "continuous thread": one headline + sub per raw frame.
frames=(01-discovery 02-tracking 03-found 04-celebrate 05-safety)
heads=("Find what you lost" "Walk right to it" "You're on top of it" "Found it" "Is something tracking you?")
subs=("Every nearby Bluetooth thing, ranked by signal." \
      "A warm/cold pointer guides every step — no map." \
      "Calibrate, sweep, walk, done." \
      "Free & open source. No ads, no tracking." \
      "Spot unwanted AirTags & trackers — then find them.")

echo "› composing captioned sets (phone/tablet 9:16 + chromebook 16:9)…"
for i in "${!frames[@]}"; do
  n=$(printf "%02d" $((i + 1)))
  cap_portrait  "$RAW/${frames[$i]}.png" "${heads[$i]}" "${subs[$i]}" "$PHONE/$n.png"
  cap_landscape "$RAW/${frames[$i]}.png" "${heads[$i]}" "${subs[$i]}" "$CHROME/$n.png"
done

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
echo ""
echo "✓ Phone + 7\" + 10\" tablet  →  $PHONE/{01..05}.png   (1440×2560, 9:16)"
echo "✓ Chromebook                →  $CHROME/{01..05}.png  (2560×1440, 16:9)"
echo "✓ App icon                  →  $STORE/icon-512.png    (512×512)"
echo "✓ Feature graphic           →  $STORE/feature-1024x500.png"
