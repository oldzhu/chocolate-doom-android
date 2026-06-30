#!/bin/bash
# Setup Android toolchain for Chocolate Doom Android build
# Downloads and installs: NDK, SDK, JDK, SDL2, SDL2_mixer
# Usage: ./scripts/setup-toolchain.sh
#
# Prerequisites: wget, unzip, Java 17 (for sdkmanager)
# Time: ~30 min (mostly download — ~1GB total)
# Disk: ~4 GB after extraction
set -e

TOOLCHAIN_DIR="${ANDROID_TOOLCHAIN:-$HOME/android-toolchain}"
mkdir -p "$TOOLCHAIN_DIR"

echo "============================================================"
echo "  Chocolate Doom Android — Toolchain Setup"
echo "  Target: $TOOLCHAIN_DIR"
echo "============================================================"
echo ""

# ── NDK r27 (arm64-v8a, API 24) ──
NDK_DIR="$TOOLCHAIN_DIR/ndk"
NDK_ZIP="$TOOLCHAIN_DIR/ndk.zip"
NDK_URL="https://dl.google.com/android/repository/android-ndk-r27-linux.zip"

if [ -f "$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android24-clang" ]; then
    echo "✓ NDK r27 already installed at $NDK_DIR"
else
    echo "--- Downloading NDK r27 (~650 MB) ---"
    if [ ! -f "$NDK_ZIP" ]; then
        wget -O "$NDK_ZIP" "$NDK_URL" || {
            echo "ERROR: NDK download failed."
            echo "Please download manually from: $NDK_URL"
            echo "And extract to: $NDK_DIR"
            exit 1
        }
    fi
    echo "   Extracting..."
    unzip -qo "$NDK_ZIP" -d "$TOOLCHAIN_DIR"
    # NDK zip extracts to android-ndk-r27/
    if [ -d "$TOOLCHAIN_DIR/android-ndk-r27" ]; then
        mv "$TOOLCHAIN_DIR/android-ndk-r27" "$NDK_DIR"
    fi
    echo "   ✓ NDK installed"
fi

# ── Android SDK (cmdline-tools) ──
SDK_DIR="$TOOLCHAIN_DIR/SDK"
CMDLINE_ZIP="$TOOLCHAIN_DIR/cmdline-tools.zip"
CMDLINE_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"

if [ -f "$SDK_DIR/platforms/android-34/android.jar" ] && \
   [ -f "$SDK_DIR/build-tools/34.0.0/aapt2" ]; then
    echo "✓ Android SDK already set up at $SDK_DIR"
else
    echo "--- Setting up Android SDK ---"
    if [ ! -f "$CMDLINE_ZIP" ]; then
        wget -O "$CMDLINE_ZIP" "$CMDLINE_URL" || {
            echo "ERROR: SDK download failed."
            echo "Please download manually from: $CMDLINE_URL"
            echo "And extract into: $SDK_DIR"
            exit 1
        }
    fi

    mkdir -p "$SDK_DIR/cmdline-tools"
    unzip -qo "$CMDLINE_ZIP" -d "$SDK_DIR/cmdline-tools"
    # Rename extracted dir to 'latest'
    EXTRACTED_DIR=$(ls -d "$SDK_DIR/cmdline-tools"/cmdline-tools-* 2>/dev/null || ls -d "$SDK_DIR/cmdline-tools"/cmdline-tools 2>/dev/null || echo "")
    if [ -d "$EXTRACTED_DIR" ] && [ "$EXTRACTED_DIR" != "$SDK_DIR/cmdline-tools/latest" ]; then
        mv "$EXTRACTED_DIR" "$SDK_DIR/cmdline-tools/latest"
    fi

    # sdkmanager needs Java
    export JAVA_HOME="${JAVA_HOME:-$TOOLCHAIN_DIR/jdk17}"
    export PATH="$JAVA_HOME/bin:$PATH"

    echo "   Installing platform-34 + build-tools-34.0.0..."
    yes | "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" \
        --sdk_root="$SDK_DIR" \
        "platforms;android-34" "build-tools;34.0.0" "platform-tools" 2>&1 | tail -5
    echo "   ✓ SDK installed"
fi

# ── JDK 17 ──
JDK_DIR="$TOOLCHAIN_DIR/jdk17"
JDK_TAR="$TOOLCHAIN_DIR/jdk17.tar.gz"
JDK_URL="https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.9%2B9/OpenJDK17U-jdk_x64_linux_hotspot_17.0.9_9.tar.gz"

if [ -f "$JDK_DIR/bin/javac" ] || [ -f "$JDK_DIR/bin/javac.exe" ]; then
    echo "✓ JDK 17 already installed at $JDK_DIR"
else
    echo "--- Downloading JDK 17 (~190 MB) ---"
    if [ ! -f "$JDK_TAR" ]; then
        wget -O "$JDK_TAR" "$JDK_URL" || {
            echo "WARNING: JDK download failed."
            echo "You need JDK 17 at: $JDK_DIR"
            echo "Manual download: $JDK_URL"
            # Don't exit — user might have system JDK 17
        }
    fi
    if [ -f "$JDK_TAR" ]; then
        echo "   Extracting..."
        mkdir -p "$JDK_DIR"
        tar xzf "$JDK_TAR" -C "$JDK_DIR" --strip-components=1 2>/dev/null || true
        echo "   ✓ JDK 17 installed"
    fi
fi

