# Changelog & Fix Log

> [中文版 (Chinese)](../zh/changelog.md)

Complete record of all decisions, fixes, and changes made during the Chocolate Doom Android port development.

---

## Pre-Release Features (2026-06-26)

### Feature 1: Analog Joystick Touch-to-Move

| Aspect | Detail |
|--------|--------|
| **Problem** | Fixed D-pad buttons cumbersome for touchscreen movement |
| **Solution** | Floating analog joystick on left half of screen |
| **Implementation** | New `AnalogJoystick.java` class + modified `TouchControls.java` |
| **Design** | Touch anywhere on left half → joystick appears. Drag for 8-direction movement. Auto-run at 90%+ drag. |
| **Files** | `AnalogJoystick.java` (new), `TouchControls.java` (modified) |

### Feature 2: God Mode (IDDQD + IDKFA Cheat)

| Aspect | Detail |
|--------|--------|
| **Problem** | No way to activate DOOM cheat codes without physical keyboard |
| **Solution** | Triple-tap ☰ (Menu) within 1 second → injects `iddqd` + `idkfa` via keyboard events |
| **Implementation** | `TouchControls.java` — `onMenuTapped()` triple-tap detection, `injectCheatSequence()` character injection, `showOverlay()` visual feedback |
| **Activation** | Tap ☰ three times within 1000ms |
| **Visual** | "⚡ GOD MODE ⚡" overlay text for 2 seconds |
| **Effect** | Invulnerability (IDDQD) + all weapons/ammo/keys (IDKFA) |

### Feature 2b: God Mode — Health & Ammo Lock (2026-06-29)

| Aspect | Detail |
|--------|--------|
| **Problem** | Initial God Mode only injected cheats once — ammo decreased when firing, health decreased from damage |
| **Solution** | Fast periodic re-injection: IDFA every **500ms** (ammo lock), double-IDDQD every **5s** (health lock) |
| **Implementation** | `TouchControls.java` — `scheduleAmmoRefill()` at 500ms loop, `scheduleHealthRefresh()` double-IDDQD at 5s loop, silent (no overlay spam) |
| **Health Bug Root Cause** | DOOM's IDDQD uses XOR toggle — single re-injection turns god mode OFF instead of refreshing it |
| **Health Fix** | Inject `iddqd` TWICE in sequence (`iddqdiddqd`): 1st toggles OFF → 2nd toggles back ON with health=100. Net: god mode stays ON, health restored. |
| **Ammo Bug** | Original 15s refill was too slow — ammo visibly dropped between refills |
| **Ammo Fix** | Reduced interval to 500ms; ammo restored faster than fire rate, never visibly decreases |
| **Note** | This is cheat-code injection, not native memory locking. True zero-frame lock would require native C code modification. |

### Feature 3: Context-Aware Tap Controls (2026-06-29)

| Aspect | Detail |
|--------|--------|
| **Problem** | Too many on-screen buttons (11 total) cluttering the game viewport; Fire/Use/Enter required precise button taps |
| **Solution** | Single tap on game area injects Ctrl+Space+Enter simultaneously — the game engine decides which key is valid in context |
| **Design Doc** | `docs/en/controls-design.md` + `docs/zh/controls-design.md` (bilingual) |
| **Implementation** | `TouchControls.java` — tap detection (max 250ms duration, 25px movement), `onGameTap()` injects 3 keys, auto-release 80ms |
| **Removed Buttons** | 🔫 Fire, 🚪 Use/Open, ↵ Enter (all replaced by game-area tap) |
| **Added Buttons** | ◀ Strafe Left (symmetric with Strafe Right) |
| **Layout** | Simplified to 8 buttons: ☰ Menu, Y, 🗺 Map, ◀◀/▶▶ Weapons, ◀/▶ Strafe, 🏃 Run |
| **Context Logic** | Facing enemy → fires; facing door → opens; in menu → selects item. No state tracking needed. |

### God Mode Fixes — Iteration (2026-06-29)

