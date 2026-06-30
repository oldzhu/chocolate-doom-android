# Nightmare Difficulty — God Mode Blocked

> [中文版 (Chinese)](../zh/nightmare-godmode.md)

## Issue

When playing on **Nightmare** skill level, the God Mode cheat (triple-tap ☰ Menu) has no effect:

- Health is **not locked** — it decreases when taking damage
- Ammo is **not infinite** — it depletes when firing
- All weapons/keys are **not granted** — IDFA/IDKFA has no effect

On all other difficulty levels (I'm Too Young to Die through Ultra-Violence), God Mode works correctly.

---

## Root Cause Analysis

### The Blocking Code

Chocolate Doom's cheat handler (`src/doom/st_stuff.c`, line 393) wraps all cheat processing inside a difficulty check:

```c
if (!netgame && gameskill != sk_nightmare)
{
    // IDDQD — god mode
    if (cht_CheckCheat(&cheat_god, ev->data2)) { ... }

    // IDFA — ammo + weapons
    else if (cht_CheckCheat(&cheat_ammonokey, ev->data2)) { ... }

    // IDKFA — ammo + weapons + keys
    else if (cht_CheckCheat(&cheat_ammo, ev->data2)) { ... }
}
```

The condition `gameskill != sk_nightmare` means: **if the skill is Nightmare, skip the entire cheat processing block**. No cheat codes — IDDQD, IDFA, IDKFA, or any other — are processed on Nightmare.

### This is Vanilla Doom Behavior

This is not a bug in our port. It's original Doom v1.9 behavior faithfully reproduced by Chocolate Doom (which aims for 100% vanilla accuracy). The rationale: Nightmare is meant to be the ultimate challenge — no cheats allowed.

### How Our Cheat Injection Fails

Our `TouchControls.java` injects cheat characters via `SDLActivity.onNativeKeyDown/Up`:

```
Java (TouchControls.java)
    → SDLActivity.onNativeKeyDown(keyCode)
        → SDL2 → Android_OnKeyDown
            → SDL_SendKeyboardKey (type=SDL_KEYDOWN)
                → Chocolate Doom event loop
                    → ST_Responder checks: "gameskill != sk_nightmare?" → YES → SKIP
```

The keyboard events arrive correctly, but the game engine's `ST_Responder` function refuses to process them because of the difficulty check. Our Java layer has no control over this — the rejection happens deep in native C code.

---

## Resolution Options

### Option A: Binary Patch libdoom.so (Quick)

Hex-patch the compiled `libdoom.so` to NOP out the Nightmare comparison instruction.

**Pros:**
- Fast — no full rebuild needed
- Works with existing prebuilt binary

**Cons:**
- **Will be overwritten** every time you rebuild `libdoom.so` from source
- Fragile — binary patches can break with different compiler versions/optimizations
- Harder to reproduce and maintain

**Effort:** ~30 min one-time, but must re-apply after every native rebuild.

### Option B: Source Patch + Rebuild (✅ Recommended)

Modify the Chocolate Doom source to remove the Nightmare check, then rebuild `libdoom.so` via the existing `build-native.sh` pipeline.

**Pros:**
- **Permanent fix** — survives all future rebuilds
- Clean, auditable, version-controllable change
- NDK toolchain already available (`~/android-toolchain/ndk/`)
- Source is local — `build-native.sh` already builds from `~/android-toolchain/chocolate-doom-src/`

**Cons:**
- Requires cloning Chocolate Doom source (one-time, ~5 min)
- Full native rebuild takes ~5-10 minutes

**Effort:** ~15 min (clone + patch + rebuild).

### Option C: Accept the Limitation (No Change)

Nightmare mode + no cheats = vanilla behavior. Working as intended.

**Pros:**
- Zero effort
- True to original DOOM experience

**Cons:**
- God Mode feature partially broken (advertised but doesn't work on Nightmare)

---

## Chosen Solution: Option B

**Decision:** Option B — source patch + rebuild.

**Rationale:**
1. `libdoom.so` is built from **local source** (`build-native.sh` → `~/android-toolchain/chocolate-doom-src/`), not a prebuilt blob.
2. The NDK toolchain is already installed and functional.
3. A source patch is persistent and won't be overwritten by future rebuilds.
4. We can commit the patch to our repo for reproducibility.

### Implementation Plan

1. **Clone source**: The git submodule already provides Chocolate Doom at `native/chocolate-doom/` (tag `chocolate-doom-3.1.1`). Run `git submodule update --init` if needed.
2. **Patch**: Remove `gameskill != sk_nightmare` from `st_stuff.c:393`
3. **Rebuild**: Run `scripts/build-native.sh`
4. **Rebuild APK**: Run `scripts/build-apk.sh doom`
5. **Deploy**: Install on device

### Patch Details

In `src/doom/st_stuff.c`, change:

```c
// Before (blocks cheats on Nightmare):
    if (!netgame && gameskill != sk_nightmare)
    {

// After (allows cheats on all difficulties):
    if (!netgame)
    {
```

This removes only the Nightmare restriction. Netgame cheat blocking is preserved (cheats still disabled in multiplayer — a separate and valid restriction).

---

## References

- **Chocolate Doom source**: `src/doom/st_stuff.c`, function `ST_Responder`, line ~393
- **Cheat definitions**: Same file, lines 315-320 (`cheat_god`, `cheat_ammo`, `cheat_ammonokey`)
- **Our cheat injection**: `app/src/main/java/com/chocolate/doom/TouchControls.java`, `injectCheatSequence()`, `activateGodMode()`
- **Build scripts**: `scripts/build-native.sh`, `scripts/build-apk.sh`