# ── SDL2 (static, for Android) ──
SDL2_LIB="$TOOLCHAIN_DIR/SDL2-android/lib/libSDL2.a"

if [ -f "$SDL2_LIB" ]; then
    echo "✓ SDL2 already built at $SDL2_LIB"
else
    echo "--- Building SDL2 for Android (~5 min) ---"
    SDL2_SRC="$TOOLCHAIN_DIR/SDL2-src"
    SDL2_BUILD="$TOOLCHAIN_DIR/SDL2-build"
    SDL2_INSTALL="$TOOLCHAIN_DIR/SDL2-android"

    if [ ! -d "$SDL2_SRC" ]; then
        echo "   Cloning SDL2 (release-2.30.0)..."
        git clone --depth 1 --branch release-2.30.0 \
            https://github.com/libsdl-org/SDL.git "$SDL2_SRC"
    fi

    mkdir -p "$SDL2_BUILD"
    cd "$SDL2_BUILD"

    NDK_TOOLCHAIN="$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64"
    export CC="$NDK_TOOLCHAIN/bin/aarch64-linux-android24-clang"
    export CXX="$NDK_TOOLCHAIN/bin/aarch64-linux-android24-clang++"

    cmake "$SDL2_SRC" \
        -DCMAKE_SYSTEM_NAME=Android \
        -DCMAKE_SYSTEM_VERSION=24 \
        -DCMAKE_ANDROID_ARCH_ABI=arm64-v8a \
        -DCMAKE_ANDROID_NDK="$NDK_DIR" \
        -DCMAKE_ANDROID_STL_TYPE=c++_static \
        -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_INSTALL_PREFIX="$SDL2_INSTALL" \
        -DSDL_SHARED=OFF \
        -DSDL_STATIC=ON \
        -DSDL_STATIC_PIC=ON \
        -DSDL_TEST=OFF \
        -DSDL_HAPTIC=OFF \
        -DSDL_JOYSTICK=OFF \
        2>&1 | tail -3

    make -j$(nproc) 2>&1 | tail -3
    make install 2>&1 | tail -3
    echo "   ✓ SDL2 built and installed"
fi

# ── SDL2_mixer (static, for Android) ──
SDL2_MIXER_LIB="$TOOLCHAIN_DIR/SDL2-android/lib/libSDL2_mixer.a"

if [ -f "$SDL2_MIXER_LIB" ]; then
    echo "✓ SDL2_mixer already built at $SDL2_MIXER_LIB"
else
    echo "--- Building SDL2_mixer for Android (~3 min) ---"
    SDL2_MIXER_SRC="$TOOLCHAIN_DIR/SDL2_mixer-src"
    SDL2_MIXER_BUILD="$TOOLCHAIN_DIR/SDL2_mixer-build"
    SDL2_INSTALL="$TOOLCHAIN_DIR/SDL2-android"

    if [ ! -d "$SDL2_MIXER_SRC" ]; then
        echo "   Cloning SDL2_mixer (release-2.8.0)..."
        git clone --depth 1 --branch release-2.8.0 \
            https://github.com/libsdl-org/SDL_mixer.git "$SDL2_MIXER_SRC"
    fi

    mkdir -p "$SDL2_MIXER_BUILD"
    cd "$SDL2_MIXER_BUILD"

    NDK_TOOLCHAIN="$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64"

    cmake "$SDL2_MIXER_SRC" \
        -DCMAKE_SYSTEM_NAME=Android \
        -DCMAKE_SYSTEM_VERSION=24 \
        -DCMAKE_ANDROID_ARCH_ABI=arm64-v8a \
        -DCMAKE_ANDROID_NDK="$NDK_DIR" \
        -DCMAKE_ANDROID_STL_TYPE=c++_static \
        -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_PREFIX_PATH="$SDL2_INSTALL" \
        -DSDL2_DIR="$SDL2_INSTALL/lib/cmake/SDL2" \
        -DSDL2MIXER_VENDORED=ON \
        -DSDL2MIXER_SAMPLES=OFF \
        -DSDL2MIXER_DEPS_SHARED=OFF \
        2>&1 | tail -3

    make -j$(nproc) 2>&1 | tail -3

    # Copy the built lib to the SDL2-android install directory
    cp "$SDL2_MIXER_BUILD/libSDL2_mixer.a" "$SDL2_INSTALL/lib/"
    echo "   ✓ SDL2_mixer built and installed"
fi

# ── Git Submodule ──
echo ""
echo "--- Initializing git submodule ---"
cd "$(dirname "$0")/.."
git submodule update --init 2>&1 || {
    echo "WARNING: git submodule update failed."
    echo "If you just cloned the repo, run: git submodule update --init"
}
echo "   ✓ Submodule ready"

echo ""
echo "============================================================"
echo "  ✅ Toolchain setup complete!"
echo "============================================================"
echo ""
echo "Next steps:"
echo "  1. Build native libs:  ./scripts/build-native.sh"
echo "  2. Build APK:          ./scripts/build-apk.sh doom"
echo "  3. Install to device:  adb install -r release/chocolate-doom.apk"
echo ""
echo "You also need a DOOM WAD file. Place it at:"
echo "  /data/data/com.chocolate.doom/files/doom.wad"
echo "  (via: adb push doom.wad /data/local/tmp/ && adb shell run-as com.chocolate.doom cp /data/local/tmp/doom.wad files/)"
echo ""
