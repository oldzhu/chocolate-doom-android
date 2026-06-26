# 端侧 AI 代打 —— 技术规格

> [English version](../en/ai-player.md)

## 问题定义

在 Android 手机上运行本地 AI 模型，自主游玩 DOOM：观察画面、决策行动、通过虚拟按键注入执行、并通过自对弈不断进步。

## 架构

```
┌─────────────────────────────────────────────────────────┐
│                    AI 代打进程                           │
│  ┌──────────────┐   ┌──────────┐   ┌──────────────────┐│
│  │ 帧截取       │ → │ 状态     │ → │  推理引擎        ││
│  │ (120×68×1)   │   │ 编码器   │   │  (TFLite)        ││
│  └──────────────┘   │ (向量)   │   │  2-5 MB 模型     ││
│                     └──────────┘   └────────┬─────────┘│
│                                             │          │
│  ┌──────────────┐              ┌────────────▼────────┐  │
│  │ 奖励计算     │←─────────────│ 动作选择             │  │
│  │ (生命/击杀/  │  游戏状态    │ (ε-贪心 / argmax)    │  │
│  │  道具)       │              └────────┬───────────┘  │
│  └──────────────┘                       │              │
│                                         ▼              │
│                              ┌──────────────────┐      │
│                              │ 按键注入器       │      │
│                              │ (SDL 原生按键)   │      │
│                              └──────────────────┘      │
└─────────────────────────────────────────────────────────┘
```

## 模型选择

**结论：DQN（深度 Q 网络）** — 大小 2-5 MB，推理 <5ms，适合 DOOM。

| 模型 | 大小 | 推理速度 | 是否可行 |
|-------|------|-----------|-------------|
| **DQN** | ~2 MB | <5ms (GPU) | ✅ 推荐 |
| DRQN (DQN+LSTM) | ~5 MB | <10ms | ✅ 适合序列 |
| PPO | ~8 MB | <15ms | ⚠️ 复杂 |
| Tiny Llama 1.1B | ~2 GB | 500ms+ | ❌ 太大太慢 |

**为什么不选大语言模型**：LLM 需要逐 token 处理，读一帧画面就需上百次推理（5 秒+），无法实时游戏。DQN 单次前向传播处理整帧，<5ms。

### DQN 网络结构

```
输入：4 帧叠加 (120×68×4) — 捕捉运动信息
CNN: Conv(32, 8×8, stride=4) → ReLU
     Conv(64, 4×4, stride=2) → ReLU
     Conv(64, 3×3, stride=1) → ReLU
     Flatten → 3136 维
FC:  Dense(512) → ReLU
     Dense(224) → 每个动作的 Q 值
```

## 输入流水线

### 屏幕截取：原生 glReadPixels

```c
// JNI 原生代码 — 最快方式，无需 Android 权限
glReadPixels(0, 0, 480, 270, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
// 转为灰度 → 缩放到 120×68
```

### 游戏状态提取：JNI 读取 DOOM 内存结构

```c
// DOOM player_t 结构体 (d_player.h)
typedef struct {
    int health;          // 生命值 0-100
    int armorpoints;     // 护甲值
    int ammo[4];         // 4 种弹药
    int killcount;       // 击杀数
    int itemcount;       // 道具数
    int secretcount;     // 秘密数
    int cheats;          // 作弊状态（CF_GODMODE 等）
} player_t;
```

## 动作空间：17 种离散动作

| 类别 | 动作 |
|------|------|
| 移动 (7) | 无移动、前进、后退、左转、右转、左平移、右平移 |
| 速度 (2) | 走、跑 |
| 开火 (2) | 不开火、开火 |
| 使用 (2) | 不使用、使用 |
| 武器 (4) | 拳头、手枪、霰弹枪、BFG |

有效组合：7 × 2 × 2 × 2 × 4 = **224 种**

AI 每 4 帧决策一次（DOOM 35fps → AI 8.75Hz），动作重复 4 帧后重新决策。

## 奖励函数

