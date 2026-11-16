#!/usr/bin/env bash
# Install/upload script for Flexi World-Clock Widget.
#
# Usage:
#   scripts/install.sh              # adb install -r (uses the v4 idsig when present)
#   scripts/install.sh --launch     # install, then open the app
#   scripts/install.sh --uninstall  # uninstall first
#   scripts/install.sh --apk a.apk  # install a specific APK

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
sdk_dir="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
adb="$sdk_dir/platform-tools/adb"
package_name="com.flexi.worldclock"

apk="$repo_root/dist/flexi-world-clock-widget.apk"
launch=0
uninstall=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --launch) launch=1 ;;
    --uninstall) uninstall=1 ;;
    --apk) shift; apk="$1" ;;
    *) echo "Unknown option: $1" >&2; exit 1 ;;
  esac
  shift
done

[[ -f "$apk" ]] || { echo "APK not found: $apk (run scripts/build.sh first)" >&2; exit 1; }

serial="$("$adb" devices | awk 'NR>1 && $2=="device" {print $1; exit}')"
[[ -n "$serial" ]] || { echo "No Android device or emulator connected." >&2; exit 1; }
echo "==> Target device: $serial"

if [[ $uninstall -eq 1 ]]; then
  "$adb" -s "$serial" uninstall "$package_name" || true
fi

echo "==> Installing $apk"
if [[ -f "$apk.idsig" ]]; then
  "$adb" -s "$serial" install -r --v4 "$apk"
else
  "$adb" -s "$serial" install -r "$apk"
fi

"$adb" -s "$serial" shell dumpsys package "$package_name" | grep -m1 versionName || true

if [[ $launch -eq 1 ]]; then
  "$adb" -s "$serial" shell am start -n "$package_name/.MainActivity" >/dev/null
fi