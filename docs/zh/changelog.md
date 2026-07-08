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
| **采用方案** | **源码补丁**：从 `st_stuff.c` 移除 `gameskill != sk_nightmare`，将 `^= CF_GODMODE`（XOR 切换）改为 `|= CF_GODMODE`（始终 SET）。通过 `build-native.sh` 重新构建 `libdoom.so`。 |
| **选择源码补丁的原因** | `libdoom.so` 是从 `native/chocolate-doom/` 的 git 子模块构建的。源码补丁在重新构建时持久有效。 |
| **XOR 切换修复** | 原始 `^=` 每次注入会在开↔关之间切换上帝模式。改为 `|=` 确保 IDDQD 始终开启上帝模式 — 无脆弱窗口，不可能死亡。 |
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

---

## 声音回归 — 无音频输出 (2026-06-30)

### 症状

链接器修复和构建系统迁移后，音效和音乐停止工作。游戏可以启动运行，但完全静音。

### 根因

`build-native.sh` 在 cmake 配置中有 `-DENABLE_SDL2_MIXER=OFF`（在早期构建修复中添加，旨在避免 autotools `sdl2-config` 失败）。该 cmake 标志在编译时定义了 `DISABLE_SDL2MIXER=1`，将**整个 `i_sdlsound.c`** 文件包裹在 `#ifndef DISABLE_SDL2MIXER ... #endif` 中。

```c
// i_sdlsound.c:55 — 整个文件被此守卫包裹
#ifndef DISABLE_SDL2MIXER
    // 所有声音初始化、混音和回放代码
    // I_SDL_InitSound()、Mix_OpenAudioDevice() 等
#endif  // 第 1146 行
```

当 `DISABLE_SDL2MIXER` 被定义时，`sound_sdl_module`（基于 SDL 的声音模块）完全为空。唯一剩余的模块是 `sound_pcsound_module`（PC 扬声器模拟），在 Android 设备上不产生可听输出。

**影响链：**
```
-DENABLE_SDL2_MIXER=OFF
  → DISABLE_SDL2MIXER=1  (CMakeLists.txt 中的 add_compile_definitions)
    → i_sdlsound.c 被完全排除
      → sound_sdl_module = 空
        → I_InitSound() 只能尝试 pcsound
          → PC 扬声器模拟 = Android 上静音
```

### 修复

1. **更改 cmake 标志**：`-DENABLE_SDL2_MIXER=ON`（而非 OFF）
2. **提供 SDL2_mixer cmake 模块**：Chocolate Doom 的 cmake 调用 `find_package(SDL2_mixer)`，需要 `SDL2_MIXER_INCLUDE_DIR` 和 `SDL2_MIXER_LIBRARY`。由于我们的预编译 SDL2_mixer 不包含 cmake 配置文件，直接以 cmake 变量形式传入：
   ```
   -DSDL2_MIXER_INCLUDE_DIR="$SDL2_ANDROID/include/SDL2"
   -DSDL2_MIXER_LIBRARY="$SDL2_ANDROID/lib/libSDL2_mixer.a"
   -DSDL2_mixer_FOUND=TRUE
   ```
3. **创建 `FindSDL2_mixer.cmake`**：一个最小化的 cmake find-module，位于 `$SDL2_ANDROID/lib/cmake/SDL2_mixer/FindSDL2_mixer.cmake`，创建 `SDL2_mixer::SDL2_mixer` 导入目标。cmake 的 `find_package(SDL2_mixer)` 在 MODULE 模式下需要此文件。
4. **添加 `-DCMAKE_MODULE_PATH`**：指向包含 `FindSDL2_mixer.cmake` 的目录。

### 验证

```bash
# 确认声音模块已编译进 .so
nm -D libdoom.so | grep "Mix_OpenAudio"
# 必须显示：T Mix_OpenAudio（已定义，不是 U 未定义）

nm -D libdoom.so | grep "sound_sdl_module"
# 必须显示：D sound_sdl_module（数据符号存在）
```

### 运行时行为

- AAudio：已请求但被 **OPPO/Realme ColorOS 系统策略拒绝**（"aaudio denied with incompatible policy such as performance noise"）。这是 OPPO 特有的限制。
- 回退：**标准 AudioTrack** 成功以 44100 Hz、16-bit、立体声创建
- 音频轨道：`createTrack state 0`（成功），无错误

### 为何发生此回归

