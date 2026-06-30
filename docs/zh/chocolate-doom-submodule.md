# 源码管理决策：Chocolate Doom 子模块

> [English version](../en/chocolate-doom-submodule.md)

## 背景

在修复 [噩梦难度上帝模式被禁用](nightmare-godmode.md) 时，我们需要修改 Chocolate Doom 的源码（`src/doom/st_stuff.c`）。这引出了一个问题：**应该在哪里、以何种方式管理上游源码？**

## 之前的状态

Chocolate Doom 源码位于 `~/android-toolchain/chocolate-doom-src/` — 一个在仓库之外的、未跟踪的 git 克隆。构建脚本（`build-native.sh`）通过 `$CHOCO_SRC` 指向这个外部路径。这对于原版构建来说没问题，但**源码修改**（如移除噩梦作弊码限制）需要版本控制。

## 决策：Git 子模块

Chocolate Doom 现在作为一个 **git 子模块** 固定在标签 `chocolate-doom-3.1.1`。

```
chocolate-doom-android/
├── native/
│   ├── chocolate-doom/          ← git 子模块 (chocolate-doom/chocolate-doom，标签 3.1.1)
│   └── patches/
│       └── nightmare-cheats.patch   ← 我们的本地修改
├── scripts/build-native.sh      ← 构建前应用补丁
├── app/
└── docs/
```

### 变更对比

| 之前 | 之后 |
|--------|------|
| 源码位于 `~/android-toolchain/chocolate-doom-src/`（未跟踪） | 子模块位于 `native/chocolate-doom/`（已跟踪） |
| `CHOCO_SRC=$HOME/android-toolchain/chocolate-doom-src` | `CHOCO_SRC=$REPO_ROOT/native/chocolate-doom` |
| 无补丁机制 | `build-native.sh` 在 cmake 前应用 `native/patches/*.patch` |
| 仅本机可复现 | 任何机器 `git submodule update --init` 即可复现 |

---

## 为何不直接将源码复制到仓库中？

将完整的 Chocolate Doom 源码（~5000 个 `.c`/`.h` 文件）复制到我们的仓库中会：

- 用与 Android 移植无关的上游提交膨胀我们的 git 历史
- 难以区分哪些变更是上游的、哪些是我们的
- 合并上游更新（chocolate-doom 3.2、安全修复等）变得复杂

子模块提供了**清晰的溯源**：确切的上游提交 + 我们在此基础上的补丁。

---

## 工作原理

### 1. 初始设置（一次性）

```bash
git submodule add https://github.com/chocolate-doom/chocolate-doom native/chocolate-doom
cd native/chocolate-doom
git checkout chocolate-doom-3.1.1
cd ../..
git add native/chocolate-doom
git commit -m "添加 chocolate-doom 子模块 v3.1.1"
```

### 2. 克隆我们的仓库后

```bash
git clone <our-repo> chocolate-doom-android
cd chocolate-doom-android
git submodule update --init    # 将 chocolate-doom 源码拉取到 native/chocolate-doom/
```

### 3. 构建

`build-native.sh` 现在：
1. 必要时初始化和检出子模块
2. 按顺序应用 `native/patches/*.patch`
3. 像以前一样使用 cmake 构建

### 4. 更新上游

```bash
cd native/chocolate-doom
git fetch --tags
git checkout chocolate-doom-3.2.0   # 或任何新标签
cd ../..
git add native/chocolate-doom
git commit -m "将 chocolate-doom 子模块升级至 v3.2.0"
```

---

## 当前补丁

| 补丁 | 文件 | 用途 |
|-------|------|---------|
| `nightmare-cheats.patch` | `src/doom/st_stuff.c` | 移除 `gameskill != sk_nightmare` — 在噩梦难度下允许作弊码（IDDQD/IDFA/IDKFA） |

所有 `native/patches/` 中的补丁由 `build-native.sh` 按字母顺序应用。

---

## 参考资料

- [噩梦难度上帝模式分析](nightmare-godmode.md)
- [构建指南](build.md)
