# Build Guide

> [中文版 (Chinese)](../zh/build.md)

## Quick Start

```bash
# 1. One-time toolchain setup (~30 min, ~4 GB disk)
./scripts/setup-toolchain.sh

# 2. Build native libraries (arm64-v8a)
./scripts/build-native.sh

# 3. Build APK
./scripts/build-apk.sh doom

# 4. Install to device
adb install -r release/chocolate-doom.apk

# 5. Push WAD + launch
adb push doom.wad /data/local/tmp/
adb shell run-as com.chocolate.doom cp /data/local/tmp/doom.wad files/doom.wad
adb shell am start -n com.chocolate.doom/.ChocolateDoom
```

## Prerequisites

### Automated Setup

`scripts/setup-toolchain.sh` handles everything automatically. If you prefer manual setup:

### Manual Toolchain Setup

| Tool | Version | Path |
|------|---------|------|
| Android NDK | r27 | `~/android-toolchain/ndk/` |
| Android SDK | 34.0.0 | `~/android-toolchain/SDK/` |
| JDK | 17 | `~/android-toolchain/jdk17/` |
| Chocolate Doom | 3.1.1 | `native/chocolate-doom/` (git submodule) |
| SDL2 | 2.30.0 | `~/android-toolchain/SDL2-android/` (prebuilt for Android) |
| SDL2_mixer | 2.8.0 | `~/android-toolchain/SDL2-android/` (built alongside SDL2) |

#### NDK

```bash
wget https://dl.google.com/android/repository/android-ndk-r27-linux.zip
unzip android-ndk-r27-linux.zip -d ~/android-toolchain/
mv ~/android-toolchain/android-ndk-r27 ~/android-toolchain/ndk
```

#### SDK

```bash
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
mkdir -p ~/android-toolchain/SDK/cmdline-tools
unzip commandlinetools-linux-*.zip -d ~/android-toolchain/SDK/cmdline-tools
mv ~/android-toolchain/SDK/cmdline-tools/cmdline-tools ~/android-toolchain/SDK/cmdline-tools/latest
export ANDROID_HOME=~/android-toolchain/SDK
~/android-toolchain/SDK/cmdline-tools/latest/bin/sdkmanager \
    "platforms;android-34" "build-tools;34.0.0" "platform-tools"
```

#### JDK 17

```bash
wget https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.9%2B9/OpenJDK17U-jdk_x64_linux_hotspot_17.0.9_9.tar.gz
mkdir -p ~/android-toolchain/jdk17
tar xzf OpenJDK17U-jdk_x64_linux_hotspot_*.tar.gz -C ~/android-toolchain/jdk17 --strip-components=1
```

#### SDL2 + SDL2_mixer (prebuilt for Android)

```bash
# Clone
git clone --depth 1 --branch release-2.30.0 \
    https://github.com/libsdl-org/SDL.git ~/android-toolchain/SDL2-src

# Build SDL2
mkdir -p ~/android-toolchain/SDL2-build && cd ~/android-toolchain/SDL2-build
export CC=~/android-toolchain/ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android24-clang
cmake ~/android-toolchain/SDL2-src \
    -DCMAKE_SYSTEM_NAME=Android \
    -DCMAKE_SYSTEM_VERSION=24 \
    -DCMAKE_ANDROID_ARCH_ABI=arm64-v8a \
    -DCMAKE_ANDROID_NDK=~/android-toolchain/ndk \
    -DCMAKE_ANDROID_STL_TYPE=c++_static \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_INSTALL_PREFIX=~/android-toolchain/SDL2-android \
    -DSDL_SHARED=OFF -DSDL_STATIC=ON -DSDL_STATIC_PIC=ON \
    -DSDL_TEST=OFF -DSDL_HAPTIC=OFF
make -j$(nproc) && make install

# Build SDL2_mixer (vendored deps — no external libs needed)
git clone --depth 1 --branch release-2.8.0 \
    https://github.com/libsdl-org/SDL_mixer.git ~/android-toolchain/SDL2_mixer-src
mkdir -p ~/android-toolchain/SDL2_mixer-build && cd ~/android-toolchain/SDL2_mixer-build
cmake ~/android-toolchain/SDL2_mixer-src \
    -DCMAKE_SYSTEM_NAME=Android \
    -DCMAKE_SYSTEM_VERSION=24 \
    -DCMAKE_ANDROID_ARCH_ABI=arm64-v8a \
    -DCMAKE_ANDROID_NDK=~/android-toolchain/ndk \
    -DCMAKE_ANDROID_STL_TYPE=c++_static \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_PREFIX_PATH=~/android-toolchain/SDL2-android \
    -DSDL2_DIR=~/android-toolchain/SDL2-android/lib/cmake/SDL2 \
    -DSDL2MIXER_VENDORED=ON \
    -DSDL2MIXER_SAMPLES=OFF -DSDL2MIXER_DEPS_SHARED=OFF
make -j$(nproc)
cp libSDL2_mixer.a ~/android-toolchain/SDL2-android/lib/
```

