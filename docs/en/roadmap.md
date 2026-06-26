# Feature Roadmap — Pre v1.0 Release

> [中文版 (Chinese)](../zh/roadmap.md)

Three features to implement before the first GitHub Release.

---

## Feature 1: Touch-to-Move (Analog Joystick)

**Priority**: 🔴 High — core UX
**Complexity**: Medium
**Files affected**: `TouchControls.java` (+ new `AnalogJoystick.java`)

### Problem
The current D-pad (▲▼◄► buttons) is cumbersome on a touchscreen. Players must find and press small fixed buttons. Modern mobile shooters use a floating analog joystick — touch anywhere, drag to move.

### Design

#### Concept
```
┌──────────────────────────────────────────┐
│  LEFT HALF (joystick zone)   RIGHT HALF  │
│                                          │
│   ●  ← joystick appears at touch point  │
│  ╱ ╲                                     │
│ ╱   ╲    radius = 80px                  │
│╲     ╱   dead zone = 20px               │
│ ╲   ╱                                    │
│  ╲ ╱                                     │
│   ●  ← current drag position (thumb)    │
│                                          │
│  [Joystick]     🔫 🚪 🏃 🗺️ etc.       │
└──────────────────────────────────────────┘
```

#### Behavior
1. **Touch anywhere on left half** → joystick origin appears at touch point
2. **Drag** → joystick knob follows finger (clamped to max radius)
3. **Direction mapping** (8-way):
   - Up → DPAD_UP (forward)
   - Down → DPAD_DOWN (backward)
   - Left → DPAD_LEFT (turn left)
   - Right → DPAD_RIGHT (turn right)
   - Up-Left → DPAD_UP + DPAD_LEFT simultaneously
   - Up-Right → DPAD_UP + DPAD_RIGHT
   - Down-Left → DPAD_DOWN + DPAD_LEFT
   - Down-Right → DPAD_DOWN + DPAD_RIGHT
4. **Dead zone**: 20px radius from origin (no movement)
5. **Lift finger** → all keys released, joystick disappears
6. **Running**: If drag exceeds 90% of max radius → also press SHIFT (auto-run)

#### Implementation
```java
class AnalogJoystick {
    float originX, originY;   // touch-down point
    float knobX, knobY;       // current thumb position
    float maxRadius = 80f;    // max drag distance
    float deadZone = 20f;     // ignore small movements
    int pointerId = -1;       // tracking finger
    boolean active = false;
    
    void onTouchDown(float x, float y, int id) { ... }
    void onTouchMove(float x, float y) { ... }
    void onTouchUp(int id) { ... }
    void draw(Canvas canvas) { ... }
}
```

#### Keystates
| Direction | Keys Pressed |
|-----------|-------------|
| Up (dx small, dy < -deadZone) | DPAD_UP |
| Down (dx small, dy > deadZone) | DPAD_DOWN |
| Left (dy small, dx < -deadZone) | DPAD_LEFT |
| Right (dy small, dx > deadZone) | DPAD_RIGHT |
| Up-Left | DPAD_UP + DPAD_LEFT |
| Up-Right | DPAD_UP + DPAD_RIGHT |
| Down-Left | DPAD_DOWN + DPAD_LEFT |
| Down-Right | DPAD_DOWN + DPAD_RIGHT |
| Auto-run (radius > 90%) | + SHIFT_LEFT |

#### Configuration (user-adjustable later)
- `joystickEnabled`: boolean
- `joystickMaxRadius`: float (50-150px)
- `joystickDeadZone`: float (10-40px)
- `autoRunEnabled`: boolean
- `invertY`: boolean (some players prefer inverted)

---

## Feature 2: God Mode Cheat

**Priority**: 🟡 Medium — fun/testing feature
**Complexity**: Low
**Files affected**: `TouchControls.java`, `ChocolateDoom.java`

### Design

#### Activation
Press **☰ (Menu) 3 times within 1 second** → toggles god mode.

Alternative: Long-press **Y button for 2 seconds** → god mode.

#### Cheat Codes (Chocolate Doom built-in)
Chocolate Doom supports standard DOOM cheat codes via keyboard input:

