# Changelog & Fix Log

> [中文版 (Chinese)](../zh/changelog.md)

Complete record of all decisions, fixes, and changes made during the Chocolate Doom Android port development.

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