| # | Attempt | Issue | Fix |
|---|---------|-------|-----|
| G1 | IDDQD re-injection every 10s | XOR toggle turns god mode OFF on every other injection | Switched to `idbeholdv` (invuln artifact) every 25s |
| G2 | `idbeholdv` every 25s | Artifact prevents damage but doesn't restore health already lost | Double-IDDQD (`iddqdiddqd`) every 5s: 1st toggles OFF, 2nd toggles ON (health=100) |

---

## v1.0 — Initial Working Port (2026-06-26)

### Dependencies Setup

| Component | Version | Decision |
|-----------|---------|----------|
| Chocolate Doom | 3.1.1 | `chocolate-doom-3.1.1` tag |
| SDL2 | 2.30.0 | `release-2.30.0` tag |
| SDL2_mixer | 2.6.3 | Static build, OGG/FLAC/WAV support |
| Android NDK | r27 | arm64-v8a, API 24 |
| Android SDK | 34 | build-tools 34.0.0 |

### Build Fixes

| # | Issue | Fix | Date |
|---|-------|-----|------|
| B1 | `-DDISABLE_SDL2MIXER=1` in CMake | Removed from all `flags.make`, downloaded SDL2_mixer 2.6.3, cross-compiled, linked statically | 2026-06-26 |
| B2 | `libmain.so` missing audio symbols | Added `-Wl,--whole-archive` for choc objects, linked `libSDL2_mixer.a` | 2026-06-26 |
| B3 | IWAD not found on Android 11+ | Changed `-iwad` path from `/sdcard/` to `getFilesDir()` internal storage | 2026-06-26 |
| B4 | APK too large for GitHub | Used ZIP compression; APK ≈3.3 MB on disk (≈10 MB uncompressed .so) | 2026-06-26 |

### OPPO ColorOS Fixes

| # | Issue | Root Cause | Fix | Date |
|---|-------|------------|-----|------|
| O1 | Black screen on launch | `setOrientationBis()` destroys/recreates Surface on OPPO | Skip `setOrientationBis()` entirely in `ChocolateDoom.java` | 2026-06-26 |
| O2 | Surface destroyed on screen-off → SDL deadlock | OPPO destroys Surface when screen turns off, pauses Activity before new Surface ready | `SDLSurface.java`: skip native pause if `System.currentTimeMillis() - mSurfaceCreatedTime < 500ms` | 2026-06-26 |
| O3 | App paused on resume | Activity paused before Surface ready | `ChocolateDoom.java`: call `handleNativeState(RESUMED)` in `onWindowFocusChanged()` | 2026-06-26 |

### Touch Controls — Iteration History

| Version | Changes | Date |
|---------|---------|------|
| v1 | Initial layout: D-pad + Fire + Use + Run + Map + Strafe + Menu | 2026-06-26 |
| v2 | Added Enter button (replaced strafe-left), added Y button (non-functional) | 2026-06-26 |
| v3 | **Size increase 1**: D-pad outer 90→140px, buttons 35→50px, Fire 70→100px, others 50→65px, Menu 40→55px | 2026-06-26 |
| v4 | **Icon size increase 1**: arrows 28→42px, emojis 26→36px | 2026-06-26 |
| v5 | **Icon size increase 2**: arrows 42→56px, emojis 36→44px | 2026-06-26 |
| v6 | **Y button fix**: added `sendText()` wrapper in `SDLActivity.java` → Y button calls `sendText("y")` instead of `onNativeKeyDown` | 2026-06-26 |
| v7 | **Y button fix v2**: `sendText("y")` → `SDL_TEXTINPUT` (wrong event type for DOOM quit). Changed to `onNativeKeyDown(KEYCODE_Y=53)` + auto-release 80ms. Fixed keycode 54→53 (was KEYCODE_Z). | 2026-06-26 |
| v8 | **Context-aware tap**: removed 🔫 Fire, 🚪 Use, ↵ Enter. Tap game area → Ctrl+Space+Enter. Added ◀ Strafe Left. Layout: 11→8 buttons. Design doc: `controls-design.md`. | 2026-06-29 |
| v9 | **Button overlap fix**: recalculated all positions with minimum 10px gaps. Adjusted radii: Menu/Y 50, Map 60, Weapon 40, Strafe 45, Run 60. | 2026-06-29 |
| v10 | **AI toggle button**: added 🤖 button (top bar, r=45). Green when active, dim when off. Wired to AIController for DQN agent control. | 2026-06-30 |

