#!/usr/bin/env bash
# Build the debug APK and install it straight to a connected phone over adb —
# the fast dev loop, no re-downloading on the device.
#
#   bash tools/deploy.sh          # build + install to the connected phone
#   bash tools/deploy.sh --run    # also launch the app after installing
#
# One-time phone setup: enable Developer Options (tap Build number 7x) and
# USB debugging, connect USB, and authorise this computer.
set -euo pipefail

TOOLCHAIN="D:/Project/EdgeEdu_Offline_AI/toolchain"
export JAVA_HOME="$TOOLCHAIN/jdk/jdk-17.0.19+10"
GRADLE="$TOOLCHAIN/gradle-8.10.2/bin/gradle"
ADB="$TOOLCHAIN/sdk/platform-tools/adb.exe"
APK="android/app/build/outputs/apk/debug/app-debug.apk"

cd "$(dirname "$0")/.."

echo "==> Checking for a connected device…"
if ! "$ADB" devices | grep -qE "\sdevice$"; then
    echo "No device found. Connect your phone over USB with USB debugging on,"
    echo "then run:  $ADB devices   (accept the 'Allow debugging?' prompt)."
    exit 1
fi

echo "==> Building debug APK…"
( cd android && "$GRADLE" assembleDebug --console=plain -q )

echo "==> Installing to device (reinstall, keep data)…"
"$ADB" install -r "$APK"
cp "$APK" EdgeEdu.apk

if [[ "${1:-}" == "--run" ]]; then
    echo "==> Launching EdgeEdu…"
    "$ADB" shell monkey -p com.edgeedu.app -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
fi

echo "==> Done. Installed com.edgeedu.app ($(du -h "$APK" | cut -f1))."
