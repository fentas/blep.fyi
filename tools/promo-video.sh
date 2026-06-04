#!/usr/bin/env bash
# Generate the Play / YouTube promo video from the in-app demo.
#
# Records the scripted demo "hunt" on the emulator, then composites the screen
# recording onto a branded 1920×1080 canvas (dog logo + wordmark + tagline beside
# the live phone) with ffmpeg.
#
#   tools/promo-video.sh             # record + compose
#   tools/promo-video.sh --compose   # re-compose from the existing recording
#
# Output: screenshots/promo.mp4 (upload to YouTube, then link it in Play Console).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/screenshots/promo.mp4"
REC="$ROOT/screenshots/raw/promo-rec.mp4"
APP="$ROOT/app"
AVD="blep"
PKG="fyi.blep"
mkdir -p "$ROOT/screenshots/raw"

ANDROID_HOME="${ANDROID_HOME:-$(sed -n 's/^sdk.dir=//p' "$APP/local.properties" 2>/dev/null)}"
export ANDROID_HOME ANDROID_SDK_ROOT="$ANDROID_HOME"
for d in "$HOME/.config/.android/avd" "$HOME/.android/avd"; do [ -d "$d/$AVD.avd" ] && export ANDROID_AVD_HOME="$d"; done
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

FONT_B="$(fc-match -f '%{file}' 'DejaVu Sans:bold' 2>/dev/null || true)"; FONT_B="${FONT_B:-DejaVu-Sans-Bold}"
FONT_R="$(fc-match -f '%{file}' 'DejaVu Sans' 2>/dev/null || true)"; FONT_R="${FONT_R:-DejaVu-Sans}"

# ── record the scripted hunt (discovery → track → found → celebrate) ──────────
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

  echo "› recording the demo hunt (English)…"
  adb shell "setprop persist.sys.locale en-US; setprop persist.sys.language en; setprop persist.sys.country US" || true
  adb shell pm clear "$PKG" >/dev/null
  adb shell screenrecord --time-limit 26 --bit-rate 10000000 /sdcard/promo-rec.mp4 &
  sleep 1
  adb shell am start -n "$PKG/.MainActivity" --ez demo true >/dev/null
  sleep 5                              # discovery
  adb shell input tap 540 721          # → tracking (first device)
  sleep 11                             # the hunt → "Right here?"
  adb shell input tap 540 2024         # "Got it" → celebration
  sleep 5                              # celebration + let screenrecord finalize
  adb pull /sdcard/promo-rec.mp4 "$REC" >/dev/null
fi

[ -f "$REC" ] || { echo "no recording at $REC — run without --compose first" >&2; exit 1; }

# ── composite onto the branded canvas ────────────────────────────────────────
echo "› compositing branded 1920×1080 video…"
rsvg-convert -w 150 -h 150 "$ROOT/logo.svg" -o /tmp/blep-dog.png
DUR="$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$REC")"
FADEOUT="$(awk -v d="$DUR" 'BEGIN{printf "%.2f", d-0.7}')"
FILTER="$(mktemp)"
cat > "$FILTER" <<EOF
[0:v]scale=-2:980,format=yuv420p[ph];
[1:v][ph]overlay=x=1360:y=(H-h)/2:shortest=1[a];
[a][2:v]overlay=x=150:y=150[b];
[b]drawtext=fontfile=$FONT_B:text='blep':fontsize=150:fontcolor=0x27313B:x=150:y=400,
drawtext=fontfile=$FONT_R:text='Find lost Bluetooth things':fontsize=50:fontcolor=0x566472:x=156:y=600,
drawtext=fontfile=$FONT_R:text='— and catch trackers following you':fontsize=50:fontcolor=0x566472:x=156:y=668,
drawtext=fontfile=$FONT_B:text='Free & open source':fontsize=40:fontcolor=0x4A90D9:x=156:y=800,
fade=t=in:st=0:d=0.6,fade=t=out:st=$FADEOUT:d=0.7[v]
EOF

ffmpeg -y -i "$REC" -f lavfi -i "color=c=0xEEF2F6:s=1920x1080:d=$DUR" -i /tmp/blep-dog.png \
  -filter_complex_script "$FILTER" -map "[v]" \
  -r 30 -c:v libx264 -pix_fmt yuv420p -movflags +faststart "$OUT" >/dev/null 2>&1
rm -f "$FILTER" /tmp/blep-dog.png

echo "✓ $OUT  ($(awk -v d="$DUR" 'BEGIN{printf "%.0f", d}')s, 1920×1080) — upload to YouTube, link in Play"
