package com.chocolate.doom.ai;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.chocolate.doom.GameState;
import org.libsdl.app.SDLActivity;

/**
 * AI Controller — orchestrates the DQN agent, state extraction,
 * and key injection for autonomous DOOM play.
 *
 * Lifecycle:
 *   enable()  → starts AI loop on background thread
 *   disable() → stops AI, returns control to human
 *   toggle()  → switches between human and AI
 *
 * The AI loop runs at ~8 Hz (acts every 4 game frames with frame-skip).
 */
public class AIController {
    private static final String TAG = "AIController";

    // ── State features (v2.0: JNI game state) ──
    // state[0]  = health / 100       (normalized 0-2)
    // state[1]  = armor / 100        (normalized 0-2)
    // state[2]  = ammo bullets / max
    // state[3]  = ammo shells / max
    // state[4]  = ammo rockets / max
    // state[5]  = ammo cells / max
    // state[6]  = current weapon / NUMWEAPONS
    // state[7]  = has backpack (0/1)
    // state[8]  = has god mode (0/1)
    // state[9]  = recently damaged (damagecount > 0 ? 1 : 0)
    // state[10] = is dead (0/1)
    // state[11] = weapon diversity (owned count / NUMWEAPONS)
    // state[12] = time alive / 600
    // state[13] = kills / 100
    // state[14] = secrets / 10
    private static final int STATE_SIZE = 15;

    // ── Actions (14 discrete) ──
    private static final int NUM_ACTIONS = 14;
    // Action indices + key sequences
    private static final int[][] ACTION_KEYS = {
        {},                                   // 0: NOOP
        {19},                                 // 1: MOVE_FORWARD  (DPAD_UP)
        {20},                                 // 2: MOVE_BACKWARD (DPAD_DOWN)
        {21},                                 // 3: TURN_LEFT     (DPAD_LEFT)
        {22},                                 // 4: TURN_RIGHT    (DPAD_RIGHT)
        {19, 21},                             // 5: STRAFE_LEFT   (UP+LEFT)
        {19, 22},                             // 6: STRAFE_RIGHT  (UP+RIGHT)
        {113},                                // 7: FIRE          (CTRL)
        {62},                                 // 8: USE           (SPACE)
        {59},                                 // 9: RUN           (SHIFT)
        {8},                                  // 10: WEAPON_FIST  (1)
        {9},                                  // 11: WEAPON_PISTOL(2)
        {10},                                 // 12: WEAPON_SHOTGUN(3)
        {14},                                 // 13: WEAPON_BFG   (7)
    };

    private static final String[] ACTION_NAMES = {
        "NOOP", "FWD", "BACK", "LEFT", "RIGHT",
        "STRAFEL", "STRAFER", "FIRE", "USE", "RUN",
        "FIST", "PISTOL", "SHOTGUN", "BFG"
    };

    // ── DQN Agent ──
    private final DQNPlayer dqn;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ── State ──
    private boolean enabled = false;
    private boolean running = false;
    private Thread aiThread;
    private float[] currentState = new float[STATE_SIZE];
    private float[] prevState = new float[STATE_SIZE];
    private int prevAction = 0;
    private long startTime = 0;
    private int totalSteps = 0;
    private int totalFires = 0;

    // ── Frame-skip ──
    private static final int FRAME_SKIP = 4;       // act every 4 game frames
    private static final long STEP_DELAY_MS = 114;  // ~8.75 Hz (35fps / 4)

    // ── Callback for UI updates ──
    public interface Callback {
        void onAIStatus(String status);
        void onAIStep(int step, float epsilon, int replaySize);
    }
    private Callback callback;

    public AIController() {
        dqn = new DQNPlayer(STATE_SIZE, NUM_ACTIONS, new int[]{64, 32},
                10000, 32);
    }

    public void setCallback(Callback cb) { this.callback = cb; }

    // ──────── Toggle ────────

    public boolean isEnabled() { return enabled; }

    public void toggle() {
        if (enabled) disable(); else enable();
    }

    public void enable() {
        if (enabled) return;
        enabled = true;
        startTime = System.currentTimeMillis();
        totalSteps = 0;
        totalFires = 0;
        prevAction = 0;

        running = true;
        aiThread = new Thread(this::aiLoop, "AI-Player");
        aiThread.setPriority(Thread.MIN_PRIORITY);
        aiThread.start();

        Log.i(TAG, "AI ENABLED");
        notifyStatus("🤖 AI ON");
    }

    public void disable() {
        enabled = false;
        running = false;
        if (aiThread != null) {
            aiThread.interrupt();
            aiThread = null;
        }
        // Release all keys
        releaseAllKeys();
        Log.i(TAG, "AI DISABLED");
        notifyStatus(null);
    }

    // ──────── AI Loop ────────

    private void aiLoop() {
        try {
            Thread.sleep(1000); // wait for game to settle

            while (running && enabled) {
                long loopStart = System.currentTimeMillis();

                // 1. Observe state
                extractState(currentState);

                // 2. Compute reward from previous step
                float reward = calculateReward(prevState, currentState, prevAction);

                // 3. Store experience (if we have a previous transition)
                if (totalSteps > 0) {
                    dqn.storeExperience(prevState, prevAction, reward,
                            currentState.clone(), false);
                }

                // 4. Choose action
                int action = dqn.chooseAction(currentState);

                // 5. Execute action
                executeAction(action);

                // 6. Train
                dqn.step();

                // 7. Update tracking
                System.arraycopy(currentState, 0, prevState, 0, STATE_SIZE);
                prevAction = action;
                totalSteps++;

                if (action == 7) totalFires++; // FIRE action

                // Update UI callback
                notifyStep();

                // 8. Sleep for frame-skip duration
                long elapsed = System.currentTimeMillis() - loopStart;
                long sleepTime = STEP_DELAY_MS - elapsed;
                if (sleepTime > 0) Thread.sleep(sleepTime);
            }
        } catch (InterruptedException e) {
            // Normal shutdown
        } catch (Exception e) {
            Log.e(TAG, "AI loop error", e);
        }
        running = false;
    }

