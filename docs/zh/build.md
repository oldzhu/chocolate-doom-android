# 构建指南

> [English version](../en/build.md)

## 快速开始

```bash
# 1. 一次性工具链安装（约30分钟，约4 GB磁盘空间）
./scripts/setup-toolchain.sh

# 2. 构建原生库（arm64-v8a）
./scripts/build-native.sh

# 3. 构建 APK
./scripts/build-apk.sh doom

# 4. 安装到设备
adb install -r release/chocolate-doom.apk

# 5. 推送 WAD 并启动
adb push doom.wad /data/local/tmp/
adb shell run-as com.chocolate.doom cp /data/local/tmp/doom.wad files/doom.wad
adb shell am start -n com.chocolate.doom/.ChocolateDoom
```

## 前置依赖

### 自动安装

`scripts/setup-toolchain.sh` 自动处理所有安装。如需手动安装：

### 手动工具链安装

| 工具 | 版本 | 路径 |
|------|---------|------|
| Android NDK | r27 | `~/android-toolchain/ndk/` |
| Android SDK | 34.0.0 | `~/android-toolchain/SDK/` |
| JDK | 17 | `~/android-toolchain/jdk17/` |
| Chocolate Doom | 3.1.1 | `native/chocolate-doom/`（git 子模块） |
| SDL2 | 2.30.0 | `~/android-toolchain/SDL2-android/`（Android 预编译版） |
| SDL2_mixer | 2.8.0 | `~/android-toolchain/SDL2-android/`（与 SDL2 一起构建） |

#### NDK

```bash
wget https://dl.google.com/android/repository/android-ndk-r27-linux.zip
unzip android-ndk-r27-linux.zip -d ~/android-toolchain/
mv ~/android-toolchain/android-ndk-r27 ~/android-toolchain/ndk
```

#### SDK

```bash
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
mkdir -p ~/android-toolchain/SDK/cmdline-tools
unzip commandlinetools-linux-*.zip -d ~/android-toolchain/SDK/cmdline-tools
mv ~/android-toolchain/SDK/cmdline-tools/cmdline-tools ~/android-toolchain/SDK/cmdline-tools/latest
export ANDROID_HOME=~/android-toolchain/SDK
~/android-toolchain/SDK/cmdline-tools/latest/bin/sdkmanager \
    "platforms;android-34" "build-tools;34.0.0" "platform-tools"
```

#### JDK 17

```bash
wget https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.9%2B9/OpenJDK17U-jdk_x64_linux_hotspot_17.0.9_9.tar.gz
mkdir -p ~/android-toolchain/jdk17
tar xzf OpenJDK17U-jdk_x64_linux_hotspot_*.tar.gz -C ~/android-toolchain/jdk17 --strip-components=1
```

#### SDL2 + SDL2_mixer（Android 预编译版）

```bash
# 克隆
git clone --depth 1 --branch release-2.30.0 \
    https://github.com/libsdl-org/SDL.git ~/android-toolchain/SDL2-src

# 构建 SDL2
mkdir -p ~/android-toolchain/SDL2-build && cd ~/android-toolchain/SDL2-build
export CC=~/android-toolchain/ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android24-clang
cmake ~/android-toolchain/SDL2-src \
    -DCMAKE_SYSTEM_NAME=Android \
    -DCMAKE_SYSTEM_VERSION=24 \
    -DCMAKE_ANDROID_ARCH_ABI=arm64-v8a \
    -DCMAKE_ANDROID_NDK=~/android-toolchain/ndk \
    -DCMAKE_ANDROID_STL_TYPE=c++_static \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_INSTALL_PREFIX=~/android-toolchain/SDL2-android \
    -DSDL_SHARED=OFF -DSDL_STATIC=ON -DSDL_STATIC_PIC=ON \
    -DSDL_TEST=OFF -DSDL_HAPTIC=OFF
make -j$(nproc) && make install

# 构建 SDL2_mixer（使用内置依赖 — 无需外部库）
git clone --depth 1 --branch release-2.8.0 \
    https://github.com/libsdl-org/SDL_mixer.git ~/android-toolchain/SDL2_mixer-src
mkdir -p ~/android-toolchain/SDL2_mixer-build && cd ~/android-toolchain/SDL2_mixer-build
cmake ~/android-toolchain/SDL2_mixer-src \
    -DCMAKE_SYSTEM_NAME=Android \
    -DCMAKE_SYSTEM_VERSION=24 \
    -DCMAKE_ANDROID_ARCH_ABI=arm64-v8a \
    -DCMAKE_ANDROID_NDK=~/android-toolchain/ndk \
    -DCMAKE_ANDROID_STL_TYPE=c++_static \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_PREFIX_PATH=~/android-toolchain/SDL2-android \
    -DSDL2_DIR=~/android-toolchain/SDL2-android/lib/cmake/SDL2 \
    -DSDL2MIXER_VENDORED=ON \
    -DSDL2MIXER_SAMPLES=OFF -DSDL2MIXER_DEPS_SHARED=OFF
make -j$(nproc)
cp libSDL2_mixer.a ~/android-toolchain/SDL2-android/lib/
```

