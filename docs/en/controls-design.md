# Touch Controls — Design & Specification

> [中文版 (Chinese)](../zh/controls-design.md)

Complete specification for the Chocolate Doom Android touch control system, including layout, key mappings, and the context-aware tap interaction model.

---

## Design Philosophy

Traditional Android DOOM ports use a D-pad + row of action buttons. This works but wastes screen space and forces the player to hunt for small buttons during combat.

Our design follows **modern mobile FPS conventions**:

1. **Left thumb → movement** (analog joystick, touch anywhere on left half)
2. **Right thumb → actions** (tap to shoot, buttons for special functions)
3. **Context-aware**: the same tap does different things depending on what's in front of you

---

## Control Layout

```
┌──────────────────────────────────────────────────┐
│  LEFT HALF               RIGHT HALF              │
│  ┌─────────────────┐    ┌──────────────────┐     │
│  │                 │    │  ☰ Menu     Y    │     │  ← top bar
│  │                 │    │                  │     │
│  │   ANALOG        │    │  🗺 Map         │     │  ← toggle map
│  │   JOYSTICK      │    │                  │     │
│  │   (move/aim)    │    │  ◀◀   ▶▶       │     │  ← weapon cycle
│  │                 │    │                  │     │
│  │                 │    │                  │     │
│  │                 │    │  ╔══════════════╗│     │
│  │                 │    │  ║              ║│     │
│  │                 │    │  ║  GAME AREA   ║│     │  ← tap = fire/use/select
│  │                 │    │  ║              ║│     │
│  │                 │    │  ║  (DOOM       ║│     │
│  │                 │    │  ║  viewport)   ║│     │
│  │                 │    │  ║              ║│     │
│  │                 │    │  ╚══════════════╝│     │
│  │                 │    │                  │     │
│  └─────────────────┘    │  ◀ Strafe  🏃 Run│     │  ← bottom bar
│                         │  ▶               │     │
│                         └──────────────────┘     │
└──────────────────────────────────────────────────┘
```

---

## Complete Control Table

| # | Control | Type | Key(s) | Game Action | Notes |
|---|---------|------|--------|-------------|-------|
| 1 | **Analog Joystick** | Drag (left half) | ↑↓←→ + Shift | Move, turn, auto-run | Floating joystick, 8-direction. Auto-run at 90%+ displacement. |
| 2 | **Game Tap** | Single tap (game area) | Ctrl + Space + Enter | Fire / Use / Select | Context-aware (see below) |
| 3 | 🏃 **Run** | Hold button | Shift (L) | Run/sprint | Hold to run. Does NOT auto-release. |
| 4 | 🗺️ **Map** | Tap button | Tab | Toggle automap | |
| 5 | ◀ **Strafe Left** | Tap button | DPAD_LEFT | Sidestep left | |
| 6 | ▶ **Strafe Right** | Tap button | DPAD_RIGHT | Sidestep right | |
| 7 | ☰ **Menu** | Tap button | Escape | Open/close menu | Triple-tap (3× in 1s) → God Mode |
| 8 | Y **Yes/Quit** | Tap button | Y (53) | Confirm / quit to DOS | KEYDOWN event (not TEXTINPUT) |
| 9 | ◀◀ **Prev Weapon** | Tap button | 1–7 cycle | Previous weapon slot | Wraps 1→7 |
| 10 | ▶▶ **Next Weapon** | Tap button | 1–7 cycle | Next weapon slot | Wraps 7→1 |
| 11 | ⚡ **God Mode** | Triple-tap ☰ | iddqd + idkfa | Invulnerability + all gear | Ammo refill 500ms, health refresh 10s |

---

## Context-Aware Tap — How It Works

When the player taps the game area (not on a button, not on the joystick zone), the system injects **three keys simultaneously**:

| Key | DOOM Binding | When it does something |
|-----|-------------|----------------------|
| **Ctrl** (113) | Fire weapon | Always — fires if weapon ready |
| **Space** (62) | Use / Open | Only when facing a door, switch, or usable wall |
| **Enter** (66) | Menu select | Only when a menu is open |

The DOOM engine itself decides which key is valid in the current context. The extra keys are silently ignored:

| Context | Ctrl | Space | Enter | Result |
|---------|------|-------|-------|--------|
| Facing enemy | ✅ Fire | — | — | **Shoots weapon** |
| Facing door/switch | ✅ Fire | ✅ Open | — | **Opens door** (+ fires at door) |
| In DOOM menu | — | — | ✅ Select | **Selects menu item** |
| "Press Y to quit" | — | — | — | Use Y button instead |

**Why this works**: DOOM processes all keyboard events in a single frame. Unrecognized keys (Ctrl in menu, Enter in-game) are silently consumed. No state tracking needed — the game engine IS the context detector.

---

## Multi-Touch Support

The control system supports two simultaneous touches:

| Finger | Action | Example |
|--------|--------|---------|
| **Left thumb** | Moving via joystick | Strafe-dodging while aiming |
| **Right thumb** | Tapping to fire | Shooting while moving |

Each pointer (finger) is tracked independently. The joystick claims the first pointer on the left half. Subsequent pointers on the game area trigger taps. This enables the core FPS loop: **move + shoot simultaneously**.

---

## Button Auto-Release

Most buttons auto-release after **80ms** to behave as "taps" rather than "holds":

| Button | Auto-Release | Reason |
|--------|-------------|--------|
| 🏃 Run | ❌ No | Hold-to-sprint |
| All others | ✅ 80ms | Tap actions (shoot, open, toggle, etc.) |

---

## Key Mapping Reference

### Android → SDL → DOOM

| Android KeyCode | SDL Scancode | DOOM Action |
|----------------|-------------|-------------|
| 113 (CTRL_LEFT) | SDL_SCANCODE_LCTRL | Fire |
| 62 (SPACE) | SDL_SCANCODE_SPACE | Use / Open |
| 66 (ENTER) | SDL_SCANCODE_RETURN | Menu select |
| 59 (SHIFT_LEFT) | SDL_SCANCODE_LSHIFT | Run |
| 61 (TAB) | SDL_SCANCODE_TAB | Toggle map |
| 111 (ESCAPE) | SDL_SCANCODE_ESCAPE | Menu |
| 53 (Y) | SDL_SCANCODE_Y | Yes / Quit |
| 21 (DPAD_LEFT) | SDL_SCANCODE_LEFT | Turn left / Strafe left |
| 22 (DPAD_RIGHT) | SDL_SCANCODE_RIGHT | Turn right / Strafe right |
| 19 (DPAD_UP) | SDL_SCANCODE_UP | Move forward |
| 20 (DPAD_DOWN) | SDL_SCANCODE_DOWN | Move backward |
| 8–14 (1–7) | SDL_SCANCODE_1–7 | Weapon select |

### Cheat Character Mapping

| Char | Android KeyCode | Notes |
|------|----------------|-------|
| a | 29 | KEYCODE_A |
| d | 32 | KEYCODE_D |
| f | 33 | KEYCODE_F |
| i | 37 | KEYCODE_I |
| k | 38 | KEYCODE_K |
| q | 45 | KEYCODE_Q |

---

## Future Improvements

| # | Idea | Priority |
|---|------|----------|
| 1 | Auto-detect WAD (assets/ extraction on first launch) | High |
| 2 | Configurable button opacity | Low |
| 3 | Swipe-up/swipe-down for weapon cycle (instead of ◀◀/▶▶ buttons) | Medium |
| 4 | Gyroscope aim assist (tilt phone to fine-aim) | Low |
| 5 | Haptic feedback on fire / damage | Low |
| 6 | Save/load touch layout presets | Low |