### Key Fix: Y Button Not Quitting

**Symptom**: Y button visible on screen, touch detected, but game does not quit when "Press Y to quit to DOS" is displayed.

**Investigation**:
1. DOOM's quit handler (`m_menu.c:M_Responder`) checks `ev->type == ev_keydown` and `ev->data1 == 'y'`
2. `sendText("y")` calls `SDLInputConnection.nativeCommitText("y", 1)` → generates `SDL_TEXTINPUT` event
3. `SDL_TEXTINPUT` has `type != ev_keydown` → DOOM ignores it ❌

**Fix** (commit in `TouchControls.java`):
```java
// Before (broken):
if (btn == btnYes) {
    SDLActivity.sendText("y");              // TEXTINPUT event — DOOM ignores
    handler.postDelayed(() -> { fb.pressed = false; }, 80);
}

// After (working):
if (btn == btnYes) {
    SDLActivity.onNativeKeyDown(btn.keyCode);  // KEYCODE_Y=53 → KEYDOWN event
    handler.postDelayed(() -> {
        if (fb.pressed) {
            fb.pressed = false;
            SDLActivity.onNativeKeyUp(fb.keyCode);
        }
    }, 80);
}
```

**Key insight**: Android `KEYCODE_Y` (53) → `Android_Keycodes[53]` → `SDL_SCANCODE_Y` → `SDL_SendKeyboardKey` generates `KEYDOWN` with `keysym.sym = SDLK_y = 'y'`. DOOM's event loop matches this.

### Known Limitations

| # | Issue | Status |
|---|-------|--------|
| K1 | APK must be signed with debug keystore (production signing TBD) | Open |
| K2 | No on-screen keyboard for save game naming | Open |
| K3 | Heretic/Hexen/Strife APKs not fully tested with correct WADs | Open |
| K4 | Touch controls opacity not user-adjustable | Open |
| K5 | No multi-touch support for simultaneous actions (fire + move) | Open |
| K6 | WAD auto-discovery not implemented (manual push required) | Open |

---

## F3 AI Player — Design Phase (2026-06-29)

| Aspect | Detail |
|--------|--------|
| **Discussion** | On-device RL vs PC pre-training, VLM/VLA/WAM viability for real-time DOOM |
| **Decision** | Phase 1: Double Dueling DQN, pure on-device RL. Phase 2: BC+RL bootstrapping. Phase 3: Hybrid DQN+VLM strategy layer. |
| **Design Docs** | `docs/en/ai-player-options.md` + `docs/zh/ai-player-options.md` — full comparison of 7 approaches |
| **Excluded** | DreamerV3 (too large/slow), pure VLM (100-400x too slow), MCTS (needs world model), Rainbow DQN (over-engineered for v1.0) |
| **AI Player Spec** | `docs/en/ai-player.md` + `docs/zh/ai-player.md` updated with cross-links |

---

## F3 AI Player — v1.0 Implementation (2026-06-30)

| Aspect | Detail |
|--------|--------|
| **Architecture** | Pure-Java DQN: FC(13→128→64→14), double Q-learning, replay buffer 10K |
| **Files Created** | `ai/NeuralNet.java` (270 lines), `ai/DQNPlayer.java` (265 lines), `ai/AIController.java` (292 lines) |
| **State Features** | v1.0: heuristic (time alive, recent actions). v1.1: JNI game state extraction planned. |
| **Actions** | 14 discrete: NOOP, FWD, BACK, LEFT, RIGHT, STRAFEL, STRAFER, FIRE, USE, RUN, FIST, PISTOL, SHOTGUN, BFG |
| **Training** | On-device RL: 8.75 Hz (frame-skip=4), batch=32, γ=0.99, ε=1.0→0.05 |
| **Toggle** | 🤖 button (top bar, green when active). Toggles between human and AI play. |
| **Model Size** | ~50 KB (~10K params), 0 MB APK impact (pure Java) |
| **Frame Capture** | Not yet — state is heuristic. JNI glReadPixels planned for v1.1. |