#### Chocolate Doom Submodule

```bash
cd chocolate-doom-android
git submodule update --init
```

## Step 1: Build Native Libraries

```bash
./scripts/build-native.sh
```

This script runs three phases:

### Phase 1: Apply Patches

`build-native.sh` auto-applies all `.patch` files from `native/patches/` to the Chocolate Doom submodule. Currently includes:
- `nightmare-cheats.patch` — enables cheat codes on Nightmare difficulty

### Phase 2: Build Static Libraries (cmake + make)

```bash
# Configures Chocolate Doom from native/chocolate-doom/ (submodule)
cmake native/chocolate-doom \
    -DCMAKE_SYSTEM_NAME=Android \
    -DCMAKE_SYSTEM_VERSION=24 \
    -DCMAKE_ANDROID_ARCH_ABI=arm64-v8a \
    -DCMAKE_ANDROID_NDK=~/android-toolchain/ndk \
    -DCMAKE_ANDROID_STL_TYPE=c++_static \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_C_FLAGS=-fPIC \
    -DENABLE_SDL2_MIXER=OFF -DENABLE_SDL2_NET=OFF

# Builds static libraries only
make textscreen opl pcsound doom heretic hexen strife -j$(nproc)

# Compiles chocolate-doom target for common .o files (i_video.c, etc.)
# Link step fails (no main() on Android) — we only need the .o files
make chocolate-doom -j$(nproc) || true
```

### Phase 3: Link Game .so Files

```bash
$CXX -shared -fPIC -o libdoom.so \
    -Wl,--whole-archive libdoom.a libtextscreen.a libopl.a libpcsound.a \
    -Wl,--no-whole-archive \
    $COMMON_OBJ_DIR/*.c.o \
    ~/android-toolchain/SDL2-android/lib/libSDL2.a \
    ~/android-toolchain/SDL2-android/lib/libSDL2_mixer.a \
    -lm -ldl -llog -lOpenSLES -lGLESv1_CM -lGLESv2 -landroid
```

**Key details:**
- **`$CXX` (clang++), not clang** — SDL2 static lib contains C++ code; C linker won't resolve C++ ABI symbols
- **Common `.c.o` files** — `i_video.c`, `i_input.c`, etc. are in the executable target, not static libs. We compile the executable target for `.o` files only (link failure is expected and ignored).
- **`-lGLESv1_CM -lGLESv2`** — Chocolate Doom uses both GLES 1.x (2D rendering) and 2.0 (scaling/shaders)
- **`-llog -landroid`** — Android system libs needed by SDL2
- **`-lOpenSLES`** — Audio output on Android

**Bundled in APK:**
- `libdoom.so` (game engine)
- `libc++_shared.so` (C++ runtime, from NDK)

Output: `~/android-toolchain/apk/jniLibs/arm64-v8a/libdoom.so` (≈6.6 MB)

## Step 2: Build APK

```bash
./scripts/build-apk.sh doom       # Also: heretic, hexen, strife
```

This script:

