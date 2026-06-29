# 更新日志与修复记录

> [English version](../en/changelog.md)

Chocolate Doom Android 移植开发过程中所有决策、修复与变更的完整记录。

---

## 预发布功能 (2026-06-26)

### 功能 1：模拟摇杆触摸移动

| 方面 | 详情 |
|--------|--------|
| **问题** | 固定 D-pad 按钮在触摸屏上操作不便 |
| **方案** | 屏幕左半区浮动模拟摇杆 |
| **实现** | 新增 `AnalogJoystick.java` + 修改 `TouchControls.java` |
| **设计** | 触摸左半区任意位置 → 摇杆出现。拖拽实现 8 方向移动。拖拽超过 90% 自动跑步。 |
| **文件** | `AnalogJoystick.java`（新增），`TouchControls.java`（修改） |

### 功能 2：上帝模式（IDDQD + IDKFA 作弊码）

| 方面 | 详情 |
|--------|--------|
| **问题** | 无物理键盘无法激活 DOOM 作弊码 |
| **方案** | 1 秒内连按 ☰（菜单）3 次 → 通过键盘事件注入 `iddqd` + `idkfa` |
| **实现** | `TouchControls.java` — `onMenuTapped()` 三连击检测，`injectCheatSequence()` 字符注入，`showOverlay()` 视觉反馈 |
| **激活** | 1000ms 内连点 ☰ 三次 |
| **视觉** | 屏幕中央显示 "⚡ GOD MODE ⚡" 2 秒 |
| **效果** | 无敌（IDDQD）+ 所有武器/弹药/钥匙（IDKFA） |

### 功能 2b：上帝模式 — 生命值和弹药锁定 (2026-06-29)

| 方面 | 详情 |
|--------|--------|
| **问题** | 初始上帝模式只注入一次作弊码 — 开火弹药减少，受伤生命值下降 |
| **方案** | 快速周期性重注入：IDFA 每 **500ms** 一次（锁定弹药），双重 IDDQD 每 **5s** 一次（锁定生命值） |
| **实现** | `TouchControls.java` — `scheduleAmmoRefill()` 500ms 循环，`scheduleHealthRefresh()` 双重 IDDQD 5s 循环，静默注入（无覆盖文字刷屏） |
| **生命值 Bug 根因** | DOOM 的 IDDQD 使用 XOR 切换 — 单次重注入会关闭而非刷新上帝模式 |
| **生命值修复** | 注入 `iddqd` 连续两次（`iddqdiddqd`）：第一次关闭 → 第二次重新开启（生命值=100）。净效果：上帝模式保持开启，生命值恢复。 |
| **弹药 Bug** | 原 15s 弹药补充太慢 — 弹药在补充间明显下降 |
| **弹药修复** | 间隔缩短至 500ms；弹药恢复速度快于开火速度，永不明显下降 |
| **注** | 此方案基于作弊码注入，非原生内存锁定。真正的零帧锁定需要修改原生 C 代码。 |

### 功能 3：上下文感知点击控制 (2026-06-29)

| 方面 | 详情 |
|--------|--------|
| **问题** | 屏幕上按钮过多（11 个）遮挡游戏画面；开火/使用/确认需要精确点击按钮 |
| **方案** | 点击游戏区域同时注入 Ctrl+Space+Enter — 游戏引擎根据上下文判断有效按键 |
| **设计文档** | `docs/en/controls-design.md` + `docs/zh/controls-design.md`（中英双语） |
| **实现** | `TouchControls.java` — 点击检测（最长 250ms、最大移动 25px），`onGameTap()` 注入 3 个按键，80ms 自动释放 |
| **移除按钮** | 🔫 开火、🚪 使用/开门、↵ 确认（全部由游戏区域点击替代） |
| **新增按钮** | ◀ 左横移（与右横移对称） |
| **布局** | 简化为 8 个按钮：☰ 菜单、Y、🗺 地图、◀◀/▶▶ 武器、◀/▶ 横移、🏃 跑步 |
| **上下文逻辑** | 面对敌人 → 开火；面对门 → 开门；在菜单中 → 选择项目。无需状态追踪。 |

