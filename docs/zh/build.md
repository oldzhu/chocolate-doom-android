# 构建指南

> [English version](../en/build.md)

## 前置条件

| 工具 | 版本 | 路径（默认） |
|------|---------|----------------|
| Android NDK | r27 | `~/android-toolchain/ndk/` |
| Android SDK | 34.0.0 | `~/android-toolchain/SDK/` |
| JDK | 17 | `~/android-toolchain/jdk17/` |
| Chocolate Doom | 3.1.1 | `~/android-toolchain/chocolate-doom-src/` |
| SDL2 | 2.30.0 | `~/android-toolchain/SDL2-src/` |
| SDL2_mixer | 2.6.3 | `~/android-toolchain/SDL2_mixer-2.6.3/` |

### 安装 NDK 和 SDK

```bash
# NDK
unzip ndk.zip -d ~/android-toolchain/

# SDK（命令行工具）
unzip cmdline-tools.zip -d ~/android-toolchain/
export ANDROID_HOME=~/android-toolchain/SDK
sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"
```

### 克隆依赖

```bash
cd ~/android-toolchain

# Chocolate Doom
git clone https://github.com/chocolate-doom/chocolate-doom.git chocolate-doom-src
cd chocolate-doom-src && git checkout chocolate-doom-3.1.1 && cd ..

# SDL2
git clone https://github.com/libsdl-org/SDL.git SDL2-src
cd SDL2-src && git checkout release-2.30.0 && cd ..

# SDL2_mixer
wget https://github.com/libsdl-org/SDL_mixer/releases/download/release-2.6.3/SDL2_mixer-2.6.3.tar.gz
tar xzf SDL2_mixer-2.6.3.tar.gz
```

## 第一步：编译原生库

```bash
cd chocolate-doom-android
./scripts/build-native.sh
```

此脚本执行三个阶段：

### 阶段 1：SDL2（静态库）

```bash
cd ~/android-toolchain/SDL2-build
cmake ~/android-toolchain/SDL2-src \
    -DCMAKE_SYSTEM_NAME=Android \
    -DCMAKE_SYSTEM_VERSION=24 \
    -DCMAKE_ANDROID_ARCH_ABI=arm64-v8a \
    -DCMAKE_ANDROID_NDK=~/android-toolchain/ndk \
    -DSDL_SHARED=OFF \
    -DSDL_STATIC=ON
make -j$(nproc)
```

产物：`libSDL2.a`（≈2 MB）

### 阶段 2：SDL2_mixer（静态库）

```bash
cd ~/android-toolchain/SDL2_mixer-2.6.3/build-android
../configure \
    --host=aarch64-linux-android \
    --enable-static --disable-shared \
    CFLAGS="-fPIC -O2"
make -j$(nproc)
```

产物：`build/.libs/libSDL2_mixer.a`（≈500 KB）

### 阶段 3：Chocolate Doom → libmain.so

首先以静态库形式构建各游戏，确保 `-DDISABLE_SDL2MIXER=0`：

```bash
cd ~/android-toolchain/choc-build-pic
cmake ~/android-toolchain/chocolate-doom-src \
    -DCMAKE_SYSTEM_NAME=Android \
    -DCMAKE_SYSTEM_VERSION=24 \
    -DCMAKE_ANDROID_ARCH_ABI=arm64-v8a \
    -DCMAKE_ANDROID_NDK=~/android-toolchain/ndk \
    -DCMAKE_C_FLAGS="-fPIC"
make -j$(nproc)
```

然后将各游戏链接为 `lib<game>.so`：

```bash
aarch64-linux-android24-clang -shared -fPIC -o libdoom.so \
    -Wl,--whole-archive \
    src/doom/*.a textscreen/*.a opl/*.a pcsound/*.a \
    -Wl,--no-whole-archive \
    ~/android-toolchain/SDL2-build/libSDL2.a \
    ~/android-toolchain/SDL2_mixer-2.6.3/build-android/build/.libs/libSDL2_mixer.a \
    -lm -ldl -lOpenSLES
```

产物：`libdoom.so`、`libheretic.so`、`libhexen.so`、`libstrife.so`、`libsetup.so`

## 第二步：构建 APK

```bash
./scripts/build-apk.sh doom       # 也可选: heretic hexen strife setup
```

此脚本执行：

1. 编译所有 `.java` → `.class`（javac，source=8，target=8）
2. 转换 `.class` → `classes.dex`（d8）
3. 生成 `AndroidManifest.xml`（aapt2）
4. 打包 `classes.dex` + `lib/arm64-v8a/libmain.so` → `.apk`（zip）
5. 使用调试密钥库签名（apksigner）

产物：`release/chocolate-doom.apk`（≈10 MB）

## 第三步：安装到设备

```bash
./scripts/adb-install.sh
```

或手动操作：

```bash
# 安装 APK
adb install -r release/chocolate-doom.apk

# 将 WAD 推送到内部存储
adb push freedoom1.wad /data/local/tmp/doom.wad
adb shell run-as com.chocolate.doom cp /data/local/tmp/doom.wad files/doom.wad

# 启动
adb shell am start -n com.chocolate.doom/.ChocolateDoom
```

## 故障排查

### "WAD file not found"（找不到 WAD 文件）
- 确认 WAD 位于 `/data/data/com.chocolate.doom/files/doom.wad`
- 验证：`adb shell run-as com.chocolate.doom ls -la files/`

### "App crashes on launch"（应用启动闪退）
- `libmain.so` 是否使用 `-fPIC` 编译？检查：`readelf -d libmain.so | grep TEXTREL`
- OPPO 设备？启动前确保屏幕已亮且已解锁。

### "No sound"（无声音）
- 确认 SDL2_mixer 已链接：`readelf -s libmain.so | grep Mix_`
- 检查 CMake 标志中未设置 `-DDISABLE_SDL2MIXER`。

### "APK install fails with signature mismatch"（签名不匹配）
- 先卸载旧版本：`adb uninstall com.chocolate.doom`
- 再重新安装。

### "Y button doesn't quit"（Y 键无法退出）
- 确认 `TouchControls.java` 使用 `onNativeKeyDown(53)` 而非 `sendText()`。
- 确认 `SDLActivity.java` 包含 `sendText()` 包装方法（作为备选方案）。