| Cheat | Code | Effect |
|-------|------|--------|
| IDDQD | `i d d q d` | God mode (invulnerability) |
| IDKFA | `i d k f a` | All weapons + keys + full ammo + full armor |
| IDCLIP | `i d c l i p` | No clipping (walk through walls) |
| IDBEHOLD | various | Power-ups (berserk, invisibility, etc.) |

#### Implementation
When god mode is activated:
1. Inject `iddqd` + `idkfa` character sequence via keyboard events
2. Each character sent with 50ms delay (mimics typing speed)
3. Visual feedback: flash screen + show "GOD MODE ACTIVATED" overlay text for 2s

```java
void activateGodMode() {
    String cheat = "iddqdidkfa";
    for (int i = 0; i < cheat.length(); i++) {
        final char c = cheat.charAt(i);
        handler.postDelayed(() -> {
            SDLActivity.onNativeKeyDown(keyForChar(c));
            handler.postDelayed(() -> {
                SDLActivity.onNativeKeyUp(keyForChar(c));
            }, 50);
        }, i * 60);
    }
    showOverlayText("GOD MODE", 2000);
}
```

#### Tap Detection (3 taps in 1 second)
```java
long[] menuTapTimes = new long[3];
int tapIndex = 0;

void onMenuTap() {
    long now = System.currentTimeMillis();
    menuTapTimes[tapIndex % 3] = now;
    tapIndex++;
    
    // Check if last 3 taps within 1000ms
    if (tapIndex >= 3) {
        long span = menuTapTimes[2] - menuTapTimes[0];
        if (span < 1000) {
            activateGodMode();
            tapIndex = 0; // reset
        }
    }
}
```

---

## Feature 3: Local AI Player (On-Device)

**Priority**: 🟢 Low — ambitious, experimental
**Complexity**: Very High
**Files affected**: New native module + `AIOverlay.java` + model files

### Problem Statement
Run a small local AI model on the phone that can:
1. See the game screen
2. Understand the game state (health, ammo, enemies, map)
3. Decide what actions to take (move, shoot, use doors, switch weapons)
4. Execute those actions via virtual key injection
5. Learn and improve over time (self-evolving)

### Architecture

```
┌──────────────────────────────────────────────┐
│                  AI Layer                     │
│  ┌────────────┐  ┌──────────┐  ┌──────────┐ │
│  │ Screen Grab│→ │  Vision  │→ │  Policy  │ │
│  │ (120×68 px)│  │ Encoder  │  │ Network  │ │
│  └────────────┘  │ (CNN)    │  │ (LSTM)   │ │
│                  └──────────┘  └────┬─────┘ │
│                                     │       │
│  ┌──────────┐              ┌───────▼──────┐ │
│  │ Reward   │←─────────────│   Action     │ │
│  │ Signal   │  health/ammo │   Selection  │ │
│  │ (health, │  /kills etc  │ (key events) │ │
│  │  kills,  │              └──────┬───────┘ │
│  │  prog)   │                    │          │
│  └──────────┘              ┌─────▼──────┐   │
│                            │ SDL Key    │   │
│                            │ Injection  │   │
│                            └────────────┘   │
└──────────────────────────────────────────────┘
```

### Model Selection

| Option | Size | Speed on Phone | Quality |
|--------|------|---------------|---------|
| **Tiny Llama 1.1B** | ~2 GB | Too slow, too big | Overkill |
| **SmolLM2 135M** | ~270 MB | Slow (~1 fps) | Good for text, not vision |
| **Custom CNN+LSTM** | ~5-10 MB | Fast (~30 fps) | Good for game playing |
| **DQN (Deep Q-Network)** | ~2-5 MB | Fast (~30 fps) | Proven for DOOM (ViZDoom) |

**Recommendation**: Custom small CNN+LSTM or DQN, trained offline, then converted to ONNX or TFLite for on-device inference. Model size ~5-10 MB, inference < 10ms per frame.

### Input Encoding (What the AI "Sees")

```python
# Screen capture: 120×68 grayscale (DOOM's native resolution scaled down)
frame = capture_screen(0.25x)  # 120×68×1

# Game state (parsed from memory - requires native hook):
state = {
    'health': player.health,      # 0-200
    'armor': player.armor,        # 0-200
    'ammo': [bullet, shell, ...],  # 4 types
    'weapon': player.readyweapon,  # 0-8
    'keys': [blue, red, yellow],   # 3 bools
    'kills': player.killcount,
    'items': player.itemcount,
    'secrets': player.secretcount,
}
```