### Cheat Injection Fix — Queue Serialization (2026-06-30)

| Aspect | Detail |
|--------|--------|
| **Bug** | IDFA (500ms) and double-IDDQD (5s) injected characters via the same Handler. When overlapping, characters interleaved (e.g. "i i d d f d a q"), garbling cheat input. DOOM's cheat detector matched nothing. |
| **Symptom** | Health not restoring in god mode. Ammo sometimes not refilling. |
| **Fix** | Queue-based injection (`cheatQueue` LinkedList). Only one cheat sequence processes at a time. 50ms key hold, 100ms gap between sequences. |
| **Result** | No interleaving possible. Cheat sequences always reach DOOM intact. |

### Nightmare God Mode Blocker — Root Cause & Fix (2026-06-30)

| Aspect | Detail |
|--------|--------|
| **Symptom** | God Mode (triple-tap ☰) has no effect on Nightmare difficulty — health decreases, ammo depletes, no weapons granted |
| **Root Cause** | Chocolate Doom `st_stuff.c:393`: `if (!netgame && gameskill != sk_nightmare)` — ALL cheat codes blocked on Nightmare. Vanilla DOOM behavior faithfully reproduced. |
| **Why Java can't fix it** | `TouchControls.java` injects keyboard events correctly, but `ST_Responder` (native C) refuses to process them on Nightmare |
| **Chosen Solution** | **Source patch**: remove `gameskill != sk_nightmare` from `st_stuff.c`, change `^= CF_GODMODE` (XOR toggle) to `|= CF_GODMODE` (always SET). Rebuild `libdoom.so` via `build-native.sh`. |
| **Reason for source patch** | `libdoom.so` is built from the git submodule at `native/chocolate-doom/`. Source patch is permanent across rebuilds. |
| **XOR toggle fix** | Original `^=` flips god mode ON↔OFF each injection. Changed to `|=` so IDDQD always enables god mode — no vulnerability window, no death possible. |
| **Full Analysis** | `docs/en/nightmare-godmode.md` + `docs/zh/nightmare-godmode.md` |

---

## Chocolate Doom Submodule + Build System Migration (2026-06-30)

### Git Submodule Decision

| Aspect | Detail |
|--------|--------|
| **Why submodule** | Keeps repo lightweight, clear upstream provenance at exact tag `chocolate-doom-3.1.1`, reproducible builds |
| **Submodule path** | `native/chocolate-doom/` (pinned to upstream tag `chocolate-doom-3.1.1`) |
| **Patches** | Version-controlled at `native/patches/nightmare-cheats.patch` |
| **Auto-apply** | `build-native.sh` runs `git apply` on all `.patch` files before cmake |
| **Commit** | `29c9e92` — submodule + patches + decision docs (EN+ZH) |

### Build System Changes

