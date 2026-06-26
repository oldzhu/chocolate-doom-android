# Build Guide

> [中文版 (Chinese)](../zh/build.md)

## Prerequisites

| Tool | Version | Path (default) |
|------|---------|----------------|
| Android NDK | r27 | `~/android-toolchain/ndk/` |
| Android SDK | 34.0.0 | `~/android-toolchain/SDK/` |
| JDK | 17 | `~/android-toolchain/jdk17/` |
| Chocolate Doom | 3.1.1 | `~/android-toolchain/chocolate-doom-src/` |
| SDL2 | 2.30.0 | `~/android-toolchain/SDL2-src/` |
| SDL2_mixer | 2.6.3 | `~/android-toolchain/SDL2_mixer-2.6.3/` |

### Install NDK & SDK

```bash
# NDK
unzip ndk.zip -d ~/android-toolchain/

# SDK (command-line tools)
unzip cmdline-tools.zip -d ~/android-toolchain/
export ANDROID_HOME=~/android-toolchain/SDK
sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"
```

### Clone Dependencies

```bash
cd ~/android-toolchain

# Chocolate Doom
git clone https://github.com/chocolate-doom/chocolate-doom.git chocolate-doom-src
cd chocolate-doom-src && git checkout chocolate-doom-3.1.1 && cd ..

# SDL2
git clone https://github.com/libsdl-org/SDL.git SDL2-src
cd SDL2-src && git checkout release-2.30.0 && cd ..

# SDL2_mixer
wget https://github.com/libsdl-org/SDL_mixer/releases/download/release-2.6.3/SDL2_mixer-2.6.3.tar.gz
tar xzf SDL2_mixer-2.6.3.tar.gz
```

## Step 1: Build Native Libraries

```bash
cd chocolate-doom-android
./scripts/build-native.sh
```

This runs three phases:

### Phase 1: SDL2 (static)

```bash
cd ~/android-toolchain/SDL2-build
cmake ~/android-toolchain/SDL2-src \
    -DCMAKE_SYSTEM_NAME=Android \
    -DCMAKE_SYSTEM_VERSION=24 \
    -DCMAKE_ANDROID_ARCH_ABI=arm64-v8a \
    -DCMAKE_ANDROID_NDK=~/android-toolchain/ndk \
    -DSDL_SHARED=OFF \
    -DSDL_STATIC=ON
make -j$(nproc)
```

Output: `libSDL2.a` (≈2 MB)

### Phase 2: SDL2_mixer (static)

```bash
cd ~/android-toolchain/SDL2_mixer-2.6.3/build-android
../configure \
    --host=aarch64-linux-android \
    --enable-static --disable-shared \
    CFLAGS="-fPIC -O2"
make -j$(nproc)
```

Output: `build/.libs/libSDL2_mixer.a` (≈500 KB)

### Phase 3: Chocolate Doom → libmain.so

First build each game as static libraries with `-DDISABLE_SDL2MIXER=0`:

```bash
cd ~/android-toolchain/choc-build-pic
cmake ~/android-toolchain/chocolate-doom-src \
    -DCMAKE_SYSTEM_NAME=Android \
    -DCMAKE_SYSTEM_VERSION=24 \
    -DCMAKE_ANDROID_ARCH_ABI=arm64-v8a \
    -DCMAKE_ANDROID_NDK=~/android-toolchain/ndk \
    -DCMAKE_C_FLAGS="-fPIC"
make -j$(nproc)
```

Then link each game into `lib<game>.so`:

```bash
aarch64-linux-android24-clang -shared -fPIC -o libdoom.so \
    -Wl,--whole-archive \
    src/doom/*.a textscreen/*.a opl/*.a pcsound/*.a \
    -Wl,--no-whole-archive \
    ~/android-toolchain/SDL2-build/libSDL2.a \
    ~/android-toolchain/SDL2_mixer-2.6.3/build-android/build/.libs/libSDL2_mixer.a \
    -lm -ldl -lOpenSLES
```

Output: `libdoom.so`, `libheretic.so`, `libhexen.so`, `libstrife.so`, `libsetup.so`

## Step 2: Build APK

```bash
./scripts/build-apk.sh doom       # Also: heretic, hexen, strife, setup
```

This script:

1. Compiles all `.java` files → `.class` (javac, source=8, target=8)
2. Converts `.class` → `classes.dex` (d8)
3. Generates `AndroidManifest.xml` (aapt2)
4. Packages `classes.dex` + `lib/arm64-v8a/libmain.so` → `.apk` (zip)
5. Signs with debug keystore (apksigner)

Output: `release/chocolate-doom.apk` (≈10 MB)

## Step 3: Install to Device

```bash
./scripts/adb-install.sh
```

Or manually:

```bash
# Install APK
adb install -r release/chocolate-doom.apk

# Push WAD to internal storage
adb push freedoom1.wad /data/local/tmp/doom.wad
adb shell run-as com.chocolate.doom cp /data/local/tmp/doom.wad files/doom.wad

# Launch
adb shell am start -n com.chocolate.doom/.ChocolateDoom
```

## Troubleshooting

### "WAD file not found"
- Ensure WAD is at `/data/data/com.chocolate.doom/files/doom.wad`
- Verify with: `adb shell run-as com.chocolate.doom ls -la files/`

### "App crashes on launch"
- Is `libmain.so` compiled with `-fPIC`? Check: `readelf -d libmain.so | grep TEXTREL`
- OPPO device? Ensure screen is ON and unlocked before launching.

### "No sound"
- Verify SDL2_mixer is linked: `readelf -s libmain.so | grep Mix_`
- Check `-DDISABLE_SDL2MIXER` is NOT set in CMake flags.

### "APK install fails with signature mismatch"
- Uninstall old version first: `adb uninstall com.chocolate.doom`
- Then reinstall.

### "Y button doesn't quit"
- Verify `TouchControls.java` uses `onNativeKeyDown(53)` not `sendText()`.
- Verify `SDLActivity.java` has the `sendText()` wrapper (for fallback).
