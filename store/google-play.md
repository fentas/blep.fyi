# Google Play listing — blep (Android / Wear OS)

## App name (30 char max)
`BLEP: BLE Pointer & Tracker`

## Short description (80 char max)
`Find lost Bluetooth devices with a warm/cold pointer. Calibrate, sweep, done.`

## Full description (4000 char max)

**Find your lost Bluetooth things — guided by feel.**

blep is a beautifully minimal finder for the Bluetooth devices already around
you. Pick one, and a single animated arrow plus a colour that shifts from cool
to warm guides you in — calibrate, sweep, walk, done.

**The body-shielding trick**
Hold your phone to your chest and your body shields the signal from behind,
making it directional — strongest when the device is in front of you. blep
reads the change in signal strength (RSSI) as you move and tells you *warmer*,
*turn back*, or *keep going*.

**How it works**
• Hold at your chest to calibrate a baseline
• Turn slowly until the signal is warmest
• Walk forward while it keeps improving
• Kneel and pinpoint up close
• Finished — congratulations!

**Made to be lovely**
• Clean, high-contrast typography and soft pastel motion
• One calm arrow — no clutter, no maps, no accounts
• Works on Wear OS too, right from your wrist
• Free, open source, and private — scanning stays on your device

blep is an assistive guide based on Bluetooth signal strength. It shines for the
"it's somewhere in this room" moment — it is not a centimetre-precise locator,
and like all RSSI tools it can be affected by walls and reflections.

Open source: https://github.com/fentas/blep.fyi

## Permissions rationale
• **Nearby devices (BLUETOOTH_SCAN/CONNECT)** — to discover and range nearby
  Bluetooth Low Energy devices. blep declares `neverForLocation`; it does not
  use Bluetooth to derive your physical location.
• **Location (Android 11 and below only)** — older Android requires location
  permission for any BLE scan; blep never reads your location.

## Tags / category
Category: Tools · Tags: bluetooth, finder, tracker, locator, ble, proximity

## Privacy
No data collected or shared. All scanning is on-device.

## Branding note
Title, icon, screenshots and copy are identical to the App Store listing so the
brand reads the same across stores.
