# Development Lessons Learned

> [中文版 (Chinese)](../zh/lessons-learned.md)

Quick-reference for future development. Each entry records a problem, its cause, the fix, and how to avoid it next time.

---

## 1. Common source files are in the executable target, not static libs

**Problem:** `I_VideoBuffer`, `I_InitSound`, `M_CheckParm`, and dozens of other symbols undefined at `dlopen`.

**Cause:** Chocolate Doom's cmake puts shared sources (`i_video.c`, `i_input.c`, `i_sound.c`, `m_argv.c`, etc.) into the `chocolate-doom` executable target — not into `libdoom.a` or any static library.

**Fix:** Compile the executable target for `.o` files only (link step fails — no `main()` on Android):
```bash
make chocolate-doom -j$(nproc) || true
```
Then link `$BUILD_DIR/src/CMakeFiles/chocolate-doom.dir/*.c.o` into `libdoom.so`.

**Avoid next time:** Run `nm -D libdoom.so | grep " U "` after linking. Any `U` symbol from `i_*.c` or `m_*.c` means a common source is missing.

---

## 2. Both GLES 1.x and 2.0 are needed

**Problem:** 50+ undefined OpenGL symbols (`glBindTexture`, `glUseProgram`, etc.).

**Cause:** Chocolate Doom uses GLES 1.x for 2D screen rendering and GLES 2.0 for scaling/shaders. Only one was linked.

**Fix:** Link both:
```
-lGLESv1_CM -lGLESv2
```

**Avoid next time:** If you see `gl*` undefined symbols, you're missing a GLES library. Both v1 and v2 are likely needed.

---

## 3. C++ ABI symbols require `clang++` linker

**Problem:** `_gxx_personality_v0`, `__cxa_begin_catch` undefined.

**Cause:** SDL2 static library contains C++ code. `clang` (C linker) doesn't pull in C++ runtime. Also: `libc++_shared.so` must be bundled in the APK.

**Fix:**
1. Use `$CXX` (clang++) as linker, not `$CC` (clang)
2. Copy `libc++_shared.so` from NDK into `jniLibs/arm64-v8a/`

**Avoid next time:** If you see `_gxx_*` or `__cxa_*` undefined symbols, switch to `clang++`. Rule: if any linked static lib has C++ code, use the C++ linker.

---

## 4. Android system libs: `-llog`, `-landroid`, `-lOpenSLES`

**Problem:** `__android_log_write`, `ANativeWindow_setBuffersGeometry` undefined.

**Cause:** These are Android system libraries that SDL2 depends on but aren't implicitly linked.

**Fix:** Full Android linker line:
```
-lm -ldl -llog -lOpenSLES -lGLESv1_CM -lGLESv2 -landroid
```

**Mnemonic:** `m-dl-log-sles-gles1-gles2-android` — every SDL2-on-Android build needs these.

---

## 5. `-DENABLE_SDL2_MIXER=OFF` silently disables ALL sound

**Problem:** No sound, no errors, build succeeds.

**Cause:** `-DENABLE_SDL2_MIXER=OFF` defines `DISABLE_SDL2MIXER=1`, which wraps **the entire `i_sdlsound.c`** file in `#ifndef DISABLE_SDL2MIXER`. The sound module becomes empty. Only PC speaker emulation remains — silent on Android.

**Fix:**
1. Set `-DENABLE_SDL2_MIXER=ON`
2. Pass SDL2_mixer paths directly as cmake variables (prebuilt SDL2_mixer has no cmake config):
   ```
   -DSDL2_MIXER_INCLUDE_DIR=... -DSDL2_MIXER_LIBRARY=... -DSDL2_mixer_FOUND=TRUE
   ```

**Avoid next time:** After any cmake flag change, verify with:
```bash
nm -D libdoom.so | grep "Mix_OpenAudio"   # must show T (defined), not U
nm -D libdoom.so | grep "sound_sdl_module"  # must show D (data)
```

---

## 6. IDDQD XOR toggle kills you on Nightmare

**Problem:** God Mode works on all difficulties except Nightmare. Even on Nightmare with cheat code injection, health drops to 0 and player dies.

**Root cause (two parts):**

**Part A — Nightmare blocks all cheats:**
`st_stuff.c:393`: `if (!netgame && gameskill != sk_nightmare)` — all cheat processing skipped on Nightmare. Vanilla DOOM behavior.

**Part B — XOR toggle creates vulnerability window:**
`st_stuff.c:398`: `plyr->cheats ^= CF_GODMODE;` — every IDDQD injection FLIPS god mode ON↔OFF. Double-IDDQD workaround had ~300ms off-window between injections. A killing blow in that window = death.

**Fix:**
1. Remove `gameskill != sk_nightmare` check (allow cheats on all difficulties)
2. Change `^= CF_GODMODE` to `|= CF_GODMODE` (always SET, never toggle)

**Result:** Once God Mode activates, permanently invulnerable. No vulnerability window.

**Avoid next time:** XOR toggles are dangerous for state you want permanently ON. Use `|=` for always-set behavior.

---

## 7. Build success ≠ feature present

**Problem:** Multiple features were silently missing despite clean builds:
- Sound (see #5)
- Cheats on Nightmare (not compiled with our patches if submodule wasn't updated)

**Practice:** After any build, verify expected symbols are present:
```bash
# Sound
nm -D libdoom.so | grep Mix_OpenAudio

# God mode
strings libdoom.so | grep "cheats |= CF_GODMODE"

# Check NEEDED libraries
readelf -d libdoom.so | grep NEEDED
```

---

## 8. OPPO ColorOS quirks

| Issue | Mitigation |
|-------|-----------|
| Screen lock kills app surface | Always unlock before launching |
| AAudio denied by system policy | Falls back to standard AudioTrack — OK |
| ADB disconnects after reboot | Reapply udev rules: `sudo tee /etc/udev/rules.d/51-android.rules` with idVendor 22d9 |
| `setOrientationBis()` destroys Surface | Skipped entirely in `ChocolateDoom.java` |

---

## 9. Setup-toolchain reproducibility

**Problem:** Fresh clones couldn't build because toolchain paths were hardcoded.

**Fix:** Created `scripts/setup-toolchain.sh` that downloads and installs everything:
- NDK r27
- Android SDK 34 + build-tools 34.0.0
- JDK 17 (Temurin)
- SDL2 2.30.0 (cmake build, cross-compiled for arm64-v8a)
- SDL2_mixer 2.8.0 (cmake build, vendored deps)
- Chocolate Doom submodule init

All versions pinned. Script is idempotent — skips already-installed components.

---

## Quick Reference: Linker Flags

```
# Final linker invocation for libdoom.so:
$CXX -shared -fPIC -o libdoom.so \
    -Wl,--whole-archive libdoom.a libtextscreen.a libopl.a libpcsound.a \
    -Wl,--no-whole-archive \
    $COMMON_OBJ_DIR/*.c.o \
    $SDL2_LIB $SDL2_MIXER_LIB \
    -lm -ldl -llog -lOpenSLES -lGLESv1_CM -lGLESv2 -landroid
```

## Quick Reference: Build Commands

```bash
# One-time setup (~30 min)
./scripts/setup-toolchain.sh

# Build + deploy
./scripts/build-native.sh          # → libdoom.so
./scripts/build-apk.sh doom        # → release/chocolate-doom.apk
adb install -r release/chocolate-doom.apk

# Launch
adb shell am start -n com.chocolate.doom/.ChocolateDoom

# Debug
adb logcat | grep -E "(SDL|chocolate|FATAL|dlopen)"
```
