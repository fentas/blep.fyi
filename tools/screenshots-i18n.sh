#!/usr/bin/env bash
# Localized store screenshots + feature graphic, one set per app language.
#
# For each locale it switches the emulator's system language (so the app UI
# renders translated), captures the scripted demo, and composites the captioned
# 9:16 phone set (mirrored to tablet/), a 16:9 Chromebook set, and a localized
# 1024×500 feature graphic. Output: screenshots/store/i18n/<play-locale>/
# {phone,tablet,chromebook}/ — upload each to that listing language.
# (Play falls back to the default English graphics for any locale you skip.)
#
#   tools/screenshots-i18n.sh            # all locales
#   tools/screenshots-i18n.sh de-DE sl   # only the named Play locales
# (No `set -e`: a transient adb hiccup on one locale must not abort the whole run.)
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STORE="$ROOT/screenshots/store/i18n"
APP="$ROOT/app"
AVD="blep"; PKG="fyi.blep"
mkdir -p "$STORE"

ANDROID_HOME="${ANDROID_HOME:-$(sed -n 's/^sdk.dir=//p' "$APP/local.properties" 2>/dev/null)}"
export ANDROID_HOME ANDROID_SDK_ROOT="$ANDROID_HOME"
for d in "$HOME/.config/.android/avd" "$HOME/.android/avd"; do [ -d "$d/$AVD.avd" ] && export ANDROID_AVD_HOME="$d"; done
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

# Fonts: DejaVu for Latin/Greek/Cyrillic, Noto Sans CJK for zh/ja.
DV_B="$(fc-match -f '%{file}' 'DejaVu Sans:bold')"; DV_R="$(fc-match -f '%{file}' 'DejaVu Sans')"
CJK_B="$(fc-match -f '%{file}' 'Noto Sans CJK JP:bold')"; CJK_R="$(fc-match -f '%{file}' 'Noto Sans CJK JP')"
CFB="$DV_B"; CFR="$DV_R"   # active caption fonts (swapped per locale)

# locale rows: "play-folder  androidLang  androidCountry  androidLocale  isCJK"
read -r -d '' ROWS <<'EOF' || true
en-US en US en-US 0
de-DE de DE de-DE 0
sl    sl SI sl-SI 0
it-IT it IT it-IT 0
nl-NL nl NL nl-NL 0
es-ES es ES es-ES 0
el-GR el GR el-GR 0
et    et EE et-EE 0
fr-FR fr FR fr-FR 0
zh-CN zh CN zh-CN 1
ja-JP ja JP ja-JP 1
hr    hr HR hr-HR 0
pl-PL pl PL pl-PL 0
pt-PT pt PT pt-PT 0
EOF