### 上帝模式修复 — 迭代过程 (2026-06-29)

| # | 尝试 | 问题 | 修复 |
|---|---------|-------|-----|
| G1 | 每 10s 重注入 IDDQD | XOR 切换每隔一次关闭上帝模式 | 改用 `idbeholdv`（无敌神器）每 25s |
| G2 | 每 25s 注入 `idbeholdv` | 神器防止伤害但不恢复已损失的生命值 | 双重 IDDQD（`iddqdiddqd`）每 5s：第一次关闭，第二次重新开启（生命值=100） |

---

## v1.0 — 首个可运行版本 (2026-06-26)

### 依赖配置

| 组件 | 版本 | 决策 |
|-----------|---------|----------|
| Chocolate Doom | 3.1.1 | `chocolate-doom-3.1.1` 标签 |
| SDL2 | 2.30.0 | `release-2.30.0` 标签 |
| SDL2_mixer | 2.6.3 | 静态构建，支持 OGG/FLAC/WAV |
| Android NDK | r27 | arm64-v8a，API 24 |
| Android SDK | 34 | build-tools 34.0.0 |

### 构建修复

| # | 问题 | 修复方案 | 日期 |
|---|-------|-----|------|
| B1 | CMake 中 `-DDISABLE_SDL2MIXER=1` 导致无声音 | 从所有 `flags.make` 中移除，下载 SDL2_mixer 2.6.3，交叉编译，静态链接 | 2026-06-26 |
| B2 | `libmain.so` 缺少音频符号 | 对 choc 对象添加 `-Wl,--whole-archive`，链接 `libSDL2_mixer.a` | 2026-06-26 |
| B3 | Android 11+ 找不到 IWAD | 将 `-iwad` 路径从 `/sdcard/` 改为 `getFilesDir()` 内部存储 | 2026-06-26 |
| B4 | APK 过大不适合 GitHub | 使用 ZIP 压缩；APK 磁盘占用 ≈3.3 MB（.so 未压缩约 10 MB） | 2026-06-26 |

### OPPO ColorOS 修复

| # | 问题 | 根本原因 | 修复方案 | 日期 |
|---|-------|------------|-----|------|
| O1 | 启动黑屏 | `setOrientationBis()` 在 OPPO 上销毁/重建 Surface | 在 `ChocolateDoom.java` 中完全跳过 `setOrientationBis()` | 2026-06-26 |
| O2 | 熄屏时 Surface 销毁 → SDL 死锁 | OPPO 在熄屏时销毁 Surface，在新 Surface 就绪前暂停 Activity | `SDLSurface.java`：若 `System.currentTimeMillis() - mSurfaceCreatedTime < 500ms`，跳过原生暂停 | 2026-06-26 |
| O3 | 恢复时 App 暂停 | Activity 在 Surface 就绪前被暂停 | `ChocolateDoom.java`：在 `onWindowFocusChanged()` 中调用 `handleNativeState(RESUMED)` | 2026-06-26 |

### 触控控件 — 迭代历史

