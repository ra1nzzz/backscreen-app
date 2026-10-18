#!/bin/bash
# 背屏工坊 - 免 Gradle 构建脚本（aapt2 + javac + d8 + zipalign + apksigner）
set -e

ROOT="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_HOME:-/root/build/sdk}"
JDK="${JAVA_HOME:-/root/build/jdk-17.0.20.1+1}"
export JAVA_HOME="$JDK"
export PATH="$JAVA_HOME/bin:$SDK/build-tools/34.0.0:$SDK/platform-tools:$PATH"

ANDROID_JAR="$SDK/platforms/android-34/android.jar"
OUT="$ROOT/out"
VERSION_CODE="${VERSION_CODE:-1}"
VERSION_NAME="${VERSION_NAME:-1.0.0}"
KEYSTORE="${KEYSTORE:-/root/build/keystore.jks}"
KS_ALIAS="${KS_ALIAS:-stepstudio}"
KS_PASS="${KS_PASS:-stepstudio2026}"

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/dex"

echo "== aapt2 compile"
aapt2 compile --dir "$ROOT/res" -o "$OUT/res.zip"

echo "== aapt2 link"
aapt2 link -o "$OUT/base.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$ROOT/AndroidManifest.xml" \
  -R "$OUT/res.zip" \
  --java "$OUT/gen" \
  --min-sdk-version 23 \
  --target-sdk-version 34 \
  --version-code "$VERSION_CODE" \
  --version-name "$VERSION_NAME" \
  --auto-add-overlay

echo "== javac"
CP="$ANDROID_JAR"
for j in "$ROOT"/libs/*.jar; do
  [ -e "$j" ] && CP="$CP:$j"
done
find "$ROOT/src" "$OUT/gen" -name "*.java" 2>/dev/null > "$OUT/sources.txt"
javac -encoding UTF-8 -source 1.8 -target 1.8 -nowarn \
  -classpath "$CP" \
  -d "$OUT/classes" @"$OUT/sources.txt"

echo "== d8"
JARS=$(find "$ROOT/libs" -name "*.jar" 2>/dev/null | tr '\n' ' ')
find "$OUT/classes" -name "*.class" > "$OUT/classes.txt"
d8 --lib "$ANDROID_JAR" --min-api 23 --release --output "$OUT/dex" @"$OUT/classes.txt" $JARS
cp "$OUT/dex/classes.dex" "$OUT/classes.dex"

echo "== package dex"
python3 - "$OUT" <<'PY'
import sys, zipfile, shutil, os
out = sys.argv[1]
base = os.path.join(out, 'base.apk')
tmp = os.path.join(out, 'base2.apk')
shutil.copy(base, tmp)
with zipfile.ZipFile(tmp, 'a', zipfile.ZIP_DEFLATED) as z:
    z.write(os.path.join(out, 'classes.dex'), 'classes.dex')
shutil.move(tmp, base)
print('dex added')
PY

echo "== zipalign"
zipalign -f -p 4 "$OUT/base.apk" "$OUT/aligned.apk"

echo "== sign"
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -v -keystore "$KEYSTORE" -alias "$KS_ALIAS" \
    -keyalg RSA -keysize 2048 -validity 10950 \
    -storepass "$KS_PASS" -keypass "$KS_PASS" \
    -dname "CN=BackScreen Studio, OU=App, O=StepX Creators, L=Shanghai, C=CN" > /dev/null 2>&1
fi
apksigner sign --ks "$KEYSTORE" --ks-key-alias "$KS_ALIAS" \
  --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
  --v1-signing-enabled true --v2-signing-enabled true \
  --out "$OUT/BackScreen.apk" "$OUT/aligned.apk"

apksigner verify --print-certs "$OUT/BackScreen.apk" | head -4
ls -lh "$OUT/BackScreen.apk"
