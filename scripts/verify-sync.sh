#!/usr/bin/env bash
# Verify phone↔watch sync between two PAIRED emulators (or real devices).
#
# Pairing itself can't be scripted (use Android Studio: Device Manager → the watch
# AVD → "Pair Wearable" → pick the phone AVD). Once paired, this drives the rest:
# it seeds a favourite on the phone, then confirms it propagates to the watch's
# synced copy over the Wear Data Layer — proving the CRDT + DataLayerTransport e2e.
#
# Usage: PHONE=emulator-5554 WEAR=emulator-5556 scripts/verify-sync.sh
set -euo pipefail
PHONE="${PHONE:-emulator-5554}"
WEAR="${WEAR:-emulator-5556}"
PKG="fyi.blep"
PREFS="/data/data/$PKG/shared_prefs/blep_safety.xml" # createKeyValueStore() uses "blep_safety"
TESTID="SYNCTEST:AA:BB:CC:DD"

say() { printf '\n\033[1m%s\033[0m\n' "$*"; }
fav_of() { adb -s "$1" exec-out run-as "$PKG" cat "$PREFS" 2>/dev/null | grep -oE 'name="devices.favorites">[^<]*' | sed 's/.*>//' || true; }

say "0. Pairing check"
if adb -s "$WEAR" logcat -d 2>/dev/null | grep -q "companionDisconnected=false"; then
  echo "  ✓ watch reports the phone companion connected"
else
  echo "  ⚠ watch still shows companionDisconnected — pair them in Android Studio first."
  echo "    (Device Manager → blepwear → ⋮ → Pair Wearable → blep)"
fi

say "1. Seed a favourite on the phone ($TESTID)"
adb -s "$PHONE" shell am force-stop "$PKG"
cur="$(adb -s "$PHONE" exec-out run-as "$PKG" cat "$PREFS" 2>/dev/null || true)"
new="$(python3 - "$cur" "$TESTID" <<'PY'
import sys, re
xml = sys.argv[1].strip() or '<?xml version="1.0" encoding="utf-8" standalone="yes" ?>\n<map>\n</map>'
fav = f'    <string name="devices.favorites">{sys.argv[2]}</string>'
if 'devices.favorites' in xml:
    xml = re.sub(r'[ \t]*<string name="devices.favorites">.*?</string>', fav, xml, flags=re.S)
else:
    xml = xml.replace('</map>', fav + '\n</map>')
sys.stdout.write(xml)
PY
)"
tmp="$(mktemp)"; printf '%s' "$new" > "$tmp"
adb -s "$PHONE" shell "run-as $PKG sh -c 'cat > $PREFS'" < "$tmp"
rm -f "$tmp"
echo "  phone favourites now: $(fav_of "$PHONE")"
adb -s "$PHONE" shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
echo "  relaunched phone app → SyncManager publishes on start"

say "2. Watch for it to land on the watch (≤40s)"
for i in $(seq 1 20); do
  sleep 2
  got="$(fav_of "$WEAR")"
  if printf '%s' "$got" | grep -q "$TESTID"; then
    say "PASS ✓ — '$TESTID' synced phone→watch (watch favourites: $got)"
    exit 0
  fi
done
say "FAIL ✗ — the favourite did not reach the watch within 40s."
echo "  Watch favourites: '$(fav_of "$WEAR")'"
echo "  Likely not paired, or sync is off in Settings → Sync. Check 'adb -s $WEAR logcat | grep -i wearable'."
exit 1
