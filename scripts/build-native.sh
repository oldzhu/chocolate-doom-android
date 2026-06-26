#!/bin/bash
# Cross-compile SDL2 + SDL2_mixer + chocolate-doom for Android (arm64-v8a)
# Produces libmain.so for each game variant.
# Prerequisites: NDK, chocolate-doom source, SDL2 source, SDL2_mixer source
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
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
CHOCO_SRC="${CHOCO_SRC:-$TOOLCHAIN/chocolate-doom-src}"
CHOCO_BUILD="${CHOCO_BUILD:-$TOOLCHAIN/choc-build-pic}"

# Export vars for CMake
export CC CXX AR RANLIB STRIP
export CFLAGS="-fPIC -O2 -D__ANDROID__ -DANDROID"
export LDFLAGS="-fPIC"

GAMES="doom heretic hexen strife setup"

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

echo "=== Building Chocolate Doom variants ==="
for GAME in $GAMES; do
    echo "--- $GAME ---"
    BUILD_DIR="$CHOCO_BUILD/src/$GAME"
    mkdir -p "$BUILD_DIR"
    cd "$BUILD_DIR"

    # Link everything into one libmain.so
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
done

echo "=== ✅ Native build complete ==="
echo "Libraries are in: $CHOCO_BUILD/src/<game>/lib<game>.so"
