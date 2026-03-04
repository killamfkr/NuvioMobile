#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLEW="$ROOT_DIR/gradlew"

ANDROID_APP_ID="com.nuvio.app"
ANDROID_ACTIVITY=".MainActivity"
IOS_PROJECT="$ROOT_DIR/iosApp/iosApp.xcodeproj"
IOS_SCHEME="iosApp"
IOS_DERIVED_DATA="$ROOT_DIR/build/ios-derived"
IOS_APP_PATH="$IOS_DERIVED_DATA/Build/Products/Debug-iphonesimulator/Nuvio.app"
IOS_BUNDLE_ID="com.nuvio.app.Nuvio"

usage() {
  cat <<'EOF'
Usage:
  ./scripts/run-mobile.sh android
  ./scripts/run-mobile.sh ios

Builds the debug app for the selected platform, installs it on the first running
Android emulator or booted iOS simulator, and launches the app.
EOF
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

first_booted_android_emulator() {
  adb devices | awk '$2 == "device" && $1 ~ /^emulator-/ { print $1; exit }'
}

first_booted_ios_simulator() {
  xcrun simctl list devices booted | awk -F '[()]' '/Booted/ { print $2; exit }'
}

run_android() {
  require_command adb

  local serial
  serial="$(first_booted_android_emulator)"

  if [[ -z "$serial" ]]; then
    echo "No running Android emulator found." >&2
    echo "Start an emulator first, then rerun: ./scripts/run-mobile.sh android" >&2
    exit 1
  fi

  echo "Building Android debug APK..."
  "$GRADLEW" :composeApp:assembleDebug

  local apk_path
  apk_path="$ROOT_DIR/composeApp/build/outputs/apk/debug/composeApp-debug.apk"

  if [[ ! -f "$apk_path" ]]; then
    echo "Expected APK not found at: $apk_path" >&2
    exit 1
  fi

  echo "Installing on emulator $serial..."
  adb -s "$serial" install -r "$apk_path"

  echo "Launching app..."
  adb -s "$serial" shell am start -n "$ANDROID_APP_ID/$ANDROID_ACTIVITY"
}

run_ios() {
  require_command xcodebuild
  require_command xcrun

  local simulator_udid
  simulator_udid="$(first_booted_ios_simulator)"

  if [[ -z "$simulator_udid" ]]; then
    echo "No booted iOS simulator found." >&2
    echo "Start a simulator first, then rerun: ./scripts/run-mobile.sh ios" >&2
    exit 1
  fi

  echo "Building iOS debug app for simulator $simulator_udid..."
  xcodebuild \
    -project "$IOS_PROJECT" \
    -scheme "$IOS_SCHEME" \
    -configuration Debug \
    -destination "id=$simulator_udid" \
    -derivedDataPath "$IOS_DERIVED_DATA" \
    CODE_SIGNING_ALLOWED=NO \
    build

  if [[ ! -d "$IOS_APP_PATH" ]]; then
    echo "Expected iOS app not found at: $IOS_APP_PATH" >&2
    exit 1
  fi

  echo "Installing on simulator $simulator_udid..."
  xcrun simctl install "$simulator_udid" "$IOS_APP_PATH"

  echo "Launching app..."
  xcrun simctl launch "$simulator_udid" "$IOS_BUNDLE_ID"
}

main() {
  if [[ $# -ne 1 ]]; then
    usage
    exit 1
  fi

  case "$1" in
    android)
      run_android
      ;;
    ios)
      run_ios
      ;;
    -h|--help|help)
      usage
      ;;
    *)
      echo "Unknown platform: $1" >&2
      usage
      exit 1
      ;;
  esac
}

main "$@"
