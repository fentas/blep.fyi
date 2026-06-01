# Project BLEP: Mobile BLE Pointer & Tracker

## 1. Project Initialization & Architecture
- [ ] Initialize Git repository and set up branching strategy for GitHub Pages (`gh-pages`).
- [ ] Scaffold cross-platform mobile app (React Native / Expo or Kotlin Multiplatform) targeting iOS and Android.
- [ ] Configure native BLE permissions (`NSBluetoothAlwaysUsageDescription` for iOS, `ACCESS_FINE_LOCATION` and `BLUETOOTH_SCAN` for Android).
- [ ] Scaffold standard web directory for the `blep.fyi` landing page.
- [ ] Set up testing frameworks (Jest for JS/TS, or JUnit for Kotlin).

## 2. Core BLE Engine (The "Body-Shielding" Logic)
- [ ] Implement BLE scanner service to fetch nearby device UUIDs, Names, and real-time RSSI values.
- [ ] Build the RSSI Delta (ΔRSSI) calculator to measure signal strength changes over time.
- [ ] Create the State Machine for the tracking flow:
    - `STATE_DISCOVERY`: Polling list of active devices.
    - `STATE_CALIBRATION`: Logging baseline RSSI (phone at chest).
    - `STATE_AXIS_SWEEP`: Calculating orientation via ΔRSSI (spinning).
    - `STATE_VECTOR_WALK`: Tracking forward progression.
    - `STATE_PINPOINT`: Final floor-level proximity check.

## 3. Minimalist UX & Design System
- [ ] Define global design tokens: 
    - Typography: High-contrast, beautiful geometric sans-serif (e.g., Inter or SF Pro).
    - Colors: Dynamic pastel palette that shifts based on proximity (e.g., cool pastel blue for 'far', shifting seamlessly to warm pastel green/yellow for 'close').
- [ ] Build the `Discovery` screen: Clean list, highlight actively connected devices, hide unknown/null names by default. (but explicit show button/action)
- [ ] Implement the `Tracking` screen UI:
    - Build the animated vector arrow component that scales and rotates smoothly based on the state machine's guidance.
    - Add subtle, typography-driven instructional text ("Hold at chest", "Turn slowly", "Stop, turn back").
- [ ] Implement the `Completion` screen: Arrow shrinks, UI transitions to a polite "Finished. Congratulations!" state.
- [ ] Integrate a minimalistic "Help/Donate" button on the completion screen.

## 4. Website & GitHub Pages Deployment
- [ ] Design a one-page static HTML/Tailwind site for `blep.fyi`.
- [ ] Match the app's minimalist aesthetic and pastel typography.
- [ ] Include App Store and Google Play placeholder badges.
- [ ] Configure GitHub Actions to automatically deploy the `/web` directory to the `gh-pages` branch.

## 5. Documentation & Testing
- [ ] Write unit tests for the RSSI Delta calculator and State Machine transitions.
- [ ] Write integration tests for the BLE permission flow.
- [ ] Generate comprehensive `README.md` with:
    - Project architecture.
    - Build instructions for iOS and Android.
    - Explanation of the body-shielding RSSI localization technique.

## 6. App Store Optimization (ASO) & Release Prep
- [ ] Generate the iOS App Store listing text:
    - **Title:** BLEP: BLE Pointer & Tracker
    - **Subtitle:** Find Lost Bluetooth Devices
    - **Description:** Highlight the unique body-shielding tracking method and minimalist design.
- [ ] Generate the Google Play Store listing text (using identical branding).
- [ ] Export high-resolution graphical assets (App icons featuring the 'Blep' mascot concept, minimalist screenshots).



# Work

- logo: ./logo.svg
- commmit in between
- do all autonoumues
- clean structure
- feature proof code, maintainable
- trigger yourself (monitor) if all is done

- beautiful UI and best UX (minimal for website and app) suttle animations 

Human description:
sure, I think for this usecase we can display what we get, no big deal breaker. can add a rename function.

My idea is you open the app, minimalistic, beatuiful typeography, you see devices (including connected - mybe highlighed). You tab on one. you see an animated arrow, for the following procedure (including settel screen color changes)

1. (keep device at your chest - this limits signal strength in front of you

2. advices so you turn on your accesses.

3. (signal keeps better, keep turning, if worse it shows stop and back)

4. then advices to keep going foward until signal gets worse

5. repeat 2. then advises to kneel down.



-> finished.

Arrow gets smaller and buttons apear.

Polite: Finished, congratiualtions help -> donate. 


