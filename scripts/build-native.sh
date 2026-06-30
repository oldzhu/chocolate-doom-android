#!/bin/bash
# Cross-compile SDL2 + SDL2_mixer + chocolate-doom for Android (arm64-v8a)
# Produces libmain.so for each game variant.
# Prerequisites: NDK, SDL2 source, SDL2_mixer source
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
AR="$TOOLCHAIN_PREFIX/bin/llvm-ar"
RANLIB="$TOOLCHAIN_PREFIX/bin/llvm-ranlib"
STRIP="$TOOLCHAIN_PREFIX/bin/llvm-strip"
SYSROOT="$TOOLCHAIN_PREFIX/sysroot"

# Source directories
SDL2_SRC="${SDL2_SRC:-$TOOLCHAIN/SDL2-src}"
SDL2_BUILD="${SDL2_BUILD:-$TOOLCHAIN/SDL2-build}"
SDL2_MIXER_SRC="${SDL2_MIXER_SRC:-$TOOLCHAIN/SDL2_mixer-2.6.3}"
CHOCO_SRC="$REPO_ROOT/native/chocolate-doom"          # ← git submodule
CHOCO_BUILD="${CHOCO_BUILD:-$TOOLCHAIN/choc-build}"    # out-of-tree build
CHOCO_PATCHES="$REPO_ROOT/native/patches"              # our patches

# Export vars for CMake
export CC CXX AR RANLIB STRIP
export CFLAGS="-fPIC -O2 -D__ANDROID__ -DANDROID"
export LDFLAGS="-fPIC"

GAMES="doom heretic hexen strife setup"

# ── Submodule check ──
if [ ! -f "$CHOCO_SRC/src/doom/st_stuff.c" ]; then
    echo "⚠️  Chocolate Doom source not found at $CHOCO_SRC"
    echo "   Run: git submodule update --init"
    exit 1
fi

# ── Apply our patches ──
echo "=== Applying patches ==="
cd "$CHOCO_SRC"
# Reset any previously applied patches (ignore errors if clean)
git checkout -- . 2>/dev/null || true
for patch in "$CHOCO_PATCHES"/*.patch; do
    if [ -f "$patch" ]; then
        echo "   Applying: $(basename "$patch")"
        git apply "$patch"
    fi
done
echo "   ✓ All patches applied"

# ── Phase 1: SDL2 (static) ──
echo "=== Building SDL2 (static) ==="
mkdir -p "$SDL2_BUILD"
cd "$SDL2_BUILD"

cmake "$SDL2_SRC" \
    -DCMAKE_SYSTEM_NAME=Android \
    -DCMAKE_SYSTEM_VERSION="$API_LEVEL" \
    -DCMAKE_ANDROID_ARCH_ABI=arm64-v8a \
    -DCMAKE_ANDROID_NDK="$NDK" \
    -DCMAKE_ANDROID_STL_TYPE=c++_static \
    -DCMAKE_BUILD_TYPE=Release \
    -DSDL_SHARED=OFF \
    -DSDL_STATIC=ON \
    2>&1 | tail -3

make -j$(nproc) 2>&1 | tail -3

# ── Phase 2: SDL2_mixer (static) ──
echo "=== Building SDL2_mixer (static) ==="
MIXER_BUILD="$SDL2_MIXER_SRC/build-android"
mkdir -p "$MIXER_BUILD"
cd "$MIXER_BUILD"

../configure \
    --host="$HOST" \
    --enable-static --disable-shared \
    --disable-music-mp3 \
    CFLAGS="-fPIC -O2" \
    2>&1 | tail -2

make -j$(nproc) 2>&1 | tail -2

# ── Phase 3: Chocolate Doom → static libs ──
echo "=== Building Chocolate Doom (static, PIC) ==="
mkdir -p "$CHOCO_BUILD"
cd "$CHOCO_BUILD"

cmake "$CHOCO_SRC" \
    -DCMAKE_SYSTEM_NAME=Android \
    -DCMAKE_SYSTEM_VERSION="$API_LEVEL" \
    -DCMAKE_ANDROID_ARCH_ABI=arm64-v8a \
    -DCMAKE_ANDROID_NDK="$NDK" \
    -DCMAKE_ANDROID_STL_TYPE=c++_static \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_C_FLAGS="-fPIC" \
    2>&1 | tail -3

make -j$(nproc) 2>&1 | tail -3

# ── Phase 4: Link game .so files ──
echo "=== Linking game shared libraries ==="
for GAME in $GAMES; do
    echo "--- $GAME ---"
    GAME_DIR="$CHOCO_BUILD/src/$GAME"
    mkdir -p "$GAME_DIR"
    cd "$GAME_DIR"

    "$CC" -shared -fPIC -o "lib${GAME}.so" \
        -Wl,--whole-archive \
        "$CHOCO_BUILD/src/$GAME"/*.a \
        "$CHOCO_BUILD/textscreen"/*.a \
        "$CHOCO_BUILD/opl"/*.a \
        "$CHOCO_BUILD/pcsound"/*.a \
        -Wl,--no-whole-archive \
        "$SDL2_BUILD/libSDL2.a" \
        "$MIXER_BUILD/build/.libs/libSDL2_mixer.a" \
        -lm -ldl -lOpenSLES \
        2>&1 | tail -2

    echo "     → lib${GAME}.so built"

    # Copy to expected location for build-apk.sh
    mkdir -p "$TOOLCHAIN/apk/jniLibs/arm64-v8a"
    cp "lib${GAME}.so" "$TOOLCHAIN/apk/jniLibs/arm64-v8a/"
done

echo ""
echo "=== ✅ Native build complete ==="
echo "Output: $CHOCO_BUILD/src/<game>/lib<game>.so"
echo ""
echo "Next: ./scripts/build-apk.sh doom"