```python
奖励 = (
    +0.01  # 每帧存活奖励
    +生命变化 * 0.02  # 受伤扣分，拾取血包加分
    +击杀变化 * 1.0   # 每次击杀 +1
    +道具变化 * 0.1   # 拾取道具
    +秘密变化 * 0.5   # 发现秘密区域
    -5.0              # 死亡
    -0.01             # 空射惩罚（鼓励节约弹药）
)
```

## 训练策略

### 第一阶段：PC 离线训练（3-5 天）
- 环境：ViZDoom + Freedoom WAD
- 算法：Double DQN + Dueling 架构
- 训练局数：50 万局
- 经验回放：10 万条转移
- 导出：PyTorch → ONNX → TFLite `.tflite`

### 第二阶段：端侧推理（Android）
```java
Interpreter tflite = new Interpreter(modelFile);
tflite.setUseNNAPI(true);  // Android 神经网络 API
tflite.run(stackedFrames, qValues);
int action = argmax(qValues);
// 执行动作 → 注入按键事件
```

### 第三阶段：端侧微调（自我进化）
- 经验回放缓冲区：1 万条（约 50 MB）
- Q-learning 更新每 4 个动作一次
- 仅微调，不从零训练
- 模型权重跨会话持久化

## 性能指标

| 指标 | 目标 | 实际估算 |
|--------|--------|---------|
| 推理耗时 | <10ms | ~3-5ms (TFLite GPU) |
| 帧截取 | <5ms | ~2ms (glReadPixels) |
| 状态提取 | <1ms | ~0.5ms (原生指针读取) |
| 总决策周期 | <20ms | ~8ms |
| 耗电 | <5%/小时 | ~3%/小时 |
| 模型大小 | <10 MB | ~2-5 MB (.tflite) |
| 运行时内存 | <100 MB | ~60 MB |

## 实施阶段

| 阶段 | 内容 | 预估 |
|------|------|------|
| **P1** | JNI 帧截取 + 游戏状态提取 | 1 周 |
| **P2** | PC 端 DQN 训练 + TFLite 导出 | 1 周 |
| **P3** | Android TFLite 推理 + 动作映射 + 主循环 | 1 周 |
| **P4** | 端侧 Q-learning 微调 + 持久化 | 1 周 |

## 风险与对策

| 风险 | 概率 | 对策 |
|------|------|------|
| TFLite 训练 API 不成熟 | 中 | 先做纯推理，训练放 PC |
| DOOM 内存偏移因版本不同 | 中 | 用已知作弊码验证（IDDQD→health=100） |
| 模型过大超 APK 限制 | 低 | TFLite 量化压缩到 2-3 MB |
| AI 陷入局部最优 | 高 | ε=5% 随机探索保证不卡死 |
| 低端 GPU 帧截取太慢 | 低 | 跳帧 + 降分辨率到 80×45 |

## 文件规划

```
app/src/main/
├── java/com/chocolate/doom/ai/
│   ├── AIPlayer.java           # AI 总控（启停、帧循环）
│   ├── AIOverlay.java          # 调试可视化（Q 值、状态）
│   ├── FrameGrabber.java       # 屏幕截取（原生 glReadPixels）
│   ├── GameStateExtractor.java # JNI 包装 DOOM player 结构体
│   ├── ActionMapper.java       # 动作枚举 → SDL 按键序列
│   ├── DQNInference.java       # TFLite 模型加载 + 推理
│   ├── DQNTrainer.java         # 端侧 RL（回放缓冲区 + 更新）
│   └── RewardCalculator.java   # 基于游戏状态差值的奖励函数
├── cpp/
│   ├── frame_grabber.cpp       # glReadPixels 原生代码
│   └── game_state.cpp          # DOOM player 结构体读取
└── assets/models/
    └── doom_dqn.tflite         # 预训练模型（~3 MB）
```

## 参考资料

- Mnih et al. (2015) — "Human-level control through deep reinforcement learning" (Nature)
- ViZDoom: https://github.com/Farama-Foundation/ViZDoom
- TFLite GPU 委托: https://www.tensorflow.org/lite/performance/gpu
- Chocolate Doom player 结构体: `src/doom/d_player.h`
