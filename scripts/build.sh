#!/usr/bin/env bash
# Build script for Flexi World-Clock Widget.
# Builds the release APK, zipaligns it, then signs it with APK Signature Scheme
# v2 + v3 and writes a v4 signature (.idsig). v1 (JAR signing) is deliberately off.
#
# Usage:
#   scripts/build.sh          # full build + sign + verify
#   scripts/build.sh --clean  # wipe intermediates first
#   scripts/build.sh --test   # also run the JVM unit tests

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
sdk_dir="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
build_tools="$sdk_dir/build-tools/35.0.0"
gradlew="$repo_root/gradlew"
[[ -x "$gradlew" ]] || gradlew="$repo_root/gradlew.bat"

store_file="${FLEXI_STORE_FILE:-keystore/flexi-release.jks}"
store_pass="${FLEXI_STORE_PASSWORD:-flexiwidget}"
key_alias="${FLEXI_KEY_ALIAS:-flexi}"
key_pass="${FLEXI_KEY_PASSWORD:-flexiwidget}"

if [[ -n "${JAVA_HOME:-}" ]]; then :; elif [[ -d "/c/P/Android Studio/jbr" ]]; then
  export JAVA_HOME="/c/P/Android Studio/jbr"
fi
export ANDROID_HOME="$sdk_dir"
export ANDROID_SDK_ROOT="$sdk_dir"

echo "==> gradle assembleRelease"
if [[ "${1:-}" == "--clean" ]]; then
  "$gradlew" clean --console=plain
fi
"$gradlew" :app:assembleRelease --console=plain

if [[ "${1:-}" == "--test" || "${2:-}" == "--test" ]]; then
  echo "==> gradle unit tests"
  "$gradlew" :app:testReleaseUnitTest --console=plain
fi

unsigned_apk="$repo_root/app/build/outputs/apk/release/app-release-unsigned.apk"
[[ -f "$unsigned_apk" ]] || unsigned_apk=$(find "$repo_root/app/build/outputs/apk/release" -name '*.apk' | head -n1)

dist_dir="$repo_root/dist"
mkdir -p "$dist_dir"
aligned_apk="$dist_dir/flexi-world-clock-widget-aligned.apk"
signed_apk="$dist_dir/flexi-world-clock-widget.apk"

[[ -f "$repo_root/$store_file" ]] || store_file_abs="$store_file"
keystore="$([[ -f "$repo_root/$store_file" ]] && echo "$repo_root/$store_file" || echo "$store_file")"
[[ -f "$keystore" ]] || { echo "Keystore not found: $keystore" >&2; exit 1; }

echo "==> zipalign"
"$build_tools/zipalign" -p -f 4 "$unsigned_apk" "$aligned_apk"

echo "==> apksigner (v2 + v3 + v4)"
"$build_tools/apksigner" sign \
  --ks "$keystore" \
  --ks-key-alias "$key_alias" \
  --ks-pass "pass:$store_pass" \
  --key-pass "pass:$key_pass" \
  --v1-signing-enabled false \
  --v2-signing-enabled true \
  --v3-signing-enabled true \
  --v4-signing-enabled true \
  --out "$signed_apk" \
  "$aligned_apk"

echo "==> apksigner verify"
"$build_tools/apksigner" verify --verbose --print-certs --min-sdk-version 24 "$signed_apk"

if [[ -f "$signed_apk.idsig" ]]; then
  echo "==> v4 signature verify"
  verifier_dir="${TMPDIR:-/tmp}/flexi-v4-verify"
  mkdir -p "$verifier_dir"
  tool_jar="$build_tools/lib/apksigner.jar"
  "${JAVA_HOME}/bin/javac" -nowarn -cp "$tool_jar" -d "$verifier_dir" "$repo_root/scripts/V4Verifier.java"
  "${JAVA_HOME}/bin/java" -cp "$verifier_dir:$tool_jar" V4Verifier "$signed_apk" "$signed_apk.idsig"
else
  echo "WARNING: no .idsig produced, so the APK has no v4 signature" >&2
fi

rm -f "$aligned_apk"
echo "Signed APK: $signed_apk"
[[ -f "$signed_apk.idsig" ]] && echo "v4 signature: $signed_apk.idsig"