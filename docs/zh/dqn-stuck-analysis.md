# DQN 卡死与无进展问题分析

> [English version](../en/dqn-stuck-analysis.md)

## 问题描述

DQN agent 运行一段时间后不再进步——要么站着不动（NOOP），要么撞墙，要么乱开枪。尽管 JNI 游戏状态桥接（v2.x）提供了真实 DOOM 引擎数据（血量、弹药、击杀、位置），agent 仍然学不会有效的战斗和导航行为。

**根因**：训练管线、奖励函数和 stuck 检测机制中的五个独立问题。单独每一个都不致命，但组合在一起阻止了学习。

---

## 架构回顾

```
DOOM 引擎 (players[consoleplayer])
       ↓ JNI (22个函数)
   GameState.java
       ↓ 每114ms (~8.75 Hz)
   AIController.extractState() → 17维特征 → DQNPlayer
       ↓
   chooseAction() → 14个离散动作 → executeAction()
       ↓
   SDLActivity.onNativeKeyDown() → DOOM 引擎
```

| 组件 | 文件 | 行数 |
|------|------|------|
| NeuralNet | `ai/NeuralNet.java` | 270 |
| DQNPlayer | `ai/DQNPlayer.java` | 241 |
| AIController | `ai/AIController.java` | 372 |
| GameState | `GameState.java` | 57 |

---

## 问题1：逐个样本训练（非真正的 Mini-Batch）

### 问题所在

```java
// DQNPlayer.java:130-158 — 当前代码
for (int b = 0; b < batchSize; b++) {
    int idx = rng.nextInt(replaySize);
    // ... 计算 Bellman target ...
    onlineNet.train(state, targetVec, mask);  // ← 立即更新权重！
}
```

采样的 32 条经验每条都调用 `train()`——它一次性完成 forward → backward → **权重更新**。第 32 号样本的权重更新会覆盖第 1 号样本的结果。实际有效 batch size 是 **1**，不是 32。

batch_size=1 时梯度噪声极大——损失曲面震荡剧烈，优化器永远无法收敛到好的策略。

### 为什么重要

真正的 mini-batch SGD 在应用更新前对 batch 内的梯度取平均，抵消噪声，产生稳定的更新方向。当前方法等价于每次用一个随机样本做 online SGD——这是该环境下最差的配置。

### 修复方案

为 NeuralNet 添加梯度累积：
1. `zeroGradients()` — 重置累积器
2. `backward(target, mask)` — 基于上次 forward 计算梯度，累加到累积器
3. `applyGradients(batchSize)` — 将累积梯度 / batchSize 后应用（带 momentum）

然后在 DQNPlayer.step() 中：
```java
onlineNet.zeroGradients();
for (int b = 0; b < batchSize; b++) {
    // ... 采样，计算 target ...
    onlineNet.forward(state);
    onlineNet.backward(targetVec, mask);  // 仅累积
}
onlineNet.applyGradients(batchSize);  // 一次性应用
```

---

## 问题2：不是真正的 Double DQN

### 问题所在

```java
// DQNPlayer.java:144-146 — 当前代码
float[] nextQ = targetNet.forward(nextState);
targetQ = reward + gamma * max(nextQ);
```

这是**标准 DQN**：target 网络既负责选动作（通过 argmax）又负责评估 Q 值。这会导致**高估偏差（overestimation bias）**——max 算子总是挑最高 Q 值，而被选中的更可能是被高估的动作而非真正最优的动作。

### 正确的 Double DQN

```java
// 正确的 Double DQN
int bestAction = argmax(onlineNet.forward(nextState));  // online 选动作
float[] nextQ_target = targetNet.forward(nextState);     // target 估值
targetQ = reward + gamma * nextQ_target[bestAction];     // 无偏估计
```

### 为什么在 DOOM 中重要

击杀和秘密是稀疏奖励（rare events）。当 agent 偶然杀敌（比如每 500 步一次），标准 DQN 可能给那之前的随机动作序列分配一个膨胀的 Q 值。Double DQN 的解耦减轻了这个问题：online 网络可能高估动作 A，但 target 网络给 A 一个较低（更真实）的值，产生保守的 target。

### 修复方案

修改 `DQNPlayer.java` 中的训练循环，使用 onlineNet 选动作，targetNet 估值。

---

## 问题3：Stuck 检测对所有动作都触发

### 问题所在

```java
// AIController.java:244-253 — 当前代码
int currX = GameState.getPlayerX();
int currY = GameState.getPlayerY();
if (currX == prevX && currY == prevY) {
    stuckCounter++;  // ← 连开火、使用、换武器、NOOP 都算！
} else {
    stuckCounter = 0;
}
```

任何不改变地图坐标的动作都会累加计数器——包括 FIRE、USE、换武器和 NOOP。这意味着：

