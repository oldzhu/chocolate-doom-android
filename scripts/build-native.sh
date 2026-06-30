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

# Make sure cmake and sdl2-config are findable
export PATH="$SDK/cmake/3.22.1/bin:$TOOLCHAIN_PREFIX/bin:$PATH"

# Source directories
SDL2_SRC="${SDL2_SRC:-$TOOLCHAIN/SDL2-src}"
SDL2_BUILD="${SDL2_BUILD:-$TOOLCHAIN/SDL2-build}"
SDL2_MIXER_SRC="${SDL2_MIXER_SRC:-$TOOLCHAIN/SDL2_mixer-2.6.3}"
CHOCO_SRC="$REPO_ROOT/native/chocolate-doom"          # ← git submodule
CHOCO_BUILD="${CHOCO_BUILD:-$TOOLCHAIN/choc-build-pic}" # out-of-tree build
CHOCO_PATCHES="$REPO_ROOT/native/patches"              # our patches

# Export vars
export CC CXX AR RANLIB STRIP

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

if [ ! -f "libSDL2.a" ]; then
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
else
    echo "   libSDL2.a already built, skipping."
fi

# Make sdl2-config executable (needed by SDL2_mixer configure)
chmod +x "$SDL2_BUILD/sdl2-config" 2>/dev/null || true

# ── Phase 2: SDL2_mixer (static) ──
echo "=== Building SDL2_mixer (static) ==="
MIXER_BUILD="$SDL2_MIXER_SRC/build-android"
MIXER_LIB="$MIXER_BUILD/build/.libs/libSDL2_mixer.a"

# Check multiple locations for prebuilt lib
for candidate in \
    "$SDL2_MIXER_SRC/build/.libs/libSDL2_mixer.a" \
    "$TOOLCHAIN/SDL2-android/lib/libSDL2_mixer.a" \
    "$MIXER_LIB"; do
    if [ -f "$candidate" ]; then
        MIXER_LIB="$candidate"
        break
    fi
done

if [ -f "$MIXER_LIB" ]; then
    echo "   Using prebuilt: $MIXER_LIB"
else
    echo "   Building from source..."
    mkdir -p "$MIXER_BUILD"
    cd "$MIXER_BUILD"

    # SDL2_mixer's configure needs sdl2-config in PATH
    export PATH="$SDL2_BUILD:$PATH"

    ../configure \
        --host="$HOST" \
        --enable-static --disable-shared \
        --disable-music-mp3 \
        CFLAGS="-fPIC -O2 -I$SDL2_BUILD/include" \
        2>&1 | tail -5

    make -j$(nproc) 2>&1 | tail -2
    MIXER_LIB="$MIXER_BUILD/build/.libs/libSDL2_mixer.a"
fi

# ── Phase 3: Chocolate Doom — recompile patched sources ──
echo "=== Building Chocolate Doom ==="
CHOCO_SRC_DIR="$CHOCO_SRC/src/doom"

# If the full build directory doesn't exist, we need a full cmake build
if [ ! -f "$CHOCO_BUILD/src/doom/libdoom.a" ]; then
    echo "   Full Chocolate Doom build not found at $CHOCO_BUILD"
    echo "   Running cmake+build (one-time, ~5 min)..."

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
        -DSDL2_DIR="$SDL2_BUILD" \
        -DSDL2_MIXER_DIR="$SDL2_MIXER_SRC" \
        -DENABLE_SDL2_NET=OFF \
        2>&1 | tail -5

    make -j$(nproc) 2>&1 | tail -3
else
    echo "   Full build exists at $CHOCO_BUILD"
    echo "   Recompiling patched sources only..."
fi

# Always recompile patched source files into the existing build
echo "   Compiling patched st_stuff.c → libdoom.a"
CPPFLAGS="-I$CHOCO_SRC/src/doom -I$CHOCO_SRC/textscreen -I$CHOCO_SRC/src \
    -I$CHOCO_BUILD -I$CHOCO_BUILD/src \
    -I$SDL2_BUILD/include -I$SDL2_BUILD/include-config \
    -I$SDL2_MIXER_SRC \
    -D__ANDROID__ -DANDROID"

# Use cmake naming convention: st_stuff.c.o (matches what cmake generates)
"$CC" -fPIC -O2 $CPPFLAGS -c "$CHOCO_SRC/src/doom/st_stuff.c" \
    -o /tmp/st_stuff.c.o 2>&1

# Replace the object in the static library
cd "$CHOCO_BUILD/src/doom"
cp libdoom.a libdoom.a.bak
"$AR" d libdoom.a st_stuff.c.o 2>/dev/null || true
"$AR" r libdoom.a /tmp/st_stuff.c.o 2>&1
rm -f libdoom.a.bak /tmp/st_stuff.c.o
echo "   ✓ st_stuff.c.o replaced in libdoom.a"

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
        "$MIXER_LIB" \
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