#### Chocolate Doom 子模块

```bash
cd chocolate-doom-android
git submodule update --init
```

## 步骤 1：构建原生库

```bash
./scripts/build-native.sh
```

此脚本运行三个阶段：

### 阶段 1：应用补丁

`build-native.sh` 自动将 `native/patches/` 目录下的所有 `.patch` 文件应用到 Chocolate Doom 子模块。当前包括：
- `nightmare-cheats.patch` — 在噩梦难度下启用作弊码

### 阶段 2：构建静态库（cmake + make）

```bash
# 从 native/chocolate-doom/（子模块）配置 Chocolate Doom
cmake native/chocolate-doom \
    -DCMAKE_SYSTEM_NAME=Android \
    -DCMAKE_SYSTEM_VERSION=24 \
    -DCMAKE_ANDROID_ARCH_ABI=arm64-v8a \
    -DCMAKE_ANDROID_NDK=~/android-toolchain/ndk \
    -DCMAKE_ANDROID_STL_TYPE=c++_static \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_C_FLAGS=-fPIC \
    -DENABLE_SDL2_MIXER=OFF -DENABLE_SDL2_NET=OFF

# 仅构建静态库
make textscreen opl pcsound doom heretic hexen strife -j$(nproc)

# 编译 chocolate-doom 目标以获取公共 .o 文件（i_video.c 等）
# 链接步骤失败（Android 上没有 main()）— 我们只需要 .o 文件
make chocolate-doom -j$(nproc) || true
```

### 阶段 3：链接游戏 .so 文件

```bash
$CXX -shared -fPIC -o libdoom.so \
    -Wl,--whole-archive libdoom.a libtextscreen.a libopl.a libpcsound.a \
    -Wl,--no-whole-archive \
    $COMMON_OBJ_DIR/*.c.o \
    ~/android-toolchain/SDL2-android/lib/libSDL2.a \
    ~/android-toolchain/SDL2-android/lib/libSDL2_mixer.a \
    -lm -ldl -llog -lOpenSLES -lGLESv1_CM -lGLESv2 -landroid
```

**关键细节：**
- **`$CXX` (clang++)，非 clang** — SDL2 静态库包含 C++ 代码；C 链接器无法解析 C++ ABI 符号
- **公共 `.c.o` 文件** — `i_video.c`、`i_input.c` 等在可执行目标中，不在静态库中。我们编译可执行目标仅获取 `.o` 文件（链接失败是预期且被忽略的）。
- **`-lGLESv1_CM -lGLESv2`** — Chocolate Doom 同时使用 GLES 1.x（2D 渲染）和 2.0（缩放/着色器）
- **`-llog -landroid`** — SDL2 需要的 Android 系统库
- **`-lOpenSLES`** — Android 音频输出

**打包到 APK 中：**
- `libdoom.so`（游戏引擎）
- `libc++_shared.so`（C++ 运行时，来自 NDK）

输出：`~/android-toolchain/apk/jniLibs/arm64-v8a/libdoom.so`（约 6.6 MB）

## 步骤 2：构建 APK

```bash
./scripts/build-apk.sh doom       # 也可用：heretic、hexen、strife
```

此脚本：

1. 编译所有 `.java` 文件 → `.class`（javac，source=8，target=8）
2. 转换 `.class` → `classes.dex`（d8）
3. 生成 `AndroidManifest.xml`（aapt2 link）
4. 打包 `classes.dex` + `lib/arm64-v8a/libmain.so` + `lib/arm64-v8a/libc++_shared.so` → `.apk`（zip）
5. 使用调试密钥库签名（apksigner）

