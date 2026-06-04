# Store listing — localized (de · sl · it · nl · es · el · et · fr · zh · ja · hr · pl · pt)

Translations of the English listing in `store-listing.md`. **blep** stays
untranslated (it's the brand). Locale-code note for Play Console:

| You said | Play locale | Notes |
|----------|-------------|-------|
| en | `en-US` | default |
| de | `de-DE` | German (Germany) |
| at | `de-AT`* | *Play may not list Austrian German separately — if not, `de-DE` already covers Austria (same written standard). The app **does** ship a `de-AT` resource locale. |
| si | `sl`    | Slovenian (language code is `sl`; SI is the country) |
| it | `it-IT` | Italian |
| nl | `nl-NL` | Dutch |
| es | `es-ES` | Spanish (Spain) — use `es-419` instead if targeting Latin America |
| greek | `el-GR` | Greek |
| et | `et`    | Estonian |
| ch | `de-CH` | Swiss (High) German — same text as de-DE with `ß`→`ss`. (If you meant Swiss **French**, that's `fr-CH`; tell me.) |
| fr | `fr-FR` | French |

---

## Release notes — paste-ready (the multi-language box)

Play's release-notes field takes all languages at once, wrapped in locale tags.
Paste this whole block. Regional/script variants (de-AT, de-CH, zh-Hans) are **not**
separate Play locales and are rejected — de-DE and zh-CN cover them, so they're omitted.

```
<en-US>
First public build of blep 🐾 — find your lost Bluetooth things, guided by signal
strength. Calibrate, sweep, walk, done. Plus a safety scan that finds unwanted
trackers following you — and points you to them. Free & open source, no tracking.
</en-US>
<de-DE>
Erste öffentliche Version von blep 🐾 — finde verlorene Bluetooth-Geräte, geführt
von der Signalstärke. Kalibrieren, schwenken, gehen, fertig. Plus ein Sicherheits-
Scan, der unerwünschte Tracker aufspürt, die dir folgen — und dich zu ihnen führt.
Kostenlos & quelloffen, kein Tracking.
</de-DE>
<sl>
Prva javna različica aplikacije blep 🐾 — najdi izgubljene naprave Bluetooth s
pomočjo moči signala. Umeri, zanihaj, pojdi, končano. Poleg tega varnostni pregled
poišče neželene sledilnike, ki ti sledijo — in te pripelje do njih. Brezplačno in
odprtokodno, brez sledenja.
</sl>
<it-IT>
Prima versione pubblica di blep 🐾 — ritrova i tuoi oggetti Bluetooth smarriti,
guidato dall'intensità del segnale. Calibra, ruota, cammina, fatto. In più una
scansione di sicurezza che individua i tracker indesiderati che ti seguono — e ti
guida fino a loro. Gratis e open source, nessun tracciamento.
</it-IT>
<nl-NL>
Eerste openbare versie van blep 🐾 — vind je verloren Bluetooth-spullen, geleid door
de signaalsterkte. Kalibreren, zwenken, lopen, klaar. Plus een veiligheidsscan die
ongewenste trackers vindt die je volgen — en je ernaartoe leidt. Gratis en open
source, geen tracking.
</nl-NL>
<es-ES>
Primera versión pública de blep 🐾 — encuentra tus cosas Bluetooth perdidas, guiado
por la intensidad de la señal. Calibra, gira, camina, listo. Además, un escaneo de
seguridad que detecta rastreadores no deseados que te siguen — y te lleva hasta
ellos. Gratis y de código abierto, sin rastreo.
</es-ES>
<el-GR>
Πρώτη δημόσια έκδοση του blep 🐾 — βρες τα χαμένα σου αντικείμενα Bluetooth, με οδηγό
την ισχύ του σήματος. Βαθμονόμησε, σάρωσε, περπάτα, έτοιμο. Επιπλέον, μια σάρωση
ασφαλείας που εντοπίζει ανεπιθύμητους ιχνηλάτες που σε ακολουθούν — και σε οδηγεί σε
αυτούς. Δωρεάν και ανοιχτού κώδικα, χωρίς παρακολούθηση.
</el-GR>
<et>
blep'i esimene avalik versioon 🐾 — leia oma kadunud Bluetooth-asjad signaalitugevuse
järgi. Kalibreeri, pööra, kõnni, valmis. Lisaks turvaskann, mis tuvastab soovimatud
jälgijad, kes sind jälgivad — ja juhatab sind nendeni. Tasuta ja avatud lähtekoodiga,
ilma jälgimiseta.
</et>
<fr-FR>
Première version publique de blep 🐾 — retrouve tes objets Bluetooth perdus, guidé
par la force du signal. Calibre, balaie, marche, terminé. Plus une analyse de
sécurité qui repère les traceurs indésirables qui te suivent — et t'y conduit.
Gratuit et open source, sans pistage.
</fr-FR>
<zh-CN>
blep 首个公开版本 🐾 —— 借助信号强度找回丢失的蓝牙物品。校准、扫动、行走、完成。还有一项安全扫描，可发现跟踪你的不需要的追踪器，并引导你找到它们。免费开源，无跟踪。
</zh-CN>
<ja-JP>
blep の初の公開版 🐾 — 信号強度を頼りに、なくした Bluetooth の物を見つけます。較正、スイープ、歩く、完了。さらに、あなたを追跡する不要なトラッカーを見つけ、そこへ案内する安全スキャンも。無料・オープンソース、追跡なし。
</ja-JP>
<hr>
Prva javna verzija blepa 🐾 — pronađi izgubljene Bluetooth stvari uz pomoć jačine signala. Kalibriraj, zakreni, hodaj, gotovo. Uz to, sigurnosno skeniranje pronalazi neželjene pratitelje koji te slijede — i vodi te do njih. Besplatno i otvorenog koda, bez praćenja.
</hr>
<pl-PL>
Pierwsza publiczna wersja blep 🐾 — znajdź zgubione rzeczy Bluetooth dzięki sile sygnału. Skalibruj, obróć, idź, gotowe. Do tego skan bezpieczeństwa znajduje niechciane lokalizatory, które cię śledzą — i prowadzi cię do nich. Bezpłatne i open source, bez śledzenia.
</pl-PL>
<pt-PT>
Primeira versão pública do blep 🐾 — encontra os teus objetos Bluetooth perdidos, guiado pela intensidade do sinal. Calibra, roda, anda, concluído. Além disso, uma análise de segurança encontra rastreadores indesejados que te seguem — e leva-te até eles. Gratuito e de código aberto, sem rastreio.
</pt-PT>
```

> Only languages added to your store listing are accepted. de-AT, de-CH and zh-Hans
> aren't separate Play locales (de-DE / zh-CN cover them), so they're left out here.

---

## English — en-US (default)

**Short description (≤80):**
```
Find lost Bluetooth things — and catch unwanted trackers following you. Free.
```

**Full description:**
```
blep turns your phone into a warm/cold pointer that walks you right to your lost
Bluetooth things — keys with a tag, earbuds, a watch, a speaker, your car, almost
anything that advertises Bluetooth.

No map, no account, no setup. Just pick the device and follow the feel.

HOW IT WORKS
1. Calibrate — hold the phone flat to your chest. Your body shields the signal
   from behind, which makes it directional.
2. Sweep & walk — turn slowly until it's warmest, then walk forward. A big arrow
   and a warm/cold colour guide every step.
3. Pinpoint — up close, blep celebrates the moment you find it.

WHAT MAKES IT GOOD
• Direction by signal — a compass-and-signal pointer, not a fuzzy radar.
• An on-screen trail of where you've searched, coloured by signal strength.
• "Warmer this way" — if you stray, it points you back to the strongest spot.
• Tells you when the field is clean vs noisy, and when there's no signal at all.
• Optional sound + haptics that pulse faster as you close in.

IS SOMETHING TRACKING YOU?
Tap "Is something tracking you?" to scan for unwanted trackers travelling with
you — an AirTag, Tile, SmartTag or Find My beacon someone may have slipped into
your bag, coat or car. blep also catches the trick the others miss: a tracker
that rotates its Bluetooth ID to stay anonymous gives itself away by reappearing
at the same close range, again and again — the un-correlation is the correlation.
And because blep is a finder, it doesn't just warn — it points you to it. Turn on
"Remember across sessions" and a tag that keeps showing up over the hours gets
flagged. (On-device only — nothing ever leaves your phone — and you can turn it
off any time.)

PRIVATE BY DESIGN
No accounts. No ads. No analytics. No data collection. Everything happens on
your device — your Bluetooth scans and motion sensing never leave the phone.
blep is free and open source: https://github.com/fentas/blep.fyi

GOOD TO KNOW
blep finds things within Bluetooth range (roughly a room, a floor, or a parking
lot). It is not a GPS/cloud tracker — it can't locate a tag across town the way a
Find My network does. It guides you the last stretch, by signal.
```

(The full English listing — with the App Store copy, data-safety answers and
permission notes — lives in `store-listing.md`.)

---

## German — de-DE

**Short description (≤80):**
```
Bluetooth-Geräte wiederfinden – und unerwünschte Verfolger-Tracker aufspüren.
```

**Full description:**
```
blep verwandelt dein Handy in einen Warm/Kalt-Zeiger, der dich direkt zu deinen
verlorenen Bluetooth-Dingen führt — Schlüssel mit Anhänger, Kopfhörer, eine Uhr,
ein Lautsprecher, dein Auto, fast alles, was Bluetooth aussendet.

Keine Karte, kein Konto, keine Einrichtung. Wähl einfach das Gerät und folge dem Gefühl.

SO FUNKTIONIERT'S
1. Kalibrieren — halte das Handy flach an die Brust. Dein Körper schirmt das Signal
   von hinten ab und macht es so richtungsabhängig.
2. Schwenken & gehen — dreh dich langsam, bis es am wärmsten ist, dann geh vorwärts.
   Ein großer Pfeil und eine Warm/Kalt-Farbe leiten jeden Schritt.
3. Orten — aus der Nähe feiert blep den Moment, in dem du es findest.

WAS ES GUT MACHT
• Richtung per Signal — ein Kompass-und-Signal-Zeiger, kein verschwommenes Radar.
• Eine Spur auf dem Bildschirm, wo du gesucht hast, eingefärbt nach Signalstärke.
• „Wärmer hier entlang" — wenn du abweichst, weist es dich zurück zum stärksten Punkt.
• Sagt dir, wann das Umfeld sauber oder gestört ist und wann es gar kein Signal gibt.
• Optionaler Ton + Vibration, die schneller pulsiert, je näher du kommst.

VERFOLGT DICH ETWAS?
Tippe auf „Verfolgt dich etwas?", um nach unerwünschten Trackern zu suchen, die mit
dir reisen — ein AirTag, Tile, SmartTag oder Find-My-Sender, den jemand in deine
Tasche, Jacke oder dein Auto geschmuggelt haben könnte. blep erkennt auch den Trick,
den andere übersehen: Ein Tracker, der seine Bluetooth-ID wechselt, um anonym zu
bleiben, verrät sich, indem er immer wieder im gleichen Nahbereich auftaucht — die
fehlende Zuordnung ist die Zuordnung. Und weil blep ein Finder ist, warnt es nicht
nur — es führt dich hin. Aktiviere „Über Sitzungen merken", und ein Tracker, der
über Stunden immer wieder auftaucht, wird markiert. (Nur auf dem Gerät — nichts
verlässt dein Handy — und jederzeit abschaltbar.)

PRIVAT VON GRUND AUF
Keine Konten. Keine Werbung. Keine Analyse. Keine Datenerfassung. Alles passiert auf
deinem Gerät — deine Bluetooth-Scans und Bewegungsdaten verlassen das Handy nie.
blep ist kostenlos und quelloffen: https://github.com/fentas/blep.fyi

GUT ZU WISSEN
blep findet Dinge in Bluetooth-Reichweite (etwa ein Zimmer, eine Etage oder ein
Parkplatz). Es ist kein GPS-/Cloud-Tracker — es kann einen Anhänger nicht quer durch
die Stadt orten wie ein Find-My-Netzwerk. Es führt dich das letzte Stück, per Signal.
```

## Austrian German — de-AT

Austria shares the written German standard, so the listing text is identical to
**de-DE** above. (Use the de-DE block; only paste a separate `de-AT` if Play offers
the locale. The app ships a `de-AT` resource folder that inherits the German strings.)

---

## Slovenian — sl

**Short description (≤80):**
```
Najdi izgubljene naprave Bluetooth – in zaznaj neželene sledilnike. Brezplačno.
```

**Full description:**
```
blep spremeni tvoj telefon v topel/hladen kazalnik, ki te pripelje naravnost do
izgubljenih naprav Bluetooth — ključev z obeskom, slušalk, ure, zvočnika, avtomobila,
skoraj česar koli, kar oddaja Bluetooth.

Brez zemljevida, brez računa, brez nastavljanja. Samo izberi napravo in sledi občutku.

KAKO DELUJE
1. Umeritev — telefon ploščato prisloni na prsi. Tvoje telo zakrije signal od zadaj
   in ga naredi usmerjenega.
2. Zanihaj in pojdi — počasi se obračaj, dokler ni najtopleje, nato pojdi naprej.
   Velika puščica in topla/hladna barva vodita vsak korak.
3. Natančna določitev — od blizu blep proslavi trenutek, ko jo najdeš.

ZAKAJ JE DOBER
• Smer prek signala — kazalnik s kompasom in signalom, ne meglen radar.
• Sled na zaslonu, kje si že iskal, obarvana po moči signala.
• „Topleje sem" — če zaideš, te usmeri nazaj k najmočnejši točki.
• Pove ti, kdaj je okolje čisto ali moteno in kdaj signala sploh ni.
• Izbirni zvok in vibracije, ki utripajo hitreje, ko se približuješ.

TE KAJ ZASLEDUJE?
Pritisni „Te kaj zasleduje?", da poiščeš neželene sledilnike, ki potujejo s teboj —
AirTag, Tile, SmartTag ali oddajnik Find My, ki ti ga je morda kdo podtaknil v torbo,
jakno ali avto. blep zazna tudi trik, ki ga drugi spregledajo: sledilnik, ki menja
svoj Bluetooth ID, da ostane anonimen, se izda tako, da se znova in znova pojavlja na
enaki bližini — prav nepovezanost je povezava. In ker je blep iskalnik, ne le opozori
— pripelje te do njega. Vklopi „Zapomni si med sejami" in sledilnik, ki se skozi ure
vedno znova pojavlja, bo označen. (Samo na napravi — nič ne zapusti telefona — in
kadar koli izklopljivo.)

ZASNOVANO ZASEBNO
Brez računov. Brez oglasov. Brez analitike. Brez zbiranja podatkov. Vse se dogaja na
tvoji napravi — tvoji pregledi Bluetooth in gibanje nikoli ne zapustijo telefona.
blep je brezplačen in odprtokoden: https://github.com/fentas/blep.fyi

DOBRO JE VEDETI
blep najde stvari v dosegu Bluetooth (približno soba, nadstropje ali parkirišče). Ni
sledilnik GPS/v oblaku — obeska ne more najti čez mesto kot omrežje Find My. Vodi te
zadnji del poti, prek signala.
```

---

## Italian — it-IT

**Short description (≤80):**
```
Ritrova oggetti Bluetooth smarriti – e scopri i tracker che ti seguono. Gratis.
```

**Full description:**
```
blep trasforma il tuo telefono in un puntatore caldo/freddo che ti guida dritto agli
oggetti Bluetooth smarriti — chiavi con un tag, auricolari, un orologio, una cassa,
l'auto, quasi tutto ciò che trasmette Bluetooth.

Niente mappa, niente account, niente configurazione. Scegli il dispositivo e segui la
sensazione.

COME FUNZIONA
1. Calibra — tieni il telefono piatto contro il petto. Il tuo corpo scherma il segnale
   da dietro, rendendolo direzionale.
2. Ruota e cammina — gira lentamente finché non è più caldo, poi cammina in avanti.
   Una grande freccia e un colore caldo/freddo guidano ogni passo.
3. Individua — da vicino, blep festeggia il momento in cui lo trovi.

PERCHÉ È VALIDO
• Direzione tramite segnale — un puntatore bussola-e-segnale, non un radar sfocato.
• Una traccia sullo schermo di dove hai cercato, colorata in base all'intensità del
  segnale.
• „Più caldo da questa parte" — se ti allontani, ti riporta al punto più forte.
• Ti dice quando il campo è pulito o disturbato e quando non c'è segnale.
• Suono e vibrazione opzionali che pulsano più veloci man mano che ti avvicini.

QUALCOSA TI STA SEGUENDO?
Tocca „Qualcosa ti sta seguendo?" per cercare tracker indesiderati che viaggiano con
te — un AirTag, Tile, SmartTag o beacon Find My che qualcuno potrebbe averti infilato
nella borsa, nel cappotto o in auto. blep coglie anche il trucco che gli altri si
perdono: un tracker che cambia il suo ID Bluetooth per restare anonimo si tradisce
riapparendo alla stessa breve distanza, ancora e ancora — è proprio la non-correlazione
a fare la correlazione. E poiché blep è un cercatore, non si limita ad avvisarti — ti
ci porta. Attiva „Ricorda tra le sessioni" e un tag che continua a comparire nel corso
delle ore viene segnalato. (Solo sul dispositivo — nulla lascia il telefono — e
disattivabile in qualsiasi momento.)

PRIVATO PER PROGETTAZIONE
Nessun account. Nessuna pubblicità. Nessuna analisi. Nessuna raccolta dati. Tutto
avviene sul tuo dispositivo — le scansioni Bluetooth e il movimento non lasciano mai
il telefono. blep è gratuito e open source: https://github.com/fentas/blep.fyi

BUONO A SAPERSI
blep trova le cose nel raggio Bluetooth (circa una stanza, un piano o un parcheggio).
Non è un tracker GPS/cloud — non può localizzare un tag dall'altra parte della città
come una rete Find My. Ti guida nell'ultimo tratto, tramite il segnale.
```

---

## Swiss German — de-CH

Identical to **de-DE**, with the Swiss orthography rule `ß` → `ss` (e.g. "grosser
Pfeil", "Signalstärke" stays, "Strasse"-style words use ss). The only `ß` in the
de-DE full text is "großer" → **"grosser"**. Use the de-DE blocks with that change.

---

## Dutch — nl-NL

**Short description (≤80):**
```
Verloren Bluetooth-spullen vinden – en ongewenste trackers opsporen. Gratis.
```

**Full description:**
```
blep verandert je telefoon in een warm/koud-aanwijzer die je rechtstreeks naar je
verloren Bluetooth-spullen leidt — sleutels met een tag, oortjes, een horloge, een
speaker, je auto, vrijwel alles wat Bluetooth uitzendt.

Geen kaart, geen account, geen installatie. Kies gewoon het apparaat en volg het gevoel.

HOE HET WERKT
1. Kalibreren — houd de telefoon plat tegen je borst. Je lichaam schermt het signaal
   van achteren af, waardoor het richtinggevoelig wordt.
2. Zwenken & lopen — draai langzaam tot het het warmst is, loop dan vooruit. Een grote
   pijl en een warm/koud-kleur leiden elke stap.
3. Lokaliseren — van dichtbij viert blep het moment waarop je het vindt.

WAT HET GOED MAAKT
• Richting via signaal — een kompas-en-signaal-aanwijzer, geen vaag radarbeeld.
• Een spoor op het scherm van waar je hebt gezocht, gekleurd naar signaalsterkte.
• „Warmer deze kant op" — als je afdwaalt, wijst het je terug naar het sterkste punt.
• Vertelt je wanneer het veld schoon of verstoord is en wanneer er geen signaal is.
• Optioneel geluid + trilling die sneller pulseert naarmate je dichterbij komt.

WORD JE GEVOLGD?
Tik op „Word je gevolgd?" om te scannen op ongewenste trackers die met je meereizen —
een AirTag, Tile, SmartTag of Find My-baken dat iemand in je tas, jas of auto kan
hebben gestopt. blep ziet ook de truc die anderen missen: een tracker die zijn
Bluetooth-ID wisselt om anoniem te blijven, verraadt zich door telkens weer op
dezelfde korte afstand op te duiken — juist de niet-correlatie is de correlatie. En
omdat blep een zoeker is, waarschuwt het niet alleen — het brengt je erheen. Zet
„Onthouden tussen sessies" aan en een tag die urenlang blijft opduiken, wordt
gemarkeerd. (Alleen op het apparaat — niets verlaat je telefoon — en op elk moment
uit te zetten.)

PRIVÉ VAN ONTWERP
Geen accounts. Geen advertenties. Geen analyse. Geen gegevensverzameling. Alles
gebeurt op je apparaat — je Bluetooth-scans en bewegingen verlaten nooit de telefoon.
blep is gratis en open source: https://github.com/fentas/blep.fyi

GOED OM TE WETEN
blep vindt dingen binnen Bluetooth-bereik (ongeveer een kamer, een verdieping of een
parkeerplaats). Het is geen GPS-/cloudtracker — het kan een tag niet door de stad heen
lokaliseren zoals een Find My-netwerk. Het leidt je het laatste stuk, via het signaal.
```

---

## Spanish — es-ES

**Short description (≤80):**
```
Encuentra cosas Bluetooth perdidas y rastreadores que te siguen. Gratis.
```

**Full description:**
```
blep convierte tu teléfono en un puntero frío/caliente que te lleva directamente a tus
cosas Bluetooth perdidas — llaves con una etiqueta, auriculares, un reloj, un altavoz,
tu coche, casi cualquier cosa que emita Bluetooth.

Sin mapa, sin cuenta, sin configuración. Solo elige el dispositivo y sigue la sensación.

CÓMO FUNCIONA
1. Calibra — sostén el teléfono plano contra el pecho. Tu cuerpo bloquea la señal por
   detrás, lo que la hace direccional.
2. Gira y camina — gira despacio hasta que esté más caliente, luego camina hacia
   adelante. Una flecha grande y un color frío/caliente guían cada paso.
3. Localiza — de cerca, blep celebra el momento en que lo encuentras.

POR QUÉ ES BUENO
• Dirección por señal — un puntero de brújula y señal, no un radar borroso.
• Un rastro en pantalla de dónde has buscado, coloreado según la intensidad de la señal.
• „Más caliente por aquí" — si te desvías, te devuelve al punto más fuerte.
• Te dice cuándo el entorno está limpio o con interferencias y cuándo no hay señal.
• Sonido y vibración opcionales que laten más rápido a medida que te acercas.

¿ALGO TE ESTÁ SIGUIENDO?
Toca „¿Algo te está siguiendo?" para buscar rastreadores no deseados que viajan
contigo — un AirTag, Tile, SmartTag o baliza Find My que alguien podría haber metido en
tu bolso, abrigo o coche. blep también detecta el truco que a otros se les escapa: un
rastreador que cambia su ID Bluetooth para permanecer anónimo se delata al reaparecer
una y otra vez a la misma corta distancia — precisamente la no correlación es la
correlación. Y como blep es un buscador, no solo avisa — te lleva hasta él. Activa
„Recordar entre sesiones" y una etiqueta que sigue apareciendo durante horas queda
marcada. (Solo en el dispositivo — nada sale de tu teléfono — y se puede desactivar en
cualquier momento.)

PRIVADO POR DISEÑO
Sin cuentas. Sin anuncios. Sin analíticas. Sin recopilación de datos. Todo ocurre en tu
dispositivo — tus escaneos Bluetooth y tu movimiento nunca salen del teléfono. blep es
gratis y de código abierto: https://github.com/fentas/blep.fyi

BUENO SABERLO
blep encuentra cosas dentro del alcance Bluetooth (aproximadamente una habitación, una
planta o un aparcamiento). No es un rastreador GPS/en la nube — no puede localizar una
etiqueta al otro lado de la ciudad como una red Find My. Te guía el último tramo, por
la señal.
```

---

## Greek — el-GR

**Short description (≤80):**
```
Βρες χαμένα αντικείμενα Bluetooth – και εντόπισε ιχνηλάτες που σε ακολουθούν.
```

**Full description:**
```
Το blep μετατρέπει το τηλέφωνό σου σε έναν δείκτη ζεστού/κρύου που σε οδηγεί κατευθείαν
στα χαμένα σου αντικείμενα Bluetooth — κλειδιά με ετικέτα, ακουστικά, ένα ρολόι, ένα
ηχείο, το αυτοκίνητό σου, σχεδόν οτιδήποτε εκπέμπει Bluetooth.

Χωρίς χάρτη, χωρίς λογαριασμό, χωρίς ρυθμίσεις. Απλώς διάλεξε τη συσκευή και ακολούθησε
την αίσθηση.

ΠΩΣ ΛΕΙΤΟΥΡΓΕΙ
1. Βαθμονόμηση — κράτα το τηλέφωνο επίπεδο στο στήθος σου. Το σώμα σου θωρακίζει το
   σήμα από πίσω, κάνοντάς το κατευθυντικό.
2. Σάρωσε & περπάτα — γύρνα αργά μέχρι να γίνει πιο ζεστό, μετά προχώρα μπροστά. Ένα
   μεγάλο βέλος και ένα ζεστό/κρύο χρώμα καθοδηγούν κάθε βήμα.
3. Εντοπισμός — από κοντά, το blep γιορτάζει τη στιγμή που το βρίσκεις.

ΓΙΑΤΙ ΕΙΝΑΙ ΚΑΛΟ
• Κατεύθυνση μέσω σήματος — δείκτης πυξίδας και σήματος, όχι θολό ραντάρ.
• Ένα ίχνος στην οθόνη για το πού έχεις ψάξει, χρωματισμένο ανάλογα με την ισχύ του σήματος.
• „Πιο ζεστά από εδώ" — αν ξεφύγεις, σε δείχνει πίσω στο πιο δυνατό σημείο.
• Σου λέει πότε το περιβάλλον είναι καθαρό ή με παρεμβολές και πότε δεν υπάρχει σήμα.
• Προαιρετικός ήχος και δόνηση που πάλλεται πιο γρήγορα καθώς πλησιάζεις.

ΣΕ ΠΑΡΑΚΟΛΟΥΘΕΙ ΚΑΤΙ;
Πάτα „Σε παρακολουθεί κάτι;" για να σαρώσεις για ανεπιθύμητους ιχνηλάτες που ταξιδεύουν
μαζί σου — ένα AirTag, Tile, SmartTag ή φάρο Find My που κάποιος μπορεί να έβαλε στην
τσάντα, το παλτό ή το αυτοκίνητό σου. Το blep πιάνει και το κόλπο που οι άλλοι χάνουν:
ένας ιχνηλάτης που αλλάζει το Bluetooth ID του για να μείνει ανώνυμος προδίδεται
εμφανιζόμενος ξανά και ξανά στην ίδια κοντινή απόσταση — ακριβώς η μη συσχέτιση είναι η
συσχέτιση. Και επειδή το blep είναι ανιχνευτής, δεν προειδοποιεί μόνο — σε πάει σε
αυτόν. Ενεργοποίησε το „Απομνημόνευση μεταξύ συνεδριών" και μια ετικέτα που συνεχίζει να
εμφανίζεται για ώρες επισημαίνεται. (Μόνο στη συσκευή — τίποτα δεν φεύγει από το
τηλέφωνό σου — και απενεργοποιείται ανά πάσα στιγμή.)

ΣΧΕΔΙΑΣΜΕΝΟ ΓΙΑ ΙΔΙΩΤΙΚΟΤΗΤΑ
Χωρίς λογαριασμούς. Χωρίς διαφημίσεις. Χωρίς αναλυτικά στοιχεία. Χωρίς συλλογή δεδομένων.
Όλα συμβαίνουν στη συσκευή σου — οι σαρώσεις Bluetooth και η κίνησή σου δεν φεύγουν ποτέ
από το τηλέφωνο. Το blep είναι δωρεάν και ανοιχτού κώδικα: https://github.com/fentas/blep.fyi

ΚΑΛΟ ΕΙΝΑΙ ΝΑ ΞΕΡΕΙΣ
Το blep βρίσκει πράγματα εντός εμβέλειας Bluetooth (περίπου ένα δωμάτιο, έναν όροφο ή
έναν χώρο στάθμευσης). Δεν είναι ιχνηλάτης GPS/cloud — δεν μπορεί να εντοπίσει μια
ετικέτα στην άλλη άκρη της πόλης όπως ένα δίκτυο Find My. Σε καθοδηγεί στο τελευταίο
κομμάτι, μέσω σήματος.
```

---

## Estonian — et

**Short description (≤80):**
```
Leia kadunud Bluetooth-asjad – ja tuvasta soovimatud jälgijad. Tasuta.
```

**Full description:**
```
blep muudab su telefoni sooja/külma osutiks, mis juhatab sind otse kadunud
Bluetooth-asjadeni — sildiga võtmed, kõrvaklapid, kell, kõlar, su auto, peaaegu kõik,
mis Bluetoothi edastab.

Ei kaarti, ei kontot, ei seadistust. Lihtsalt vali seade ja järgi tunnet.

KUIDAS SEE TÖÖTAB
1. Kalibreeri — hoia telefoni lapiti vastu rinda. Su keha varjab signaali tagant,
   muutes selle suunatundlikuks.
2. Pööra ja kõnni — pööra aeglaselt, kuni on kõige soojem, siis kõnni edasi. Suur nool
   ja soe/külm värv juhivad iga sammu.
3. Täpne asukoht — lähedalt tähistab blep hetke, mil selle leiad.

MIKS SEE HEA ON
• Suund signaali järgi — kompassi ja signaali osuti, mitte hägune radar.
• Ekraanil jälg sellest, kus oled otsinud, värvitud signaalitugevuse järgi.
• „Soojem siiapoole" — kui eksid, juhatab see sind tagasi tugevaima punkti juurde.
• Ütleb, millal keskkond on puhas või häiritud ja millal signaali pole.
• Valikuline heli ja vibratsioon, mis pulseerib kiiremini, mida lähemale jõuad.

KAS KEEGI JÄLGIB SIND?
Puuduta „Kas keegi jälgib sind?", et otsida soovimatuid jälgijaid, kes sinuga kaasas
reisivad — AirTag, Tile, SmartTag või Find My majakas, mille keegi võis su kotti, jope
või autosse pista. blep märkab ka trikki, mille teised jätavad kahe silma vahele:
jälgija, mis vahetab oma Bluetoothi ID-d, et jääda anonüümseks, reedab end sellega, et
ilmub ikka ja jälle samale lähedusele — just korrelatsiooni puudumine ongi
korrelatsioon. Ja kuna blep on otsija, ei hoiata see ainult — see viib su kohale. Lülita
sisse „Pea seansside vahel meeles" ja silt, mis tundide jooksul aina uuesti ilmub,
märgistatakse. (Ainult seadmes — miski ei lahku su telefonist — ja igal ajal välja
lülitatav.)

PRIVAATNE JUBA DISAINILT
Ei kontosid. Ei reklaame. Ei analüütikat. Ei andmete kogumist. Kõik toimub su seadmes —
su Bluetooth-skannid ja liikumine ei lahku kunagi telefonist. blep on tasuta ja avatud
lähtekoodiga: https://github.com/fentas/blep.fyi

HEA TEADA
blep leiab asju Bluetoothi levialas (umbes tuba, korrus või parkla). See ei ole
GPS-/pilvejälgija — see ei suuda silti üle linna leida nagu Find My võrk. See juhatab
sind viimase otsa, signaali järgi.
```

---

## French — fr-FR

**Short description (≤80):**
```
Retrouve des objets Bluetooth perdus – et repère les traceurs qui te suivent.
```

**Full description:**
```
blep transforme ton téléphone en un pointeur chaud/froid qui te conduit droit à tes
objets Bluetooth perdus — des clés avec une balise, des écouteurs, une montre, une
enceinte, ta voiture, presque tout ce qui émet du Bluetooth.

Pas de carte, pas de compte, pas de configuration. Choisis simplement l'appareil et
suis la sensation.

COMMENT ÇA MARCHE
1. Calibre — tiens le téléphone à plat contre ta poitrine. Ton corps masque le signal
   par l'arrière, ce qui le rend directionnel.
2. Balaie et marche — tourne lentement jusqu'à ce que ce soit le plus chaud, puis
   avance. Une grande flèche et une couleur chaud/froid guident chaque pas.
3. Localise — de près, blep célèbre le moment où tu le trouves.

CE QUI LE REND BON
• Direction par le signal — un pointeur boussole-et-signal, pas un radar flou.
• Une trace à l'écran de l'endroit où tu as cherché, colorée selon la force du signal.
• « Plus chaud par ici » — si tu t'écartes, il te ramène vers le point le plus fort.
• Il te dit quand l'environnement est clair ou perturbé et quand il n'y a aucun signal.
• Son et vibration en option, qui pulsent plus vite à mesure que tu t'approches.

QUELQUE CHOSE TE SUIT-IL ?
Touche « Quelque chose te suit-il ? » pour rechercher des traceurs indésirables qui
voyagent avec toi — un AirTag, un Tile, un SmartTag ou une balise Find My que quelqu'un
aurait pu glisser dans ton sac, ton manteau ou ta voiture. blep repère aussi l'astuce
que les autres manquent : un traceur qui change son identifiant Bluetooth pour rester
anonyme se trahit en réapparaissant encore et encore à la même courte distance — c'est
précisément la non-corrélation qui fait la corrélation. Et comme blep est un chercheur,
il ne se contente pas d'avertir — il t'y conduit. Active « Mémoriser entre les
sessions » et une balise qui ne cesse de réapparaître au fil des heures est signalée.
(Uniquement sur l'appareil — rien ne quitte ton téléphone — et désactivable à tout
moment.)

PRIVÉ PAR CONCEPTION
Pas de comptes. Pas de publicités. Pas d'analyses. Aucune collecte de données. Tout se
passe sur ton appareil — tes analyses Bluetooth et tes mouvements ne quittent jamais le
téléphone. blep est gratuit et open source : https://github.com/fentas/blep.fyi

BON À SAVOIR
blep trouve les choses à portée Bluetooth (environ une pièce, un étage ou un parking).
Ce n'est pas un traceur GPS/cloud — il ne peut pas localiser une balise à l'autre bout
de la ville comme un réseau Find My. Il te guide sur la dernière portion, par le signal.
```

---

## Simplified Chinese — zh-CN (also zh-Hans)

**Short description (≤80):**
```
找回丢失的蓝牙物品——并发现跟踪你的追踪器。免费开源。
```

**Full description:**
```
blep 把你的手机变成一个冷热指针，引导你径直走向丢失的蓝牙物品——带标签的钥匙、耳机、手表、音箱，几乎任何会发出蓝牙信号的东西。

没有地图，没有账户，无需设置。只需选择设备，跟着感觉走。

工作原理
1. 校准——把手机平贴在胸前。你的身体从背后屏蔽信号，使其具有方向性。
2. 扫动并行走——慢慢转动，直到信号最强，然后向前走。大箭头和冷热颜色引导每一步。
3. 精确定位——靠近时，blep 会庆祝你找到它的那一刻。

它好在哪里
• 凭信号指方向——指南针加信号的指针，而非模糊的雷达。
• 在屏幕上显示你搜索过的轨迹，按信号强度着色。
• “这边更暖”——如果你偏离，它会把你指回最强的位置。
• 告诉你环境是干净还是嘈杂，以及何时完全没有信号。
• 可选的声音和振动，越靠近脉动越快。

有东西在跟踪你吗？
点按“有东西在跟踪你吗？”，扫描跟着你的不需要的追踪器——AirTag、Tile、SmartTag，或有人可能塞进你包里、外套或车里的 Find My 信标。blep 还能识破其他应用错过的伎俩：一个不断更换蓝牙 ID 以保持匿名的追踪器，会因为一次次在相同近距离出现而暴露自己——不相关本身就是相关。而且因为 blep 是个查找器，它不仅警告——还把你带到它那里。开启“跨会话记住”，一个数小时内不断出现的标签就会被标记。（仅在设备上——任何信息都不会离开你的手机——可随时关闭。）

注重隐私的设计
没有账户。没有广告。没有分析。不收集数据。一切都在你的设备上进行——你的蓝牙扫描和运动感测从不离开手机。blep 免费且开源：https://github.com/fentas/blep.fyi

须知
blep 在蓝牙范围内查找物品（大约一个房间、一层楼或一个停车场）。它不是 GPS/云端追踪器——无法像 Find My 网络那样跨越城市定位标签。它凭信号引导你走完最后一段。
```

---

## Japanese — ja-JP

**Short description (≤80):**
```
なくした Bluetooth の物を見つけ、追跡するトラッカーも発見。無料・オープンソース。
```

**Full description:**
```
blep はあなたのスマホを冷暖ポインターに変え、なくした Bluetooth の物——タグ付きの鍵、イヤホン、時計、スピーカーなど、Bluetooth を発信するほとんどすべて——へまっすぐ案内します。

地図なし、アカウントなし、設定なし。デバイスを選んで、感覚に従うだけ。

使い方
1. 較正——スマホを胸に平らに当てます。体が背後から信号を遮り、方向性を持たせます。
2. スイープして歩く——一番温かくなるまでゆっくり回り、前へ歩きます。大きな矢印と冷暖の色が一歩ごとに案内します。
3. ピンポイント——近づくと、blep が見つけた瞬間を祝います。

優れている点
• 信号で方向を——コンパスと信号のポインターで、ぼやけたレーダーではありません。
• 探した場所の軌跡を画面に表示、信号強度で色分け。
• 「こちらが暖かい」——逸れると、最も強い場所へ戻るよう示します。
• 環境がクリーンか雑音まじりか、信号が全くないかを教えます。
• 任意の音と振動が、近づくほど速く脈打ちます。

誰かに追跡されていませんか？
「誰かに追跡されていませんか？」をタップして、あなたと一緒に移動する不要なトラッカー——AirTag、Tile、SmartTag、または誰かがバッグ、コート、車に忍ばせた Find My ビーコン——をスキャンします。blep は他のアプリが見逃す手口も捉えます：匿名を保つために Bluetooth ID を変え続けるトラッカーは、同じ近距離に何度も現れることで自らを暴きます——無相関こそが相関です。そして blep は探知機なので、警告するだけでなく——そこへ案内します。「セッション間で記憶」をオンにすると、何時間も現れ続けるタグにフラグが立ちます。（デバイス上のみ——何もスマホから出ません——いつでもオフにできます。）

プライバシー重視の設計
アカウントなし。広告なし。分析なし。データ収集なし。すべてがあなたのデバイス上で行われ——Bluetooth スキャンと動きの検知はスマホから出ません。blep は無料・オープンソースです：https://github.com/fentas/blep.fyi

知っておくと良いこと
blep は Bluetooth の範囲内（おおよそ部屋、フロア、駐車場）で物を見つけます。GPS/クラウドのトラッカーではなく——Find My ネットワークのように街の向こうのタグを特定することはできません。信号で最後の道のりを案内します。
```

---

## Croatian — hr

**Short description (≤80):**
```
Pronađi izgubljene Bluetooth stvari – i uhvati nepoželjne pratitelje. Besplatno.
```

**Full description:**
```
blep pretvara tvoj telefon u topli/hladni pokazivač koji te vodi ravno do izgubljenih
Bluetooth stvari — ključeva s oznakom, slušalica, sata, zvučnika, gotovo svega što
emitira Bluetooth.

Bez karte, bez računa, bez postavljanja. Samo odaberi uređaj i prati osjećaj.

KAKO RADI
1. Kalibriraj — drži telefon plosnato uz prsa. Tvoje tijelo zaklanja signal sa stražnje
   strane, što ga čini usmjerenim.
2. Zakreni i hodaj — okreći se polako dok nije najtoplije, zatim hodaj naprijed. Velika
   strelica i topla/hladna boja vode svaki korak.
3. Precizno lociraj — izbliza blep slavi trenutak kada ga pronađeš.

ZAŠTO JE DOBAR
• Smjer prema signalu — pokazivač s kompasom i signalom, a ne mutni radar.
• Trag na zaslonu gdje si tražio, obojen prema jačini signala.
• „Toplije ovuda" — ako zalutaš, usmjeri te natrag na najjaču točku.
• Govori ti kada je okruženje čisto ili bučno i kada uopće nema signala.
• Izborni zvuk i vibracije koje pulsiraju brže kako se približavaš.

PRATI LI TE NEŠTO?
Dodirni „Prati li te nešto?" da pretražiš nepoželjne pratitelje koji putuju s tobom —
AirTag, Tile, SmartTag ili Find My odašiljač koji ti je netko možda podmetnuo u torbu,
kaput ili auto. blep hvata i trik koji drugi propuštaju: pratitelj koji mijenja svoj
Bluetooth ID da ostane anoniman odaje se ponovnim pojavljivanjem na istoj blizini, iznova
i iznova — upravo je nepovezanost povezanost. I budući da je blep tragač, ne samo da
upozorava — vodi te do njega. Uključi „Zapamti između sesija" i oznaka koja se satima
ponavlja bit će označena. (Samo na uređaju — ništa ne napušta tvoj telefon — i može se
isključiti u svakom trenutku.)

PRIVATNOST OD TEMELJA
Bez računa. Bez oglasa. Bez analitike. Bez prikupljanja podataka. Sve se događa na tvom
uređaju — tvoja Bluetooth pretraživanja i očitavanje kretanja nikad ne napuštaju telefon.
blep je besplatan i otvorenog koda: https://github.com/fentas/blep.fyi

DOBRO JE ZNATI
blep pronalazi stvari u dosegu Bluetootha (otprilike soba, kat ili parkiralište). Nije
GPS/oblak tragač — ne može locirati oznaku preko grada kao Find My mreža. Vodi te zadnji
dio puta, prema signalu.
```

---

## Polish — pl-PL

**Short description (≤80):**
```
Znajdź zgubione rzeczy Bluetooth – i wykryj śledzące lokalizatory. Bezpłatne.
```

**Full description:**
```
blep zamienia twój telefon we wskaźnik ciepło/zimno, który prowadzi cię prosto do
zgubionych rzeczy Bluetooth — kluczy z lokalizatorem, słuchawek, zegarka, głośnika,
niemal wszystkiego, co nadaje Bluetooth.

Bez mapy, bez konta, bez konfiguracji. Po prostu wybierz urządzenie i podążaj za wyczuciem.

JAK TO DZIAŁA
1. Skalibruj — trzymaj telefon płasko przy piersi. Twoje ciało osłania sygnał od tyłu,
   co czyni go kierunkowym.
2. Obracaj i idź — obracaj się powoli, aż będzie najcieplej, potem idź naprzód. Duża
   strzałka i kolor ciepło/zimno prowadzą każdy krok.
3. Namierz — z bliska blep świętuje moment, w którym to znajdujesz.

CO GO WYRÓŻNIA
• Kierunek według sygnału — wskaźnik kompasu i sygnału, a nie rozmyty radar.
• Ślad na ekranie, gdzie szukałeś, pokolorowany według siły sygnału.
• „Cieplej tędy" — jeśli zboczysz, wskaże ci drogę z powrotem do najmocniejszego punktu.
• Mówi ci, kiedy otoczenie jest czyste lub zakłócone i kiedy w ogóle nie ma sygnału.
• Opcjonalny dźwięk i wibracje, które pulsują szybciej, im bliżej jesteś.

CZY COŚ CIĘ ŚLEDZI?
Dotknij „Czy coś cię śledzi?", aby wyszukać niechciane lokalizatory podróżujące z tobą —
AirTag, Tile, SmartTag lub nadajnik Find My, który ktoś mógł wsunąć do twojej torby,
płaszcza lub samochodu. blep wyłapuje też sztuczkę, którą inni pomijają: lokalizator,
który zmienia swój identyfikator Bluetooth, aby pozostać anonimowym, zdradza się,
pojawiając się raz za razem na tej samej bliskiej odległości — to właśnie brak korelacji
jest korelacją. A ponieważ blep jest wyszukiwarką, nie tylko ostrzega — prowadzi cię do
niego. Włącz „Pamiętaj między sesjami", a etykieta, która godzinami wciąż się pojawia,
zostanie oznaczona. (Tylko na urządzeniu — nic nie opuszcza twojego telefonu — i można to
wyłączyć w każdej chwili.)

PRYWATNOŚĆ Z ZAŁOŻENIA
Bez kont. Bez reklam. Bez analityki. Bez zbierania danych. Wszystko dzieje się na twoim
urządzeniu — twoje skany Bluetooth i wykrywanie ruchu nigdy nie opuszczają telefonu.
blep jest bezpłatny i open source: https://github.com/fentas/blep.fyi

WARTO WIEDZIEĆ
blep znajduje rzeczy w zasięgu Bluetooth (mniej więcej pokój, piętro lub parking). To nie
jest lokalizator GPS/w chmurze — nie potrafi zlokalizować etykiety na drugim końcu miasta
jak sieć Find My. Prowadzi cię przez ostatni odcinek, według sygnału.
```

---

## Portuguese — pt-PT (also pt-BR with minor tweaks)

**Short description (≤80):**
```
Encontra objetos Bluetooth perdidos e rastreadores que te seguem. Grátis.
```

**Full description:**
```
blep transforma o teu telemóvel num ponteiro quente/frio que te leva diretamente aos teus
objetos Bluetooth perdidos — chaves com uma etiqueta, auriculares, um relógio, uma coluna,
quase tudo o que emite Bluetooth.

Sem mapa, sem conta, sem configuração. Basta escolher o dispositivo e seguir a sensação.

COMO FUNCIONA
1. Calibra — segura o telemóvel plano contra o peito. O teu corpo bloqueia o sinal por
   trás, tornando-o direcional.
2. Roda e anda — roda devagar até estar mais quente, depois anda em frente. Uma seta
   grande e uma cor quente/fria guiam cada passo.
3. Localiza — de perto, o blep celebra o momento em que o encontras.

PORQUE É BOM
• Direção pelo sinal — um ponteiro de bússola e sinal, não um radar difuso.
• Um rasto no ecrã de onde procuraste, colorido pela intensidade do sinal.
• „Mais quente por aqui" — se te desviares, aponta-te de volta ao ponto mais forte.
• Diz-te quando o ambiente está limpo ou com ruído e quando não há sinal nenhum.
• Som e vibração opcionais que pulsam mais depressa à medida que te aproximas.

ALGO TE ESTÁ A SEGUIR?
Toca em „Algo te está a seguir?" para procurar rastreadores indesejados que viajam contigo
— um AirTag, Tile, SmartTag ou baliza Find My que alguém possa ter metido na tua mala,
casaco ou carro. O blep também apanha o truque que os outros falham: um rastreador que
muda o seu ID Bluetooth para se manter anónimo trai-se ao reaparecer à mesma curta
distância, vezes sem conta — é precisamente a não correlação que faz a correlação. E como o
blep é um localizador, não se limita a avisar — leva-te até ele. Ativa „Lembrar entre
sessões" e uma etiqueta que continua a aparecer ao longo das horas é assinalada. (Apenas no
dispositivo — nada sai do teu telemóvel — e podes desativar a qualquer momento.)

PRIVADO POR CONCEÇÃO
Sem contas. Sem anúncios. Sem análises. Sem recolha de dados. Tudo acontece no teu
dispositivo — as tuas análises Bluetooth e a deteção de movimento nunca saem do telemóvel.
O blep é gratuito e de código aberto: https://github.com/fentas/blep.fyi

BOM SABER
O blep encontra coisas dentro do alcance Bluetooth (aproximadamente uma sala, um piso ou
um estacionamento). Não é um rastreador GPS/na nuvem — não consegue localizar uma etiqueta
do outro lado da cidade como uma rede Find My. Guia-te no último trajeto, pelo sinal.
```
