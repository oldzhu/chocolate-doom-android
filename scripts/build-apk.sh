#!/bin/bash
# Build Android APK from Java sources + native libmain.so
# Usage: ./scripts/build-apk.sh [doom|heretic|hexen|strife|setup]
set -e

GAME="${1:-doom}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(dirname "$SCRIPT_DIR")"

# Ensure Java in PATH for apksigner
export PATH="$HOME/android-toolchain/jdk17/bin:$PATH"
export JAVA_HOME="$HOME/android-toolchain/jdk17"

# Toolchain paths (override with env vars)
ANDROID_HOME="${ANDROID_HOME:-$HOME/android-toolchain/SDK}"
BUILD_TOOLS="$ANDROID_HOME/build-tools/34.0.0"
ANDROID_JAR="$ANDROID_HOME/platforms/android-34/android.jar"
JAVAC="${JAVAC:-$HOME/android-toolchain/jdk17/bin/javac}"
KEYSTORE="${KEYSTORE:-$HOME/.android/debug.keystore}"
KS_PASS="${KS_PASS:-android}"
KEY_ALIAS="${KEY_ALIAS:-androiddebugkey}"

# Game → package name + activity mapping
case "$GAME" in
    doom)    PKG="com.chocolate.doom";    ACTIVITY="ChocolateDoom" ;;
    heretic) PKG="com.chocolate.heretic"; ACTIVITY="ChocolateHeretic" ;;
    hexen)   PKG="com.chocolate.hexen";   ACTIVITY="ChocolateHexen" ;;
    strife)  PKG="com.chocolate.strife";  ACTIVITY="ChocolateStrife" ;;
    setup)   PKG="com.chocolate.setup";   ACTIVITY="ChocolateSetup" ;;
    *)       echo "Unknown game: $GAME"; exit 1 ;;
esac

# Convert package dots to path slashes for filesystem
PKG_PATH="${PKG//./\/}"

echo "=== Building $GAME ($PKG) ==="

BUILD_DIR="$REPO_ROOT/build/$GAME"
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/classes" "$BUILD_DIR/dex" "$BUILD_DIR/apk"

# Step 1: Compile Java sources
echo "--- Compiling Java ---"
SDL_SRC_DIR="$REPO_ROOT/sdl_patches"

# Build the list of Java files to compile
JAVA_FILES=(
    "$SDL_SRC_DIR/org/libsdl/app/SDLActivity.java"
    "$SDL_SRC_DIR/org/libsdl/app/SDLSurface.java"
    "$SDL_SRC_DIR/org/libsdl/app/SDL.java"
    "$SDL_SRC_DIR/org/libsdl/app/SDLAudioManager.java"
    "$SDL_SRC_DIR/org/libsdl/app/SDLControllerManager.java"
    "$SDL_SRC_DIR/org/libsdl/app/HIDDevice.java"
    "$SDL_SRC_DIR/org/libsdl/app/HIDDeviceManager.java"
    "$SDL_SRC_DIR/org/libsdl/app/HIDDeviceUSB.java"
    "$SDL_SRC_DIR/org/libsdl/app/HIDDeviceBLESteamController.java"
    "$REPO_ROOT/app/src/main/java/$PKG_PATH/${ACTIVITY}.java"
)

# For DOOM, include touch control files
if [ "$GAME" = "doom" ]; then
    JAVA_FILES+=(
        "$REPO_ROOT/app/src/main/java/$PKG_PATH/TouchControls.java"
        "$REPO_ROOT/app/src/main/java/$PKG_PATH/AnalogJoystick.java"
        "$REPO_ROOT/app/src/main/java/$PKG_PATH/ai/NeuralNet.java"
        "$REPO_ROOT/app/src/main/java/$PKG_PATH/ai/DQNPlayer.java"
        "$REPO_ROOT/app/src/main/java/$PKG_PATH/ai/AIController.java"
    )
fi

"$JAVAC" \
    -source 8 -target 8 \
    -d "$BUILD_DIR/classes" \
    -cp "$ANDROID_JAR" \
    "${JAVA_FILES[@]}" \
    2>&1

# Step 2: Convert to DEX
echo "--- Converting to DEX ---"
"$BUILD_TOOLS/d8" \
    --lib "$ANDROID_JAR" \
    --output "$BUILD_DIR/dex" \
    $(find "$BUILD_DIR/classes" -name '*.class') \
    2>&1

# Step 3: Package APK (using aapt2 + manual zip for simplicity)
echo "--- Packaging APK ---"

# Create minimal AndroidManifest
MANIFEST="$BUILD_DIR/AndroidManifest.xml"
cat > "$MANIFEST" <<MANIFEST_EOF
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="$PKG"
    android:versionCode="8"
    android:versionName="3.1.1-F1F2">
    <uses-feature android:glEsVersion="0x00020000" />
    <uses-feature android:name="android.hardware.touchscreen"
        android:required="false" />

    <uses-sdk android:minSdkVersion="21" android:targetSdkVersion="34" />

    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />

    <application android:label="Chocolate $(echo ${GAME^})"
        android:theme="@android:style/Theme.NoTitleBar.Fullscreen"
        android:hardwareAccelerated="true"
        android:debuggable="true">

        <activity android:name=".${ACTIVITY}"
            android:label="Chocolate $(echo ${GAME^})"
            android:exported="true"
            android:configChanges="keyboard|keyboardHidden|orientation|screenSize"
            android:launchMode="singleTask"
            android:keepScreenOn="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
MANIFEST_EOF

# Link APK with aapt2 (manifest passed directly, not pre-compiled)
APK_TMP="$BUILD_DIR/apk/unsigned.apk"
APK_OUT="$REPO_ROOT/release/chocolate-${GAME}.apk"

"$BUILD_TOOLS/aapt2" link \
    -o "$APK_TMP" \
    -I "$ANDROID_JAR" \
    --manifest "$MANIFEST" \
    -0 arsc \
    2>&1

# Add classes.dex and native lib
cp "$BUILD_DIR/dex/classes.dex" "$BUILD_DIR/apk/"
mkdir -p "$BUILD_DIR/apk/lib/arm64-v8a"

# Link or copy native library
NATIVE_LIB="$HOME/android-toolchain/apk/jniLibs/arm64-v8a/lib${GAME}.so"
if [ -f "$NATIVE_LIB" ]; then
    cp "$NATIVE_LIB" "$BUILD_DIR/apk/lib/arm64-v8a/libmain.so"
    # Bundle C++ runtime (needed by SDL2)
    CXX_LIB="$HOME/android-toolchain/apk/jniLibs/arm64-v8a/libc++_shared.so"
    if [ -f "$CXX_LIB" ]; then
        cp "$CXX_LIB" "$BUILD_DIR/apk/lib/arm64-v8a/"
    fi
else
    echo "⚠️  No native lib found at $NATIVE_LIB — APK will have no native code!"
    echo "   Run scripts/build-native.sh first."
fi

# Zip everything into APK
cd "$BUILD_DIR/apk"
zip -qr "$APK_TMP" classes.dex lib/ 2>&1

# Step 4: Sign
echo "--- Signing ---"
"$BUILD_TOOLS/apksigner" sign \
    --ks "$KEYSTORE" \
    --ks-pass "pass:$KS_PASS" \
    --key-pass "pass:$KS_PASS" \
    --ks-key-alias "$KEY_ALIAS" \
    --out "$APK_OUT" \
    "$APK_TMP" 2>&1

echo ""
echo "=== ✅ APK built: $APK_OUT ==="
ls -lh "$APK_OUT" | awk '{print "     Size: " $5}'