| # | Change | Reason | Commit |
|---|--------|--------|--------|
| BS1 | cmake now configures from submodule (`native/chocolate-doom/`) | Eliminates external source dependency | `29c9e92` |
| BS2 | Use Android SDK's bundled cmake (`$SDK/cmake/3.22.1/bin`) | Ensures compatible cmake version | `4fddc94` |
| BS3 | Use prebuilt SDL2/SDL2_mixer from `SDL2-android/` | Avoids slow autotools configure (SDL2_mixer's `sdl2-config` not compatible with Android cross-compile) | `4fddc94` |
| BS4 | Compile `chocolate-doom` executable target for common `.o` files | Common sources (`i_video.c`, `i_input.c`, etc.) are in the executable target, not static libs | `e8cd299` |
| BS5 | Switch linker from `clang` to `clang++` | SDL2 static lib contains C++ code needing C++ ABI symbols | `2939cd6` |
| BS6 | Bundle `libc++_shared.so` in APK | Required at runtime by SDL2's C++ code | `2939cd6` |

---

## libdoom.so Linker Fixes — dlopen Symbol Resolution (2026-06-30)

After migrating to git submodule and full cmake rebuild, five sequential `dlopen failed: cannot locate symbol` errors were encountered and fixed. Each error prevented the app from launching on the device.

### Complete Fix Log

| # | Missing Symbol | Root Cause | Fix | Commit |
|---|---------------|------------|-----|--------|
| L1 | `I_VideoBuffer` | Common sources (`i_video.c`, `i_input.c`, `i_timer.c`, etc.) compiled only into `chocolate-doom` executable target, not into static libs | Compile `chocolate-doom` target (ignore link failure — only need `.o` files), link `CMakeFiles/chocolate-doom.dir/src/*.c.o` into `libdoom.so` | `e8cd299` |
| L2 | `glBindTexture` + 50+ GLES symbols | Chocolate Doom uses both OpenGL ES 1.x and 2.0 but neither library was linked | Add `-lGLESv1_CM -lGLESv2` to linker flags | `e8cd299` |
| L3 | `_gxx_personality_v0` | C++ exception handling ABI symbol not resolved. SDL2 static lib compiled with C++ code; `clang` (C linker) doesn't pull in C++ runtime | Switch linker to `clang++` (`$CXX`), add `-lc++_shared` (implicit with C++ linker), bundle `libc++_shared.so` in APK | `2939cd6` |
| L4 | `__android_log_write` | Android logging library not linked; `-llog` was present in earlier linker line but dropped when switching to `$CXX` | Add `-llog` back to linker flags | `9331bcd` |
| L5 | `ANativeWindow_setBuffersGeometry` | Android NativeWindow API not linked; SDL2 video backend calls this function | Add `-landroid` to linker flags | `9331bcd` |

### Final Linker Invocation

```bash
$CXX -shared -fPIC -o libdoom.so \
    -Wl,--whole-archive libdoom.a libtextscreen.a \
    libopl.a libpcsound.a -Wl,--no-whole-archive \
    $COMMON_OBJ_DIR/*.c.o \
    $SDL2_LIB $SDL2_MIXER_LIB \
    -lm -ldl -llog -lOpenSLES -lGLESv1_CM -lGLESv2 -landroid
```

### Verification

```bash
# Check NEEDED libraries
readelf -d libdoom.so | grep NEEDED
# Output: libm.so, libdl.so, liblog.so, libOpenSLES.so,
#         libGLESv1_CM.so, libGLESv2.so, libc++_shared.so, libc.so

# Confirm no undefined symbols
nm -D libdoom.so | grep -E "I_VideoBuffer|glBindTexture|_gxx_personality|__android_log_write|ANativeWindow_setBuffersGeometry"
# All resolved ✓
```

### Lessons Learned

1. **Common sources in executables, not libs**: Chocolate Doom's cmake puts shared sources (`i_*.c`, `m_*.c`) into the `chocolate-doom` executable target, not into `libdoom.a`. We compile the executable target for `.o` files only (link step fails — no `main()` on Android — but that's fine).

2. **GLES v1 + v2 both needed**: Chocolate Doom uses GLES 1.x for 2D screen rendering and GLES 2.0 for scaling/shaders. Both must be linked.

3. **C++ ABI from SDL2**: Even if your code is pure C, linking a static lib with C++ code requires `clang++` as the linker. Missing C++ ABI symbols (`_gxx_personality_v0`, `__cxa_*`) are the #1 symptom.

4. **Android system libs**: `-llog` (logging), `-landroid` (NativeWindow), `-lOpenSLES` (audio) are frequently needed when linking SDL2 for Android but easy to overlook.

---

## Sound Regression — No Audio Output (2026-06-30)

### Symptom

After linker fixes and build system migration, sound effects and music stopped working. Game launched and ran, but completely silent.

### Root Cause