| 版本 | 变更内容 | 日期 |
|---------|---------|------|
| v1 | 初始布局：方向键 + 开火 + 使用 + 跑步 + 地图 + 横移 + 菜单 | 2026-06-26 |
| v2 | 添加确认键（替代左横移），添加 Y 键（尚未生效） | 2026-06-26 |
| v3 | **尺寸增大 1**：方向键外圈 90→140px，按钮 35→50px，开火 70→100px，其他 50→65px，菜单 40→55px | 2026-06-26 |
| v4 | **图标增大 1**：箭头 28→42px，表情 26→36px | 2026-06-26 |
| v5 | **图标增大 2**：箭头 42→56px，表情 36→44px | 2026-06-26 |
| v6 | **Y 键修复**：在 `SDLActivity.java` 中添加 `sendText()` 包装方法 → Y 键调用 `sendText("y")` 替代 `onNativeKeyDown` | 2026-06-26 |
| v7 | **Y 键修复 v2**：`sendText("y")` → `SDL_TEXTINPUT`（DOOM 退出逻辑所需的事件类型不对）。改为 `onNativeKeyDown(KEYCODE_Y=53)` + 80ms 自动释放。修正键码 54→53（之前是 KEYCODE_Z） | 2026-06-26 |
| v8 | **上下文感知点击**：移除 🔫 开火、🚪 使用、↵ 确认。点击游戏区域 → Ctrl+Space+Enter。新增 ◀ 左横移。布局：11→8 按钮。设计文档：`controls-design.md`。 | 2026-06-29 |
| v9 | **按钮重叠修复**：重新计算所有位置，按钮间最小间距 10px。调整半径：菜单/Y 50、地图 60、武器 40、横移 45、跑步 60。 | 2026-06-29 |

### 关键修复：Y 键无法退出

**症状**：Y 键在屏幕上可见，触摸有响应，但显示"Press Y to quit to DOS"时游戏不退出。

**调查过程**：
1. DOOM 的退出处理函数（`m_menu.c:M_Responder`）检查 `ev->type == ev_keydown` 以及 `ev->data1 == 'y'`
2. `sendText("y")` 调用 `SDLInputConnection.nativeCommitText("y", 1)` → 产生 `SDL_TEXTINPUT` 事件
3. `SDL_TEXTINPUT` 的 `type != ev_keydown` → DOOM 忽略它 ❌

**修复**（提交于 `TouchControls.java`）：
```java
// 修复前（无效）:
if (btn == btnYes) {
    SDLActivity.sendText("y");              // TEXTINPUT 事件 — DOOM 忽略
    handler.postDelayed(() -> { fb.pressed = false; }, 80);
}

// 修复后（有效）:
if (btn == btnYes) {
    SDLActivity.onNativeKeyDown(btn.keyCode);  // KEYCODE_Y=53 → KEYDOWN 事件
    handler.postDelayed(() -> {
        if (fb.pressed) {
            fb.pressed = false;
            SDLActivity.onNativeKeyUp(fb.keyCode);
        }
    }, 80);
}
```

**关键洞察**：Android `KEYCODE_Y`（53）→ `Android_Keycodes[53]` → `SDL_SCANCODE_Y` → `SDL_SendKeyboardKey` 产生 `KEYDOWN` 事件，`keysym.sym = SDLK_y = 'y'`。DOOM 的事件循环可匹配此事件。

### 已知限制

| # | 问题 | 状态 |
|---|-------|--------|
| K1 | APK 须使用调试密钥库签名（生产签名待定） | 待解决 |
| K2 | 存档命名无屏幕键盘支持 | 待解决 |
| K3 | Heretic/Hexen/Strife APK 未使用正确 WAD 完整测试 | 待解决 |
| K4 | 触控控件不透明度不可由用户调节 | 待解决 |
| K5 | 不支持多点触控同时操作（开火+移动） | 待解决 |
| K6 | WAD 自动发现未实现（需手动推送） | 待解决 |

---

## F3 AI 玩家 — 设计阶段 (2026-06-29)

| 方面 | 详情 |
|--------|--------|
| **讨论** | 手机端 RL vs PC 预训练、VLM/VLA/WAM 在实时 DOOM 中的可行性 |
| **决策** | 阶段 1：Double Dueling DQN，纯手机端 RL。阶段 2：BC+RL 引导启动。阶段 3：混合 DQN+VLM 战略层。 |
| **设计文档** | `docs/en/ai-player-options.md` + `docs/zh/ai-player-options.md` — 7 种方案全面对比 |
| **排除** | DreamerV3（太大/太慢）、纯 VLM（慢 100-400 倍）、MCTS（需要世界模型）、Rainbow DQN（v1.0 过度设计） |
| **AI 玩家规格** | `docs/en/ai-player.md` + `docs/zh/ai-player.md` 已更新交叉链接 |
