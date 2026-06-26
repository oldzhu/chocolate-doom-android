# Design & Architecture

> [中文版 (Chinese)](../zh/design.md)

## Overview

Chocolate Doom Android ports the original Chocolate Doom 3.1.1 engine to Android by embedding it inside a standard SDL2 Android Activity. The engine runs natively in a separate thread, while touch controls are implemented as a transparent Java overlay `View`.

## Architecture Layers

```
┌─────────────────────────────────────────┐
│            TouchControlView              │  ← Java overlay (draws circles + labels)
│   (captures MotionEvents → sends keys)  │
├─────────────────────────────────────────┤
│          SDLSurface (GLSurfaceView)      │  ← SDL renders here (OpenGL ES 2.0)
│   (receives native SDL rendering)       │
├─────────────────────────────────────────┤
│         ChocolateDoom (SDLActivity)      │  ← Java Activity, lifecycle manager
│   - OPPO workarounds                    │
│   - WAD path resolution                 │
│   - Native state management             │
├─────────────────────────────────────────┤
│              JNI Bridge                   │  ← SDLActivity ↔ SDL_android.c
│   onNativeKeyDown / onNativeKeyUp       │
│   nativeCommitText / nativeSendQuit     │
├─────────────────────────────────────────┤
│          SDL2 (libSDL2.a)                │  ← Static library
│   Event queue, video, audio, input      │
├─────────────────────────────────────────┤
│       SDL2_mixer (libSDL2_mixer.a)       │  ← Static library (OGG/FLAC/WAV)
├─────────────────────────────────────────┤
│     Chocolate Doom (libmain.so)          │  ← Game engine
│   doom/heretic/hexen/strife/setup       │
└─────────────────────────────────────────┘
```

## Key Design Decisions

### 1. Touch Controls in Java, Not SDL Core

**Decision**: Implement virtual controls as a transparent Java `View` overlay rather than patching SDL's input code.

**Rationale**:
- Zero changes to upstream SDL2 or Chocolate Doom C code
- Easier to iterate on layout (no C rebuild needed)
- Leverages Android's native touch event distribution
- All input eventually goes through `SDL_SendKeyboardKey()` — identical to physical keys

### 2. Static Linking for Everything

**Decision**: Link SDL2, SDL2_mixer, and all Chocolate Doom object files into a single `libmain.so`.

**Rationale**:
- Avoids Android dynamic linker issues with `dlopen` cross-references
- One `.so` per game variant = simpler APK packaging
- `-Wl,--whole-archive` ensures all symbols are exported

### 3. WAD Files in Internal Storage

**Decision**: Load WAD files from `/data/data/<package>/files/` (internal app storage) rather than `/sdcard/`.

**Rationale**:
- Android 11+ scoped storage restrictions block native file access from `/sdcard/`
- `getFilesDir()` returns a path accessible to native code
- WAD pushed once via `adb` + `run-as` copy

### 4. OPPO ColorOS Workarounds

OPPO phones (ColorOS, e.g., RMX3700) have a non-standard Surface lifecycle:

```
Normal Android:   create → resume → (surface ready)
OPPO ColorOS:     create → resume → (surface destroyed) → (surface created) → pause
```

The system destroys the GL surface when the screen turns off, then creates it again — but pauses the Activity *before* the new surface is ready, causing SDL to deadlock.

**Fixes applied** (see [changelog.md](changelog.md) for details):

| File | Fix |
|------|-----|
| `SDLSurface.java` | Skip native pause if surface was created <500ms ago (transient destroy/recreate) |
| `ChocolateDoom.java` | Call `handleNativeState(RESUMED)` in `onWindowFocusChanged()` |
| `ChocolateDoom.java` | Skip `setOrientationBis()` entirely (triggers unnecessary surface destroy) |

### 5. Key Injection for Y Button

**Decision**: Use `SDLActivity.onNativeKeyDown(KEYCODE_Y)` instead of `commitText("y")`.

**Rationale**:
- `commitText` → `SDL_TEXTINPUT` event (DOOM ignores for quit confirm)
- `onNativeKeyDown` → `SDL_KEYDOWN` event with `keysym.sym = 'y'` (DOOM checks `ev->type == ev_keydown`)

The Android `KEYCODE_Y` (53) maps to `SDL_SCANCODE_Y` via the `Android_Keycodes[]` table in `SDL_androidkeyboard.c`.

## Native Build Pipeline

```
SDL2 src (2.30.0)          SDL2_mixer src (2.6.3)       Chocolate Doom src (3.1.1)
      │                            │                              │
      ▼                            ▼                              ▼
  cmake -DANDROID            ./configure --host=aarch64      cmake -DANDROID
  -DSDL_SHARED=OFF           -enable-static                  -fPIC
      │                            │                              │
      ▼                            ▼                              ▼
  libSDL2.a                 libSDL2_mixer.a               doom/heretic/.../*.o
      │                            │                              │
      └────────────────────────────┼──────────────────────────────┘
                                   │
                        aarch64-clang -shared
                        -Wl,--whole-archive ... -Wl,--no-whole-archive
                                   │
                                   ▼
                            libmain.so (≈10 MB)
```

## Touch Control Design

The `TouchControls` class extends `View` with `setAlpha(0.5f)` for transparency.

### Layout Strategy

```
┌─────────────────────────────────────────┐
│  Menu ☰                     Y button   │  ← Top row (always visible)
│                                         │
│                                         │
│              GAME AREA                  │  ← SDL renders here
│                                         │
│                                         │
│  Map 🗺️                     Fire 🔫   │  ← Right column
│                              Use 🚪    │
│  D-PAD ▲                     Run 🏃    │
│  ◄ ● ►          Strafe ➡    Enter ↵  │  ← Bottom row
│  ▼                                     │
└─────────────────────────────────────────┘
```

### Button Behavior

| Type | Buttons | Press | Release |
|------|---------|-------|---------|
| **Tap** | Fire, Use, Map, Strafe, Enter, Menu, Y | `keyDown` → auto `keyUp` after 80ms | — |
| **Hold** | Run | `keyDown` on press | `keyUp` on release |
| **Stateful** | D-pad | `keyDown` on enter zone | `keyUp` on exit / pointer up |

### Finger Tracking

Each pointer (finger) is tracked by `pointerId`. The `dpadPointerId` field stores which finger controls the D-pad. Action buttons store their `pressed` state + `pointerId` so `ACTION_UP` can find and release the correct button.

### Y Button — Special Handling

The quit confirmation in DOOM checks for `SDL_KEYDOWN` events with `keysym.sym == 'y'`. Early experiments with `commitText()` failed because it produces `SDL_TEXTINPUT` events. The fix uses `onNativeKeyDown(53)` (`KEYCODE_Y`) which correctly generates a keydown event.
