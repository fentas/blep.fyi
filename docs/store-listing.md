# Store listing — paste-ready copy

Everything you need to fill in Google Play Console + App Store Connect. Keep it
in sync with the app; regenerate screenshots with `make screenshots`.

---

## Google Play

### App name (max 30)
```
blep
```
(The published listing title is just **blep**; the "Bluetooth finder" framing
lives in the short description and the feature graphic.)

### Short description (max 80)
```
Find lost Bluetooth things — and catch unwanted trackers following you. Free.
```

### Full description (max 4000)
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

KNOW YOUR DEVICES
Tap any nearby device to open its panel — its live signal, whether its Bluetooth
address is fixed or rotating, and how long ago you first saw it.
• "Identify this device" — blep briefly connects to read its real name, maker and
  model, turning an "unknown tracker" into "someone's earbuds".
• Rename a device, and the name follows it even when it rotates its ID.
• "Watch this device" — flag one and blep keeps a live eye on it, notifying you
  whenever it's in range.
• blep stitches a rotating ID back into one device — its history, how sure it is,
  and your rename don't reset every few minutes. All on-device; you choose how long
  a device is remembered, and can clear it any time.

IS SOMETHING TRACKING YOU?
Tap "Is something tracking you?" to scan for unwanted trackers travelling with
you — an AirTag, Tile, SmartTag or Find My beacon someone may have slipped into
your bag, coat or car. blep also catches the trick the others miss: a tracker
that rotates its Bluetooth ID to stay anonymous gives itself away by reappearing
at the same close range, again and again — the un-correlation is the correlation.
And because blep is a finder, it doesn't just warn — it points you to it.
• Remember across sessions — a tag that keeps showing up over the hours (and, if
  you allow coarse location, across separate places) gets flagged.
• Watch in the background — let blep keep an eye out while it's closed and notify
  you, on a battery-friendly schedule.
• Sensitivity — Relaxed, Balanced or Strict, to tune how eagerly it flags.
On-device only — nothing ever leaves your phone — and you can turn it all off any
time.

PRIVATE BY DESIGN
No accounts. No ads. No analytics. No data collection. Everything happens on
your device — your Bluetooth scans and motion sensing never leave the phone.
Location is off by default; switch it on and it stays coarse and on-device, used
only to count the distinct places a tracker follows you across.
blep is free and open source: https://github.com/fentas/blep.fyi

GOOD TO KNOW
blep finds things within Bluetooth range (roughly a room, a floor, or a parking
lot). It is not a GPS/cloud tracker — it can't locate a tag across town the way a
Find My network does. It guides you the last stretch, by signal.
```

### Release notes (What's new)
```
blep 🐾 — find your lost Bluetooth things, guided by signal strength. Calibrate,
sweep, walk, done. The safety scan finds unwanted trackers following you and
points you to them — now with optional background watching, Relaxed/Balanced/
Strict sensitivity, and opt-in coarse-location "distinct places" detection. New
Settings page and favourites/paired manager. Free & open source, no tracking.
```
(Per-release note; trim to the actual changes when shipping a specific version.)

### Other listing fields
- **Category:** Tools
- **Tags:** bluetooth, finder, tracker, utilities
- **Website:** https://blep.fyi
- **Privacy policy:** https://blep.fyi/privacy.html
- **Email:** (your support email)

### Data safety form
- **Does your app collect or share any required user data?** → **No.**
  (Under Play's definition, "collect" means transmitted off-device; everything
  blep touches stays on the phone, so nothing is *collected* even though some of
  it — including optional location — is *accessed* on-device.)
  - blep processes Bluetooth scan results and motion sensor readings **only on the
    device, in the moment**, to guide you. None of it is collected, stored
    off-device, transmitted, or shared. (The scan declares `neverForLocation` on
    Android 12+; on Android ≤11, location permission is required by the OS to scan
    at all, used on-device only.)
  - **Optional location (off by default):** if the user turns on location-aware
    detection, blep reads a **coarse** fix and buckets it into a ~2 km grid cell
    so it can count the distinct *places* a tracker follows the user across. It is
    used **on-device only**, never stored as raw coordinates, never transmitted —
    so still "not collected" under Play's definition. It is opt-in and can be
    turned off any time. (Location is *not* used for the finder — only detection.)
  - The safety "Remember across sessions" log stays **on-device** (a tracker
    *kind* + timestamp, plus the coarse place cell when location is on — no
    identity, no raw coordinates) and is never transmitted — so still "not
    collected." It's opt-in, can be turned off, and is cleared when switched off.
  - The "It's mine" mute list stores the addresses of trackers the user marked as
    their own — also **on-device only**, bounded, never transmitted (so likewise
    "not collected").
- **Is all user data encrypted in transit?** → N/A (no data leaves the device).
- **Do you provide a way to request data deletion?** → N/A (nothing is stored).

### App content declarations
- **Ads:** No ads.
- **Content rating:** complete the questionnaire as **Everyone / PEGI 3** — no
  violence, no user-generated content, no sharing, no purchases, no data
  collection. (IARC: select "Utility / Productivity / Communication", answer No
  to every objectionable-content question.)
- **Target audience:** 13+ (not directed at children).
- **App access:** All functionality is available without an account — note this,
  no test credentials needed.
- **Government app / Financial / Health:** No.

### Permissions justification (if asked)
- **Bluetooth (scan/connect):** core function — scanning for and ranging the
  device you're finding.
- **Location (coarse):** two distinct reasons. (1) Older Android (≤11) requires
  location permission to perform any Bluetooth scan; on Android 12+ blep declares
  `BLUETOOTH_SCAN` with `neverForLocation` for scanning. (2) The **opt-in,
  off-by-default** location-aware detection reads a coarse fix to count the
  distinct places a tracker follows the user across — used on-device only, bucketed
  to a ~2 km cell, never stored as raw coordinates, never transmitted. Finder
  direction is derived from motion sensors, never location.

---

## App Store (iOS / watchOS)

### Name (max 30)
```
blep
```

### Subtitle (max 30)
```
Find things by signal
```

### Promotional text (max 170)
```
Lost your keys, earbuds or car? blep is a warm/cold pointer that walks you right
to anything nearby with Bluetooth. No account, no ads, no tracking.
```

### Keywords (max 100, comma-separated)
```
bluetooth,finder,tracker,find,keys,earbuds,lost,signal,ble,locator,airtag,tracker detector
```

### Description
(Reuse the Play full description above — it fits within the App Store limit.)

### App privacy (nutrition labels)
- **Data Not Collected.** blep collects no data. Bluetooth, location and motion
  are used on-device only and never linked to you or sent anywhere.

### Support / marketing URLs
- Support: https://github.com/fentas/blep.fyi
- Marketing: https://blep.fyi
- Privacy: https://blep.fyi/privacy.html

---

## Screenshots
Generated by `make screenshots` (the captioned "continuous thread"):
- `screenshots/store/phone/01–05.png` — 1440×2560 (9:16). Upload to the **Phone**,
  **7-inch tablet** and **10-inch tablet** slots (one 9:16 set is valid for all three).
- `screenshots/store/chromebook/01–05.png` — 2560×1440 (16:9) for the Chromebook slot.
- `screenshots/store/icon-512.png` (app icon) · `feature-1024x500.png` (feature graphic).

Upload in order. Raw device frames are in `screenshots/raw/` if you'd rather caption
them differently.