- Agent 朝敌人连续开 5 枪 → 计数器超阈值 → **强制随机转身，打断战斗**
- Agent 换武器 → 同样问题
- 强制恢复执行 4 个随机转身/侧移动作——但如果这些动作也不改变位置（如在走廊里转身，X/Y 不变直到实际移动），计数器居高不下

### 修复方案

只在 agent 选择了**移动动作**（FWD、BACK、LEFT、RIGHT、STRAFEL、STRAFER——动作 1-6）且位置未变时才累加计数器。开火、使用、换武器和 NOOP 不应累加。

同时，任何位置变化都应重置计数器（不仅是移动动作导致的变化）——如果 agent 通过其他方式移动了（如开门等），计数器也应重置。

---

## 问题4：奖励函数不平衡

### 当前奖励

| 事件 | 奖励 | 频率 | 每秒净收益 |
|------|------|------|-----------|
| 存活 | +0.01/步 | 每步 (~8.75 Hz) | +0.0875/s |
| 击杀 | +1.0 | 稀有（好局约 1/30s） | +0.033/s |
| 秘密 | +1.0 | 极稀有 | ~+0.005/s |
| 受伤 | -0.05 | 战斗中频繁 | -0.05/次 |
| 死亡 | -1.0 | 每次死亡 | — |
| Stuck 惩罚 | 最大 -0.0225 | stuck 时 | — |
| NOOP | -0.005 | 空闲时 | — |
| 无弹药开火 | -0.05 | 瞎打时 | — |

### 问题

存活奖励（+0.0875/s）占主导。什么都不做的 agent 每秒获得 +0.0875 减去 NOOP 惩罚（-0.005 × 8.75 = -0.04375/s），净赚 **+0.04375/s**。这相当于 1 次击杀 ≈ 静止 23 秒。

而探索没有任何保证奖励——agent 可能撞上敌人、受伤（-0.05/次）、死亡（-1.0），还未必能杀到敌人。风险回报比严重偏向消极策略。

### 修复方案

1. **降低存活奖励**：`+0.01` → `+0.003` 每步
2. **保持击杀奖励**：仍是 +1.0，但添加**命中确认**奖励：开火命中（通过 damagecount 变化推断）→ `+0.02`
3. **增加 stuck 惩罚**：`-0.15 × stuckLevel` → `-0.20 × stuckLevel`
4. **增加 NOOP 惩罚**：`-0.005` → `-0.01`（使静止净收益为负）
5. **添加探索奖励**：位置变化较大时（如进入新房间），给予小额奖励

---

## 问题5：时间归一化太慢

### 问题所在

```java
// AIController.java:238
state[12] = Math.min(1.0f, elapsed / 600f);  // 10分钟才饱和
```

Agent 需要存活 **10 分钟**时间特征才饱和到 1.0。前几分钟里这个特征几乎所有状态下都接近 0——给网络提供的信息量为零。且每次死亡时间重置为 0，使该特征成为噪声源而非信息源。

### 修复方案

```java
state[12] = Math.min(1.0f, elapsed / 120f);  // 2分钟饱和
```

两分钟足以让一局 DOOM 发展出有意义的战斗。两分钟后特征到达平台期，这没问题——agent 有其他特征（击杀数、血量）来承载时间进展。

---

## 修复优先级与实施顺序

| # | 问题 | 难度 | 影响 | 风险 |
|---|------|------|------|------|
| 2 | 真正的 Double DQN | 低 | 高 | 无 |
| 3 | Stuck 检测修复 | 低 | 高 | 无 |
| 5 | 时间归一化 | 低 | 中 | 无 |
| 4 | 奖励平衡 | 中 | 高 | 中（需调参） |
| 1 | Mini-batch 梯度累积 | 中 | 高 | 中（重构 NeuralNet） |

**顺序**：2 → 3 → 5 → 4 → 1（最简单的最先，结构性改动最后）。

---

## 全部修复后的预期效果

| 修复前 | 修复后 |
|--------|--------|
| Agent 站着不动（NOOP 为主） | Agent 更愿意移动，因为 NOOP 净收益为负 |
| Agent 撞墙后永远卡住 | Stuck 检测仅在真正移动失败时触发；强制恢复有效 |
| Agent 盲目开火直到弹药耗尽 | Double DQN 给开火保守 Q 值 → agent 在敌人可能出现时才开火 |
| 训练不稳定（batch=1 SGD 噪声） | 真正的 mini-batch SGD 梯度累积 → 稳定收敛 |
| Q 值被高估约 25% | Double DQN 解耦 → 无偏估计 |
| 时间特征无效 | 2 分钟饱和 → 提供进展信号 |

Agent 应该在训练 **10-20 分钟**内开始学会靠近敌人、适时开火、避开墙壁（而当前代码永远无法取得进展）。
