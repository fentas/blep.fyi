#!/usr/bin/env bash
# Wear OS Play-listing screenshots from the in-app demo mode — localized.
#
# Boots a Wear OS round AVD (creating it if needed), installs the wear demo build,
# then for each app language switches the watch's system locale, runs the scripted
# "hunt", captures each watch screen, and composites the round watch face onto the
# brand Mist canvas (1080×1080, 1:1 — valid for the Play "Wear OS screenshots"
# slot, which wants a square image 384–3840 px per side).
#
#   tools/screenshots-wear.sh                # all locales
#   tools/screenshots-wear.sh en-US de-DE    # only the named Play locales
#   tools/screenshots-wear.sh --compose      # re-compose from existing raw captures
#
# Output: screenshots/store/wear/<play-locale>/{01-discovery,02-tracking,03-found}.png
# (Play falls back to the default-language wear graphics for any locale you skip.)
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RAW="$ROOT/screenshots/raw/wear"
STORE="$ROOT/screenshots/store/wear"
APP="$ROOT/app"
AVD="blepwear"; PKG="fyi.blep"
WEAR_IMG="system-images;android-34;android-wear;x86_64"
WEAR_DEVICE="wearos_large_round"
mkdir -p "$RAW" "$STORE"

ANDROID_HOME="${ANDROID_HOME:-$(sed -n 's/^sdk.dir=//p' "$APP/local.properties" 2>/dev/null)}"
export ANDROID_HOME ANDROID_SDK_ROOT="$ANDROID_HOME"
for d in "$HOME/.config/.android/avd" "$HOME/.android/avd"; do [ -d "$d" ] && export ANDROID_AVD_HOME="$d"; done
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

# locale rows: "play-folder  androidLang  androidCountry  androidLocale" — the same
# set the phone i18n screenshots use (the watch app localizes the same catalog).
read -r -d '' ROWS <<'EOF' || true
en-US en US en-US
de-DE de DE de-DE
sl    sl SI sl-SI
it-IT it IT it-IT
nl-NL nl NL nl-NL
es-ES es ES es-ES
el-GR el GR el-GR
et    et EE et-EE
fr-FR fr FR fr-FR
zh-CN zh CN zh-CN
ja-JP ja JP ja-JP
hr    hr HR hr-HR
pl-PL pl PL pl-PL
pt-PT pt PT pt-PT
EOF

WS=""
wear_serial() {
  for s in $(adb devices | awk '/emulator/{print $1}'); do
    [ "$(adb -s "$s" emu avd name 2>/dev/null | head -1 | tr -d '\r')" = "$AVD" ] && { echo "$s"; return; }
  done
}

set_locale() { # lang country full
  adb -s "$WS" shell "setprop persist.sys.locale $3; setprop persist.sys.language $1; setprop persist.sys.country $2" >/dev/null
  adb -s "$WS" shell "su 0 setprop ctl.restart zygote" 2>/dev/null || true
  # boot_completed stays 1 across a zygote restart, so wait for the package
  # manager to resolve our app again, then settle.
  sleep 5
  local i=0; until adb -s "$WS" shell pm path "$PKG" >/dev/null 2>&1; do sleep 2; i=$((i + 1)); [ $i -gt 40 ] && break; done
  sleep 3
}

launch_demo() { local i=0
  until adb -s "$WS" shell am start -n "$PKG/fyi.blep.wear.MainActivity" --ez demo true 2>&1 | grep -qiv "error\|does not exist"; do
    sleep 2; i=$((i + 1)); [ $i -gt 20 ] && break
  done
}

