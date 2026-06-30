# 噩梦难度 — 上帝模式被禁用

> [English version](../en/nightmare-godmode.md)

## 问题

在 **噩梦（Nightmare）** 难度下，上帝模式作弊码（连按三下 ☰ 菜单）无效：

- 生命值**未被锁定** — 受到伤害时会减少
- 弹药**并非无限** — 开火时会消耗
- 所有武器/钥匙**未被授予** — IDFA/IDKFA 无效

在其他所有难度级别（I'm Too Young to Die 至 Ultra-Violence）中，上帝模式正常工作。

---

## 根因分析

### 阻塞代码

Chocolate Doom 的作弊码处理函数（`src/doom/st_stuff.c`，第 393 行）将所有作弊码处理包裹在难度检查中：

```c
if (!netgame && gameskill != sk_nightmare)
{
    // IDDQD — 无敌模式
    if (cht_CheckCheat(&cheat_god, ev->data2)) { ... }

    // IDFA — 弹药 + 武器
    else if (cht_CheckCheat(&cheat_ammonokey, ev->data2)) { ... }

    // IDKFA — 弹药 + 武器 + 钥匙
    else if (cht_CheckCheat(&cheat_ammo, ev->data2)) { ... }
}
```

条件 `gameskill != sk_nightmare` 的意思是：**当难度为噩梦时，跳过整个作弊码处理代码块**。在噩梦难度下，任何作弊码——IDDQD、IDFA、IDKFA 或其它——都不会被处理。

### 这是原版 DOOM 的行为

这不是我们移植版的 bug。Chocolate Doom（追求 100% 还原原版）忠实地复现了原版 DOOM v1.9 的行为。设计理念是：噩梦难度应该是终极挑战——禁用作弊码。

### 我们的作弊码注入为何失败

我们的 `TouchControls.java` 通过 `SDLActivity.onNativeKeyDown/Up` 注入作弊码字符：

```
Java (TouchControls.java)
    → SDLActivity.onNativeKeyDown(keyCode)
        → SDL2 → Android_OnKeyDown
            → SDL_SendKeyboardKey (type=SDL_KEYDOWN)
                → Chocolate Doom 事件循环
                    → ST_Responder 检查: "gameskill != sk_nightmare?" → 是 → 跳过
```

键盘事件确实到达了，但游戏引擎的 `ST_Responder` 函数因为难度检查拒绝处理它们。Java 层对此无能为力——拒绝发生在原生 C 代码深处。

---

## 解决方案

### 方案 A：二进制补丁 libdoom.so（快速）

用十六进制编辑已编译的 `libdoom.so`，将噩梦难度比较指令替换为 NOP。

**优点：**
- 快 — 无需完整重新构建
- 适用于现有预编译二进制文件

**缺点：**
- **每次从源码重新构建 `libdoom.so` 都会被覆盖**
- 脆弱 — 不同编译器版本/优化级别下二进制补丁可能失效
- 难以复现和维护

**工作量：** 一次性约 30 分钟，但每次原生重新构建后必须重新应用。

### 方案 B：源码补丁 + 重新构建（✅ 推荐）

修改 Chocolate Doom 源码移除噩梦难度检查，然后通过现有的 `build-native.sh` 流程重新构建 `libdoom.so`。

**优点：**
- **永久性修复** — 所有未来的重新构建都包含此修改
- 干净的、可审计的、可版本控制的变更
- NDK 工具链已有（`~/android-toolchain/ndk/`）
- 源码是本地构建的 — `build-native.sh` 已从 `~/android-toolchain/chocolate-doom-src/` 构建

**缺点：**
- 需要克隆 Chocolate Doom 源码（一次性，约 5 分钟）
- 完整原生重新构建需要约 5-10 分钟

**工作量：** 约 15 分钟（克隆 + 补丁 + 重新构建）。

### 方案 C：接受限制（不做修改）

噩梦模式 + 无作弊码 = 原版行为。按设计运行。

**优点：**
- 零工作量
- 忠实于原版 DOOM 体验

**缺点：**
- 上帝模式功能部分失效（已宣传但噩梦难度下不可用）

---

## 采用方案：B

**决策：** 方案 B — 源码补丁 + 重新构建。

**理由：**
1. `libdoom.so` 是从**本地源码**构建的（`build-native.sh` → `~/android-toolchain/chocolate-doom-src/`），不是预编译的 blob。
2. NDK 工具链已安装且可用。
3. 源码补丁是持久的，不会被未来的重新构建覆盖。
4. 可以将补丁提交到我们的仓库以供复现。

### 实施计划

1. **克隆源码**：`git clone https://github.com/chocolate-doom/chocolate-doom.git ~/android-toolchain/chocolate-doom-src`
2. **修补**：从 `st_stuff.c:393` 移除 `gameskill != sk_nightmare`
3. **重新构建**：运行 `scripts/build-native.sh`
4. **重新构建 APK**：运行 `scripts/build-apk.sh doom`
5. **部署**：安装到设备

### 补丁详情

在 `src/doom/st_stuff.c` 中，将：

```c
// 修改前（噩梦难度禁用作弊码）:
    if (!netgame && gameskill != sk_nightmare)
    {

// 修改后（所有难度允许作弊码）:
    if (!netgame)
    {
```

仅移除噩梦限制。联机游戏作弊码禁用保留（多人游戏中禁用作弊码 — 一个独立且有效的限制）。

---

## 参考资料

- **Chocolate Doom 源码**：`src/doom/st_stuff.c`，函数 `ST_Responder`，约第 393 行
- **作弊码定义**：同文件，第 315-320 行（`cheat_god`、`cheat_ammo`、`cheat_ammonokey`）
- **我们的作弊码注入**：`app/src/main/java/com/chocolate/doom/TouchControls.java`，`injectCheatSequence()`、`activateGodMode()`
- **构建脚本**：`scripts/build-native.sh`、`scripts/build-apk.sh`
