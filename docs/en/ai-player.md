# On-Device AI Player — Technical Specification

> [中文版 (Chinese)](../zh/ai-player.md)
>
> **📘 See also**: [AI Player Options Deep Dive](ai-player-options.md) — full comparison of DQN, PPO, DreamerV3, VLMs, imitation learning & more.

## Problem Statement

Run a local AI model on an Android phone that can play DOOM autonomously: observe the screen, decide actions, execute them via virtual key injection, and improve over time through self-play.

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    AI Agent Process                      │
│                                                         │
│  ┌──────────────┐   ┌──────────┐   ┌──────────────────┐│
│  │ Frame Grabber│ → │ State    │ → │  Inference       ││
│  │ (120×68×1)   │   │ Encoder  │   │  Engine (TFLite) ││
│  └──────────────┘   │ (flatvec)│   │  2-5 MB model    ││
│                     └──────────┘   └────────┬─────────┘│
│                                             │          │
│  ┌──────────────┐              ┌────────────▼────────┐  │
│  │ Reward Calc  │←─────────────│ Action Selection    │  │
│  │ (health/     │  game state  │ (ε-greedy / argmax) │  │
│  │  kills/items)│              └────────┬───────────┘  │
│  └──────────────┘                       │              │
│                                         ▼              │
│                              ┌──────────────────┐      │
│                              │ Key Injector     │      │
│                              │ (SDL native keys)│      │
│                              └──────────────────┘      │
└─────────────────────────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────┐
│              Chocolate Doom Engine (Native)              │
│  SDL_SendKeyboardKey → DOOM event loop → next frame     │
└─────────────────────────────────────────────────────────┘
```

## Model Selection

### Comparison

| Model | Size | Inference | Training | Feasibility |
|-------|------|-----------|----------|-------------|
| **DQN (2 conv + 2 fc)** | ~2 MB | <5ms (GPU) | Offline | ✅ **Recommended** |
| DRQN (DQN + LSTM 256) | ~5 MB | <10ms | Offline | ✅ Good for sequences |
| PPO (actor-critic) | ~8 MB | <15ms | Both | ⚠️ Complex |
| Tiny Llama 1.1B | ~2 GB | 500ms+ | No | ❌ Too big/slow |
| SmolLM2 135M | ~270 MB | 200ms+ | No | ❌ Too slow for real-time |

### Recommendation: DQN (Deep Q-Network)

```
Input: 4 stacked frames (120×68×1) → captures motion
CNN:  Conv(32, 8×8, stride=4) → ReLU
      Conv(64, 4×4, stride=2) → ReLU
      Conv(64, 3×3, stride=1) → ReLU
      Flatten → 3136 units
FC:   Dense(512) → ReLU
      Dense(actions) → Q-values per action
```

**Why DQN over LLM**:
- LLMs process text tokens sequentially, requiring 100+ forward passes per frame → 5+ seconds just to "read" the screen
- DQN processes the entire frame in a single forward pass → <5ms
- DOOM is a visual/spatial game, not a language task
- ViZDoom benchmarks prove DQN works for DOOM

### Framework: TensorFlow Lite

| Aspect | Decision |
|--------|----------|
| Training | PyTorch/TF on PC (ViZDoom + Freedoom WADs) |
| Export | PyTorch → ONNX → TFLite |
| Inference | TFLite GPU Delegate (OpenGL ES 3.1) |
| On-device RL | TFLite experimental training (Q-learning updates) |

## Input Pipeline

### Screen Capture

```java
// Option A: SDLSurface pixel readback (fast, no permission needed)
Bitmap bitmap = Bitmap.createBitmap(480, 270, Bitmap.Config.ARGB_8888);
mSurface.capture(bitmap); // NOT available — SDLSurface is GLSurfaceView

// Option B: PixelCopy from SurfaceView (API 24+)
PixelCopy.request(mSurface, bitmap, callback, handler);

// Option C: Read framebuffer via native code (fastest)
// JNI: glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
// Convert RGBA → grayscale → downsample to 120×68
```

**Recommendation**: Option C (native `glReadPixels`) — zero-copy, no Android permissions, synchronous with render thread.

### Game State Extraction

To provide the AI with health/ammo/kills data, we need to read DOOM's internal player structure. This requires a **JNI hook** into the native memory.

```c
// DOOM player_t struct (from d_player.h)
typedef struct {
    int health;         // offset 0x00: 0-100 (200 with soulsphere)
    int armorpoints;    // offset varies
    int armortype;
    int powers[NUMPOWERS];  // invulnerability, berserk, etc.
    weaponinfo_t readyweapon;
    int ammo[NUMAMMO];      // bullet, shell, rocket, cell
    int attackdown;
    int usedown;
    int cheats;             // CF_GODMODE, CF_NOCLIP, etc.
    int killcount;
    int itemcount;
    int secretcount;
} player_t;

