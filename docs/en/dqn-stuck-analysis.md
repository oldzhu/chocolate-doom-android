# DQN Stuck & No-Progress Analysis

> [中文版 (Chinese)](../zh/dqn-stuck-analysis.md)

## Problem Statement

The DQN agent stops making progress after a while — it either stands still (NOOP), walks into walls, or fires aimlessly. Despite the JNI game-state bridge (v2.x) providing real DOOM engine data (health, ammo, kills, position), the agent does not learn effective combat or navigation behavior.

**Root cause**: Five independent issues in the training pipeline, reward function, and stuck-detection mechanism. None are fatal alone, but together they prevent learning.

---

## Architecture Recap

```
DOOM Engine (players[consoleplayer])
       ↓ JNI (22 functions)
   GameState.java
       ↓ every 114ms (~8.75 Hz)
   AIController.extractState() → 17 features → DQNPlayer
       ↓
   chooseAction() → 14 discrete actions → executeAction()
       ↓
   SDLActivity.onNativeKeyDown() → DOOM engine
```

| Component | File | Lines |
|-----------|------|-------|
| NeuralNet | `ai/NeuralNet.java` | 270 |
| DQNPlayer | `ai/DQNPlayer.java` | 241 |
| AIController | `ai/AIController.java` | 372 |
| GameState | `GameState.java` | 57 |

---

## Issue #1: Sample-by-Sample Training (Not Mini-Batch)

### What's wrong

```java
// DQNPlayer.java:130-158 — current code
for (int b = 0; b < batchSize; b++) {
    int idx = rng.nextInt(replaySize);
    // ... compute Bellman target ...
    onlineNet.train(state, targetVec, mask);  // ← updates weights IMMEDIATELY
}
```

Each of the 32 sampled transitions calls `train()` which does forward → backward → **weight update** in one shot. The weight update from sample #32 overwrites sample #1's work. The effective batch size is **1**, not 32.

With batch_size=1, gradient noise is extreme — the loss surface is erratic and the optimizer never settles on a good policy.

### Why it matters

True mini-batch SGD averages gradients across the batch before applying, canceling noise and producing a stable update direction. The current approach effectively uses online SGD with a single random sample per training step — the worst possible configuration for this environment.

### Fix

Add gradient accumulation to NeuralNet:
1. `zeroGradients()` — reset accumulators
2. `backward(target, mask)` — compute gradients from last forward, add to accumulators
3. `applyGradients(batchSize)` — apply accumulated gradients / batchSize with momentum

Then in DQNPlayer.step():
```java
onlineNet.zeroGradients();
for (int b = 0; b < batchSize; b++) {
    // ... sample, compute target ...
    onlineNet.forward(state);
    onlineNet.backward(targetVec, mask);  // accumulate only
}
onlineNet.applyGradients(batchSize);  // apply once
```

---

## Issue #2: Not True Double DQN

### What's wrong

```java
// DQNPlayer.java:144-146 — current code
float[] nextQ = targetNet.forward(nextState);
targetQ = reward + gamma * max(nextQ);
```

This is **standard DQN**: the target network both selects the action (via argmax) AND evaluates its Q-value. This causes **overestimation bias** — the max operator consistently picks the highest Q-value, which is more likely to be an overestimate than an underestimate.

### What Double DQN should do

```java
// Correct Double DQN
int bestAction = argmax(onlineNet.forward(nextState));  // online selects action
float[] nextQ_target = targetNet.forward(nextState);     // target evaluates it
targetQ = reward + gamma * nextQ_target[bestAction];     // unbiased estimate
```

### Why it matters in DOOM

Kills and secrets are rare (sparse reward). When the agent accidentally gets a kill (say once every 500 steps), standard DQN can assign an inflated Q-value to whatever random action sequence preceded it. Double DQN's decorrelation mitigates this: the online network may overestimate action A, but the target network gives a lower (more realistic) value for A, producing a conservative target.

### Fix

Change the training loop in `DQNPlayer.java` to use onlineNet for action selection and targetNet for evaluation.

---

## Issue #3: Stuck Detection Triggers on All Actions

### What's wrong

```java
// AIController.java:244-253 — current code
int currX = GameState.getPlayerX();
int currY = GameState.getPlayerY();
if (currX == prevX && currY == prevY) {
    stuckCounter++;  // ← increments even on FIRE, USE, WEAPON switch, NOOP
} else {
    stuckCounter = 0;
}
```

Every action that doesn't change map position increments the counter — including FIRE, USE, weapon switches, and NOOP. This means:

