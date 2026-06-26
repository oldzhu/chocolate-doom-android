# Touch Controls Specification

> [中文版 (Chinese)](../zh/controls.md)

## Layout

```
┌──────────────────────────────────────────────────┐
│  ☰ Menu            Y (Quit confirm)             │  ← y=120
│  (Escape, 55px)    (KEYCODE_Y, 55px)           │
│                                                  │
│                                                  │
│                GAME RENDER AREA                  │
│                                                  │
│                                                  │
│  🗺 Map           🔫 Fire                       │  ← y=h-430
│  (Tab, 65px)      (Ctrl, 100px)                │
│                                                  │
│                 🚪 Use                           │  ← y=h-230
│                 (Space, 65px)                   │
│                                                  │
│  ◄ ▲ ► ▼        ➡ Strafe    🏃 Run   ↵ Enter   │  ← y=h-100
│  (D-pad 140px)   (DPAD_R,65) (Shift,65)(Enter,65)
└──────────────────────────────────────────────────┘
```

## Button Specification

| # | Label | Key | Android Keycode | Size | Behavior | Purpose |
|---|-------|-----|-----------------|------|----------|---------|
| 1 | ▲ | DPAD_UP | 19 | 50px circle | Hold (stateful) | Move forward |
| 2 | ▼ | DPAD_DOWN | 20 | 50px circle | Hold (stateful) | Move backward |
| 3 | ◄ | DPAD_LEFT | 21 | 50px circle | Hold (stateful) | Turn left |
| 4 | ► | DPAD_RIGHT | 22 | 50px circle | Hold (stateful) | Turn right |
| 5 | 🔫 | CTRL_LEFT | 113 | 100px circle | Tap (80ms auto-release) | Fire weapon |
| 6 | 🚪 | SPACE | 62 | 65px circle | Tap (80ms auto-release) | Use / Open door |
| 7 | 🏃 | SHIFT_LEFT | 59 | 65px circle | Hold (press to hold) | Run / Sprint |
| 8 | 🗺️ | TAB | 61 | 65px circle | Tap (80ms auto-release) | Toggle auto-map |
| 9 | ➡ | DPAD_RIGHT | 22 | 65px circle | Tap (80ms auto-release) | Strafe right |
| 10 | ↵ | ENTER | 66 | 65px circle | Tap (80ms auto-release) | Select / Confirm |
| 11 | ☰ | ESCAPE | 111 | 55px circle | Tap (80ms auto-release) | Open menu |
| 12 | Y | KEYCODE_Y | 53 | 55px circle | Tap (80ms auto-release) | Yes / Quit confirm |

## Dimensions & Positioning

### Reference Frame
- `w` = screen width (pixels)
- `h` = screen height (pixels)
- `rightEdge = w - 30` (right margin)
- `bottomY = h - 30` (bottom margin)

### D-Pad
- Center: `(DPAD_RADIUS + 30, h - DPAD_RADIUS - 30)`
- Outer ring radius: `DPAD_RADIUS = 140px`
- Direction buttons: ±`DPAD_RADIUS * 0.55` from center (≈77px offset)
- Button radius: `50px`

### Action Buttons
| Button | X | Y (offset from bottomY) | Radius |
|--------|---|-------------------------|--------|
| Fire | `rightEdge - 110` | `-400` | 100px |
| Use | `rightEdge - 110` | `-200` | 65px |
| Run | `rightEdge - 110` | `-70` | 65px |
| Map | `rightEdge - 260` | `-400` | 65px |
| Strafe | `rightEdge - 300` | `-200` | 65px |
| Enter | `rightEdge - 300` | `-70` | 65px |
| Menu | `rightEdge - 100` | (y=120 from top) | 55px |
| Y | `rightEdge - 200` | (y=120 from top) | 55px |

## Touch Hit Detection

```java
private boolean isInCircle(float px, float py, float cx, float cy, float r) {
    float dx = px - cx;
    float dy = py - cy;
    return dx * dx + dy * dy <= r * r;
}
```

Hit test radius = button radius + 30px padding.

## Finger State Machine

```
                   ACTION_DOWN
  IDLE ────────────────────────────► PRESSED
   │                                    │
   │    ACTION_MOVE (finger moves)      │
   └────────────────────────────────────┘
   │                                    │
   │  ACTION_UP / ACTION_CANCEL         │
   ▼                                    ▼
  IDLE ◄──────────────────────────── IDLE
              (auto-release 80ms)
```

For D-pad:
- `updateDpad(fingerX, fingerY)` on every `ACTION_MOVE`
- Compares dx/dy against 40px threshold
- `updateDpadKey(oldState, newState, keyCode)` sends events only on state transitions

## Rendering

### Colors
| State | Fill | Text |
|-------|------|------|
| D-pad idle | `argb(100, 255,255,255)` | White |
| D-pad pressed | `argb(180, 255,255,255)` | Black |
| Action idle | `argb(100, 200,200,200)` | White |
| Action pressed | `argb(180, 255,200,100)` (orange) | White |

### Text Sizes
- D-pad arrows: `56px`
- Action labels: `44px`

### Transparency
- Overall View alpha: `0.5` (semi-transparent over game)

## Key Injection Pipeline

```
Touch → ACTION_DOWN → Handler → onNativeKeyDown(AndroidKeycode) → JNI
  → Android_OnKeyDown(keycode) [C]
  → TranslateKeycode(keycode) → Android_Keycodes[] lookup
  → SDL_SendKeyboardKey(SDL_PRESSED, scancode)
  → SDL_GetKeyFromScancode(scancode) [keyboard layout]
  → Push SDL_KEYDOWN event to queue (with scancode + keysym)
  → DOOM: SDL_PollEvent() → if (ev.type == ev_keydown && ev.data1 == 'y') quit
```

## Y Button — Why It Needs Special Handling

DOOM's quit handler (`m_menu.c:M_Responder`):

```c
if (ev->type == ev_keydown) {
    ch = ev->data1;  // keysym.sym
    if (ch == 'y' || ch == 'Y' || ch == KEY_ENTER) {
        // quit to DOS
    }
}
```

- `commitText("y")` → generates `SDL_TEXTINPUT` → event type != `ev_keydown` → ignored ❌
- `onNativeKeyDown(KEYCODE_Y=53)` → generates `SDL_KEYDOWN` with `keysym.sym = SDLK_y = 'y'` → matched ✅
