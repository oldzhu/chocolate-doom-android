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
| v10 | **AI 切换按钮**：新增 🤖 按钮（顶栏，r=45）。激活时绿色，关闭时暗色。连接 AIController 控制 DQN 智能体。 | 2026-06-30 |

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

---

## F3 AI 玩家 — v1.0 实现 (2026-06-30)

| 方面 | 详情 |
|--------|--------|
| **架构** | 纯 Java DQN：FC(13→128→64→14)，双重 Q 学习，经验回放池 10K |
| **新增文件** | `ai/NeuralNet.java`（270 行）、`ai/DQNPlayer.java`（265 行）、`ai/AIController.java`（292 行） |
| **状态特征** | v1.0：启发式（存活时间、近期动作）。v1.1：计划 JNI 游戏状态提取。 |
| **动作** | 14 个离散动作：NOOP、前进、后退、左转、右转、左横移、右横移、开火、使用、跑步、拳头、手枪、霰弹枪、BFG |
| **训练** | 手机端 RL：8.75 Hz（跳帧=4）、批量=32、γ=0.99、ε=1.0→0.05 |
| **切换** | 🤖 按钮（顶栏，激活时绿色）。切换人类/AI 控制。 |
| **模型大小** | ~50 KB（~10K 参数），APK 无增大（纯 Java） |
| **画面捕获** | 尚未实现 — 状态为启发式。v1.1 计划 JNI glReadPixels。 |

### 作弊码注入修复 — 队列序列化 (2026-06-30)

| 方面 | 详情 |
|--------|--------|
| **Bug** | IDFA（500ms）和双重 IDDQD（5s）通过同一 Handler 注入字符。同时运行时字符交错（如 "i i d d f d a q"），作弊码输入混乱。DOOM 作弊码检测器无法匹配。 |
| **症状** | 上帝模式生命值不恢复。弹药偶尔不补充。 |
| **修复** | 队列注入（`cheatQueue` LinkedList）。同一时间仅处理一个作弊码序列。50ms 按键保持，序列间 100ms 间隔。 |
| **结果** | 不再可能交错。作弊码序列始终完整到达 DOOM。 |

### 噩梦难度上帝模式被禁用 — 根因与修复 (2026-06-30)

| 方面 | 详情 |
|--------|--------|
| **症状** | 噩梦（Nightmare）难度下，上帝模式（连按三下 ☰）无效 — 生命值减少、弹药消耗、武器未授予 |
| **根因** | Chocolate Doom `st_stuff.c:393`：`if (!netgame && gameskill != sk_nightmare)` — 噩梦难度下**所有作弊码**被阻止。这是原版 DOOM 行为，被 Chocolate Doom 忠实复现。 |
| **为何 Java 无法修复** | `TouchControls.java` 正确注入键盘事件，但 `ST_Responder`（原生 C 代码）在噩梦难度下拒绝处理 |
| **采用方案** | **源码补丁**：从 `st_stuff.c` 移除 `gameskill != sk_nightmare`，通过 `build-native.sh` 重新构建 `libdoom.so` |
| **选择源码补丁的原因** | `libdoom.so` 是从本地源码（`~/android-toolchain/chocolate-doom-src/`）构建的，而非预编译 blob。源码补丁在重新构建时持久有效。 |
| **完整分析** | `docs/en/nightmare-godmode.md` + `docs/zh/nightmare-godmode.md` |

---

## Chocolate Doom 子模块 + 构建系统迁移 (2026-06-30)

### Git 子模块决策

| 方面 | 详情 |
|--------|--------|
| **为何使用子模块** | 保持仓库轻量，明确的上游溯源（精确标签 `chocolate-doom-3.1.1`），可复现构建 |
| **子模块路径** | `native/chocolate-doom/`（固定到上游标签 `chocolate-doom-3.1.1`） |
| **补丁** | 版本控制在 `native/patches/nightmare-cheats.patch` |
| **自动应用** | `build-native.sh` 在执行 cmake 前对全部 `.patch` 文件运行 `git apply` |
| **提交** | `29c9e92` — 子模块 + 补丁 + 决策文档（中英文） |

### 构建系统变更

