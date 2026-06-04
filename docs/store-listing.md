# Store listing — paste-ready copy

Everything you need to fill in Google Play Console + App Store Connect. Keep it
in sync with the app; regenerate screenshots with `make screenshots`.

---

## Google Play

### App name (max 30)
```
blep — Bluetooth Finder
```

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
• Companion apps for Apple Watch and Wear OS.

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

### Release notes (What's new)
```
First public build of blep 🐾 — find your lost Bluetooth things, guided by signal
strength. Calibrate, sweep, walk, done. Plus a safety scan that finds unwanted
trackers following you — and points you to them. Free & open source, no tracking.
```

### Other listing fields
- **Category:** Tools
- **Tags:** bluetooth, finder, tracker, utilities
- **Website:** https://blep.fyi
- **Privacy policy:** https://blep.fyi/privacy.html
- **Email:** (your support email)

### Data safety form
- **Does your app collect or share any required user data?** → **No.**
  - blep processes Bluetooth scan results and motion sensor readings **only on the
    device, in the moment**, to guide you. None of it is collected, stored
    off-device, transmitted, or shared. (On Android 12+ the scan declares
    `neverForLocation` and no location is accessed; on Android ≤11, location
    permission is required by the OS to scan at all, used on-device only.)
  - The safety "Remember across sessions" log stays **on-device** (a tracker
    *type* + timestamp, no identity or location) and is never transmitted — so it
    is still "not collected" under Play's definition (data that never leaves the
    device). It's on by default (because it never leaves the phone), can be turned
    off, and is cleared when switched off.
  - The "It's mine" mute list stores the addresses of trackers the user marked as
    their own — also **on-device only**, bounded, never transmitted (so likewise
    "not collected"). No location data is stored anywhere.
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
- **Location (Android ≤11 only):** older Android requires location permission to
  perform any Bluetooth scan. On Android 12+ blep declares `BLUETOOTH_SCAN` with
  `neverForLocation` and accesses no location at all. Direction is derived from the
  motion sensors, not location. Used on-device only, never stored or transmitted.

---

## App Store (iOS / watchOS)

### Name (max 30)
```
blep — Bluetooth Finder
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
bluetooth,finder,tracker,find,keys,earbuds,lost,signal,ble,locator,airtag,anti stalker
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
