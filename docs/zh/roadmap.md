# 功能路线图 — v1.0 发布前

> [English version](../en/roadmap.md)

首次 GitHub Release 前需实现的三个功能。

---

## 功能 1：触摸移动（模拟摇杆）

**优先级**：🔴 高 — 核心体验
**复杂度**：中等
**涉及文件**：`TouchControls.java`（新增 `AnalogJoystick.java`）

### 问题
当前的 D-pad（▲▼◄► 按钮）在触摸屏上操作不便。玩家需要找到并按下固定的按钮。现代手游采用浮动模拟摇杆 — 触摸任意位置，拖动即可移动。

### 设计

#### 概念
```
┌──────────────────────────────────────────┐
│  左半区（摇杆区域）        右半区        │
│                                          │
│   ●  ← 摇杆在触摸点出现                  │
│  ╱ ╲                                     │
│ ╱   ╲    最大半径 = 80px                │
│╲     ╱   死区 = 20px                    │
│ ╲   ╱                                    │
│  ╲ ╱                                     │
│   ●  ← 当前拖拽位置（拇指）              │
│                                          │
│  [摇杆]          🔫 🚪 🏃 🗺️ 等        │
└──────────────────────────────────────────┘
```

#### 行为
1. **触摸左半区任意位置** → 摇杆原点出现在触摸点
2. **拖拽** → 摇杆手柄跟随手指（限制在最大半径内）
3. **方向映射**（8方向）：
   - 上 → DPAD_UP（前进）
   - 下 → DPAD_DOWN（后退）
   - 左 → DPAD_LEFT（左转）
   - 右 → DPAD_RIGHT（右转）
   - 左上 → DPAD_UP + DPAD_LEFT 同时
   - 右上 → DPAD_UP + DPAD_RIGHT 同时
   - 左下 → DPAD_DOWN + DPAD_LEFT 同时
   - 右下 → DPAD_DOWN + DPAD_RIGHT 同时
4. **死区**：距原点 20px 以内（无移动）
5. **抬起手指** → 所有按键释放，摇杆消失
6. **自动跑步**：拖拽超过最大半径 90% → 同时按 SHIFT（自动冲刺）

#### 实现
```java
class AnalogJoystick {
    float originX, originY;   // 触摸点
    float knobX, knobY;       // 当前拇指位置
    float maxRadius = 80f;    // 最大拖拽距离
    float deadZone = 20f;     // 忽略微小移动
    int pointerId = -1;       // 追踪手指
    boolean active = false;
    
    void onTouchDown(float x, float y, int id) { ... }
    void onTouchMove(float x, float y) { ... }
    void onTouchUp(int id) { ... }
    void draw(Canvas canvas) { ... }
}
```

#### 按键状态
| 方向 | 按键 |
|-----------|-------------|
| 上（dx 小，dy < -死区） | DPAD_UP |
| 下（dx 小，dy > 死区） | DPAD_DOWN |
| 左（dy 小，dx < -死区） | DPAD_LEFT |
| 右（dy 小，dx > 死区） | DPAD_RIGHT |
| 左上 | DPAD_UP + DPAD_LEFT |
| 右上 | DPAD_UP + DPAD_RIGHT |
| 左下 | DPAD_DOWN + DPAD_LEFT |
| 右下 | DPAD_DOWN + DPAD_RIGHT |
| 自动跑（半径 > 90%） | + SHIFT_LEFT |

#### 配置项（后续可调）
- `joystickEnabled`：布尔值
- `joystickMaxRadius`：浮点（50-150px）
- `joystickDeadZone`：浮点（10-40px）
- `autoRunEnabled`：布尔值
- `invertY`：布尔值（部分玩家习惯反转）

---

## 功能 2：上帝模式作弊

**优先级**：🟡 中 — 趣味/测试功能
**复杂度**：低
**涉及文件**：`TouchControls.java`，`ChocolateDoom.java`

### 设计

#### 激活方式
**1 秒内连按 ☰（菜单）3 次** → 切换上帝模式。

备选：**长按 Y 键 2 秒** → 上帝模式。

#### 作弊码（Chocolate Doom 内置）
Chocolate Doom 支持通过键盘输入标准 DOOM 作弊码：

