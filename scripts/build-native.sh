#!/bin/bash
# Cross-compile Chocolate Doom for Android (arm64-v8a)
# Produces libdoom.so → used by build-apk.sh as libmain.so
# Prerequisites: NDK, prebuilt SDL2 + SDL2_mixer (in SDL2-android/)
# Usage: ./scripts/build-native.sh
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(dirname "$SCRIPT_DIR")"
TOOLCHAIN="${ANDROID_TOOLCHAIN:-$HOME/android-toolchain}"
NDK="${NDK:-$TOOLCHAIN/ndk}"
SDK="${SDK:-$TOOLCHAIN/SDK}"

# Cross-compilation toolchain
HOST="aarch64-linux-android"
API_LEVEL="24"
TOOLCHAIN_PREFIX="$NDK/toolchains/llvm/prebuilt/linux-x86_64"
CC="$TOOLCHAIN_PREFIX/bin/${HOST}${API_LEVEL}-clang"
CXX="$TOOLCHAIN_PREFIX/bin/${HOST}${API_LEVEL}-clang++"

# Make cmake findable
export PATH="$SDK/cmake/3.22.1/bin:$TOOLCHAIN_PREFIX/bin:$PATH"

# Source directories
CHOCO_SRC="$REPO_ROOT/native/chocolate-doom"          # ← git submodule
CHOCO_BUILD="${CHOCO_BUILD:-$TOOLCHAIN/choc-build-pic}" # out-of-tree build
CHOCO_PATCHES="$REPO_ROOT/native/patches"              # our patches

# Prebuilt SDL2 + SDL2_mixer for Android (built separately, rarely changes)
SDL2_ANDROID="${SDL2_ANDROID:-$TOOLCHAIN/SDL2-android}"
SDL2_LIB="$SDL2_ANDROID/lib/libSDL2.a"
SDL2_MIXER_LIB="$SDL2_ANDROID/lib/libSDL2_mixer.a"
SDL2_CMAKE_DIR="$SDL2_ANDROID/lib/cmake/SDL2"

export CC CXX

# ── Submodule check ──
if [ ! -f "$CHOCO_SRC/src/doom/st_stuff.c" ]; then
    echo "⚠️  Chocolate Doom source not found at $CHOCO_SRC"
    echo "   Run: git submodule update --init"
    exit 1
fi

# ── Check prebuilt SDL2 ──
if [ ! -f "$SDL2_LIB" ]; then
    echo "⚠️  Prebuilt SDL2 not found at $SDL2_LIB"
    echo "   Expected at: $SDL2_ANDROID/lib/"
    echo "   Run: ./scripts/setup-toolchain.sh"
    exit 1
fi

# ── Apply our patches ──
echo "=== Applying patches ==="
cd "$CHOCO_SRC"
git checkout -- . 2>/dev/null || true
for patch in "$CHOCO_PATCHES"/*.patch; do
    if [ -f "$patch" ]; then
        echo "   Applying: $(basename "$patch")"
        git apply "$patch"
    fi
done
echo "   ✓ All patches applied"

# ── cmake configure + build static libs ──
echo ""
echo "=== Building Chocolate Doom (static libs) ==="
mkdir -p "$CHOCO_BUILD"
cd "$CHOCO_BUILD"

echo "   Configuring..."
cmake "$CHOCO_SRC" \
    -DCMAKE_SYSTEM_NAME=Android \
    -DCMAKE_SYSTEM_VERSION="$API_LEVEL" \
    -DCMAKE_ANDROID_ARCH_ABI=arm64-v8a \
    -DCMAKE_ANDROID_NDK="$NDK" \
    -DCMAKE_ANDROID_STL_TYPE=c++_static \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_C_FLAGS="-fPIC" \
    -DCMAKE_PREFIX_PATH="$SDL2_ANDROID" \
    -DSDL2_DIR="$SDL2_CMAKE_DIR" \
    -DENABLE_SDL2_MIXER=ON \
    -DSDL2_MIXER_INCLUDE_DIR="$SDL2_ANDROID/include/SDL2" \
    -DSDL2_MIXER_LIBRARY="$SDL2_ANDROID/lib/libSDL2_mixer.a" \
    -DSDL2_mixer_FOUND=TRUE \
    -DCMAKE_MODULE_PATH="$SDL2_ANDROID/lib/cmake/SDL2_mixer" \
    -DENABLE_SDL2_NET=OFF \
    2>&1 | tail -3

# Build static libraries (only what DOOM needs)
echo "   Compiling static libs..."
make textscreen opl pcsound doom -j$(nproc) 2>&1 | tail -5
echo "   ✓ Static libs built"

# Build chocolate-doom target to compile common sources (i_video.c, i_input.c, etc.)
# These are compiled as part of the executable, not the static libs.
# The link step fails (no main() on Android), but we only need the .o files.
echo "   Compiling common sources (chocolate-doom target)..."
make chocolate-doom -j$(nproc) 2>&1 | tail -3 || true
echo "   ✓ Common sources compiled"

# Common object directory (where chocolate-doom's .o files end up)
COMMON_OBJ_DIR="$CHOCO_BUILD/src/CMakeFiles/chocolate-doom.dir"

# ── Link libdoom.so ──
echo ""
echo "=== Linking libdoom.so ==="
DOOM_DIR="$CHOCO_BUILD/src/doom"
mkdir -p "$DOOM_DIR"
cd "$DOOM_DIR"

"$CXX" -shared -fPIC -o libdoom.so \
    -Wl,--whole-archive \
    "$CHOCO_BUILD/src/doom"/*.a \
    "$CHOCO_BUILD/textscreen"/*.a \
    "$CHOCO_BUILD/opl"/*.a \
    "$CHOCO_BUILD/pcsound"/*.a \
    -Wl,--no-whole-archive \
    "$COMMON_OBJ_DIR"/*.c.o \
    "$SDL2_LIB" \
    "$SDL2_MIXER_LIB" \
    -lm -ldl -llog -lOpenSLES -lGLESv1_CM -lGLESv2 -landroid \
    2>&1 | tail -2

echo "   → libdoom.so built ($(du -h libdoom.so | cut -f1))"

# Copy to expected location for build-apk.sh
mkdir -p "$TOOLCHAIN/apk/jniLibs/arm64-v8a"
cp libdoom.so "$TOOLCHAIN/apk/jniLibs/arm64-v8a/"
echo "   → Copied to $TOOLCHAIN/apk/jniLibs/arm64-v8a/"

# Copy C++ runtime (needed by SDL2)
CXX_SHARED="$NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so"
if [ -f "$CXX_SHARED" ]; then
    cp "$CXX_SHARED" "$TOOLCHAIN/apk/jniLibs/arm64-v8a/"
    echo "   ✓ libc++_shared.so copied"
fi

echo ""
echo "=== ✅ Native build complete ==="
echo "Output: $TOOLCHAIN/apk/jniLibs/arm64-v8a/libdoom.so"
echo ""
echo "Next: ./scripts/build-apk.sh doom"
