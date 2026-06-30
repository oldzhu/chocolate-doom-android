# 开发经验教训

> [English version](../en/lessons-learned.md)

供未来开发快速参考。每条记录一个问题、其原因、修复方法以及如何避免再次发生。

---

## 1. 公共源码文件在可执行目标中，不在静态库中

**问题：** `I_VideoBuffer`、`I_InitSound`、`M_CheckParm` 等数十个符号在 `dlopen` 时未定义。

**原因：** Chocolate Doom 的 cmake 将共享源码（`i_video.c`、`i_input.c`、`i_sound.c`、`m_argv.c` 等）放入 `chocolate-doom` 可执行目标 — 而非 `libdoom.a` 或任何静态库。

**修复：** 编译可执行目标仅获取 `.o` 文件（链接步骤失败 — Android 上没有 `main()`）：
```bash
make chocolate-doom -j$(nproc) || true
```
然后将 `$BUILD_DIR/src/CMakeFiles/chocolate-doom.dir/*.c.o` 链接进 `libdoom.so`。

**下次避免：** 链接后运行 `nm -D libdoom.so | grep " U "`。任何来自 `i_*.c` 或 `m_*.c` 的 `U` 符号表示公共源码缺失。

---

## 2. GLES 1.x 和 2.0 两者都需要

**问题：** 50+ 未定义的 OpenGL 符号（`glBindTexture`、`glUseProgram` 等）。

**原因：** Chocolate Doom 使用 GLES 1.x 进行 2D 屏幕渲染，使用 GLES 2.0 进行缩放/着色器。只链接了其中一个。

**修复：** 两者都链接：
```
-lGLESv1_CM -lGLESv2
```

**下次避免：** 如果看到 `gl*` 未定义符号，说明缺少 GLES 库。v1 和 v2 很可能都需要。

---

## 3. C++ ABI 符号需要 `clang++` 链接器

**问题：** `_gxx_personality_v0`、`__cxa_begin_catch` 未定义。

**原因：** SDL2 静态库包含 C++ 代码。`clang`（C 链接器）不引入 C++ 运行时。此外：必须在 APK 中打包 `libc++_shared.so`。

**修复：**
1. 使用 `$CXX`（clang++）作为链接器，而非 `$CC`（clang）
2. 从 NDK 复制 `libc++_shared.so` 到 `jniLibs/arm64-v8a/`

**下次避免：** 如果看到 `_gxx_*` 或 `__cxa_*` 未定义符号，切换到 `clang++`。规则：如果任何链接的静态库包含 C++ 代码，就使用 C++ 链接器。

---

## 4. Android 系统库：`-llog`、`-landroid`、`-lOpenSLES`

**问题：** `__android_log_write`、`ANativeWindow_setBuffersGeometry` 未定义。

**原因：** 这些是 SDL2 依赖的 Android 系统库，但不会隐式链接。

**修复：** 完整的 Android 链接行：
```
-lm -ldl -llog -lOpenSLES -lGLESv1_CM -lGLESv2 -landroid
```

**记忆口诀：** `m-dl-log-sles-gles1-gles2-android` — 每个 Android 上的 SDL2 构建都需要这些。

---

## 5. `-DENABLE_SDL2_MIXER=OFF` 静默禁用所有声音

**问题：** 无声音，无错误，构建成功。

**原因：** `-DENABLE_SDL2_MIXER=OFF` 定义了 `DISABLE_SDL2MIXER=1`，将**整个 `i_sdlsound.c`** 包裹在 `#ifndef DISABLE_SDL2MIXER` 中。声音模块变为空。仅剩 PC 扬声器模拟 — 在 Android 上静音。

**修复：**
1. 设置 `-DENABLE_SDL2_MIXER=ON`
2. 直接以 cmake 变量形式传入 SDL2_mixer 路径（预编译的 SDL2_mixer 没有 cmake 配置）：
   ```
   -DSDL2_MIXER_INCLUDE_DIR=... -DSDL2_MIXER_LIBRARY=... -DSDL2_mixer_FOUND=TRUE
   ```

**下次避免：** 任何 cmake 标志更改后，运行以下验证：
```bash
nm -D libdoom.so | grep "Mix_OpenAudio"    # 必须显示 T（已定义），不是 U
nm -D libdoom.so | grep "sound_sdl_module"  # 必须显示 D（数据）
```