| 作弊 | 代码 | 效果 |
|-------|------|--------|
| IDDQD | `i d d q d` | 无敌模式 |
| IDKFA | `i d k f a` | 所有武器 + 钥匙 + 满弹药 + 满护甲 |
| IDCLIP | `i d c l i p` | 穿墙模式 |
| IDBEHOLD | 多种 | 强化道具（狂暴、隐身等） |

#### 实现
上帝模式激活时：
1. 通过键盘事件注入 `iddqd` + `idkfa` 字符序列
2. 每个字符间隔 50ms 发送（模拟打字速度）
3. 视觉反馈：屏幕闪烁 + 显示 "GOD MODE ACTIVATED" 叠加文字 2 秒

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

#### 连击检测（1秒内3次点击）
```java
long[] menuTapTimes = new long[3];
int tapIndex = 0;

void onMenuTap() {
    long now = System.currentTimeMillis();
    menuTapTimes[tapIndex % 3] = now;
    tapIndex++;
    
    // 检查最近3次点击是否在1000ms内
    if (tapIndex >= 3) {
        long span = menuTapTimes[2] - menuTapTimes[0];
        if (span < 1000) {
            activateGodMode();
            tapIndex = 0; // 重置
        }
    }
}
```

---

## 功能 3：手机端本地 AI 代打

**优先级**：🟢 低 — 雄心勃勃，实验性
**复杂度**：非常高
**涉及文件**：新增原生模块 + `AIOverlay.java` + 模型文件

### 问题描述
在手机上运行小型本地 AI 模型，能够：
1. 观察游戏画面
2. 理解游戏状态（生命、弹药、敌人、地图）
3. 决定行动（移动、射击、开门、换武器）
4. 通过虚拟按键注入执行动作
5. 不断学习提升（自我进化）

### 架构

```
┌──────────────────────────────────────────────┐
│                  AI 层                        │
│  ┌────────────┐  ┌──────────┐  ┌──────────┐ │
│  │ 屏幕截取   │→ │  视觉    │→ │  策略    │ │
│  │ (120×68 px)│  │ 编码器   │  │ 网络     │ │
│  └────────────┘  │ (CNN)    │  │ (LSTM)   │ │
│                  └──────────┘  └────┬─────┘ │
│                                     │       │
│  ┌──────────┐              ┌───────▼──────┐ │
│  │ 奖励     │←─────────────│  动作        │ │
│  │ 信号     │  生命/弹药   │  选择        │ │
│  │ (生命,   │  /击杀等     │ (按键事件)   │ │
│  │  击杀,   │              └──────┬───────┘ │
│  │  进度)   │                    │          │
│  └──────────┘              ┌─────▼──────┐   │
│                            │ SDL 按键   │   │
│                            │ 注入       │   │
│                            └────────────┘   │
└──────────────────────────────────────────────┘
```

### 模型选择

| 方案 | 大小 | 手机速度 | 质量 |
|--------|------|---------------|---------|
| **Tiny Llama 1.1B** | ~2 GB | 太慢，太大 | 杀鸡用牛刀 |
| **SmolLM2 135M** | ~270 MB | 慢（~1 fps） | 文本好，视觉不行 |
| **自定义 CNN+LSTM** | ~5-10 MB | 快（~30 fps） | 适合游戏 |
| **DQN（深度Q网络）** | ~2-5 MB | 快（~30 fps） | DOOM 验证过（ViZDoom） |

**建议**：自定义小型 CNN+LSTM 或 DQN，离线训练，然后转换为 ONNX 或 TFLite 用于端侧推理。模型大小 ~5-10 MB，每帧推理 <10ms。

### 输入编码（AI 的"眼睛"）

```python
# 屏幕截取：120×68 灰度图
frame = capture_screen(0.25x)  # 120×68×1

# 游戏状态（内存解析 — 需原生钩子）：
state = {
    'health': player.health,      # 0-200
    'armor': player.armor,        # 0-200
    'ammo': [子弹, 霰弹, ...],    # 4 种
    'weapon': player.readyweapon,  # 0-8
    'keys': [蓝, 红, 黄],         # 3 布尔值
    'kills': player.killcount,
    'items': player.itemcount,
    'secrets': player.secretcount,
}
```

