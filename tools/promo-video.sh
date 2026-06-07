#!/usr/bin/env bash
# Generate the full Play / YouTube promo video — a multi-segment, captioned tour of
# every feature, composed from live recordings of the in-app demo.
#
# Segments: intro card → discovery → find (radar) → anti-stalking → settings →
# outro card. Each phone capture is framed (rounded + soft shadow) on a branded
# 1920×1080 canvas with an animated caption, then crossfaded together.
#
#   tools/promo-video.sh             # record the 4 clips on the emulator, then compose
#   tools/promo-video.sh --compose   # re-compose from the existing recordings only
#
# Output: screenshots/promo.mp4 (upload to YouTube, then link it in Play Console).
# Needs: a running blep emulator (demo mode), ffmpeg, and ImageMagick (magick).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP="$ROOT/app"; AVD="blep"; PKG="fyi.blep"
RAW="$ROOT/screenshots/raw/promo"; OUT="$ROOT/screenshots/promo.mp4"
P="$(mktemp -d)"; mkdir -p "$RAW"

ANDROID_HOME="${ANDROID_HOME:-$(sed -n 's/^sdk.dir=//p' "$APP/local.properties" 2>/dev/null)}"
export ANDROID_HOME ANDROID_SDK_ROOT="$ANDROID_HOME"
for d in "$HOME/.config/.android/avd" "$HOME/.android/avd"; do [ -d "$d/$AVD.avd" ] && export ANDROID_AVD_HOME="$d"; done
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

FB="$(fc-match -f '%{file}' 'Inter:bold' 2>/dev/null)"; [ -f "$FB" ] || FB="$(fc-match -f '%{file}' 'sans-serif:bold')"
FR="$(fc-match -f '%{file}' 'Inter' 2>/dev/null)"; [ -f "$FR" ] || FR="$(fc-match -f '%{file}' 'sans-serif')"
INK=0x27313B; SLATE=0x566472; BLUE=0x5F90C3
AFADE="if(lt(t,0.25),0,min(1,(t-0.25)/0.5))"

