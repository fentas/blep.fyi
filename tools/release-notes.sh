#!/usr/bin/env bash
# Draft the Play Store "What's new" (English) from user-facing commit subjects
# since the last release, into store/release-notes/en-US.txt.
#
# Only feat:/fix: land in notes — build/ci/docs/refactor/test/chore/release are
# dropped (they aren't user-facing). The conventional-commit prefix + scope are
# stripped, the first letter capitalised, duplicates removed, and the result
# bulleted and capped to Play's 500-char limit.
#
# Workflow:
#   tools/release-notes.sh                 # draft en-US from the last `play/*` tag
#   tools/release-notes.sh <since-ref>     # …or from an explicit ref/commit
#   # → translate en-US.txt to the other locales (the assistant can do this),
#   #   then ship:  make release PLAY_COMMIT=1   (passes --notes-dir automatically)
#   # On a successful upload, tag the release so the next draft starts here:
#   #   git tag play/<versionCode>
#
# Play "what's new" is ONE block per locale (no markdown); files are <locale>.txt
# named for the Play language (en-US, de-DE, …) — matching store/listing/.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/store/release-notes"
LIMIT=500
mkdir -p "$OUT"

# Range: since the last `play/*` tag (newest), or an explicit arg, else last 30.
since="${1:-$(git -C "$ROOT" tag -l 'play/*' --sort=-creatordate | head -1)}"
if [ -n "$since" ]; then range="$since..HEAD"; else range="-30"; fi

notes="$(git -C "$ROOT" log --no-merges --format='%s' $range \
  | grep -E '^(feat|fix)(\(|!|:)' \
  | sed -E 's/^(feat|fix)(\([^)]*\))?!?:[[:space:]]*//' \
  | sed -E 's/^(.)/\U\1/' \
  | awk '!seen[$0]++')"
[ -n "$notes" ] || notes="Bug fixes and improvements."

# Bullet, then trim whole lines until within the char limit.
out="$(printf '%s\n' "$notes" | sed 's/^/• /')"
while [ "$(printf '%s' "$out" | wc -m)" -gt "$LIMIT" ]; do
  out="$(printf '%s\n' "$out" | sed '$d')"
done
printf '%s\n' "$out" > "$OUT/en-US.txt"

echo "→ $OUT/en-US.txt  ($(wc -m < "$OUT/en-US.txt" | tr -d ' ')/$LIMIT chars, range: ${since:-last 30})"
echo "──────────────────────────────────────────"
cat "$OUT/en-US.txt"
echo "──────────────────────────────────────────"
echo "Next: translate to the other locales (de-DE, el-GR, es-ES, et, fr-FR, hr,"
echo "it-IT, ja-JP, nl-NL, pl-PL, pt-PT, sl, zh-CN), then 'make release PLAY_COMMIT=1'."
