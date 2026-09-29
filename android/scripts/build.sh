#!/usr/bin/env bash
set -euo pipefail
PROJECT="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
TOOLS="$SDK/build-tools/35.0.0"
PLATFORM="$SDK/platforms/android-34/android.jar"
BUILD="$PROJECT/build"
OUT="$PROJECT/dist/QuestFan-1.0.apk"
mkdir -p "$BUILD/testclasses" "$BUILD/classes" "$BUILD/dex" "$BUILD/resources" "$PROJECT/dist" "$HOME/.android"

javac -encoding UTF-8 -d "$BUILD/testclasses" \
  "$PROJECT/app/src/main/java/com/doxton/questfan/FanProtocol.java" \
  "$PROJECT/tests/FanProtocolTest.java"
java -cp "$BUILD/testclasses" com.doxton.questfan.FanProtocolTest

"$TOOLS/aapt2" compile -o "$BUILD/resources" \
  "$PROJECT/app/src/main/res/values/strings.xml" \
  "$PROJECT/app/src/main/res/drawable/ic_fan.xml"
"$TOOLS/aapt2" link -o "$BUILD/base.apk" -I "$PLATFORM" \
  --manifest "$PROJECT/app/src/main/AndroidManifest.xml" \
  "$BUILD/resources/values_strings.arsc.flat" \
  "$BUILD/resources/drawable_ic_fan.xml.flat"

javac -source 8 -target 8 -encoding UTF-8 -cp "$PLATFORM" -d "$BUILD/classes" \
  "$PROJECT/app/src/main/java/com/doxton/questfan/FanProtocol.java" \
  "$PROJECT/app/src/main/java/com/doxton/questfan/MainActivity.java"
"$TOOLS/d8" --min-api 29 --lib "$PLATFORM" --output "$BUILD/dex" \
  "$BUILD/classes/com/doxton/questfan/FanProtocol.class" \
  "$BUILD/classes/com/doxton/questfan/MainActivity.class"
(cd "$BUILD/dex" && zip -q -u "$BUILD/base.apk" classes.dex)
"$TOOLS/zipalign" -f -p 4 "$BUILD/base.apk" "$BUILD/aligned.apk"
KEY="$HOME/.android/debug.keystore"
if [[ ! -f "$KEY" ]]; then
  keytool -genkeypair -keystore "$KEY" -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
    -dname 'CN=Quest Fan Local Debug,O=Doxton,C=CN' -noprompt
fi
"$TOOLS/apksigner" sign --ks "$KEY" --ks-key-alias androiddebugkey \
  --ks-pass pass:android --key-pass pass:android --out "$OUT" "$BUILD/aligned.apk"
"$TOOLS/apksigner" verify --verbose "$OUT"
"$TOOLS/aapt" dump badging "$OUT"
printf 'APK: %s\n' "$OUT"