// Access via global pointer
extern player_t* players; // consoleplayer = &players[displayplayer]
```

**Implementation**: Export player struct offsets via a native JNI function `getGameState()`, called from Java every N frames.

## Action Space

### Discrete Actions (17 total)

```java
enum Action {
    // Movement (7)
    NO_MOVE, MOVE_FORWARD, MOVE_BACKWARD,
    TURN_LEFT, TURN_RIGHT, STRAFE_LEFT, STRAFE_RIGHT,

    // Modifiers (3)
    SPEED_WALK, SPEED_RUN,
    FIRE_NO, FIRE_YES,

    // World interaction (2)
    USE_NO, USE_YES,

    // Weapon selection (4) — simplified from 9 weapons
    WEAPON_FIST, WEAPON_PISTOL, WEAPON_SHOTGUN,
    WEAPON_BFG
}
```

**Effective combinations**: 7 × 2 × 2 × 2 × 4 = 224 (pruned from full 1008 by merging weapon slots)

### Frame Stacking

DQN uses 4 stacked grayscale frames (120×68×4) to capture motion direction and speed. This is the standard Atari DQN approach (Mnih et al., 2015).

### Frame Skip

AI acts every 4 game frames (action repeated 4 times). DOOM runs at 35 fps, so AI decision rate = 8.75 Hz. This reduces inference load while maintaining responsiveness.

## Reward Function

```python
def calculate_reward(prev_state, curr_state, action, alive):
    reward = 0.0

    # Survival
    reward += 0.01  # small positive per frame alive

    # Health delta: incentivize avoiding damage
    health_delta = curr_state.health - prev_state.health
    reward += health_delta * 0.02

    # Kills
    kill_delta = curr_state.kills - prev_state.kills
    reward += kill_delta * 1.0

    # Item collection
    item_delta = curr_state.items - prev_state.items
    reward += item_delta * 0.1

    # Secret discovery
    secret_delta = curr_state.secrets - prev_state.secrets
    reward += secret_delta * 0.5

    # Death penalty
    if curr_state.health <= 0:
        reward -= 5.0

    # Ammo efficiency (discourage wasteful shooting)
    if action == FIRE_YES and kill_delta == 0:
        reward -= 0.01

    return reward
```

## Training Strategy

### Phase 1: Offline Training (PC — 3-5 days)

```
┌──────────────────────────────────────────────────────┐
│  Environment: ViZDoom + Freedoom WADs                │
│  Training: DQN with Double DQN + Dueling architecture│
│  Episodes: 500K                                      │
│  Replay Buffer: 100K transitions                     │
│  Epsilon: 1.0 → 0.1 (linear decay over 100K steps)  │
│  Target network update: every 1000 steps             │
│  Batch size: 32                                      │
│  Learning rate: 1e-4 (Adam)                         │
│  GPU: NVIDIA (CUDA) for training                     │
│  Output: PyTorch model → ONNX → TFLite (.tflite)     │
└──────────────────────────────────────────────────────┘
```

### Phase 2: On-Device Inference (Android)

```java
// Load model
Interpreter tflite = new Interpreter(modelFile, options);
tflite.setUseNNAPI(true);  // Android Neural Networks API

// Inference loop
float[] input = new float[120 * 68 * 4];  // stacked frames
float[][] output = new float[1][224];     // Q-values
tflite.run(input, output);

// Action selection: ε-greedy (ε = 0.05 for exploration)
int action = Math.random() < epsilon
    ? randomAction()
    : argmax(output[0]);
```

### Phase 3: On-Device Fine-Tuning (Self-Evolution)

On-device RL using Double DQN updates:

```java
// Store transition
replayBuffer.add(state, action, reward, nextState, done);

