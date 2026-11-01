#!/usr/bin/env bash
# Builds Platea Home without Gradle: aapt2, javac, d8, zipalign, apksigner. Output: build/platea-home.apk
# CONFIG=<json file> picks the TV's configuration (default: config/default.json).
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
BT="$SDK/build-tools/36.0.0"
JAR="$SDK/platforms/android-34/android.jar"
export JAVA_HOME="${JAVA_HOME:-$HOME/Library/Java/JavaVirtualMachines/corretto-17.0.18/Contents/Home}"
export PATH="$JAVA_HOME/bin:$PATH"
CONFIG="${CONFIG:-config/default.json}"
KEYSTORE="${KEYSTORE:-keys/platea-home.keystore}"
[ -f "$JAR" ] && [ -x "$BT/aapt2" ] || { echo "Needs platforms;android-34 and build-tools;36.0.0 in $SDK"; exit 1; }
python3 -c "import json,sys; json.load(open(sys.argv[1]))" "$CONFIG" || { echo "Invalid config: $CONFIG"; exit 1; }

rm -rf build && mkdir -p build/classes build/dex build/gen build/assets
cp -R app/assets/. build/assets/
cp "$CONFIG" build/assets/config.json

"$BT/aapt2" compile --dir app/res -o build/res.zip
"$BT/aapt2" link -o build/res.apk -I "$JAR" --manifest app/AndroidManifest.xml --java build/gen -A build/assets build/res.zip
find app/src build/gen -name '*.java' > build/sources.txt
javac -encoding UTF-8 --release 8 -Xlint:-options -classpath "$JAR" -d build/classes @build/sources.txt
find build/classes -name '*.class' > build/classes.txt
"$BT/d8" --release --lib "$JAR" --min-api 26 --output build/dex @build/classes.txt
cp build/res.apk build/unsigned.apk
(cd build/dex && zip -q -u ../unsigned.apk classes.dex)
"$BT/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk

if [ ! -f "$KEYSTORE" ]; then
  mkdir -p "$(dirname "$KEYSTORE")"
  keytool -genkeypair -keystore "$KEYSTORE" -storepass platea-home -keypass platea-home -alias platea-home \
    -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Platea Home, O=Local build' >/dev/null 2>&1
  echo "[!!] Created a new signing key in $KEYSTORE: keep it safe, without it the installed app cannot be updated."
fi
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:platea-home --ks-key-alias platea-home --out build/platea-home.apk build/aligned.apk
echo "[OK] build/platea-home.apk ($(du -h build/platea-home.apk | cut -f1 | tr -d ' '), config: $CONFIG)"
