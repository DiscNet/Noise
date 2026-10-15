#!/usr/bin/env bash
# Secrets enter only through the environment. Do not run with shell tracing.
set +x
set -euo pipefail

required=(NOISE_SIGNING_KEYSTORE_B64 NOISE_SIGNING_STORE_PASSWORD NOISE_SIGNING_KEY_ALIAS NOISE_SIGNING_KEY_PASSWORD)
missing=0
for name in "${required[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    echo "::error::Missing repository secret: $name. See docs/APK-UPDATES.md."
    missing=1
  fi
done
[[ "$missing" == 0 ]] || exit 1
if [[ "${1:-}" == --check-secrets ]]; then
  exit 0
fi
[[ $# == 0 ]] || { echo '::error::Unknown argument.'; exit 1; }

umask 077
SIGNING_DIR=$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/noise-signing.XXXXXX")
trap 'rm -rf "$SIGNING_DIR"' EXIT
export NOISE_KEYSTORE_PATH="$SIGNING_DIR/noise-release.keystore"
export NOISE_KEYSTORE_PASSWORD="$NOISE_SIGNING_STORE_PASSWORD"
export NOISE_KEY_ALIAS="$NOISE_SIGNING_KEY_ALIAS"
export NOISE_KEY_PASSWORD="$NOISE_SIGNING_KEY_PASSWORD"
if ! printf '%s' "$NOISE_SIGNING_KEYSTORE_B64" | base64 --decode > "$NOISE_KEYSTORE_PATH"; then
  echo '::error::Keystore secret is not valid Base64.'
  exit 1
fi
[[ -s "$NOISE_KEYSTORE_PATH" ]] || { echo '::error::Empty keystore.'; exit 1; }
unset NOISE_SIGNING_KEYSTORE_B64

# Export only the public certificate, to verify the APK signer against the key.
keytool -exportcert -keystore "$NOISE_KEYSTORE_PATH" \
  -alias "$NOISE_KEY_ALIAS" -storepass:env NOISE_KEYSTORE_PASSWORD \
  -file "$SIGNING_DIR/certificate.der"
EXPECTED_CERT=$(sha256sum "$SIGNING_DIR/certificate.der" | cut -d ' ' -f 1)
gradle --no-daemon --no-configuration-cache :app:assembleRelease

export SIGNING_METADATA="$SIGNING_DIR/metadata.env"
python3 - <<'PY'
import json, os, pathlib, re, shutil
metadata = pathlib.Path('app/build/outputs/apk/release/output-metadata.json')
data = json.loads(metadata.read_text())
assert data['applicationId'] == 'com.discnet.noise', 'Unexpected applicationId'
assert len(data['elements']) == 1, 'Expected a single universal APK'
item = data['elements'][0]
version = item['versionName']
assert re.fullmatch(r'\d+\.\d+(?:\.\d+)?', version), 'Invalid release version'
assert isinstance(item['versionCode'], int) and item['versionCode'] > 0
assert item['outputFile'] == 'app-release.apk', 'Release APK must be signed'
output = pathlib.Path('release')
output.mkdir(exist_ok=True)
filename = f'Noise.V{version}.apk'
shutil.copyfile(metadata.parent / item['outputFile'], output / filename)
# Public build metadata only; never persist a password to GITHUB_ENV.
values = f'APP_VERSION={version}\nAPK_FILE={filename}\nAPP_VERSION_CODE={item["versionCode"]}\n'
pathlib.Path(os.environ['SIGNING_METADATA']).write_text(values)
PY

source "$SIGNING_METADATA"
BUILD_TOOLS="${ANDROID_HOME:?Android SDK is required}/build-tools/35.0.0"
"$BUILD_TOOLS/apksigner" verify --verbose --print-certs "release/$APK_FILE" > "$SIGNING_DIR/verification.txt"
ACTUAL_CERT=$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' "$SIGNING_DIR/verification.txt")
[[ "$ACTUAL_CERT" == "$EXPECTED_CERT" ]] || {
  echo '::error::APK certificate does not match the configured keystore.'; exit 1;
}
if grep -q 'CN=Android Debug' "$SIGNING_DIR/verification.txt"; then
  echo '::error::A debug keystore cannot be used for official releases.'; exit 1
fi
"$BUILD_TOOLS/aapt" dump badging "release/$APK_FILE" > "$SIGNING_DIR/badging.txt"
if grep -q '^application-debuggable' "$SIGNING_DIR/badging.txt"; then
  echo '::error::The official APK must not be debuggable.'; exit 1
fi
grep -Fq "package: name='com.discnet.noise' versionCode='$APP_VERSION_CODE' versionName='$APP_VERSION'" "$SIGNING_DIR/badging.txt"
(
  cd release
  sha256sum "$APK_FILE" > "$APK_FILE.sha256"
)
{
  printf 'Noise! V%s\nPackage: com.discnet.noise\nversionCode: %s\n' "$APP_VERSION" "$APP_VERSION_CODE"
  printf 'Signing certificate SHA-256: %s\n' "$ACTUAL_CERT"
  printf 'Commit: %s\n' "${GITHUB_SHA:-local-build}"
  printf 'Verified by apksigner; release APK is not debuggable.\n'
} > release/SIGNING-CERTIFICATE.txt
if [[ -n "${GITHUB_ENV:-}" ]]; then
  cat "$SIGNING_METADATA" >> "$GITHUB_ENV"
fi
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  cat release/SIGNING-CERTIFICATE.txt >> "$GITHUB_STEP_SUMMARY"
fi
printf 'Verified release APK: release/%s\n' "$APK_FILE"