### 动作空间（AI 能做什么）

```
离散动作（多维离散）：
- 移动：[无, 前进, 后退, 左转, 右转, 左移, 右移]
- 速度：[走, 跑]
- 开火：[否, 是]
- 使用：[否, 是]
- 武器：[拳, 手枪, 霰弹, 机枪, 火箭, 等离子, BFG, 电锯, 超级霰弹]
- 平移：[否, 是]
```

总计：7 × 2 × 2 × 2 × 9 × 2 = 1,008 种可能动作

### 奖励函数

```python
reward = (
    +0.01 * delta_health        # 存活
    +1.0  * delta_kills          # 杀敌
    +0.1  * delta_items          # 收集道具
    +0.5  * delta_secrets        # 发现秘密
    -0.05                        # 时间惩罚（鼓励快速通关）
    +5.0  * level_completed      # 通关奖励
    -5.0  * player_died          # 死亡惩罚
)
```

### 训练策略

**第一阶段：离线训练（PC）**
- 使用 ViZDoom 或 Chocolate Doom Python 绑定
- 在 Freedoom 关卡上训练 DQN/PPO 100 万+ 局
- 导出模型 → ONNX → 转换 TFLite

**第二阶段：端侧推理**
- 在 Android 上加载 TFLite 模型
- 每帧推理一次（约 33ms，30fps）
- 不在设备上训练（仅推理）

**第三阶段：端侧微调（自我进化）**
- 使用 TensorFlow Lite 的训练 API 实现端侧 RL
- 小型经验回放缓冲区（最近 10,000 次转移）
- 每 4 帧进行 Q-learning 更新
- 缓慢逐步提升

### 技术挑战

| 挑战 | 难度 | 方案 |
|-----------|------------|----------|
| Android 屏幕截取 | 中等 | MediaProjection API 或 SurfaceView.getBitmap() |
| 游戏状态提取 | 困难 | 通过 JNI 钩子解析 DOOM 内存结构 |
| 快速推理 | 中等 | TFLite GPU 委托（OpenGL ES） |
| 端侧训练 | 非常高 | TFLite Model Maker 或自定义 C++ RL 循环 |
| 电池消耗 | 高 | 限制推理为 10fps，跳帧 |
| 内存限制 | 中等 | 梯度存储在 C++ 缓冲区，非 Java 堆 |

### 分阶段方案

| 阶段 | 交付物 | 时间 |
|-------|------------|----------|
| **P1** | 屏幕截取 + 状态提取 | 2-3 天 |
| **P2** | 离线训练模型在设备上运行 | 3-5 天 |
| **P3** | 可玩的 AI（基础移动+射击） | 5-7 天 |
| **P4** | 端侧微调 | 7-10 天 |
| **P5** | 跨会话自我进化 | 10+ 天 |

### 建议
功能 3 是一个完整的研究项目。**v1.0 实现简化版**：一个离线训练、表现尚可的模型，打包进 APK。自我进化放到 v2.0。

---

## 实施顺序

```
┌─────────────────────────────────────────────────┐
│  第1周：功能 1（触摸移动）                       │
│  ▸ 设计模拟摇杆类                               │
│  ▸ 实现 8 方向触摸映射                          │
│  ▸ 替换当前 D-pad（或提供双模式）               │
│  ▸ 真机测试                                     │
├─────────────────────────────────────────────────┤
│  第2周：功能 2（上帝模式）                       │
│  ▸ 实现三连击检测                               │
│  ▸ 实现作弊码注入                               │
│  ▸ 添加视觉反馈叠加层                           │
│  ▸ 真机测试                                     │
├─────────────────────────────────────────────────┤
│  第3-4周：功能 3（AI 代打 — 第一阶段）          │
│  ▸ 调研 + 原型屏幕截取                          │
│  ▸ 从内存提取游戏状态                           │
│  ▸ PC 端训练初始模型                            │
│  ▸ 将模型打包进 APK                             │
│  ▸ 实现基础 AI 游戏循环                         │
│  ▸ 真机测试                                     │
└─────────────────────────────────────────────────┘
```

---

## 下一步

开始 **功能 1（触摸移动）**？我会先实现模拟摇杆，然后你的 OPPO 手机测试。