# Captions per language: H1|S1|H2|S2|H3|S3|H4|S4|H5|S5|TAGLINE
caps() { case "$1" in
en) echo "Find what you lost|Every nearby Bluetooth thing, by signal.|Walk right to it|A warm/cold pointer — no map needed.|You're on top of it|Calibrate, sweep, walk, done.|Found it|Free & open source. No ads, no tracking.|Is something tracking you?|Catch unwanted AirTags & trackers.|Find lost Bluetooth things";;
de) echo "Finde, was du verloren hast|Jedes Bluetooth-Gerät in der Nähe, nach Signal.|Geh direkt hin|Ein Warm/Kalt-Zeiger — keine Karte nötig.|Du bist direkt darüber|Kalibrieren, schwenken, gehen, fertig.|Gefunden|Kostenlos & quelloffen. Keine Werbung, kein Tracking.|Verfolgt dich etwas?|Unerwünschte AirTags & Tracker aufspüren.|Verlorene Bluetooth-Geräte finden";;
sl) echo "Najdi, kar si izgubil|Vsaka naprava Bluetooth v bližini, po signalu.|Pojdi naravnost tja|Topel/hladen kazalnik — brez zemljevida.|Si točno nad njim|Umeri, zanihaj, pojdi, končano.|Najdeno|Brezplačno in odprtokodno. Brez oglasov, brez sledenja.|Te kaj zasleduje?|Zaznaj neželene AirTage in sledilnike.|Najdi izgubljene naprave Bluetooth";;
it) echo "Trova ciò che hai perso|Ogni dispositivo Bluetooth vicino, per segnale.|Vai dritto da lui|Un puntatore caldo/freddo — senza mappa.|Ci sei proprio sopra|Calibra, ruota, cammina, fatto.|Trovato|Gratis e open source. Niente pubblicità, niente tracciamento.|Qualcosa ti sta seguendo?|Scopri AirTag e tracker indesiderati.|Ritrova oggetti Bluetooth smarriti";;
nl) echo "Vind wat je kwijt bent|Elk Bluetooth-apparaat dichtbij, op signaal.|Loop er recht naartoe|Een warm/koud-aanwijzer — geen kaart nodig.|Je staat er precies op|Kalibreren, zwenken, lopen, klaar.|Gevonden|Gratis en open source. Geen advertenties, geen tracking.|Word je gevolgd?|Spoor ongewenste AirTags & trackers op.|Verloren Bluetooth-spullen vinden";;
es) echo "Encuentra lo que perdiste|Cada dispositivo Bluetooth cercano, por señal.|Ve directo hacia él|Un puntero frío/caliente — sin mapa.|Estás justo encima|Calibra, gira, camina, listo.|Encontrado|Gratis y de código abierto. Sin anuncios, sin rastreo.|¿Algo te está siguiendo?|Detecta AirTags y rastreadores no deseados.|Encuentra cosas Bluetooth perdidas";;
el) echo "Βρες ό,τι έχασες|Κάθε συσκευή Bluetooth κοντά, κατά σήμα.|Πήγαινε κατευθείαν σε αυτό|Δείκτης ζεστού/κρύου — χωρίς χάρτη.|Είσαι ακριβώς από πάνω|Βαθμονόμησε, σάρωσε, περπάτα, έτοιμο.|Βρέθηκε|Δωρεάν & ανοιχτού κώδικα. Χωρίς διαφημίσεις, χωρίς παρακολούθηση.|Σε παρακολουθεί κάτι;|Εντόπισε ανεπιθύμητα AirTag & ιχνηλάτες.|Βρες χαμένα αντικείμενα Bluetooth";;
et) echo "Leia, mille kaotasid|Iga lähedal olev Bluetooth-seade, signaali järgi.|Mine otse selle juurde|Soe/külm osuti — kaarti pole vaja.|Oled otse selle peal|Kalibreeri, pööra, kõnni, valmis.|Leitud|Tasuta ja avatud lähtekoodiga. Reklaamideta, jälgimiseta.|Kas keegi jälgib sind?|Tuvasta soovimatud AirTagid ja jälgijad.|Leia kadunud Bluetooth-asjad";;
fr) echo "Retrouve ce que tu as perdu|Chaque appareil Bluetooth proche, par signal.|Va droit dessus|Un pointeur chaud/froid — sans carte.|Tu es juste dessus|Calibre, balaie, marche, terminé.|Trouvé|Gratuit et open source. Sans pub, sans pistage.|Quelque chose te suit-il ?|Repère les AirTags & traceurs indésirables.|Retrouve des objets Bluetooth perdus";;
zh) echo "找回你丢失的东西|附近每个蓝牙设备，按信号排序。|径直走过去|冷热指针引导——无需地图。|你正好在它上面|校准、扫动、行走、完成。|找到了|免费开源。无广告，无跟踪。|有东西在跟踪你吗？|发现不需要的 AirTag 和追踪器。|找回丢失的蓝牙物品";;
ja) echo "なくした物を見つけよう|近くのすべての Bluetooth 機器を信号順に。|まっすぐ向かう|冷暖ポインターが案内——地図不要。|ちょうど真上にいます|較正、スイープ、歩く、完了。|見つけた|無料・オープンソース。広告なし、追跡なし。|誰かに追跡されていませんか？|不要な AirTag やトラッカーを発見。|なくした Bluetooth の物を見つける";;
hr) echo "Pronađi što si izgubio|Svaki Bluetooth uređaj u blizini, po signalu.|Idi ravno do njega|Topli/hladni pokazivač — bez karte.|Točno si iznad njega|Kalibriraj, zakreni, hodaj, gotovo.|Pronađeno|Besplatno i otvorenog koda. Bez oglasa, bez praćenja.|Prati li te nešto?|Otkrij neželjene AirTagove i pratitelje.|Pronađi izgubljene Bluetooth stvari";;
pl) echo "Znajdź to, co zgubiłeś|Każde urządzenie Bluetooth w pobliżu, wg sygnału.|Idź prosto do niego|Wskaźnik ciepło/zimno — bez mapy.|Jesteś dokładnie nad tym|Skalibruj, obróć, idź, gotowe.|Znalezione|Bezpłatne i open source. Bez reklam, bez śledzenia.|Czy coś cię śledzi?|Wykryj niechciane AirTagi i lokalizatory.|Znajdź zgubione rzeczy Bluetooth";;
pt) echo "Encontra o que perdeste|Cada dispositivo Bluetooth perto, por sinal.|Vai direto até ele|Um ponteiro quente/frio — sem mapa.|Estás mesmo em cima dele|Calibra, roda, anda, concluído.|Encontrado|Gratuito e open source. Sem anúncios, sem rastreio.|Algo te está a seguir?|Deteta AirTags e rastreadores indesejados.|Encontra objetos Bluetooth perdidos";;
esac; }

