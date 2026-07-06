package com.chocolate.doom;

/**
 * JNI bridge for reading DOOM player state from native code.
 *
 * All methods map to jni_game_state.c JNI functions that read
 * players[consoleplayer] directly from the DOOM engine memory.
 *
 * Zero-allocation, fast — called from AI loop at ~8.75 Hz.
 */
public class GameState {
    static { System.loadLibrary("main"); }

    // ── Health & Armor ──
    public static native int getPlayerHealth();
    public static native int getPlayerArmor();

    // ── Position (for stuck detection) ──
    /** Player X in map units */
    public static native int getPlayerX();
    /** Player Y in map units */
    public static native int getPlayerY();
    /** Player facing angle (0-359, BAM units / ANG1) */
    public static native int getPlayerAngle();

    // ── Ammo (current + max for normalization) ──
    public static native int getAmmoBullets();
    public static native int getAmmoShells();
    public static native int getAmmoRockets();
    public static native int getAmmoCells();

    public static native int getMaxAmmoBullets();
    public static native int getMaxAmmoShells();
    public static native int getMaxAmmoRockets();
    public static native int getMaxAmmoCells();

    // ── Weapons ──
    /** Current ready weapon (weapontype_t enum: 0=fist, 1=pistol, 2=shotgun, ...) */
    public static native int getCurrentWeapon();
    /** Bitmask of owned weapons */
    public static native int getWeaponsOwned();

    // ── Stats ──
    public static native int getKillCount();
    public static native int getItemCount();
    public static native int getSecretCount();

    // ── Status ──
    public static native boolean isPlayerDead();
    /** Number of tics remaining on the damage screen flash (>0 = recently hurt) */
    public static native int getDamageCount();
    public static native boolean hasBackpack();
    public static native boolean hasGodMode();

    /** Whether the current weapon has ammo remaining */
    public static native boolean hasAmmoForCurrentWeapon();
}