`-DENABLE_SDL2_MIXER=OFF` 标志最初是为了避免从源码构建 SDL2_mixer 时的 autotools `sdl2-config` 配置失败。切换到预编译 SDL2_mixer（来自 `SDL2-android` 项目）后，配置问题已解决，但该标志从未被移除。代码编译和链接均无错误 — 构建过程没有任何迹象表明声音已被静默禁用。

**教训**：构建成功并不保证所有功能都已编译。检查预处理器定义和符号表以验证二进制文件中实际包含的内容。

| 方面 | 详情 |
|--------|--------|
| **症状** | 无音效或音乐。游戏运行但静音。 |
| **根因** | `-DENABLE_SDL2_MIXER=OFF` → `DISABLE_SDL2MIXER=1` → 整个 `i_sdlsound.c` 被排除 → 仅剩 PC 扬声器声音（Android 上静音） |
| **修复** | `-DENABLE_SDL2_MIXER=ON` + 直接传入 SDL2_mixer cmake 变量 |
| **提交** | `b906842` |

---

## AI 控制器 — JNI 游戏状态桥接 (2026-07-06)

### 时间线

| 版本 | 提交 | 关键变更 |
|---------|--------|------------|
| v1.0 | — | 4 个启发式特征，盲代理 |
| v2.0 | `bb15830` | 15 个通过 JNI 获取的真实游戏状态特征（生命、弹药、武器、击杀） |
| v2.1 | `79b80c7` | 卡墙检测特征 + 无弹药惩罚 + 位置 JNI |
| v2.2 | `ab46902` | **强制卡墙恢复** — 撞墙时覆盖 DQN 动作，连续卡墙特征 |

### AI v2.0：真实 DOOM 状态 (`bb15830`)

**之前（v1.0）：** AI 使用 4 个自我追踪的启发式特征 — 生存时间、最近开火标志、最近使用标志、移动方向编码。对实际游戏状态完全无感知。随机开火，撞墙行走，无法学习。

**新增：**
- `native/jni_game_state.c`：19 个 JNI 函数直接读取 `players[consoleplayer]`
- `GameState.java`：原生方法声明
- 15 个真实特征：生命值、护甲、4 种弹药、当前武器、武器位掩码、背包、上帝模式、伤害闪烁、死亡状态、武器多样性、时间、击杀数、秘密数
- 真实奖励：击杀 +1.0、秘密 +1.0、受伤 -0.05、死亡 -1.0
- 网络：15→64→32→14（之前 4→64→32→14）

### AI v2.1：卡墙检测 + 弹药感知 (`79b80c7`)

**问题：** AI 仍然打光弹药后撞墙。无法判断行动是否有效。

**新增：**
- `getPlayerX()`、`getPlayerY()` JNI — 地图坐标通过 `mo->x/y >> FRACBITS`
- `hasAmmoForCurrentWeapon()` JNI — 检查 `weaponinfo[readyweapon].ammo`
- 卡墙检测器：跟踪位置变化，10+ 次前进移动但位置不变时标记
- 新特征：state[15]=是否卡墙（二进制），state[16]=是否有弹药
- 网络：17→64→32→14
- 开火奖励重设计：无弹药开火 -0.05，杀敌开火 +0.02（之前盲目 +0.01）
- 卡墙惩罚：-0.10

**问题（测试后）：** Epsilon=1.0 意味着 93% 随机动作。尽管卡墙惩罚存在，AI 仍然选择在墙上前进。DQN 需要约 10K 步才能传播惩罚信号。卡墙检测器只计算移动动作 — FIRE/NOOP 在墙边不增加计数器。

### AI v2.2：强制卡墙恢复 (`ab46902`)

**关键洞察：** 卡墙惩罚只在网络**已学习**之后才有效。在探索阶段（epsilon ~1.0），DQN 忽略自身的 Q 值。修复必须是预学习的：物理上强制 AI 在卡住时转向。

**变更：**
- **所有步骤均检测卡墙**（不仅是移动动作）— 计数器即使在墙边的 FIRE/NOOP 也会增长
- **连续卡墙特征**：state[15] = `stuckCounter / STUCK_RECOVERY`（之前二进制 0/1）
- **阈值降低**：10 → 5 步（8.75 Hz 下约 0.57 秒）
- **强制卡墙恢复**：当 `stuckCounter >= 5`，**绕过 DQN** 强制随机转向/侧移：
  ```java
  int[] turnActions = {3, 4, 5, 6}; // LEFT, RIGHT, STRAFEL, STRAFER
  action = turnActions[rng.nextInt(turnActions.length)];
  ```