# ── record the four demo clips ────────────────────────────────────────────────
if [ "${1:-}" != "--compose" ]; then
  # Resolve the blep emulator's serial (tolerate several attached devices).
  ADB="adb"; for s in $(adb devices | awk '/emulator/{print $1}'); do
    [ "$(adb -s "$s" emu avd name 2>/dev/null | head -1 | tr -d '\r')" = "$AVD" ] && ADB="adb -s $s" && break
  done
  echo "› building + installing demo build…"
  ( cd "$APP" && ./gradlew :composeApp:assembleDebug -q )
  $ADB install -r "$APP/composeApp/build/outputs/apk/debug/composeApp-debug.apk" >/dev/null
  $ADB shell "setprop persist.sys.locale en-US; setprop persist.sys.language en; setprop persist.sys.country US" || true
  $ADB shell "su 0 setprop ctl.restart zygote" 2>/dev/null || true
  until [ "$($ADB shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do sleep 2; done; sleep 2

  relaunch() { $ADB shell am force-stop "$PKG" >/dev/null 2>&1; $ADB shell am start -n "$PKG/.MainActivity" --ez demo true >/dev/null 2>&1; sleep 4; }
  rec() { local out=$1 dur=$2; $ADB shell screenrecord --time-limit "$dur" --bit-rate 12000000 /sdcard/r.mp4 & RP=$!; }
  pull() { wait "$RP"; $ADB pull /sdcard/r.mp4 "$RAW/$1" >/dev/null; }

  echo "› recording discovery…"; relaunch; rec discovery.mp4 6; sleep 2
  $ADB shell input swipe 540 1400 540 1150 900; pull discovery.mp4
  echo "› recording the hunt (radar)…"; relaunch; rec hunt.mp4 22; sleep 1
  $ADB shell input tap 540 760; pull hunt.mp4
  echo "› recording the safety scan…"; relaunch; rec safety.mp4 12; sleep 1
  $ADB shell input tap 418 375; pull safety.mp4
  echo "› recording settings…"; relaunch; rec settings.mp4 11; sleep 1
  $ADB shell input tap 984 580; sleep 2
  $ADB shell input swipe 540 1700 540 760 700; sleep 2; $ADB shell input swipe 540 1700 540 900 700; pull settings.mp4
fi
for f in discovery hunt safety settings; do
  [ -f "$RAW/$f.mp4" ] || { echo "missing $RAW/$f.mp4 — run without --compose first" >&2; exit 1; }
done

# ── brand assets ──────────────────────────────────────────────────────────────
magick -size 1920x1080 gradient:'#F4F8FC'-'#D8E6F4' \
  \( -size 1920x1080 radial-gradient:'#FFFFFF'-'#00000000' -evaluate multiply 0.5 \) -compose over -composite "$P/bg.png"
magick -background none "$ROOT/logo.svg" -resize 200x200 "$P/logo.png"
magick -size 432x960 xc:none -fill white -draw "roundrectangle 0,0,431,959,40,40" "$P/mask.png"
magick -size 472x1000 xc:none -fill 'rgba(20,30,45,0.30)' -draw "roundrectangle 0,0,471,999,46,46" -blur 0x22 "$P/shadow.png"

seg_phone() { # out raw ss dur title sub1 [sub2]
  local out=$1 raw=$2 ss=$3 dur=$4 title=$5 sub1=$6 sub2=${7:-}
  local d="drawtext=fontfile=$FB:text='$title':fontcolor=$INK:fontsize=70:x=150:y=392:alpha='$AFADE'"
  d+=",drawtext=fontfile=$FR:text='$sub1':fontcolor=$SLATE:fontsize=38:x=152:y=505:alpha='$AFADE'"
  [ -n "$sub2" ] && d+=",drawtext=fontfile=$FR:text='$sub2':fontcolor=$SLATE:fontsize=38:x=152:y=559:alpha='$AFADE'"
  ffmpeg -y -loop 1 -t "$dur" -i "$P/bg.png" -ss "$ss" -t "$dur" -i "$RAW/$raw" -i "$P/shadow.png" -i "$P/mask.png" \
    -filter_complex "[1:v]scale=-2:960,setsar=1[ph];[ph][3:v]alphamerge[phr];
      [0:v]setsar=1[b0];[b0][2:v]overlay=1220:40[b1];[b1][phr]overlay=1240:60[bs];[bs]$d,format=yuv420p[v]" \
    -map "[v]" -r 30 -c:v libx264 -pix_fmt yuv420p -t "$dur" "$P/$out" -loglevel error
}
seg_card() { # out dur big line1 line2 [line3] [in|out]
  local out=$1 dur=$2 big=$3 l1=$4 l2=$5 l3=${6:-} fade=${7:-}
  local d="drawtext=fontfile=$FB:text='$big':fontcolor=$INK:fontsize=140:x=(w-text_w)/2:y=452"
  d+=",drawtext=fontfile=$FR:text='$l1':fontcolor=$SLATE:fontsize=46:x=(w-text_w)/2:y=648"
  [ -n "$l2" ] && d+=",drawtext=fontfile=$FR:text='$l2':fontcolor=$SLATE:fontsize=46:x=(w-text_w)/2:y=712"
  [ -n "$l3" ] && d+=",drawtext=fontfile=$FB:text='$l3':fontcolor=$BLUE:fontsize=44:x=(w-text_w)/2:y=812"
  [ "$fade" = in ]  && d+=",fade=t=in:st=0:d=0.7"
  [ "$fade" = out ] && d+=",fade=t=out:st=$(awk -v x="$dur" 'BEGIN{printf "%.2f",x-0.8}'):d=0.8"
  ffmpeg -y -loop 1 -t "$dur" -i "$P/bg.png" -i "$P/logo.png" \
    -filter_complex "[0:v]format=yuv420p[bg];[bg][1:v]overlay=(W-w)/2:236[b];[b]$d,format=yuv420p[v]" \
    -map "[v]" -r 30 -c:v libx264 -pix_fmt yuv420p -t "$dur" "$P/$out" -loglevel error
}

echo "› composing segments…"
seg_card  s0.mp4 3.0 "blep" "Find lost Bluetooth things" "— and catch trackers following you" "" in
seg_phone s1.mp4 discovery.mp4 0.6 3.5 "Everything nearby, by signal" "Tap to track. Favourite, rename," "and manage your paired devices."
seg_phone s2.mp4 hunt.mp4      7.0 5.0 "Walk right to it" "A warm/cold pointer to anything" "with Bluetooth — no map needed."
seg_phone s3.mp4 safety.mp4    4.0 5.0 "Is something following you?" "Catch unwanted AirTags & trackers —" "and blep points you to them."
seg_phone s4.mp4 settings.mp4  1.0 3.5 "Yours to tune" "Background watch, sensitivity," "on-device. Private by design."
seg_card  s5.mp4 3.8 "blep" "Free & open source" "iOS · Android · Wear OS · watchOS" "blep.fyi" out

echo "› crossfading…"
D=(3.0 3.5 5.0 5.0 3.5 3.8); T=0.6
off() { awk -v n="$1" -v t=$T 'BEGIN{s=0;split(ENVIRON["DD"],x," ");for(i=1;i<=n;i++)s+=x[i];printf "%.2f",s-n*t}'; }
export DD="${D[*]}"
ffmpeg -y -i "$P/s0.mp4" -i "$P/s1.mp4" -i "$P/s2.mp4" -i "$P/s3.mp4" -i "$P/s4.mp4" -i "$P/s5.mp4" -filter_complex "
  [0][1]xfade=transition=fade:duration=$T:offset=$(off 1)[a];
  [a][2]xfade=transition=fade:duration=$T:offset=$(off 2)[b];
  [b][3]xfade=transition=fade:duration=$T:offset=$(off 3)[c];
  [c][4]xfade=transition=fade:duration=$T:offset=$(off 4)[d];
  [d][5]xfade=transition=fade:duration=$T:offset=$(off 5),format=yuv420p[v]" \
  -map "[v]" -r 30 -c:v libx264 -pix_fmt yuv420p -movflags +faststart "$OUT" -loglevel error
rm -rf "$P"
echo "✓ $OUT  ($(ffprobe -v error -show_entries format=duration -of csv=p=0 "$OUT")s, 1920×1080) — upload to YouTube, link in Play"
