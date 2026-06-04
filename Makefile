# blep — developer tasks. Run `make` (or `make help`) for the list.
# Toolchain (JDK + Gradle) comes from mise; the Android SDK from $(ANDROID_HOME).

ANDROID_HOME ?= $(HOME)/android-sdk
ADB         := $(ANDROID_HOME)/platform-tools/adb
SDKMANAGER  := $(ANDROID_HOME)/cmdline-tools/latest/bin/sdkmanager
AVDMANAGER  := $(ANDROID_HOME)/cmdline-tools/latest/bin/avdmanager
EMULATOR    := $(ANDROID_HOME)/emulator/emulator
GRADLE      := mise exec -- ./gradlew
APP_ID      := fyi.blep
AVD         ?= blep
SYSIMG      ?= system-images;android-35;google_apis;x86_64

.DEFAULT_GOAL := help
.PHONY: help setup doctor test sim scenarios chaos robustness build apk aab \
        install install-wear run demo uninstall devices logcat \
        emulator-setup emulator screenshots web web-build web-icons \
        ci apple clean

help: ## Show this help
	@awk 'BEGIN{FS=":.*##"; printf "\nblep — make targets\n\n"} /^[a-zA-Z0-9_-]+:.*##/ {printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)
	@echo ""

# ───────────────────────────── setup ──────────────────────────────
setup: ## Install toolchain (mise tools) and website deps
	mise install
	cd web && npm install

doctor: ## Print tool versions and connected devices
	@mise exec -- java -version 2>&1 | head -1
	@mise exec -- ./app/gradlew --version 2>/dev/null | grep Gradle || true
	@echo "ANDROID_HOME=$(ANDROID_HOME)"
	@$(ADB) devices

# ─────────────────────────── app: build ───────────────────────────
test: ## Run core unit tests on the JVM (no Android SDK needed)
	cd app && $(GRADLE) :core:jvmTest -Pblep.android=false

# Pull the println'd table out of a sim test's captured stdout.
define print_sim
	@python3 -c "import re,glob; f=sorted(glob.glob('app/core/build/test-results/jvmTest/*TrackingSimulation*.xml'))[-1]; s=open(f).read(); m=re.search(r'CDATA\[(.*?)\]\]',s,re.S); print(m.group(1).strip() if m else '(no output)')"
endef

sim: ## Run the closed-loop tracking simulation + print the scenario table
	-cd app && $(GRADLE) :core:jvmTest --tests '*TrackingSimulationTest.simulation*' -Pblep.android=false --rerun-tasks -q
	$(print_sim)

scenarios: sim ## Alias for `sim`

chaos: ## Random-search every tuning knob (CHAOS_N=60, override on the CLI)
	-cd app && CHAOS_N=$${CHAOS_N:-60} $(GRADLE) :core:jvmTest --tests '*TrackingSimulationTest.chaos*' -Pblep.android=false --rerun-tasks -q
	$(print_sim)

robustness: ## Held-out generalisation over random worlds (ROBUST_N, ROBUST_SEED)
	-cd app && $(GRADLE) :core:jvmTest --tests '*TrackingSimulationTest.robustness*' -Pblep.android=false --rerun-tasks -q
	$(print_sim)

build: ## Build the phone + Wear debug APKs
	cd app && $(GRADLE) :composeApp:assembleDebug :wearApp:assembleDebug
	@echo "APKs:"
	@find app -path '*outputs/apk/debug/*.apk'

apk: build ## Alias for `build`

aab: ## Build the signed release AAB for Play (needs app/keystore.properties)
	cd app && $(GRADLE) :composeApp:bundleRelease
	@echo "AAB: app/composeApp/build/outputs/bundle/release/composeApp-release.aab"

clean: ## Clean Gradle + web build outputs
	cd app && $(GRADLE) clean
	rm -rf web/dist

# ─────────────────────── app: device / phone ──────────────────────
devices: ## List connected devices (adb)
	$(ADB) devices

install: ## Build + install the phone app on a connected device
	@$(ADB) get-state >/dev/null 2>&1 || { echo "No device. Plug in + enable USB debugging, then 'make devices'."; exit 1; }
	cd app && $(GRADLE) :composeApp:installDebug

run: install ## Install and launch blep on the phone
	$(ADB) shell am start -n $(APP_ID)/.MainActivity

install-wear: ## Build + install the Wear OS app (needs a watch/Wear device)
	cd app && $(GRADLE) :wearApp:installDebug

demo: ## Install + launch the app in demo mode (scripted data, no BLE needed)
	cd app && $(GRADLE) :composeApp:assembleDebug -q
	$(ADB) install -r app/composeApp/build/outputs/apk/debug/composeApp-debug.apk
	$(ADB) shell am start -n $(APP_ID)/.MainActivity --ez demo true

uninstall: ## Remove blep from the connected device
	-$(ADB) uninstall $(APP_ID)

logcat: ## Tail blep logs from the device (app must be running)
	$(ADB) logcat --pid=$$($(ADB) shell pidof -s $(APP_ID))

# ───────────────────────── app: emulator ──────────────────────────
# Note: emulators have NO Bluetooth/motion sensors, so REAL tracking can't run
# there — use a real phone for that. But `make demo`/`make screenshots` feed
# scripted data, so every screen (incl. the radar) renders on the emulator.
emulator-setup: ## Install emulator + system image and (re)create the AVD
	yes | $(SDKMANAGER) "platform-tools" "emulator" "$(SYSIMG)"
	echo "no" | $(AVDMANAGER) create avd -n $(AVD) -k "$(SYSIMG)" -d pixel_6 --force

emulator: ## Boot the blep emulator
	ANDROID_HOME=$(ANDROID_HOME) ANDROID_SDK_ROOT=$(ANDROID_HOME) $(EMULATOR) -avd $(AVD) -netdelay none -netspeed full

screenshots: ## Regenerate the captioned store screenshots from demo mode
	tools/screenshots.sh

# ───────────────────────────── website ────────────────────────────
web: ## Run the website dev server
	cd web && npm run dev

web-build: ## Build the static website into web/dist
	cd web && npm run build

web-icons: ## Regenerate PWA icons from logo.svg
	cd web && npm run gen:icons

# ───────────────────────────── ci-ish ─────────────────────────────
ci: test build ## Run locally what CI checks on Linux (tests + Android build)

apple: ## Compile the iOS/watchOS Kotlin targets (requires macOS + Xcode)
	@uname -s | grep -qi darwin || { echo "Apple targets need macOS + Xcode."; exit 1; }
	cd app && $(GRADLE) :core:compileKotlinIosSimulatorArm64 :core:compileKotlinWatchosSimulatorArm64 :composeApp:compileKotlinIosSimulatorArm64