输出：`release/chocolate-doom.apk`（约 2.7 MB）

## 步骤 3：安装并运行

```bash
# 安装
adb install -r release/chocolate-doom.apk

# 推送 WAD（一次性）
adb push doom.wad /data/local/tmp/
adb shell run-as com.chocolate.doom cp /data/local/tmp/doom.wad files/doom.wad

# 启动
adb shell am start -n com.chocolate.doom/.ChocolateDoom
```

## 故障排查

### "WAD file not found"

确保 WAD 位于 `/data/data/com.chocolate.doom/files/doom.wad`：
```bash
adb shell run-as com.chocolate.doom ls -la files/
```

### "dlopen failed: cannot locate symbol"

这意味着缺少原生库依赖。常用诊断方法：
```bash
# 检查 libdoom.so 需要什么
readelf -d ~/android-toolchain/apk/jniLibs/arm64-v8a/libdoom.so | grep NEEDED

# 验证所有符号是否已解析
nm -D ~/android-toolchain/apk/jniLibs/arm64-v8a/libdoom.so | grep " U "
```

已知符号错误及其修复：
| 缺失符号 | 所需库 |
|---------------|-----------------|
| `_gxx_personality_v0` | `libc++_shared.so`（C++ 运行时）— 切换到 clang++ 链接器 |
| `__android_log_write` | `liblog.so` — 添加 `-llog` |
| `ANativeWindow_setBuffersGeometry` | `libnativewindow.so` — 添加 `-landroid` |
| `glBindTexture` | `libGLESv1_CM.so` 或 `libGLESv2.so` — 添加 `-lGLESv1_CM -lGLESv2` |
| `I_VideoBuffer` | 公共 `.o` 文件未链接 — 编译 `chocolate-doom` 目标 |

### "应用启动即崩溃"

- 屏幕是否已开启且已解锁？OPPO 手机在屏幕锁定时会主动暂停应用。
- 检查 logcat：`adb logcat | grep -E "(dlopen|FATAL|chocolate)"`
- 验证 APK 中包含 `libc++_shared.so`：`unzip -l release/chocolate-doom.apk | grep libc++`

### "没有声音"

- 验证 SDL2_mixer 已链接：`readelf -s libdoom.so | grep Mix_`
- 在 Android 上，音频使用 OpenSL ES — 验证链接器标志中包含 `-lOpenSLES`

### "APK 安装失败，签名不匹配"

先卸载旧版本：
```bash
adb uninstall com.chocolate.doom
adb install -r release/chocolate-doom.apk
```

### "build-native.sh 失败，找不到 cmake"

此脚本使用 Android SDK 自带的 cmake。确保 SDK 已安装：
```bash
ls ~/android-toolchain/SDK/cmake/3.22.1/bin/cmake
```

### "OPPO（ColorOS）手机：udev / adb 不工作"

每次重启后，OPPO 需要手动设置 udev：
```bash
echo 'SUBSYSTEM=="usb", ATTR{idVendor}=="22d9", MODE="0666"' | sudo tee /etc/udev/rules.d/51-android.rules
sudo udevadm control --reload-rules && sudo udevadm trigger
```

## 构建可复现性

构建设计为完全可复现：
- **Chocolate Doom 源码**：通过子模块固定到精确的 git 标签 `chocolate-doom-3.1.1`
- **SDL2**：固定到 `release-2.30.0` 标签，作为静态库构建
- **SDL2_mixer**：固定到 `release-2.8.0` 标签，使用内置依赖（无需外部音频库）
- **补丁**：在 `native/patches/` 中进行版本控制，每次构建前自动应用
- **工具链**：NDK r27、API 级别 24、arm64-v8a — 全部固定版本

验证干净的构建是否可行：
```bash
# 全新克隆
git clone <repo-url> chocolate-doom-android && cd chocolate-doom-android

# 安装工具链
./scripts/setup-toolchain.sh

# 构建所有内容
./scripts/build-native.sh && ./scripts/build-apk.sh doom

# APK 应位于 release/chocolate-doom.apk（约 2.7 MB）
```