- 位置变化后，`stuckCounter` 重置为 0 — 恢复正常 DQN
- **连续惩罚**：`-curr[15] * 0.15`（之前固定 `-0.10`）

**结果：** 无论网络训练状态如何，AI 无法在墙上停留超过约 0.6 秒。

### AI 现在能看到的（共 22 个 JNI 函数）

| 类别 | 函数 |
|----------|-----------|
| 生命/护甲 | `getPlayerHealth`、`getPlayerArmor` |
| 位置 | `getPlayerX`、`getPlayerY`、`getPlayerAngle` |
| 弹药 | `getAmmoBullets/Shells/Rockets/Cells` + 4 个最大值变体 |
| 武器 | `getCurrentWeapon`、`getWeaponsOwned`、`hasAmmoForCurrentWeapon` |
| 统计 | `getKillCount`、`getItemCount`、`getSecretCount` |
| 状态 | `isPlayerDead`、`getDamageCount`、`hasBackpack`、`hasGodMode` |

### 已知限制（v2.1）

- **无敌人检测**：AI 看不到怪物（需要 JNI 遍历 `thinkercap` 查找 mobj_t）
- **无地图几何**：AI 不知道房间布局 — 依赖卡墙检测作为代理
- **无视距检测**：无法判断开火命中与否（仅从 killDelta 推断）
- **状态瞬时**：无帧历史（可通过堆叠最近 N 个状态改进）

---

## DQN 卡死与无进展 — 五项修复大修 (2026-07-07)

**问题：** DQN agent 停止进步——站着不动、撞墙、乱开枪。尽管有 JNI 游戏状态桥接（v2.x），agent 仍无法学会有效行为。

**分析文档：** `docs/en/dqn-stuck-analysis.md` + `docs/zh/dqn-stuck-analysis.md`

### 修复1：真正的 Mini-Batch 梯度累积

|| 方面 | 详情 |
||------|------|
|| **之前** | 32 条样本逐条训练——第 N 条样本的权重更新覆盖前 N-1 条的。实际 batch size = 1。 |
|| **根因** | `NeuralNet.train()` 一次性完成 forward → backward → 权重更新。在循环中被调用 32 次。 |
|| **修复** | 添加梯度累积：`zeroGradients()` → 32× `train()`（仅累积）→ `applyGradients(batchSize)`（一次性应用平均值）。 |
|| **文件** | `NeuralNet.java`（新增 `accumGradW`/`accumGradB` 字段、`zeroGradients()`、`applyGradients()`）；`DQNPlayer.java`（接入 batch 循环） |

### 修复2：真正的 Double DQN

|| 方面 | 详情 |
||------|------|
|| **之前** | 标准 DQN：`targetQ = r + γ · max(targetNet.forward(s'))` — 同一个网络既选动作又估值。高估偏差。 |
|| **根因** | `max()` 运算符挑最高 Q 值，被选中的更可能是被高估的动作。 |
|| **修复** | Double DQN：`bestAction = argmax(onlineNet.forward(s'))` 然后 `targetQ = r + γ · targetNet.forward(s')[bestAction]`。Online 选动作，target 估值。 |
|| **文件** | `DQNPlayer.java`（step() 方法） |

### 修复3：Stuck 检测 — 仅移动动作

|| 方面 | 详情 |
||------|------|
|| **之前** | `stuckCounter` 对所有动作（FIRE、USE、换武器、NOOP）位置不变时都累加。打断战斗。 |
|| **根因** | 位置检查无动作类型守卫。 |
|| **修复** | 仅在 `prevAction ∈ {1..6}`（移动动作：FWD..STRAFER）时累加计数器。FIRE/USE/换武器/NOOP 不计数。任何位置变化都重置。 |
|| **文件** | `AIController.java`（extractState()） |

### 修复4：奖励平衡

|| 方面 | 之前 | 之后 | 原因 |
||------|------|------|------|
|| 存活奖励 | +0.01/步 | +0.003/步 | 太大——消极 agent 什么都不做也能净赚 |
|| Stuck 惩罚 | -0.15 × stuckLevel | -0.20 × stuckLevel | 需要更强的脱离墙壁激励 |
|| NOOP 惩罚 | -0.005 | -0.01 | 站着不动应该净收益为负 |
|| 探索奖励 | 无 | +0.002/位置变化 | 鼓励移动 |