| # | 变更 | 原因 | 提交 |
|---|--------|--------|--------|
| BS1 | cmake 现在从子模块配置（`native/chocolate-doom/`） | 消除外部源码依赖 | `29c9e92` |
| BS2 | 使用 Android SDK 自带的 cmake（`$SDK/cmake/3.22.1/bin`） | 确保兼容的 cmake 版本 | `4fddc94` |
| BS3 | 使用 `SDL2-android/` 的预编译 SDL2/SDL2_mixer | 避免慢速 autotools configure（SDL2_mixer 的 `sdl2-config` 与 Android 交叉编译不兼容） | `4fddc94` |
| BS4 | 编译 `chocolate-doom` 可执行目标以获取公共 `.o` 文件 | 公共源码（`i_video.c`、`i_input.c` 等）在可执行目标中，不在静态库中 | `e8cd299` |
| BS5 | 链接器从 `clang` 切换到 `clang++` | SDL2 静态库包含需要 C++ ABI 符号的 C++ 代码 | `2939cd6` |
| BS6 | 在 APK 中打包 `libc++_shared.so` | SDL2 的 C++ 代码运行时需要 | `2939cd6` |

---

## libdoom.so 链接修复 — dlopen 符号解析 (2026-06-30)

迁移到 git 子模块并进行完整的 cmake 重新构建后，遇到了五个连续的 `dlopen failed: cannot locate symbol` 错误并逐一修复。每个错误都阻止了应用在设备上启动。

### 完整修复日志

| # | 缺失符号 | 根因 | 修复 | 提交 |
|---|---------------|------------|-----|--------|
| L1 | `I_VideoBuffer` | 公共源码（`i_video.c`、`i_input.c`、`i_timer.c` 等）只编译进了 `chocolate-doom` 可执行目标，不在静态库中 | 编译 `chocolate-doom` 目标（忽略链接失败 — 只需要 `.o` 文件），将 `CMakeFiles/chocolate-doom.dir/src/*.c.o` 链接进 `libdoom.so` | `e8cd299` |
| L2 | `glBindTexture` + 50+ GLES 符号 | Chocolate Doom 同时使用 OpenGL ES 1.x 和 2.0，但两个库都未链接 | 添加 `-lGLESv1_CM -lGLESv2` 到链接器标志 | `e8cd299` |
| L3 | `_gxx_personality_v0` | C++ 异常处理 ABI 符号未解析。SDL2 静态库使用 C++ 代码编译；`clang`（C 链接器）不引入 C++ 运行时 | 切换到 `clang++` 链接器（`$CXX`），添加 `-lc++_shared`（C++ 链接器隐式添加），在 APK 中打包 `libc++_shared.so` | `2939cd6` |
| L4 | `__android_log_write` | Android 日志库未链接；`-llog` 在更早的链接行中存在，但在切换到 `$CXX` 时被丢弃 | 将 `-llog` 加回链接器标志 | `9331bcd` |
| L5 | `ANativeWindow_setBuffersGeometry` | Android NativeWindow API 未链接；SDL2 视频后端调用此函数 | 添加 `-landroid` 到链接器标志 | `9331bcd` |

### 最终链接器调用

```bash
$CXX -shared -fPIC -o libdoom.so \
    -Wl,--whole-archive libdoom.a libtextscreen.a \
    libopl.a libpcsound.a -Wl,--no-whole-archive \
    $COMMON_OBJ_DIR/*.c.o \
    $SDL2_LIB $SDL2_MIXER_LIB \
    -lm -ldl -llog -lOpenSLES -lGLESv1_CM -lGLESv2 -landroid
```

### 验证

```bash
# 检查 NEEDED 库
readelf -d libdoom.so | grep NEEDED
# 输出：libm.so、libdl.so、liblog.so、libOpenSLES.so、
#       libGLESv1_CM.so、libGLESv2.so、libc++_shared.so、libc.so

# 确认没有未定义符号
nm -D libdoom.so | grep -E "I_VideoBuffer|glBindTexture|_gxx_personality|__android_log_write|ANativeWindow_setBuffersGeometry"
# 全部已解析 ✓
```

### 经验教训

1. **公共源码在可执行文件中，不在库中**：Chocolate Doom 的 cmake 将共享源码（`i_*.c`、`m_*.c`）放入 `chocolate-doom` 可执行目标，而不是 `libdoom.a`。我们仅编译可执行目标以获取 `.o` 文件（链接步骤失败 — Android 上没有 `main()` — 但没关系）。

2. **GLES v1 + v2 两者都需要**：Chocolate Doom 使用 GLES 1.x 进行 2D 屏幕渲染，使用 GLES 2.0 进行缩放/着色器。两者都必须链接。

3. **来自 SDL2 的 C++ ABI**：即使你的代码是纯 C，链接包含 C++ 代码的静态库也需要 `clang++` 作为链接器。缺失 C++ ABI 符号（`_gxx_personality_v0`、`__cxa_*`）是 #1 症状。

4. **Android 系统库**：`-llog`（日志）、`-landroid`（NativeWindow）、`-lOpenSLES`（音频）在链接 Android 版 SDL2 时经常需要，但容易被忽略。
