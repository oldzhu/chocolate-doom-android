/*
 * jni_game_state.c — JNI bridge for AI state extraction
 *
 * Exposes DOOM player state to Java via JNI so the DQN agent
 * can observe real game state instead of heuristic features.
 *
 * All functions read from players[consoleplayer] which is
 * the global player array in the DOOM engine.
 */
#include <jni.h>
#include "doomdef.h"
#include "d_player.h"
#include "doomstat.h"

/* ── Player Health ── */

JNIEXPORT jint JNICALL
Java_com_chocolate_doom_GameState_getPlayerHealth(JNIEnv *env, jclass cls) {
    return players[consoleplayer].health;
}

JNIEXPORT jint JNICALL
Java_com_chocolate_doom_GameState_getPlayerArmor(JNIEnv *env, jclass cls) {
    return players[consoleplayer].armorpoints;
}

/* ── Ammo ── */

JNIEXPORT jint JNICALL
Java_com_chocolate_doom_GameState_getAmmoBullets(JNIEnv *env, jclass cls) {
    return players[consoleplayer].ammo[am_clip];
}

JNIEXPORT jint JNICALL
Java_com_chocolate_doom_GameState_getAmmoShells(JNIEnv *env, jclass cls) {
    return players[consoleplayer].ammo[am_shell];
}

JNIEXPORT jint JNICALL
Java_com_chocolate_doom_GameState_getAmmoRockets(JNIEnv *env, jclass cls) {
    return players[consoleplayer].ammo[am_misl];
}

JNIEXPORT jint JNICALL
Java_com_chocolate_doom_GameState_getAmmoCells(JNIEnv *env, jclass cls) {
    return players[consoleplayer].ammo[am_cell];
}

/* ── Max Ammo (for normalization) ── */

JNIEXPORT jint JNICALL
Java_com_chocolate_doom_GameState_getMaxAmmoBullets(JNIEnv *env, jclass cls) {
    return players[consoleplayer].maxammo[am_clip];
}

JNIEXPORT jint JNICALL
Java_com_chocolate_doom_GameState_getMaxAmmoShells(JNIEnv *env, jclass cls) {
    return players[consoleplayer].maxammo[am_shell];
}

JNIEXPORT jint JNICALL
Java_com_chocolate_doom_GameState_getMaxAmmoRockets(JNIEnv *env, jclass cls) {
    return players[consoleplayer].maxammo[am_misl];
}

JNIEXPORT jint JNICALL
Java_com_chocolate_doom_GameState_getMaxAmmoCells(JNIEnv *env, jclass cls) {
    return players[consoleplayer].maxammo[am_cell];
}

/* ── Weapons ── */

JNIEXPORT jint JNICALL
Java_com_chocolate_doom_GameState_getCurrentWeapon(JNIEnv *env, jclass cls) {
    return (jint) players[consoleplayer].readyweapon;
}

JNIEXPORT jint JNICALL
Java_com_chocolate_doom_GameState_getWeaponsOwned(JNIEnv *env, jclass cls) {
    int mask = 0;
    for (int i = 0; i < NUMWEAPONS; i++) {
        if (players[consoleplayer].weaponowned[i])
            mask |= (1 << i);
    }
    return mask;
}

/* ── Stats ── */

JNIEXPORT jint JNICALL
Java_com_chocolate_doom_GameState_getKillCount(JNIEnv *env, jclass cls) {
    return players[consoleplayer].killcount;
}

JNIEXPORT jint JNICALL
Java_com_chocolate_doom_GameState_getItemCount(JNIEnv *env, jclass cls) {
    return players[consoleplayer].itemcount;
}

JNIEXPORT jint JNICALL
Java_com_chocolate_doom_GameState_getSecretCount(JNIEnv *env, jclass cls) {
    return players[consoleplayer].secretcount;
}

/* ── Status flags ── */

JNIEXPORT jboolean JNICALL
Java_com_chocolate_doom_GameState_isPlayerDead(JNIEnv *env, jclass cls) {
    return players[consoleplayer].playerstate == PST_DEAD;
}

JNIEXPORT jint JNICALL
Java_com_chocolate_doom_GameState_getDamageCount(JNIEnv *env, jclass cls) {
    return players[consoleplayer].damagecount;
}

JNIEXPORT jboolean JNICALL
Java_com_chocolate_doom_GameState_hasBackpack(JNIEnv *env, jclass cls) {
    return players[consoleplayer].backpack;
}

JNIEXPORT jboolean JNICALL
Java_com_chocolate_doom_GameState_hasGodMode(JNIEnv *env, jclass cls) {
    return (players[consoleplayer].cheats & CF_GODMODE) != 0;
}