**文件：** `AIController.java`（calculateReward()）

### 修复5：时间特征饱和

|| 方面 | 详情 |
||------|------|
||| **之前** | `state[12] = elapsed / 600f` — 10 分钟才饱和。游戏早期所有状态下都接近 0。 |
||| **修复** | `state[12] = elapsed / 120f` — 2 分钟饱和。 |
||| **文件** | `AIController.java`（extractState()） |

---

## 反向传播 Bug 修复 + JNI 重编译 — 运行时崩溃解决 (2026-07-08)

### Bug: NeuralNet.backward() 数组越界

||| 方面 | 详情 |
|||------|------|
||| **症状** | 每次训练步都报 `AI loop error: ArrayIndexOutOfBoundsException: length=14; index=14`，AI 线程静默死亡。 |
||| **根因** | 反向传播使用了 `weights[l]`（形状：layerSizes[l]×layerSizes[l+1]）而非 `weights[l+1]`（形状：layerSizes[l+1]×layerSizes[l+2]），且 `nextSize = layerSizes[l+1]` 应为 `layerSizes[l+2]`。17→64→32→14 网络中，当 l=1 时，`nextGrad` 仅有 14 个元素但循环迭代 32 次。 |
||| **遗留 Bug** | 此 Bug 存在于原始代码中，被 `catch(Exception)` 静默吞掉，让 AI 看似"卡死"，实则一直在崩溃。 |
||| **修复** | 重写反向传播：使用 `weights[l+1]`，步长 `layerSizes[l+2]`，梯度大小 `layerSizes[l+1]`。按链式法则验证：dL/dz[l+1] = ReLU'(z[l+1]) ⊙ (W[l+1]^T · dL/dz[l+2])。 |
||| **文件** | `NeuralNet.java`（backprop 循环） |

### Bug: libmain.so 缺少 JNI 符号

||| 方面 | 详情 |
|||------|------|
||| **症状** | `UnsatisfiedLinkError: No implementation found for GameState.getPlayerHealth()` |
||| **根因** | 预编译的 `libmain.so` 缺少 `jni_game_state.o` — 0 个 JNI 符号 |
||| **修复** | 重新编译 `jni_game_state.c` 并重链接 `libdoom.so`。通过 `llvm-nm -D` 验证 23 个 JNI 符号。 |
||| **文件** | `libmain.so`（通过 jni_game_state.c + 重链接重建） |

---

## 自动训练循环 — 自主学习 (2026-07-08)

**功能：** AI 可自主训练——自动开局、检测死亡、同关卡重开、定时保存模型。无需人工干预即可通宵训练。

### 状态机

| 状态 | 行为 |
|------|------|
| **IDLE** | 点击 🤖 后等待 5 秒。若用户未手动开局，随机选择关卡 (1-4) + 随机难度 (HMP/UV)，自动操作 DOOM 菜单开局。 |
| **PLAYING** | 正常 DQN 循环：观察 → 行动 → 学习。首次进入时静置 1.5s 避免干扰关卡加载。每 60s 保存模型。 |
| **DEAD** | 通过 JNI 检测死亡。存储终结转移。等待 2s 死亡动画，按空格跳过死亡画面，按回车跳过结算画面。5s 后转入 RESTART。 |
| **RESTART** | 等待 5s，以**相同关卡 + 相同难度**重开，保持学习一致性。重置所有步数计数器。 |

### 关键设计

- **菜单导航**：ESC → Enter（新游戏）→ 方向键 + Enter（选关）→ 方向键 + Enter（选难度）。通过 health>0 验证生效。最多重试 3 次。
- **随机变化**：初次自动开局时随机选择 1-4 关 + HMP/UV 难度（不选 NM，避免与作弊补丁交互）
- **一致重开**：死亡 → 同关卡同难度（不随机），以针对性提升
- **模型持久化**：每 60s 保存 `dqn_model.bin` 到应用内部存储
- **稳健时序**：菜单步骤间隔 800-3000ms，适配慢速 OPPO 渲染

### 文件变更

| 文件 | 变更 |
|------|------|
| `AIController.java` | +自动训练状态机（AP_IDLE/PLAYING/DEAD/RESTART），+`loopIdle()`/`loopPlaying()`/`loopDead()`/`loopRestart()`，+带重试和生命值验证的 `startNewGame()`，+`injectKey()`，+`saveModelToFile()`，+静置期守卫 |
