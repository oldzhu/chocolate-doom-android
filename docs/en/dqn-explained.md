# DQN — Deep Q-Network Explained

> [中文版 (Chinese)](../zh/dqn-explained.md)

## What is DQN?

**DQN = Deep Q-Network.** A technique that lets an AI learn to play games by trying millions of times — no human rules needed.

## Word-by-word breakdown

### Q = Quality

Every action in a game has a "quality score" — how good it is right now.

| Situation: facing an enemy in DOOM | |
|---|---|
| Action | Q score |
| 🔫 Shoot the enemy | **+8.3** ← best |
| 🏃 Move toward enemy | +2.1 |
| 😶 Stand still | -4.7 |
| 🏃 Run away | -1.2 |

Higher Q = better choice. The AI's job is to figure out these scores.

### Network = a small math function

Takes game pixels → runs some math → outputs Q scores:

```
[120×68 pixel image]
       ↓
  ┌─────────┐
  │  Math   │  (runs in <5 milliseconds)
  └─────────┘
       ↓
  Q(shoot)=8.3, Q(move)=2.1, Q(stand)=-4.7, ...
```

### Deep = multiple math layers

"Deep" just means the function has several layers of calculations, not just one. More layers = can learn more complex patterns.

## How DQN Learns

### The training loop

```
1. AI looks at game screen
2. AI picks an action (sometimes random, sometimes its best guess)
3. Game shows result — did health go up? Did something die?
4. AI gets a "reward" number:
     +0.01  every frame alive
     +1.0   killed an enemy
     +0.1   picked up an item
     -5.0   died
5. AI updates its Q scores: "in that situation, that action was +1.0 good"
6. Repeat millions of times
```

### Concrete example — what the AI actually learns

| Try # | AI does | Result | AI learns |
|-------|---------|--------|-----------|
| 1 | Stands still | Gets shot, dies, reward=-5.0 | "Standing still here = bad" |
| 2 | Walks forward | Hits wall, no change | "Wall = no progress" |
| 3 | Walks toward enemy, doesn't shoot | Gets shot, dies | "Need to shoot enemies" |
| … | … | … | … |
| 50,000 | Approaches enemy, shoots | Enemy dies! +1.0 | "Shooting enemies = good!" |
| 100,000 | Kills enemy, picks up health | +0.2 (health restored) | "Health packs = useful" |
| 500,000 | Clears room, finds key, opens door | Multiple rewards | "Keys → doors → progress" |

After **500,000+ games** (run on a PC, not your phone), the AI builds an internal "intuition" — a 3 MB file of numbers encoding: *"when I see this pattern, the best move is probably this."*

## The output: a 3 MB `.tflite` file

That file goes on your phone. No internet. No cloud. No GPU. Your phone's processor runs it in <5 milliseconds per frame:

```
Game running at 35 fps
      ↓
Every 4th frame: capture screen → run through TFLite model → pick best action → inject keys
      ↓
AI plays at ~8 decisions per second
```

## Why not ChatGPT / a Large Language Model?

| | DQN | ChatGPT-style (LLM) |
|---|---|---|
| Input | Raw pixels (120×68) | Text description of screen |
| Processing | 1 forward pass, <5ms | 100+ sequential steps, 5+ seconds |
| Speed | Real-time (30 fps capable) | NOT real-time (<1 fps) |
| Model size | 2-5 MB | 2,000+ MB |
| Training | Learns from gameplay | Learns from internet text |
| Runs on phone | ✅ Yes | ❌ No (too big, too slow) |

DOOM is a visual/spatial game. You don't need to "read" the screen with language — you need to recognize shapes (enemies, walls, items) and react fast. DQN does this naturally. An LLM would be like describing every frame in words, reading the description, then typing out instructions — far too slow.

## Self-Evolution (Phase 4)

Once the pre-trained model is on the phone, it can keep improving:

```
While you watch the AI play:
  ┌──────────────────────────────────────┐
  │ AI plays → gets reward → updates     │
  │ memory → next time on this level     │
  │ it's slightly faster / dies less     │
  └──────────────────────────────────────┘
```

Over hours/days, the AI slowly gets better at the specific levels YOU have the WAD for. No PC needed after the initial training.

## Key terms summary

| Term | Plain English |
|------|---------------|
| **DQN** | "Learn by trying millions of times" |
| **Q-value** | "How good is this action right now?" (higher = better) |
| **Reward** | A number: +1 for killing, -5 for dying |
| **Neural network** | A small math function (3 MB, 5 ms) |
| **Training** | Playing the game on PC 500K+ times to build the function |
| **Inference** | Running the function on your phone in real-time |
| **Self-evolution** | Phone keeps learning while you watch |
| **TFLite** | TensorFlow Lite — Google's tool for running AI on phones |
