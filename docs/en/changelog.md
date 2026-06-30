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
| **Chosen Solution** | **Source patch**: remove `gameskill != sk_nightmare` from `st_stuff.c`, rebuild `libdoom.so` via `build-native.sh` |
| **Reason for source patch** | `libdoom.so` is built from local source (`~/android-toolchain/chocolate-doom-src/`), not a prebuilt blob. Source patch is permanent across rebuilds. |
| **Full Analysis** | `docs/en/nightmare-godmode.md` + `docs/zh/nightmare-godmode.md` |
