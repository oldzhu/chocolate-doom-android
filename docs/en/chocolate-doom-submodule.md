# Source Management Decision: Chocolate Doom Submodule

> [中文版 (Chinese)](../zh/chocolate-doom-submodule.md)

## Context

When fixing the [Nightmare God Mode blocker](nightmare-godmode.md), we needed to modify Chocolate Doom's source code (`src/doom/st_stuff.c`). This raised the question: **where and how should we manage the upstream source?**

## Previous State

Chocolate Doom source lived at `~/android-toolchain/chocolate-doom-src/` — an untracked git clone outside our repo. The build script (`build-native.sh`) referenced it via `$CHOCO_SRC` pointing to this external path. This was fine for a vanilla build, but **source modifications** (like removing the Nightmare cheat block) require version control.

## Decision: Git Submodule

Chocolate Doom is now a **git submodule** pinned to tag `chocolate-doom-3.1.1`.

```
chocolate-doom-android/
├── native/
│   ├── chocolate-doom/          ← git submodule (chocolate-doom/chocolate-doom, tag 3.1.1)
│   └── patches/
│       └── nightmare-cheats.patch   ← our local modifications
├── scripts/build-native.sh      ← applies patch before building
├── app/
└── docs/
```

### What Changed

| Before | After |
|--------|-------|
| Source at `~/android-toolchain/chocolate-doom-src/` (untracked) | Submodule at `native/chocolate-doom/` (tracked) |
| `CHOCO_SRC=$HOME/android-toolchain/chocolate-doom-src` | `CHOCO_SRC=$REPO_ROOT/native/chocolate-doom` |
| No patching | `build-native.sh` applies `native/patches/*.patch` before cmake |
| Reproducible only on this machine | Reproducible on any machine after `git submodule update --init` |

---

## Why Not Copy Source In-Tree?

Copying the full Chocolate Doom source (~5000 `.c`/`.h` files) into our repo would:

- Bloat our git history with upstream commits irrelevant to the Android port
- Make it unclear which changes are upstream vs ours
- Complicate merging upstream updates (chocolate-doom 3.2, security fixes, etc.)

A submodule gives us **clean provenance**: exact upstream commit + our patches on top.

---

## How It Works

### 1. Initial Setup (one-time)

```bash
git submodule add https://github.com/chocolate-doom/chocolate-doom native/chocolate-doom
cd native/chocolate-doom
git checkout chocolate-doom-3.1.1
cd ../..
git add native/chocolate-doom
git commit -m "Add chocolate-doom submodule at v3.1.1"
```

### 2. After Cloning Our Repo

```bash
git clone <our-repo> chocolate-doom-android
cd chocolate-doom-android
git submodule update --init    # pulls chocolate-doom source into native/chocolate-doom/
```

### 3. Building

`build-native.sh` now:
1. Checks out the submodule if needed
2. Applies `native/patches/*.patch` in order
3. Builds with cmake as before

### 4. Updating Upstream

```bash
cd native/chocolate-doom
git fetch --tags
git checkout chocolate-doom-3.2.0   # or any new tag
cd ../..
git add native/chocolate-doom
git commit -m "Bump chocolate-doom submodule to v3.2.0"
```

---

## Current Patches

| Patch | File | Purpose |
|-------|------|---------|
| `nightmare-cheats.patch` | `src/doom/st_stuff.c` | Remove `gameskill != sk_nightmare` — allows cheat codes (IDDQD/IDFA/IDKFA) on Nightmare difficulty |

All patches in `native/patches/` are applied in alphabetical order by `build-native.sh`.

---

## References

- [Nightmare God Mode Analysis](nightmare-godmode.md)
- [Build Guide](build.md)