`build-native.sh` had `-DENABLE_SDL2_MIXER=OFF` in the cmake configuration (added during earlier build fix to avoid autotools `sdl2-config` failure). This cmake flag defines `DISABLE_SDL2MIXER=1` at compile time, which wraps the **entire `i_sdlsound.c`** file in `#ifndef DISABLE_SDL2MIXER ... #endif`.

```c
// i_sdlsound.c:55 — entire file wrapped in this guard
#ifndef DISABLE_SDL2MIXER
    // ALL sound initialization, mixing, and playback code
    // I_SDL_InitSound(), Mix_OpenAudioDevice(), etc.
#endif  // line 1146
```

When `DISABLE_SDL2MIXER` is defined, the `sound_sdl_module` (SDL-based sound) is completely empty. The only remaining module is `sound_pcsound_module` (PC speaker emulation), which produces no audible output on Android devices.

**Impact chain:**
```
-DENABLE_SDL2_MIXER=OFF
  → DISABLE_SDL2MIXER=1  (add_compile_definitions in CMakeLists.txt)
    → i_sdlsound.c is completely excluded
      → sound_sdl_module = empty
        → I_InitSound() only tries pcsound
          → PC speaker emulation = SILENT on Android
```

### Fix

1. **Change cmake flag**: `-DENABLE_SDL2_MIXER=ON` (not OFF)
2. **Provide SDL2_mixer cmake module**: Chocolate Doom's cmake calls `find_package(SDL2_mixer)` which needs `SDL2_MIXER_INCLUDE_DIR` and `SDL2_MIXER_LIBRARY`. Pass these directly as cmake variables since our prebuilt SDL2_mixer doesn't ship cmake config files:
   ```
   -DSDL2_MIXER_INCLUDE_DIR="$SDL2_ANDROID/include/SDL2"
   -DSDL2_MIXER_LIBRARY="$SDL2_ANDROID/lib/libSDL2_mixer.a"
   -DSDL2_mixer_FOUND=TRUE
   ```
3. **Create `FindSDL2_mixer.cmake`**: A minimal cmake find-module at `$SDL2_ANDROID/lib/cmake/SDL2_mixer/FindSDL2_mixer.cmake` that creates the `SDL2_mixer::SDL2_mixer` imported target. Required for cmake's `find_package(SDL2_mixer)` in MODULE mode.
4. **Add `-DCMAKE_MODULE_PATH`**: Point to the directory containing `FindSDL2_mixer.cmake`.

### Verification

```bash
# Confirm sound module is compiled into .so
nm -D libdoom.so | grep "Mix_OpenAudio"
# Must show: T Mix_OpenAudio  (defined, not U undefined)

nm -D libdoom.so | grep "sound_sdl_module"
# Must show: D sound_sdl_module  (data symbol present)
```

### Runtime Behavior

- AAudio: requested but **denied by OPPO/Realme ColorOS** system policy ("aaudio denied with incompatible policy such as performance noise"). This is an OPPO-specific restriction.
- Fallback: **standard AudioTrack** succeeds at 44100 Hz, 16-bit, stereo
- Audio track: `createTrack state 0` (success), no errors

### Why this regression happened

The `-DENABLE_SDL2_MIXER=OFF` flag was originally added to avoid an autotools `sdl2-config` configure failure when building SDL2_mixer from source. After switching to prebuilt SDL2_mixer (from the `SDL2-android` project), the configure issue was gone, but the flag was never removed. The code compiled and linked without errors — the build gave no indication that sound was silently disabled.

**Lesson**: A successful build does not guarantee all features are compiled. Check preprocessor defines and symbol tables to verify what's actually in the binary.

| Aspect | Detail |
|--------|--------|
| **Symptom** | No sound effects or music. Game runs but silent. |
| **Root Cause** | `-DENABLE_SDL2_MIXER=OFF` → `DISABLE_SDL2MIXER=1` → entire `i_sdlsound.c` excluded → only PC speaker sound (silent on Android) |
| **Fix** | `-DENABLE_SDL2_MIXER=ON` + pass SDL2_mixer cmake variables directly |
| **Commit** | `b906842` |