ui_dump() { adb -s "$WS" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb -s "$WS" shell cat /sdcard/ui.xml 2>/dev/null; }
on_discovery() { ui_dump | grep -q 'text="AirPods Pro"'; }   # a chip only the list shows
# Tap the "Keys" chip at its actual on-screen centre (robust to layout/scroll).
tap_keys() {
  local nums; nums=$(ui_dump | grep -oE 'text="Keys"[^>]*bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' \
    | grep -oE '[0-9]+' | head -4)
  set -- $nums
  if [ $# -eq 4 ]; then adb -s "$WS" shell input tap $(((${1}+${3})/2)) $(((${2}+${4})/2))
  else adb -s "$WS" shell input tap 227 225; fi
}

capture_set() { local raw="$1"
  # (Re)launch until the discovery list is actually on screen — right after a
  # zygote restart the app can launch into an unready framework and bounce back
  # to the watch face, so a single am-start isn't enough.
  local t=0
  while [ $t -lt 8 ]; do
    adb -s "$WS" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1
    adb -s "$WS" shell am force-stop "$PKG"; launch_demo
    local i=0; until on_discovery; do sleep 1; i=$((i + 1)); [ $i -gt 12 ] && break; done
    on_discovery && break
    t=$((t + 1))
  done
  sleep 1; adb -s "$WS" exec-out screencap -p > "$raw/01-discovery.png"
  local i=0; while on_discovery && [ $i -lt 6 ]; do tap_keys; sleep 2; i=$((i + 1)); done   # start the hunt (retry)
  sleep 5;  adb -s "$WS" exec-out screencap -p > "$raw/02-tracking.png"  # mid-walk, on course
  sleep 6;  adb -s "$WS" exec-out screencap -p > "$raw/03-found.png"     # "on it"
}

# Circle-crop the watch face and drop it on the brand canvas with a soft shadow.
compose() { # src out
  local tmp; tmp="$(mktemp -d)"
  local w; w=$(identify -format %w "$1")
  magick -size ${w}x${w} xc:none -fill white -draw "circle $((w/2)),$((w/2)) $((w/2)),2" "$tmp/m.png"
  magick "$1" "$tmp/m.png" -alpha set -compose DstIn -composite "$tmp/watch.png"
  magick "$tmp/watch.png" -resize 860x860 "$tmp/w.png"
  magick -size 1080x1080 xc:'#EEF2F6' \
    \( "$tmp/w.png" \( +clone -background black -shadow 60x30+0+12 \) +swap -background none -layers merge +repage \) \
    -gravity center -composite "$2"
  rm -rf "$tmp"
}

compose_locale() { # play-folder
  local out="$STORE/$1"; mkdir -p "$out"
  for f in 01-discovery 02-tracking 03-found; do
    [ -f "$RAW/$1/$f.png" ] && compose "$RAW/$1/$f.png" "$out/$f.png"
  done
}

# ── re-compose only ───────────────────────────────────────────────────────────
if [ "${1:-}" = "--compose" ]; then
  while read -r folder _ _ _; do [ -n "$folder" ] && compose_locale "$folder"; done <<< "$ROWS"
  echo "✓ re-composed wear sets in $STORE/<locale>/"
  exit 0
fi

# ── boot + build + install ────────────────────────────────────────────────────
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
# Setting persist.sys.locale needs a root adbd on the Wear image (the default
# shell user is denied, unlike the phone emulator).
adb -s "$WS" root >/dev/null 2>&1 || true
adb -s "$WS" wait-for-device
adb -s "$WS" shell svc power stayon true >/dev/null 2>&1 || true   # don't let it sleep mid-run
echo "› building + installing wear demo build…"
( cd "$APP" && ./gradlew :wearApp:assembleDebug -q )
adb -s "$WS" install -r "$APP/wearApp/build/outputs/apk/debug/wearApp-debug.apk" >/dev/null

WANT=("$@")
# Read the locale table on FD 3 so adb inside the loop can't eat the rows.
while read -r folder lang country full <&3; do
  [ -n "$folder" ] || continue
  if [ ${#WANT[@]} -gt 0 ] && [[ ! " ${WANT[*]} " == *" $folder "* ]]; then continue; fi
  echo "› $folder ($full)…"
  set_locale "$lang" "$country" "$full"
  raw="$RAW/$folder"; mkdir -p "$raw"
  capture_set "$raw"
  compose_locale "$folder"
done 3<<< "$ROWS"

set_locale en US en-US
echo "✓ localized wear sets in $STORE/<locale>/  (01-discovery,02-tracking,03-found · 1080×1080)"