// Update every 4 actions
if (stepCount % 4 == 0 && replayBuffer.size() >= BATCH_SIZE) {
    // Sample batch
    Transition[] batch = replayBuffer.sample(BATCH_SIZE);

    // Compute target Q-values
    for (Transition t : batch) {
        if (t.done) {
            targetQ[t.action] = t.reward;
        } else {
            float maxNextQ = max(tflite.run(t.nextState));
            targetQ[t.action] = t.reward + GAMMA * maxNextQ;
        }
    }

    // Gradient update via TFLite training API
    tflite.train(stateBatch, targetQBatch);
}
```

**Constraints**:
- Replay buffer: last 10,000 transitions (fits in ~50 MB)
- Update frequency: every 4 actions (stable learning)
- Gamma: 0.99
- Only fine-tune, don't train from scratch on device

## Performance Budget

| Metric | Budget | Reality |
|--------|--------|---------|
| Inference time | <10ms | ~3-5ms (TFLite GPU) |
| Frame capture time | <5ms | ~2ms (glReadPixels) |
| State extraction | <1ms | ~0.5ms (native pointer read) |
| Total per decision | <20ms | ~8ms |
| Battery drain | <5% / hour | ~3% / hour (estimated) |
| Model size | <10 MB | ~2-5 MB (.tflite) |
| RAM usage | <100 MB | ~60 MB (replay buffer + model) |

## File Structure

```
app/src/main/
├── java/com/chocolate/doom/
│   ├── ai/
│   │   ├── AIPlayer.java          # Main AI controller (enable/disable, frame loop)
│   │   ├── AIOverlay.java          # Debug visual overlay (Q-values, state)
│   │   ├── FrameGrabber.java       # Screen capture (native glReadPixels)
│   │   ├── GameStateExtractor.java # JNI wrapper for DOOM player struct
│   │   ├── ActionMapper.java       # Action enum → SDL key sequences
│   │   ├── DQNInference.java       # TFLite model loading + inference
│   │   ├── DQNTrainer.java         # On-device RL (replay buffer + updates)
│   │   └── RewardCalculator.java   # Reward function from game state delta
│   └── cpp/
│       ├── frame_grabber.cpp       # glReadPixels native code
│       ├── game_state.cpp          # DOOM player struct reader
│       └── CMakeLists.txt
├── assets/
│   └── models/
│       └── doom_dqn.tflite         # Pre-trained model (~3 MB)
└── res/
    └── values/
        └── strings.xml
```

## Implementation Phases

### Phase 1: Foundation (Week 1)
- [ ] JNI native code: `glReadPixels` frame capture
- [ ] JNI native code: game state extraction (player struct)
- [ ] Java wrappers: `FrameGrabber`, `GameStateExtractor`
- [ ] Verify: capture produces correct 120×68 grayscale frames
- [ ] Verify: state extraction reads correct health/ammo/kills values

### Phase 2: Model Training (Week 2 — on PC)
- [ ] Set up ViZDoom training environment with Freedoom WAD
- [ ] Implement DQN training loop (Python/PyTorch)
- [ ] Train for 500K episodes
- [ ] Export to ONNX → TFLite
- [ ] Test model on PC with Freedoom

### Phase 3: Android Inference (Week 2-3)
- [ ] `DQNInference.java`: load TFLite model, run inference
- [ ] `ActionMapper.java`: action → key sequence injection
- [ ] `AIPlayer.java`: main loop (capture → infer → act → repeat)
- [ ] `AIOverlay.java`: debug visualization
- [ ] Integration test: AI controls DOOM on device

### Phase 4: Self-Evolution (Week 3-4)
- [ ] `ReplayBuffer.java`: ring buffer for transitions
- [ ] `DQNTrainer.java`: on-device Q-learning updates
- [ ] Meta-controller: toggle exploration/exploitation
- [ ] Persist model weights across app sessions
- [ ] Benchmark: improvement over 1000 episodes of on-device play

## Risks & Mitigations

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|------------|
| TFLite training API not mature enough | Medium | High | Pre-train on PC, inference-only on device first |
| Game state offsets differ per DOOM version | Medium | Medium | Validate with known cheats (IDDQD → health=100) |
| Model too large for APK (100 MB limit) | Low | Medium | TFLite quantization → 2-3 MB |
| Frame capture too slow on low-end GPU | Low | High | Skip frames, reduce resolution to 80×45 |
| AI gets stuck (local minima) | High | Low | ε-greedy always allows 5% random exploration |
| Battery drain concerns | Medium | Medium | Throttle to 5 fps, suspend AI when app backgrounded |

## References

- Mnih et al. (2015) — "Human-level control through deep reinforcement learning" (Nature)
- ViZDoom: https://github.com/Farama-Foundation/ViZDoom
- TF-Agents: https://github.com/tensorflow/agents
- TFLite GPU Delegate: https://www.tensorflow.org/lite/performance/gpu
- Chocolate Doom player struct: `src/doom/d_player.h`