### Action Space (What the AI Can Do)

```
Discrete actions (multi-discrete):
- Movement: [none, forward, backward, turn_left, turn_right, strafe_left, strafe_right]
- Speed: [walk, run]
- Fire: [no, yes]
- Use: [no, yes]
- Weapon: [fist, pistol, shotgun, chaingun, rocket, plasma, BFG, chainsaw, supershotgun]
- Strafe modifier: [no, yes]
```

Total combinations: 7 × 2 × 2 × 2 × 9 × 2 = 1,008 possible actions

### Reward Function

```python
reward = (
    +0.01 * delta_health        # survive
    +1.0  * delta_kills          # kill enemies
    +0.1  * delta_items          # collect items
    +0.5  * delta_secrets        # find secrets
    -0.05                        # time penalty (encourage speed)
    +5.0  * level_completed      # major milestone
    -5.0  * player_died          # death penalty
)
```

### Training Strategy

**Phase 1: Offline Training (on PC)**
- Use ViZDoom or Chocolate Doom with Python bindings
- Train DQN/PPO for 1M+ episodes on Freedoom levels
- Export model to ONNX → convert to TFLite

**Phase 2: On-Device Inference**
- Load TFLite model in Android
- Run inference each frame (every ~33ms at 30fps)
- No training on device (only inference)

**Phase 3: On-Device Fine-Tuning (Self-Evolving)**
- Implement on-device RL using TensorFlow Lite's training APIs
- Small replay buffer (last 10,000 transitions)
- Q-learning updates every 4 frames
- Slow, gradual improvement

### Technical Challenges

| Challenge | Difficulty | Solution |
|-----------|------------|----------|
| Screen capture on Android | Medium | MediaProjection API or SurfaceView.getBitmap() |
| Game state extraction | Hard | Parse DOOM memory structures via JNI hook |
| Fast inference | Medium | TFLite GPU delegate (OpenGL ES) |
| On-device training | Very High | TFLite Model Maker or custom C++ RL loop |
| Battery drain | High | Throttle inference to 10fps, skip frames |
| Memory constraints | Medium | Gradients stored in C++ buffer, not Java heap |

### Phased Approach

| Phase | Deliverable | Timeline |
|-------|------------|----------|
| **P1** | Screen capture + state extraction | 2-3 days |
| **P2** | Offline-trained model running on device | 3-5 days |
| **P3** | Playable AI (basic movement + shooting) | 5-7 days |
| **P4** | On-device fine-tuning | 7-10 days |
| **P5** | Self-evolving across sessions | 10+ days |

### Recommendation
Feature 3 is a full research project. **For v1.0, implement a simpler version**: an offline-trained model that plays reasonably well, packaged with the APK. Self-evolving can be v2.0.

---

## Implementation Order

```
┌─────────────────────────────────────────────────┐
│  Week 1: Feature 1 (Touch-to-Move)             │
│  ▸ Design analog joystick class                │
│  ▸ Implement 8-direction touch mapping         │
│  ▸ Replace current D-pad (or offer both modes) │
│  ▸ Test on device                              │
├─────────────────────────────────────────────────┤
│  Week 2: Feature 2 (God Mode)                  │
│  ▸ Implement triple-tap detection              │
│  ▸ Implement cheat code injection              │
│  ▸ Add visual feedback overlay                 │
│  ▸ Test on device                              │
├─────────────────────────────────────────────────┤
│  Weeks 3-4: Feature 3 (AI Player — Phase 1)   │
│  ▸ Research + prototype screen capture         │
│  ▸ Extract game state from memory              │
│  ▸ Train initial model on PC                   │
│  ▸ Package model with APK                      │
│  ▸ Implement basic AI play loop                │
│  ▸ Test on device                              │
└─────────────────────────────────────────────────┘
```

---

## Next Steps

Shall we start with **Feature 1 (Touch-to-Move)**? I'll implement the analog joystick first, then we test on your OPPO phone.