- The agent fires 5 times at an enemy → counter hits threshold → **force-random-turn, interrupting combat**
- The agent switches weapons → same problem
- The forced recovery fires 4 random turn/strafe actions — but if those also don't change position (e.g., turning in place in a corridor where X/Y don't change until movement), the counter stays high

### Fix

Only increment the stuck counter when the agent chose a **movement action** (FWD, BACK, LEFT, RIGHT, STRAFEL, STRAFER — actions 1 through 6) and the position didn't change. Fire, use, weapon switch, and NOOP should NOT increment the counter.

Additionally, reset the counter when ANY position change occurs (not just from movement actions) — if the agent somehow moves without the movement actions (rare but possible via door opening etc.), the counter should still reset.

---

## Issue #4: Unbalanced Reward Function

### Current rewards

| Event | Reward | Frequency | Net per second |
|-------|--------|-----------|----------------|
| Alive | +0.01/step | every step (~8.75 Hz) | +0.0875/s |
| Kill | +1.0 | rare (~1/30s on good run) | +0.033/s |
| Secret | +1.0 | very rare | ~+0.005/s |
| Damage hit | -0.05 | frequent in combat | -0.05/hit |
| Death | -1.0 | per death | — |
| Stuck penalty | -0.0225 max | when stuck | — |
| NOOP | -0.005 | when idle | — |
| Fire w/o ammo | -0.05 | when blind-firing | — |

### The problem

The alive bonus (+0.0875/s) dominates. An agent that stands still and does nothing earns +0.0875 per second minus NOOP penalty (-0.005 × 8.75 = -0.04375/s), netting **+0.04375/s** for doing nothing. This is comparable to the kill reward (1 kill ≈ 23 seconds of standing still).

Meanwhile, exploring earns NO guaranteed reward — the agent may walk into enemies, take damage (-0.05/hit), and die (-1.0) before ever reaching a kill. The risk-reward ratio strongly favors passivity.

### Fix

1. **Reduce alive bonus**: `+0.01` → `+0.003` per step
2. **Increase kill reward**: stays +1.0 but add **hit confirmation** reward: fire-that-hits (infers from damagecount change) → `+0.02`
3. **Increase stuck penalty**: `-0.15 × stuckLevel` → `-0.20 × stuckLevel`
4. **Increase NOOP penalty**: `-0.005` → `-0.01` (making standing still net-negative)
5. **Add exploration bonus**: when position changes significantly (e.g., entering a new room), give a small reward

---

## Issue #5: Time Normalization Too Slow

### What's wrong

```java
// AIController.java:238
state[12] = Math.min(1.0f, elapsed / 600f);  // saturates after 10 minutes
```

The agent needs to survive **10 minutes** before the time feature saturates to 1.0. In the first minutes, this feature is nearly 0 for all states — providing zero useful signal to the network. Additionally, whenever the agent dies, time resets to 0, making the feature a source of noise rather than information.

### Fix

```java
state[12] = Math.min(1.0f, elapsed / 120f);  // saturates after 2 minutes
```

Two minutes is enough for a DOOM episode to develop meaningful combat. After 2 minutes the feature plateaus, which is fine — the agent has other features (kills, health) that carry temporal progress.

---

## Fix Priority & Implementation Order

| # | Issue | Difficulty | Impact | Risk |
|---|-------|-----------|--------|------|
| 2 | True Double DQN | Low | High | None |
| 3 | Stuck detection fix | Low | High | None |
| 5 | Time normalization | Low | Medium | None |
| 4 | Reward balance | Medium | High | Medium (needs tuning) |
| 1 | Mini-batch accumulation | Medium | High | Medium (refactors NeuralNet) |

**Order**: 2 → 3 → 5 → 4 → 1 (easiest fixes first, most impactful structural change last).

---

## Expected Outcomes After All Fixes

| Before | After |
|--------|-------|
| Agent stands still (NOOP dominates) | Agent moves more because NOOP is net-negative |
| Agent walks into walls, stays there forever | Stuck detection only triggers on true movement failure; forced recovery works |
| Agent fires blindly until ammo empty | Double DQN gives conservative Q-values for fire → agent fires when enemy likely present |
| Training unstable (batch=1 SGD noise) | True mini-batch SGD with gradient accumulation → stable convergence |
| Q-values overestimated by ~25% | Double DQN decorrelation → unbiased estimates |
| Time feature useless | Saturates at 2 minutes → provides progression signal |

The agent should begin learning to **move toward enemies, fire when appropriate, and avoid walls** within the first 10-20 minutes of training (vs. never making progress with the current code).
