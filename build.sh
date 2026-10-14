#!/usr/bin/env bash
# Compila Rastreadores sin Gradle, solo con las herramientas del SDK de Android.
# Uso: ANDROID_HOME=/ruta/al/sdk ./build.sh   → deja el APK en build/Rastreadores.apk
set -euo pipefail

cd "$(dirname "$0")"
: "${ANDROID_HOME:?Define ANDROID_HOME con la ruta del SDK de Android}"
BT="$ANDROID_HOME/build-tools/35.0.0"
PLATFORM="$ANDROID_HOME/platforms/android-34/android.jar"
VERSION_CODE="${VERSION_CODE:-$(git rev-list --count HEAD 2>/dev/null || echo 1)}"
VERSION_NAME="${VERSION_NAME:-1.$VERSION_CODE}"

rm -rf build && mkdir -p build/gen build/classes build/dex

# 1. Recursos (íconos, colores) y manifiesto
"$BT/aapt2" compile --dir app/res -o build/res.zip
"$BT/aapt2" link -o build/base.apk -I "$PLATFORM" \
  --manifest app/AndroidManifest.xml \
  --min-sdk-version 26 --target-sdk-version 34 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  --java build/gen build/res.zip

# 2. Código Java → bytecode → formato de Android (dex)
find app/src build/gen -name '*.java' > build/sources.txt
javac -nowarn -Xlint:-options --release 8 -encoding UTF-8 \
  -classpath "$PLATFORM" -d build/classes @build/sources.txt
"$BT/d8" --release --min-api 26 --lib "$PLATFORM" --output build/dex \
  $(find build/classes -name '*.class')

# 3. Empaquetar, alinear y firmar
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q -j ../unsigned.apk classes.dex)
"$BT/zipalign" -p -f 4 build/unsigned.apk build/aligned.apk
"$BT/apksigner" sign --ks firma/rastreadores.jks --ks-pass pass:rastreadores \
  --key-pass pass:rastreadores --out build/Rastreadores.apk build/aligned.apk
"$BT/apksigner" verify build/Rastreadores.apk
echo "Listo: build/Rastreadores.apk (versión $VERSION_NAME)"
