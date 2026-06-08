#!/usr/bin/env bash
# Increment the build number (versionCode) of both app modules, each within its
# form-factor band (phone 1xxx in :androidApp, Wear OS 2xxx in :wearApp). Play
# requires a strictly-higher versionCode for every upload; this bumps both by one.
#
# versionName (the marketing version, e.g. 1.1.0) is intentionally NOT touched —
# bump that by hand when a release deserves it; edit it in the build.gradle.kts.
#
#   tools/bump-version.sh            # +1 to each module's versionCode
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

bump() { # build.gradle.kts
  local f="$1"
  local cur next
  cur="$(grep -oE 'versionCode = [0-9]+' "$f" | head -1 | grep -oE '[0-9]+')"
  [ -n "$cur" ] || { echo "no versionCode in $f" >&2; exit 1; }
  next=$((cur + 1))
  sed -i "s/versionCode = $cur/versionCode = $next/" "$f"
  local name; name="$(grep -oE 'versionName = "[^"]+"' "$f" | head -1 | sed -E 's/.*"([^"]+)"/\1/')"
  printf "  %-11s versionCode %s -> %s  (versionName %s)\n" "$(basename "$(dirname "$f")")" "$cur" "$next" "$name"
}

echo "Bumping build numbers:"
bump "$ROOT/app/androidApp/build.gradle.kts"
bump "$ROOT/app/wearApp/build.gradle.kts"
