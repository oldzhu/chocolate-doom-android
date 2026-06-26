# 更新日志与修复记录

> [English version](../en/changelog.md)

Chocolate Doom Android 移植开发过程中所有决策、修复与变更的完整记录。

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
