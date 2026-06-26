# Chocolate Doom Android — 巧克力毁灭战士安卓移植版

[English](#english) | [中文](#chinese)

---

<a name="english"></a>
## English

Android port of [Chocolate Doom 3.1.1](https://github.com/chocolate-doom/chocolate-doom) with on-screen touch controls, working sound (SDL2_mixer), and OPPO ColorOS fixes. Playable with [Freedoom](https://freedoom.github.io/) copyright-safe WADs.

### Features

- ✅ On-screen virtual touch controls (D-pad, Fire, Use, Run, Map, Enter, Escape, Y)
- ✅ Sound: music + SFX via SDL2_mixer (OGG/Vorbis, FLAC, WAV)
- ✅ OPPO ColorOS screen-off / surface-destroy lifecycle workarounds
- ✅ Freedoom Phase 1 + Phase 2 WAD support
- ✅ All five game variants: **Doom · Heretic · Hexen · Strife · Setup**
- ✅ Arm64-v8a native code, API 24+

### Quick Start (Pre-built APK)

1. Download `release/chocolate-doom.apk` from [GitHub Releases](https://github.com/chocolate-doom-android/releases)
2. Install: `adb install chocolate-doom.apk`
3. Push WAD: `adb push freedoom1.wad /sdcard/Android/data/com.chocolate.doom/files/doom.wad`
4. Launch the app on your phone

### Building from Source

See [docs/en/build.md](docs/en/build.md) for full instructions.

Quick build (requires Android NDK + SDK):
```bash
# 1. Cross-compile native code (SDL2 + SDL2_mixer + chocolate-doom → libmain.so)
./scripts/build-native.sh

# 2. Build and sign the APK
./scripts/build-apk.sh doom      # or: heretic hexen strife setup

# 3. Install to device
./scripts/adb-install.sh
```

### Controls Layout

| Button | Key | Action |
|--------|-----|--------|
| ▲▼◄► | D-pad | Move / Menu navigate |
| 🔫 | Ctrl | Fire |
| 🚪 | Space | Use / Open |
| 🏃 | Shift | Run (hold to sprint) |
| 🗺️ | Tab | Auto-map |
| ➡ | DPAD Right | Strafe right |
| ↵ | Enter | Select / Confirm |
| ☰ | Escape | Menu |
| **Y** | Y | Yes / Quit confirm |

Full design: [docs/en/controls.md](docs/en/controls.md)

### Documentation

| Document | EN | 中文 |
|----------|----|------|
| Build Guide | [en/build.md](docs/en/build.md) | [zh/build.md](docs/zh/build.md) |
| Design & Architecture | [en/design.md](docs/en/design.md) | [zh/design.md](docs/zh/design.md) |
| Controls Specification | [en/controls.md](docs/en/controls.md) | [zh/controls.md](docs/zh/controls.md) |
| Changelog & Fixes | [en/changelog.md](docs/en/changelog.md) | [zh/changelog.md](docs/zh/changelog.md) |

### Project Layout

```
chocolate-doom-android/
├── app/                          # Android Java sources (activity + controls)
│   └── src/main/java/com/chocolate/
│       ├── doom/                 # ChocolateDoom.java, TouchControls.java
│       ├── heretic/              # ChocolateHeretic.java
│       ├── hexen/                # ChocolateHexen.java
│       ├── strife/               # ChocolateStrife.java
│       └── setup/                # ChocolateSetup.java
├── sdl_patches/                  # SDL2 2.30.0 with OPPO fixes + sendText()
│   └── org/libsdl/app/           # Full SDL Java tree (9 files)
├── scripts/                      # Build + install scripts
│   ├── build-native.sh           # Cross-compile native .so libs
│   ├── build-apk.sh              # Java → DEX → APK → sign
│   └── adb-install.sh            # Push APK + WAD to device
├── docs/                         # Bilingual documentation
│   ├── en/                       # English docs
│   └── zh/                       # 中文文档
└── release/                      # Pre-built APKs
    └── chocolate-doom.apk
```

### License

| Component | License | Full Text |
|-----------|---------|-----------|
| Our Java sources | **MIT** | [LICENSE](LICENSE) |
| Chocolate Doom | **GPLv2** | [COPYING.GPL](COPYING.GPL) |
| SDL2 & SDL2_mixer | **zlib** | [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md) |
| Freedoom WADs | **BSD 3-Clause** | [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md) |

All licenses are GPLv2-compatible. The combined APK is effectively GPLv2.
Our MIT-licensed files may also be used under GPLv2 when distributed together
with Chocolate Doom. See [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md)
for detailed compliance information.

---

<a name="chinese"></a>
## 中文

[Chocolate Doom 3.1.1](https://github.com/chocolate-doom/chocolate-doom) 的安卓移植版，带屏幕虚拟触控、音效（SDL2_mixer），以及 OPPO ColorOS 专项修复。可使用版权安全的 [Freedoom](https://freedoom.github.io/) WAD 文件游玩。

### 功能特性

- ✅ 屏幕虚拟触控（方向键、开火、使用、跑步、地图、确认、菜单、Y键）
- ✅ 完整音效：背景音乐 + 音效（SDL2_mixer，支持 OGG/FLAC/WAV）
- ✅ OPPO ColorOS 熄屏/界面销毁生命周期修复
- ✅ 支持 Freedoom Phase 1 + Phase 2 WAD 文件
- ✅ 五个游戏版本：**Doom · Heretic · Hexen · Strife · Setup**
- ✅ Arm64-v8a 原生代码，API 24+

### 快速开始（预编译 APK）

1. 从 [GitHub Releases](https://github.com/chocolate-doom-android/releases) 下载 `release/chocolate-doom.apk`
2. 安装：`adb install chocolate-doom.apk`
3. 推送 WAD：`adb push freedoom1.wad /sdcard/Android/data/com.chocolate.doom/files/doom.wad`
4. 手机启动应用

### 从源码构建

详见 [docs/zh/build.md](docs/zh/build.md)（完整构建指南）。

快速构建（需安装 Android NDK + SDK）：
```bash
# 1. 交叉编译原生代码 (SDL2 + SDL2_mixer + chocolate-doom → libmain.so)
./scripts/build-native.sh

# 2. 编译并签名 APK
./scripts/build-apk.sh doom      # 或: heretic hexen strife setup

# 3. 安装到设备
./scripts/adb-install.sh
```

### 触控布局

| 按钮 | 按键 | 功能 |
|--------|-----|--------|
| ▲▼◄► | 方向键 | 移动 / 菜单导航 |
| 🔫 | Ctrl | 开火 |
| 🚪 | Space | 使用 / 开门 |
| 🏃 | Shift | 跑步（按住快跑） |
| 🗺️ | Tab | 自动地图 |
| ➡ | DPAD Right | 向右平移 |
| ↵ | Enter | 选择 / 确认 |
| ☰ | Escape | 菜单 |
| **Y** | Y | 是 / 确认退出 |

详细设计：[docs/zh/controls.md](docs/zh/controls.md)

### 文档索引

| 文档 | EN | 中文 |
|----------|----|------|
| 构建指南 | [en/build.md](docs/en/build.md) | [zh/build.md](docs/zh/build.md) |
| 设计与架构 | [en/design.md](docs/en/design.md) | [zh/design.md](docs/zh/design.md) |
| 触控规格 | [en/controls.md](docs/en/controls.md) | [zh/controls.md](docs/zh/controls.md) |
| 更新日志与修复 | [en/changelog.md](docs/en/changelog.md) | [zh/changelog.md](docs/zh/changelog.md) |

### 项目结构

```
chocolate-doom-android/
├── app/                          # Android Java 源码（Activity + 触控）
│   └── src/main/java/com/chocolate/
│       ├── doom/                 # ChocolateDoom.java, TouchControls.java
│       ├── heretic/              # ChocolateHeretic.java
│       ├── hexen/                # ChocolateHexen.java
│       ├── strife/               # ChocolateStrife.java
│       └── setup/                # ChocolateSetup.java
├── sdl_patches/                  # SDL2 2.30.0 + OPPO 修复 + sendText()
│   └── org/libsdl/app/           # 完整 SDL Java 文件（9个）
├── scripts/                      # 构建 + 安装脚本
│   ├── build-native.sh           # 交叉编译原生 .so 库
│   ├── build-apk.sh              # Java → DEX → APK → 签名
│   └── adb-install.sh            # 推送 APK + WAD 到设备
├── docs/                         # 中英双语文档
│   ├── en/                       # 英文文档
│   └── zh/                       # 中文文档
└── release/                      # 预编译 APK
    └── chocolate-doom.apk
```

### 许可证

| 组件 | 许可证 | 全文 |
|-----------|---------|-----------|
| 本项目的 Java 源码 | **MIT** | [LICENSE](LICENSE) |
| Chocolate Doom | **GPLv2** | [COPYING.GPL](COPYING.GPL) |
| SDL2 & SDL2_mixer | **zlib** | [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md) |
| Freedoom WADs | **BSD 3-Clause** | [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md) |

所有许可证均与 GPLv2 兼容。合并后的 APK 实质为 GPLv2。
我们的 MIT 许可文件在与 Chocolate Doom 一同分发时，也可按 GPLv2 条款使用。
详见 [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md)。