1. Compiles all `.java` files → `.class` (javac, source=8, target=8)
2. Converts `.class` → `classes.dex` (d8)
3. Generates `AndroidManifest.xml` (aapt2 link)
4. Packages `classes.dex` + `lib/arm64-v8a/libmain.so` + `lib/arm64-v8a/libc++_shared.so` → `.apk` (zip)
5. Signs with debug keystore (apksigner)

Output: `release/chocolate-doom.apk` (≈2.7 MB)

## Step 3: Install & Run

```bash
# Install
adb install -r release/chocolate-doom.apk

# Push WAD (one-time)
adb push doom.wad /data/local/tmp/
adb shell run-as com.chocolate.doom cp /data/local/tmp/doom.wad files/doom.wad

# Launch
adb shell am start -n com.chocolate.doom/.ChocolateDoom
```

## Troubleshooting

### "WAD file not found"

Ensure WAD is at `/data/data/com.chocolate.doom/files/doom.wad`:
```bash
adb shell run-as com.chocolate.doom ls -la files/
```

### "dlopen failed: cannot locate symbol"

This means a native library dependency is missing. Common diagnostic:
```bash
# Check what libdoom.so needs
readelf -d ~/android-toolchain/apk/jniLibs/arm64-v8a/libdoom.so | grep NEEDED

# Verify all symbols resolved
nm -D ~/android-toolchain/apk/jniLibs/arm64-v8a/libdoom.so | grep " U "
```

Known symbol errors and their fixes:
| Missing Symbol | Required Library |
|---------------|-----------------|
| `_gxx_personality_v0` | `libc++_shared.so` (C++ runtime) — switch linker to clang++ |
| `__android_log_write` | `liblog.so` — add `-llog` |
| `ANativeWindow_setBuffersGeometry` | `libnativewindow.so` — add `-landroid` |
| `glBindTexture` | `libGLESv1_CM.so` or `libGLESv2.so` — add `-lGLESv1_CM -lGLESv2` |
| `I_VideoBuffer` | Common `.o` files not linked — compile `chocolate-doom` target |

### "App crashes on launch"

- Is the screen ON and unlocked? OPPO phones aggressively pause apps on screen lock.
- Check logcat: `adb logcat | grep -E "(dlopen|FATAL|chocolate)"`
- Verify `libc++_shared.so` is in the APK: `unzip -l release/chocolate-doom.apk | grep libc++`

### "No sound"

- Verify SDL2_mixer is linked: `readelf -s libdoom.so | grep Mix_`
- On Android, audio uses OpenSL ES — verify `-lOpenSLES` is in linker flags

### "APK install fails with signature mismatch"

Uninstall old version first:
```bash
adb uninstall com.chocolate.doom
adb install -r release/chocolate-doom.apk
```

### "build-native.sh fails with cmake not found"

The script uses the cmake bundled with Android SDK. Ensure SDK is installed:
```bash
ls ~/android-toolchain/SDK/cmake/3.22.1/bin/cmake
```

### "OPPO (ColorOS) phone: udev / adb not working"

OPPO requires manual udev setup after each reboot:
```bash
echo 'SUBSYSTEM=="usb", ATTR{idVendor}=="22d9", MODE="0666"' | sudo tee /etc/udev/rules.d/51-android.rules
sudo udevadm control --reload-rules && sudo udevadm trigger
```

## Build Reproducibility

The build is designed to be fully reproducible:
- **Chocolate Doom source**: Pinned to exact git tag `chocolate-doom-3.1.1` via submodule
- **SDL2**: Pinned to `release-2.30.0` tag, built as static library
- **SDL2_mixer**: Pinned to `release-2.8.0` tag, vendored deps (no external audio libs)
- **Patches**: Version-controlled in `native/patches/`, auto-applied before each build
- **Toolchain**: NDK r27, API level 24, arm64-v8a — all fixed versions

To verify a clean build works:
```bash
# Clone fresh
git clone <repo-url> chocolate-doom-android && cd chocolate-doom-android

# Setup toolchain
./scripts/setup-toolchain.sh

# Build everything
./scripts/build-native.sh && ./scripts/build-apk.sh doom

# APK should be at release/chocolate-doom.apk (~2.7 MB)
```