frame() { local in="$1" out="$2" R="$3" t; t="$(mktemp -d)"
  local w h; w=$(identify -format %w "$in"); h=$(identify -format %h "$in")
  magick -size ${w}x${h} xc:none -fill white -draw "roundrectangle 0,0,$((w-1)),$((h-1)),$R,$R" "$t/m.png"
  magick "$in" "$t/m.png" -alpha set -compose DstIn -composite "$t/r.png"
  magick "$t/r.png" \( +clone -background black -shadow 55x28+0+16 \) +swap -background none -layers merge +repage "$out"; rm -rf "$t"; }

cap_portrait() { local t; t="$(mktemp -d)"
  magick "$1" -resize 980x "$t/s.png"; frame "$t/s.png" "$t/sh.png" 44
  magick -size 1440x2560 xc:'#EEF2F6' \
    -font "$CFB" -pointsize 78 -fill '#27313B' -gravity north -annotate +0+150 "$2" \
    -font "$CFR" -pointsize 44 -fill '#566472' -gravity north -annotate +0+270 "$3" \
    "$t/sh.png" -gravity north -geometry +0+340 -composite "$4"; rm -rf "$t"; }

cap_landscape() { local t; t="$(mktemp -d)"
  magick "$1" -resize x1180 "$t/s.png"; frame "$t/s.png" "$t/sh.png" 40
  magick -size 2560x1440 xc:'#EEF2F6' \
    "$t/sh.png" -gravity east -geometry +240+0 -composite \
    -font "$CFB" -pointsize 84 -fill '#27313B' -gravity west -annotate +150-48 "$2" \
    -font "$CFR" -pointsize 42 -fill '#566472' -gravity west -annotate +150+56 "$3" \
    "$4"; rm -rf "$t"; }

feature() { rsvg-convert -w 360 -h 360 "$ROOT/logo.svg" -o /tmp/.bd.png
  magick -size 1024x500 xc:'#EEF2F6' /tmp/.bd.png -gravity west -geometry +90+0 -composite \
    -font "$CFB" -pointsize 132 -fill '#27313B' -gravity west -annotate +500-36 "blep" \
    -font "$CFR" -pointsize 32 -fill '#566472' -gravity west -annotate +505+70 "$1" \
    "$2"; rm -f /tmp/.bd.png; }

