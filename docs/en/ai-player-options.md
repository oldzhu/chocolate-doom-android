# F3 AI Player — Options Deep Dive & Design Discussion

> [中文版 (Chinese)](../zh/ai-player-options.md)

Complete exploration of all viable approaches for an on-device AI DOOM player, with technical analysis, trade-offs, and feasibility assessment for Android phones.

---

## Table of Contents

1. [Core Question: PC Pre-train vs Pure On-Device RL](#core-question)
2. [Approach 1: DQN Family (Deep Q-Networks)](#approach-1-dqn)
3. [Approach 2: Policy Gradient Methods (PPO, A2C)](#approach-2-ppo)
4. [Approach 3: World Models (DreamerV3)](#approach-3-world-models)
5. [Approach 4: VLMs / VLAs as Game AI](#approach-4-vlms)
6. [Approach 5: Hybrid Architectures](#approach-5-hybrid)
7. [Approach 6: Imitation Learning](#approach-6-imitation)
8. [Approach 7: Monte Carlo Tree Search](#approach-7-mcts)
9. [Master Comparison](#master-comparison)
10. [Decision Matrix](#decision-matrix)
11. [Recommendation & Phased Roadmap](#recommendation)

---

## Core Question: PC Pre-train vs Pure On-Device RL {#core-question}

### Can we skip PC training and do pure on-device RL?

**Answer: Yes. It's not only possible — it's arguably more interesting.**

| Factor | Estimate |
|--------|----------|
| DOOM framerate | 35 fps |
| CNN-DQN inference | 3-5 ms (TFLite GPU delegate) |
| Training step (every 4 frames) | 25-30 ms (backpropagation) |
| Effective game+train speed | ~30 fps |
| Replay buffer | 50K transitions (~1 MB RAM) |
| Model size | 2-5 MB (.tflite quantized) |
| Frames to "not stupid" | 50K-100K |
| **Time to basic competence** | **~30-60 minutes** |
| Frames to "decent" | 500K-1M |
| **Time to decent player** | **~5-10 hours** |
| Frames to "good" | 5M+ |
| **Time to good player** | **~50+ hours** |

### On-Device RL Loop Architecture

```
┌─────────────────────────────────────────────────────┐
│  Game Loop (35 fps)                                  │
│                                                      │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐       │
│  │ glRead   │ →  │ CNN      │ →  │ Action   │       │
│  │ Pixels   │    │ Forward   │    │ (ε-greedy│       │
│  │ 120×68   │    │ 3-5ms     │    │ or argmax│       │
│  └──────────┘    └──────────┘    └────┬─────┘       │
│                                       │              │
│  ┌──────────┐    ┌──────────┐         ▼              │
│  │ Game     │ ←  │ SDL Key  │    DOOM Engine        │
│  │ State    │    │ Inject   │    (next frame)        │
│  │ (health, │    └──────────┘                        │
│  │  ammo,   │                                        │
│  │  kills)  │    Every 4 frames:                     │
│  └────┬─────┘    ┌──────────┐                        │
│       │          │ Replay   │                        │
│       └─────────→│ Buffer   │                        │
│                  │ (50K)    │                        │
│                  └────┬─────┘                        │
│                       │                              │
│                  ┌────▼─────┐                        │
│                  │ Q-Learn  │  batch=32              │
│                  │ Update   │  γ=0.99                │
│                  │ (25ms)   │  lr=1e-4               │
│                  └──────────┘                        │
│                                                      │
│  Target network sync: every 1000 steps               │
└─────────────────────────────────────────────────────┘
```

### Trade-off: Pre-train vs Pure On-Device

| | PC Pre-train + Deploy | Pure On-Device RL |
|---|---|---|
| **First playable** | Instant (pre-trained model) | ~30-60 minutes |
| **Quality ceiling** | Fixed at training time | Keeps improving indefinitely |
| **Battery impact** | Inference only (~3%/hr) | Training drains more (~8%/hr) |
| **User experience** | Works out of box | "Watch it learn" — more engaging |
| **Personalization** | Generic playstyle | Adapts to YOUR playstyle |
| **Development effort** | High (need ViZDoom setup, PC GPU) | Medium (all Java+TFLite) |
| **Model portability** | Works on any phone immediately | Each phone learns independently |
| **Cool factor** | 😎 | 🤯 |

**Recommendation**: On-device RL with optional pre-trained seed model. Best of both worlds.

---

## Approach 1: DQN Family {#approach-1-dqn}

DQN (Deep Q-Network) learns a function Q(s,a) that estimates the expected future reward of taking action `a` in state `s`. The agent picks the action with the highest Q-value.

### 1a. Vanilla DQN (Mnih et al., 2015)

```
Input:  4 stacked frames (120×68×4) → captures motion
        8 game state values (health, ammo×4, armor, kills, weapon)

CNN:    Conv(32, 8×8, stride=4) → ReLU
        Conv(64, 4×4, stride=2) → ReLU
        Conv(64, 3×3, stride=1) → ReLU
        Flatten(3136) + StateConcat(+8) → 3144 units
FC:     Dense(512) → ReLU
        Dense(224) → Q-values for all 224 actions

Size:   ~2 MB (quantized INT8)
Infer:  ~3-5 ms (TFLite GPU)
```

**Pros**: Simple, proven on Atari, small model
**Cons**: Overestimates Q-values, slow convergence

### 1b. Double DQN

Uses two networks: one to select action (online), one to evaluate it (target). Reduces overestimation bias.

**Pros**: More stable, better final performance
**Cons**: Slightly more memory (two models in RAM), identical inference speed

### 1c. Dueling DQN

Splits Q(s,a) into V(s) + A(s,a) — state value + action advantage. Learns which states are valuable regardless of action.

```
CNN → ┌─ V(s) stream  ─┐
      │                 ├→ Q(s,a) = V(s) + A(s,a) - mean(A)
      └─ A(s,a) stream ┘
```

**Pros**: Better when many actions have similar values, faster learning
**Cons**: Slightly larger network

### 1d. Rainbow DQN

Combines 6 improvements: Double DQN + Dueling + Prioritized Replay + Multi-step + Distributional + Noisy Nets.

**Pros**: State-of-the-art DQN performance
**Cons**: ~10 MB model, complex implementation, higher training cost

### 1e. DRQN (Deep Recurrent Q-Network)

Adds an LSTM layer to handle partial observability and temporal dependencies.

```
CNN → LSTM(256) → FC → Q-values
```

**Pros**: Remembers past frames (useful for dark rooms, doors opening)
**Cons**: ~5 MB model, <10ms inference, needs sequence batches for training

### DQN Variant Summary

| Variant | Size | Inference | Training | DOOM Suitability |
|---------|------|-----------|----------|-----------------|
| Vanilla DQN | 2 MB | 3-5ms | Simple | ⭐⭐⭐ Good baseline |
| Double DQN | 2.5 MB | 3-5ms | Simple | ⭐⭐⭐⭐ More stable |
| Dueling DQN | 3 MB | 4-6ms | Simple | ⭐⭐⭐⭐ Better for DOOM |
| Rainbow | 10 MB | 8-12ms | Complex | ⭐⭐⭐⭐⭐ Best, but heavy |
| DRQN | 5 MB | 8-10ms | Moderate | ⭐⭐⭐⭐ For maze levels |

---

## Approach 2: Policy Gradient Methods {#approach-2-ppo}

Instead of learning Q-values and picking the max, policy gradient methods directly learn a policy π(a|s) — a probability distribution over actions.

### 2a. PPO (Proximal Policy Optimization)

The industry standard for continuous control and game playing. Uses an actor (policy) + critic (value) architecture.

```
Actor:  CNN → FC(256) → FC(224) → softmax → action probabilities
Critic: CNN → FC(256) → FC(1)    → state value

Total:  ~8 MB
Infer:  ~10-15 ms (TFLite)
Train:  ~40-50 ms per update
```

**Pros**:
- More sample-efficient than DQN
- Handles stochastic policies naturally
- Can output action probabilities (useful for exploration)
- OpenAI Five used PPO for Dota 2

**Cons**:
- Needs on-policy data (can't reuse old experiences as easily)
- Larger model, slower inference
- More hyperparameters to tune
- GAE (Generalized Advantage Estimation) adds complexity

### 2b. A2C / A3C (Advantage Actor-Critic)

Simpler than PPO, uses advantage function A(s,a) = Q(s,a) - V(s) to reduce variance.

**Pros**: Lighter than PPO, good for parallel training
**Cons**: Less stable, A3C needs multiple threads

### PPO vs DQN for DOOM

| | DQN | PPO |
|---|---|---|
| Sample efficiency | Medium | **Higher** |
| Stability | Medium | **Higher** |
| Model size | **2-5 MB** | 8-12 MB |
| Inference speed | **3-5 ms** | 10-15 ms |
| Implementation complexity | **Low** | High |
| On-device training | **Easier** | Harder (needs on-policy buffer) |
| DOOM benchmarks | Good | **Better in VizDoom** |

---

## Approach 3: World Models (DreamerV3) {#approach-3-world-models}

Instead of learning Q-values or policies directly, world models learn a compressed internal representation of the game world, then plan or learn a policy within that representation.

### Architecture

```
┌─────────────────────────────────────────────────────┐
│  World Model                                         │
│                                                      │
│  Frame ──→ Encoder ──→ z_t (32-dim latent)          │
│                            │                         │
│              ┌─────────────▼──────────┐              │
│              │ RSSM (Recurrent SSM)   │              │
│              │ h_t = f(h_t-1, z_t-1, a_t-1)        │
│              │ Predicts: z_t, reward, continue      │
│              └─────────────┬──────────┘              │
│                            │                         │
│              ┌─────────────▼──────────┐              │
│              │ Actor-Critic           │              │
│              │ π(a|h_t)   V(h_t)      │              │
│              │ Trained IN latent space│              │
│              └────────────────────────┘              │
└─────────────────────────────────────────────────────┘
```

**DreamerV3 Stats**:
- Model size: ~20-50 MB (too big for phone)
- Inference: ~50-100ms per step (too slow for 35fps)
- Training: needs GPU (not phone-friendly)
- Works brilliantly for Minecraft, Atari

**Pros**:
- Learns rich world models — can "imagine" consequences
- Very sample-efficient
- Handles sparse rewards well

**Cons**:
- Too large and slow for current Android phones
- Training needs GPU
- Complex implementation (RSSM, latent dynamics)
- Overkill for DOOM's relatively simple state space

**Verdict**: ❌ Not viable for v1.0. Wait for Snapdragon NPU + smaller world models in 2-3 years.

---

## Approach 4: VLMs as Game AI {#approach-4-vlms}

### Why VLMs Can't Play Real-Time DOOM

| | CNN-DQN | SmolVLM 2B (on-device) | GPT-4V (cloud) |
|---|---|---|---|
| Model size | 2-5 MB | 2-4 GB | 100+ GB |
| Per-frame inference | 3-5 ms | 500-2000 ms | 1000-5000 ms |
| FPS possible | 30-35 | <1 | <1 |
| Runs on phone | ✅ | ⚠️ Snapdragon NPU only | ❌ Needs internet |
| Output | Action vector | Text → parse | Text → parse |
| Latency (RTT for cloud) | 0 | 0 | 50-300ms |

DOOM needs 28ms per frame for 35fps. Even the smallest on-device VLM is **100-400x too slow**.

### Where VLMs COULD Work (Hybrid Architecture, v2.0)

```
┌─────────────────────────────────────────────────┐
│  FAST LAYER (on-device CNN-DQN, every frame)    │
│  • Movement, aiming, shooting, dodging          │
│  • 30 fps, 2 MB model                           │
├─────────────────────────────────────────────────┤
│  SLOW LAYER (optional cloud/local VLM, 0.2 Hz)  │
│  • Strategic decisions every 2-5 seconds        │
│  • "3 cacodemons ahead → switch to plasma"      │
│  • "Secret wall texture → try to open it"        │
│  • "Health < 25 → find nearest medkit"          │
│  • "Need blue key → head east to door"          │
└─────────────────────────────────────────────────┘
```

**How the VLM sees the game** (every 5 seconds):
```
VLM Prompt:
"You are playing DOOM. Current frame shows:
- Health: 35%, Armor: 0%
- Ammo: 12 bullets, 8 shells, 3 rockets
- Weapon: Shotgun
- Enemies visible: 2 imps, 1 cacodemon
- Nearest item: Medkit (25m ahead)
What's the best strategy for the next 5 seconds?"

VLM Response:
"STRATEGY: Defensive retreat
ACTION: Move backward, switch to rocket launcher, 
        fire at cacodemon, then rush for medkit."
```

Then the DQN executes the strategy as a high-level directive.

**VLM Strategy Providers** (for v2.0):

| Model | Size | Inference | Quality | Notes |
|-------|------|-----------|---------|-------|
| GPT-4V (API) | Cloud | 1-3s | ⭐⭐⭐⭐⭐ | Best strategy, needs internet |
| Claude 3.5 Sonnet (API) | Cloud | 1-3s | ⭐⭐⭐⭐⭐ | Great for reasoning |
| Qwen2-VL 2B | 2 GB | 500-800ms | ⭐⭐⭐ | On-device with Snapdragon NPU |
| SmolVLM 256M | 500 MB | 200-400ms | ⭐⭐ | Borderline useful |
| Phi-3.5-Vision 4B | 8 GB | 1-2s | ⭐⭐⭐⭐ | Too large for phones |

### VLAs (Vision-Language-Action Models)

VLAs like RT-2 (Google) directly output robot actions from vision+language. For gaming:

**Pros**: Single model for perception → action, understands natural language commands
**Cons**: Even larger than VLMs (RT-2 is 55B params), trained for robotics not FPS, massive overkill

**Verdict**: ❌ Not for DOOM. VLAs are designed for physical robot manipulation, not real-time FPS.

### WAMs (World Action Models)

A proposed architecture combining world models with action prediction. Closest comparison is DreamerV3 with action-head.

**Verdict**: Same problems as DreamerV3 — too large, too slow for phones.

---

## Approach 5: Hybrid Architectures {#approach-5-hybrid}

### 5a. CNN-DQN + VLM Strategy Layer

```
┌──────────┐     ┌──────────┐
│ CNN-DQN  │ ←── │ VLM      │  (every 5s: high-level strategy)
│ (30fps)  │     │ (0.2 Hz) │
│ Movement │     │ Goals    │
│ Shooting │     │ Priorities│
│ Dodging  │     │ Route    │
└──────────┘     └──────────┘
```

**Pros**: Best of both worlds — real-time control + strategic reasoning
**Cons**: VLM layer adds complexity, needs internet or large on-device model

### 5b. CNN-DQN + Hardcoded Heuristics

```
┌──────────────────────────────────────┐
│  DQN Action Selection                │
│                                      │
│  ┌──────────┐    ┌──────────┐        │
│  │ Q-values │ →  │ Heuristic│ → Final│
│  │ (224)    │    │ Filter   │ Action │
│  └──────────┘    └──────────┘        │
│                      │               │
│  Rules:              │               │
│  • Health<20% →      │               │
│    prioritize medkit │               │
│  • No ammo →         │               │
│    force weapon swap │               │
│  • Near door →       │               │
│    boost USE action  │               │
└──────────────────────────────────────┘
```

**Pros**: Simple, no extra model, fixes DQN blind spots
**Cons**: Hand-coded rules are fragile, don't generalize

### 5c. Mixture of Experts (Tiny Specialists)

Train 4-5 tiny specialist DQNs, each 0.5MB, for different situations:

| Specialist | Trained For | When Active |
|-----------|-------------|-------------|
| Combat DQN | Shooting, dodging | Enemies visible |
| Exploration DQN | Finding secrets, items | No enemies |
| Boss DQN | Cyberdemon, Spider Mastermind | Boss fight |
| Navigation DQN | Pathfinding, key hunts | Maze levels |

A tiny gating network (50KB) selects which specialist to use each frame.

**Pros**: Each model is tiny and fast, could beat a single 2MB model
**Cons**: Complex training, switching can be jarring, needs scene classifier

---

## Approach 6: Imitation Learning {#approach-6-imitation}

Instead of RL, train the AI by watching humans play.

### 6a. Behavioral Cloning (BC)

```
Human plays → Record (frame, action) pairs → Supervised learning
                                            → CNN predicts human actions
```

**Data needs**: ~10,000 human demonstrations (~5 hours of play)
**Training**: One-shot supervised, no RL needed
**Model**: Same CNN architecture as DQN, just cross-entropy loss

**Pros**:
- Fast training (hours, not days)
- No need for reward function design
- Mimics human playstyle naturally
- Leverages your OWN gameplay data

**Cons**:
- Only as good as the human demonstrator
- Can't exceed human performance
- Distribution shift: AI gets into states human never visited → fails
- Doesn't handle novel situations well

### 6b. DAgger (Dataset Aggregation)

Iterative improvement: AI plays → human corrects mistakes → add to dataset → retrain → repeat.

**Pros**: Fixes distribution shift, AI learns from its own mistakes
**Cons**: Requires ongoing human supervision during training

### 6c. BC → RL Fine-tuning

Train with BC first (cheap, fast), then fine-tune with RL on-device.

```
Phase 1: Record 5 hours of your DOOM play
Phase 2: Train BC model from your data (1 hour on PC)
Phase 3: Deploy BC model to phone
Phase 4: On-device RL fine-tunes from the BC baseline
         → Starts competent, keeps improving
```

**Pros**: No "dumb phase" — AI starts as good as you, then gets better
**Cons**: Needs data collection step

---

## Approach 7: Monte Carlo Tree Search {#approach-7-mcts}

MCTS plans ahead by simulating possible futures and picking the most promising path. Used by AlphaGo, AlphaZero.

### How MCTS works for DOOM

```
For each possible action:
  Simulate next 4 frames with a simple forward model
  Score the resulting state (health, kills, position)
  Pick action with best simulated outcome

Tree depth: 4-8 frames into the future
Branches per node: top-5 actions (pruned from 224)
Total simulations per decision: ~500
```

**Pros**:
- No training needed (model-free)
- Explores systematically
- Good for tactical decisions (which weapon, which path)

**Cons**:
- Needs a fast forward model (simulates DOOM physics — hard to implement)
- 500 simulations × 3ms each = 1.5s per decision (too slow)
- DOOM has too many actions (224) to search effectively
- State space is huge and partially observable

**Verdict**: ❌ Not viable without a learned world model (back to DreamerV3)

---

## Master Comparison {#master-comparison}

| Approach | Model Size | Inference | On-Device Train | Time to Competent | Complexity | DOOM Fit |
|----------|-----------|-----------|----------------|-------------------|------------|----------|
| **Vanilla DQN** | 2 MB | 3-5ms | ✅ Easy | 1 hr | ⭐ | ⭐⭐⭐ |
| **Double DQN** | 2.5 MB | 3-5ms | ✅ Easy | 1 hr | ⭐ | ⭐⭐⭐⭐ |
| **Dueling DQN** | 3 MB | 4-6ms | ✅ Easy | 1 hr | ⭐⭐ | ⭐⭐⭐⭐ |
| **Double+Dueling** | 3.5 MB | 4-6ms | ✅ Easy | 1 hr | ⭐⭐ | ⭐⭐⭐⭐⭐ |
| **Rainbow DQN** | 10 MB | 8-12ms | ⚠️ Hard | 30 min | ⭐⭐⭐⭐ | ⭐⭐⭐ |
| **DRQN** | 5 MB | 8-10ms | ⚠️ Moderate | 1.5 hr | ⭐⭐⭐ | ⭐⭐⭐⭐ |
| **PPO** | 8 MB | 10-15ms | ⚠️ Hard | 45 min | ⭐⭐⭐ | ⭐⭐⭐⭐ |
| **DreamerV3** | 30 MB | 50-100ms | ❌ No | N/A | ⭐⭐⭐⭐⭐ | ❌ |
| **VLM (on-device)** | 2 GB | 500ms+ | ❌ No | N/A | ⭐⭐⭐ | ❌ |
| **VLM (cloud)** | Cloud | 1-5s | ❌ No | N/A | ⭐⭐ | ❌ |
| **BC (imitation)** | 2 MB | 3-5ms | ❌ No | Instant* | ⭐⭐ | ⭐⭐⭐ |
| **BC + RL** | 2 MB | 3-5ms | ✅ After BC | Instant* | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| **MCTS** | 0 | 1-5s | ❌ No | Instant | ⭐⭐⭐⭐⭐ | ❌ |
| **Hybrid DQN+VLM** | 2 MB + cloud | Fast+Slow | ✅ (DQN) | Varies | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| **MoE Specialists** | 2.5 MB | 5-10ms | ⚠️ Moderate | 2 hr | ⭐⭐⭐⭐ | ⭐⭐⭐ |

---

## Decision Matrix {#decision-matrix}

Scored 1-5 on criteria that matter for our project.

| Criteria | Weight | Best Approach |
|----------|--------|---------------|
| Runs on phone (CPU+GPU) | 🔴 Must | DQN family, BC, small PPO |
| Real-time (≥25 fps) | 🔴 Must | DQN family, BC |
| Self-evolves on-device | 🟡 High | DQN, PPO |
| Small model (<5 MB) | 🟡 High | DQN, BC |
| Starts competent | 🟢 Medium | BC+RL, Pre-trained DQN |
| Easy to implement | 🟢 Medium | Vanilla DQN, BC |
| Handles partial observability | 🟢 Medium | DRQN |
| Personalizes to user | 🟢 Medium | On-device RL |
| Strategic reasoning | 🟢 Low | Hybrid DQN+VLM |
| Research novelty | 🟢 Low | World Models, VLM |

---

## Recommendation & Phased Roadmap {#recommendation}

### Phase 1: Foundation (v1.0) — Double Dueling DQN, Pure On-Device

```
Model: Double DQN + Dueling architecture
Training: Pure on-device RL from scratch
Mode: "Training Mode" toggle — let it play overnight
Model save: Persist weights, resume from where it left off
```

**Why**: Best balance of simplicity, performance, and "cool factor" of watching it learn.

### Phase 2: Bootstrapping (v1.1) — BC + RL

```
Record human gameplay → Train BC model → Deploy as starting point
On-device RL fine-tunes from human baseline
```

**Why**: Eliminates the "dumb phase" — AI starts competent and improves from there.

### Phase 3: Strategy (v2.0) — Hybrid DQN + VLM

```
Local DQN for real-time combat
Cloud/local VLM for occasional strategic decisions (every 5s)
```

**Why**: Adds high-level intelligence without sacrificing real-time performance.

### Phase 4: Specialization (v2.5) — Mixture of Experts

```
Multiple tiny specialists for combat / exploration / bosses
Gating network selects specialist per frame
```

**Why**: Maximum performance per MB — each specialist is hyper-optimized for one scenario.

---

## What We Won't Do (For Now)

| Approach | Reason |
|----------|--------|
| DreamerV3 | Too large, too slow for phones. Revisit when Snapdragon NPU matures. |
| Pure VLM player | 100-400x too slow for real-time FPS. Strategy layer only in v2.0. |
| MCTS | Needs learned world model. Same problem as DreamerV3. |
| Rainbow DQN | Over-engineered for v1.0. Add components incrementally. |
| On-device VLM | 2GB model on a phone is impractical. Wait for model compression advances. |

---

## Related Documents

- [AI Player Technical Spec](ai-player.md) — Architecture, implementation phases, file structure
- [DQN Explained](../en/dqn-explained.md) — Plain-English DQN explanation
- [Controls Design](controls-design.md) — How the AI's actions map to touch/key inputs