---

## 6. IDDQD XOR 切换在噩梦难度下会杀死你

**问题：** 上帝模式在除噩梦外的所有难度下有效。即使在噩梦难度下注入作弊码，生命值也会降至 0 导致玩家死亡。

**根因（两部分）：**

**部分 A — 噩梦阻止所有作弊码：**
`st_stuff.c:393`：`if (!netgame && gameskill != sk_nightmare)` — 噩梦难度下跳过所有作弊处理。原版 DOOM 的行为。

**部分 B — XOR 切换产生脆弱窗口：**
`st_stuff.c:398`：`plyr->cheats ^= CF_GODMODE;` — 每次 IDDQD 注入会翻转上帝模式的开↔关状态。双重 IDDQD 变通方案在两次注入之间有约 300ms 的关闭窗口。在该窗口内被致命一击 = 死亡。

**修复：**
1. 移除 `gameskill != sk_nightmare` 检查（允许在所有难度下使用作弊码）
2. 将 `^= CF_GODMODE` 改为 `|= CF_GODMODE`（始终 SET，永不切换）

**结果：** 一旦上帝模式激活，永久无敌。无脆弱窗口。

**下次避免：** XOR 切换对于需要永久保持 ON 的状态是危险的。使用 `|=` 来实现始终设置的语义。

---

## 7. 构建成功 ≠ 功能存在

**问题：** 尽管构建成功，但多个功能静默缺失：
- 声音（见 #5）
- 噩梦难度作弊码（如果子模块未更新，则不会编译我们的补丁）

**实践：** 每次构建后，验证预期的符号是否存在：
```bash
# 声音
nm -D libdoom.so | grep Mix_OpenAudio

# 上帝模式
strings libdoom.so | grep "cheats |= CF_GODMODE"

# 检查 NEEDED 库
readelf -d libdoom.so | grep NEEDED
```

---

## 8. OPPO ColorOS 特有怪癖

| 问题 | 缓解措施 |
|-------|-----------|
| 屏幕锁定会终止应用 surface | 启动前务必解锁屏幕 |
| AAudio 被系统策略拒绝 | 回退到标准 AudioTrack — 可接受 |
| 重启后 ADB 断开 | 重新应用 udev 规则：`sudo tee /etc/udev/rules.d/51-android.rules`，idVendor 为 22d9 |
| `setOrientationBis()` 销毁 Surface | 在 `ChocolateDoom.java` 中完全跳过 |

---

## 9. 工具链设置的可复现性

**问题：** 全新克隆无法构建，因为工具链路径是硬编码的。

**修复：** 创建了 `scripts/setup-toolchain.sh`，可下载并安装所有内容：
- NDK r27
- Android SDK 34 + build-tools 34.0.0
- JDK 17（Temurin）
- SDL2 2.30.0（cmake 构建，为 arm64-v8a 交叉编译）
- SDL2_mixer 2.8.0（cmake 构建，使用内置依赖）
- Chocolate Doom 子模块初始化

所有版本均已固定。脚本是幂等的 — 会跳过已安装的组件。

---

## 快速参考：链接器标志

```
# libdoom.so 的最终链接器调用：
$CXX -shared -fPIC -o libdoom.so \
    -Wl,--whole-archive libdoom.a libtextscreen.a libopl.a libpcsound.a \
    -Wl,--no-whole-archive \
    $COMMON_OBJ_DIR/*.c.o \
    $SDL2_LIB $SDL2_MIXER_LIB \
    -lm -ldl -llog -lOpenSLES -lGLESv1_CM -lGLESv2 -landroid
```

## 快速参考：构建命令

```bash
# 一次性工具链安装（约 30 分钟）
./scripts/setup-toolchain.sh

# 构建 + 部署
./scripts/build-native.sh          # → libdoom.so
./scripts/build-apk.sh doom        # → release/chocolate-doom.apk
adb install -r release/chocolate-doom.apk

# 启动
adb shell am start -n com.chocolate.doom/.ChocolateDoom

# 调试
adb logcat | grep -E "(SDL|chocolate|FATAL|dlopen)"
```