# Launch the demo, retrying — a zygote restart leaves the framework briefly
# unready ("Activity does not exist") even though sys.boot_completed stays 1.
launch_demo() { local i=0
  until adb shell am start -n "$PKG/.MainActivity" --ez demo true 2>&1 | grep -qiv "error\|does not exist"; do
    sleep 2; i=$((i + 1)); [ $i -gt 20 ] && break
  done; }

set_locale() { adb shell "setprop persist.sys.locale $3; setprop persist.sys.language $1; setprop persist.sys.country $2" >/dev/null
  adb shell "su 0 setprop ctl.restart zygote" 2>/dev/null || true
  # boot_completed stays 1 across a zygote restart, so wait for the package
  # manager to come back instead (resolves our app), then settle.
  sleep 5
  local i=0; until adb shell pm path "$PKG" >/dev/null 2>&1; do sleep 2; i=$((i + 1)); [ $i -gt 40 ] && break; done
  sleep 3; }

capture_set() { local raw="$1"
  adb shell pm clear "$PKG" >/dev/null; launch_demo; sleep 4
  adb exec-out screencap -p > "$raw/01.png"
  adb shell input tap 540 721; sleep 9; adb exec-out screencap -p > "$raw/02.png"
  sleep 5; adb exec-out screencap -p > "$raw/03.png"
  adb shell input tap 540 2024; sleep 2; adb exec-out screencap -p > "$raw/04.png"
  adb shell pm clear "$PKG" >/dev/null; launch_demo; sleep 4
  adb shell input tap 420 460; sleep 8; adb exec-out screencap -p > "$raw/05.png"; }

echo "› building + installing demo build…"
( cd "$APP" && ./gradlew :composeApp:assembleDebug -q )
adb install -r "$APP/composeApp/build/outputs/apk/debug/composeApp-debug.apk" >/dev/null

WANT=("$@")
# Read the locale table on FD 3 so adb/gradle inside the loop can't eat the rows.
while read -r folder lang country full cjk <&3; do
  [ -n "$folder" ] || continue
  if [ ${#WANT[@]} -gt 0 ] && [[ ! " ${WANT[*]} " == *" $folder "* ]]; then continue; fi
  echo "› $folder ($full)…"
  if [ "$cjk" = 1 ]; then CFB="$CJK_B"; CFR="$CJK_R"; else CFB="$DV_B"; CFR="$DV_R"; fi
  set_locale "$lang" "$country" "$full"
  IFS='|' read -r h1 s1 h2 s2 h3 s3 h4 s4 h5 s5 tag <<< "$(caps "$lang")"
  raw="$(mktemp -d)"; capture_set "$raw"
  out="$STORE/$folder/phone"; tb="$STORE/$folder/tablet"; cr="$STORE/$folder/chromebook"; mkdir -p "$out" "$tb" "$cr"
  cap_portrait  "$raw/01.png" "$h1" "$s1" "$out/01.png"; cap_landscape "$raw/01.png" "$h1" "$s1" "$cr/01.png"
  cap_portrait  "$raw/02.png" "$h2" "$s2" "$out/02.png"; cap_landscape "$raw/02.png" "$h2" "$s2" "$cr/02.png"
  cap_portrait  "$raw/03.png" "$h3" "$s3" "$out/03.png"; cap_landscape "$raw/03.png" "$h3" "$s3" "$cr/03.png"
  cap_portrait  "$raw/04.png" "$h4" "$s4" "$out/04.png"; cap_landscape "$raw/04.png" "$h4" "$s4" "$cr/04.png"
  cap_portrait  "$raw/05.png" "$h5" "$s5" "$out/05.png"; cap_landscape "$raw/05.png" "$h5" "$s5" "$cr/05.png"
  cp -f "$out"/*.png "$tb"/                         # 9:16 phone set is valid for tablet too
  feature "$tag" "$STORE/$folder/feature-1024x500.png"
  rm -rf "$raw"
done 3<<< "$ROWS"

set_locale en US en-US
echo "✓ localized sets in $STORE/<locale>/  (phone/ + chromebook/ 01–05.png + feature-1024x500.png)"