    // ──────── State Extraction (JNI game state v2.0) ────────

    /**
     * Extract normalized state features from native DOOM engine.
     * All values normalized to 0-1 range for neural net input.
     */
    private void extractState(float[] state) {
        float maxHealth = 100f;
        float maxArmor = 200f;

        state[0] = Math.max(0, GameState.getPlayerHealth()) / maxHealth;
        state[1] = Math.max(0, GameState.getPlayerArmor()) / maxArmor;

        // Ammo (avoid division by zero for max values)
        state[2] = safeDiv(GameState.getAmmoBullets(), GameState.getMaxAmmoBullets());
        state[3] = safeDiv(GameState.getAmmoShells(), GameState.getMaxAmmoShells());
        state[4] = safeDiv(GameState.getAmmoRockets(), GameState.getMaxAmmoRockets());
        state[5] = safeDiv(GameState.getAmmoCells(), GameState.getMaxAmmoCells());

        state[6] = (float) GameState.getCurrentWeapon() / 9f; // NUMWEAPONS
        state[7] = GameState.hasBackpack() ? 1f : 0f;
        state[8] = GameState.hasGodMode() ? 1f : 0f;
        state[9] = (GameState.getDamageCount() > 0) ? 1f : 0f;
        state[10] = GameState.isPlayerDead() ? 1f : 0f;

        // Weapon diversity: count owned / total
        int ownedMask = GameState.getWeaponsOwned();
        int ownedCount = Integer.bitCount(ownedMask);
        state[11] = (float) ownedCount / 9f;

        // Time alive (kept from v1.0)
        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
        state[12] = Math.min(1.0f, elapsed / 600f);

        // Stats
        state[13] = Math.min(1.0f, GameState.getKillCount() / 100f);
        state[14] = Math.min(1.0f, GameState.getSecretCount() / 10f);
    }

    private float safeDiv(int num, int den) {
        if (den <= 0) return 0f;
        return Math.min(1.0f, (float) num / (float) den);
    }

    // ──────── Reward Function ────────

    /**
     * Reward based on real game outcomes.
     * +kills, +secrets, +items, -damage, +alive_bonus.
     */
    private float calculateReward(float[] prev, float[] curr, int action) {
        float reward = 0.01f; // alive bonus

        // Kill reward (kills increased since last step)
        float killDelta = curr[13] - prev[13];
        if (killDelta > 0) reward += killDelta * 100f;  // 1 kill = +1.0

        // Secret discovery
        float secretDelta = curr[14] - prev[14];
        if (secretDelta > 0) reward += secretDelta * 10f;

        // Damage penalty
        if (curr[9] > prev[9]) reward -= 0.05f;  // just got hit

        // Death penalty
        if (curr[10] > 0 && prev[10] == 0) reward -= 1.0f;

        // Health loss penalty
        float healthDelta = curr[0] - prev[0];
        if (healthDelta < 0) reward += healthDelta * 0.5f;  // health decreased = penalty

        // Action shaping
        if (action == 0) reward -= 0.005f;     // NOOP penalty
        if (action == 7) reward += 0.01f;      // FIRE bonus
        if (action >= 1 && action <= 6) reward += 0.003f; // movement bonus

        return reward;
    }

    // ──────── Action Execution ────────

    private int activeAction = -1;

    private void executeAction(int action) {
        // Release previous action's keys
        if (activeAction >= 0 && activeAction < ACTION_KEYS.length) {
            for (int key : ACTION_KEYS[activeAction]) {
                SDLActivity.onNativeKeyUp(key);
            }
        }

        // Press new action's keys
        activeAction = action;
        if (action >= 0 && action < ACTION_KEYS.length) {
            int[] keys = ACTION_KEYS[action];
            for (int key : keys) {
                SDLActivity.onNativeKeyDown(key);
            }
        }
    }

    private void releaseAllKeys() {
        if (activeAction >= 0 && activeAction < ACTION_KEYS.length) {
            for (int key : ACTION_KEYS[activeAction]) {
                SDLActivity.onNativeKeyUp(key);
            }
        }
        activeAction = -1;
    }

    // ──────── UI Callbacks ────────

    private void notifyStatus(String text) {
        if (callback != null) {
            mainHandler.post(() -> callback.onAIStatus(text));
        }
    }

    private void notifyStep() {
        if (callback != null && totalSteps % 10 == 0) {
            mainHandler.post(() ->
                callback.onAIStep(totalSteps, dqn.getEpsilon(), dqn.getReplaySize()));
        }
    }

    // ──────── Public Info ────────

    public int getTotalSteps() { return totalSteps; }
    public int getTotalFires() { return totalFires; }
    public float getEpsilon() { return dqn.getEpsilon(); }
    public long getElapsedSeconds() {
        return (System.currentTimeMillis() - startTime) / 1000;
    }

    public String getStatusText() {
        if (!enabled) return null;
        return String.format("Steps:%d ε:%.2f Replay:%d",
                totalSteps, dqn.getEpsilon(), dqn.getReplaySize());
    }
}
