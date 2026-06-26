# 设计与架构

> [English version](../en/design.md)

## 概述

Chocolate Doom Android 将原始 Chocolate Doom 3.1.1 引擎移植到 Android，将其嵌入标准 SDL2 Android Activity 中。引擎在独立线程中原生运行，触控操作以透明 Java `View` 叠加层实现。

## 架构分层

```
┌─────────────────────────────────────────┐
│            TouchControlView              │  ← Java 叠加层（绘制圆形 + 标签）
│   （捕获 MotionEvents → 发送按键事件）   │
├─────────────────────────────────────────┤
│          SDLSurface (GLSurfaceView)      │  ← SDL 在此渲染（OpenGL ES 2.0）
│   （接收 SDL 原生渲染输出）              │
├─────────────────────────────────────────┤
│         ChocolateDoom (SDLActivity)      │  ← Java Activity，生命周期管理
│   - OPPO 专项修复                        │
│   - WAD 路径解析                         │
│   - 原生状态管理                         │
├─────────────────────────────────────────┤
│              JNI 桥接层                   │  ← SDLActivity ↔ SDL_android.c
│   onNativeKeyDown / onNativeKeyUp       │
│   nativeCommitText / nativeSendQuit     │
├─────────────────────────────────────────┤
│          SDL2 (libSDL2.a)                │  ← 静态库
│   事件队列、视频、音频、输入             │
├─────────────────────────────────────────┤
│       SDL2_mixer (libSDL2_mixer.a)       │  ← 静态库（OGG/FLAC/WAV）
├─────────────────────────────────────────┤
│     Chocolate Doom (libmain.so)          │  ← 游戏引擎
│   doom/heretic/hexen/strife/setup       │
└─────────────────────────────────────────┘
```

## 关键设计决策

### 1. 触控控件放在 Java 层，不改 SDL 核心

**决策**: 以透明 Java `View` 叠加层实现虚拟按键，而非修改 SDL 输入代码。

**理由**:
- 无需修改上游 SDL2 或 Chocolate Doom C 代码
- 布局迭代无需 C 重编译
- 利用 Android 原生触摸事件分发
- 所有输入最终都经过 `SDL_SendKeyboardKey()` — 与物理按键完全等价

### 2. 全静态链接

**决策**: 将 SDL2、SDL2_mixer 以及所有 Chocolate Doom 目标文件链接为单一 `libmain.so`。

**理由**:
- 避免 Android 动态链接器的 `dlopen` 交叉依赖问题
- 每个游戏变体一个 .so，APK 打包更简单
- `-Wl,--whole-archive` 确保所有符号被导出

### 3. WAD 文件放在 App 内部存储

**决策**: 从 `/data/data/<package>/files/` 加载 WAD 文件，而非 `/sdcard/`。

**理由**:
- Android 11+ 的分区存储限制阻止从 `/sdcard/` 访问文件
- `getFilesDir()` 返回的路径原生代码可访问
- WAD 通过 `adb` + `run-as` 一次性推送

### 4. OPPO ColorOS 专项修复

OPPO 手机（ColorOS，如 RMX3700）的 Surface 生命周期不同于标准 Android：

```
标准 Android:   create → resume → (surface ready)
OPPO ColorOS:   create → resume → (surface destroyed) → (surface created) → pause
```

系统在熄屏时销毁 GL Surface，然后重新创建 — 但在新 Surface 就绪前暂停 Activity，导致 SDL 死锁。

**已应用的修复**（详见 [changelog.md](changelog.md)）：

| 文件 | 修复内容 |
|------|-----|
| `SDLSurface.java` | 若 Surface 创建时间 <500ms（瞬时销毁/重建），跳过原生暂停 |
| `ChocolateDoom.java` | 在 `onWindowFocusChanged()` 中调用 `handleNativeState(RESUMED)` |
| `ChocolateDoom.java` | 完全跳过 `setOrientationBis()`（避免不必要的 Surface 销毁） |

### 5. Y 键的按键注入方式

**决策**: 使用 `SDLActivity.onNativeKeyDown(KEYCODE_Y)` 而非 `commitText("y")`。

**理由**:
- `commitText` → `SDL_TEXTINPUT` 事件（DOOM 退出确认不识别）
- `onNativeKeyDown` → `SDL_KEYDOWN` 事件，`keysym.sym = 'y'`（DOOM 检查 `ev->type == ev_keydown`）

Android `KEYCODE_Y`（53）通过 `SDL_androidkeyboard.c` 中的 `Android_Keycodes[]` 表映射到 `SDL_SCANCODE_Y`。

## 原生构建流水线

```
SDL2 源码 (2.30.0)     SDL2_mixer 源码 (2.6.3)     Chocolate Doom 源码 (3.1.1)
      │                        │                            │
      ▼                        ▼                            ▼
  cmake -DANDROID      ./configure --host=aarch64    cmake -DANDROID
  -DSDL_SHARED=OFF     --enable-static               -fPIC
      │                        │                            │
      ▼                        ▼                            ▼
  libSDL2.a             libSDL2_mixer.a           doom/heretic/.../*.o
      │                        │                            │
      └────────────────────────┼────────────────────────────┘
                               │
                    aarch64-clang -shared
                    -Wl,--whole-archive ... -Wl,--no-whole-archive
                               │
                               ▼
                        libmain.so（≈10 MB）
```

## 触控设计

`TouchControls` 类继承 `View`，以 `setAlpha(0.5f)` 实现半透明。

### 布局策略

```
┌─────────────────────────────────────────┐
│  菜单 ☰                   Y 键         │  ← 顶部行（始终可见）
│                                         │
│                                         │
│              游戏画面                    │  ← SDL 渲染区域
│                                         │
│                                         │
│  地图 🗺️                    开火 🔫   │  ← 右侧列
│                             使用 🚪    │
│  方向键 ▲                    跑步 🏃   │  ← 底部行
│  ◄ ● ►         横移 ➡      确认 ↵    │
│  ▼                                     │
└─────────────────────────────────────────┘
```

### 按钮行为

| 类型 | 按钮 | 按下 | 释放 |
|------|---------|-------|---------|
| **点按** | 开火、使用、地图、横移、确认、菜单、Y | `keyDown` → 80ms 后自动 `keyUp` | — |
| **长按** | 跑步 | 按下时 `keyDown` | 抬起时 `keyUp` |
| **状态** | 方向键 | 进入区域时 `keyDown` | 离开区域/手指抬起时 `keyUp` |

### 手指追踪

每个手指（pointer）通过 `pointerId` 追踪。`dpadPointerId` 字段记录控制方向键的手指。操作按钮存储 `pressed` 状态 + `pointerId`，以便 `ACTION_UP` 找到并释放对应按钮。

### Y 键 — 特殊处理

DOOM 的退出确认检查 `SDL_KEYDOWN` 事件中的 `keysym.sym == 'y'`。早期使用 `commitText()` 的实验失败，因为它产生 `SDL_TEXTINPUT` 事件。修复方案使用 `onNativeKeyDown(53)`（`KEYCODE_Y`），正确生成 keydown 事件。
