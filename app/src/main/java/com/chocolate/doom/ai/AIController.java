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

    // ── State features (v2.1: JNI + spatial awareness) ──
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
    // state[15] = is stuck (0/1) — position unchanged for >10 forward steps
    // state[16] = has ammo for current weapon (0/1)
    private static final int STATE_SIZE = 17;

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

    // ── Stuck detection ──
    private int prevX = 0, prevY = 0;
    private int stuckCounter = 0;
    private final java.util.Random rng = new java.util.Random();
    private int prevKills = 0;
    private static final int STUCK_THRESHOLD = 5;   // steps before considered stuck
    private static final int STUCK_RECOVERY = 5;     // consecutive stuck → force turn

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
        stuckCounter = 0;
        prevX = 0; prevY = 0;
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

                // 4. Choose action (override if stuck)
                int action;
                if (stuckCounter >= STUCK_RECOVERY) {
                    // Force random turn/strafe to unstick — bypass DQN
                    int[] turnActions = {3, 4, 5, 6}; // LEFT, RIGHT, STRAFEL, STRAFER
                    action = turnActions[rng.nextInt(turnActions.length)];
                } else {
                    action = dqn.chooseAction(currentState);
                }

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
        int maxHealth = 100;
        int maxArmor = 200;

        state[0] = Math.max(0, GameState.getPlayerHealth()) / (float) maxHealth;
        state[1] = Math.max(0, GameState.getPlayerArmor()) / (float) maxArmor;

        state[2] = safeDiv(GameState.getAmmoBullets(), GameState.getMaxAmmoBullets());
        state[3] = safeDiv(GameState.getAmmoShells(), GameState.getMaxAmmoShells());
        state[4] = safeDiv(GameState.getAmmoRockets(), GameState.getMaxAmmoRockets());
        state[5] = safeDiv(GameState.getAmmoCells(), GameState.getMaxAmmoCells());

        state[6] = (float) GameState.getCurrentWeapon() / 9f;
        state[7] = GameState.hasBackpack() ? 1f : 0f;
        state[8] = GameState.hasGodMode() ? 1f : 0f;
        state[9] = (GameState.getDamageCount() > 0) ? 1f : 0f;
        state[10] = GameState.isPlayerDead() ? 1f : 0f;

        int ownedMask = GameState.getWeaponsOwned();
        state[11] = Integer.bitCount(ownedMask) / 9f;

        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
        state[12] = Math.min(1.0f, elapsed / 600f);

        state[13] = Math.min(1.0f, GameState.getKillCount() / 100f);
        state[14] = Math.min(1.0f, GameState.getSecretCount() / 10f);

        // ── Stuck detection ──
        int currX = GameState.getPlayerX();
        int currY = GameState.getPlayerY();
        if (currX == prevX && currY == prevY) {
            stuckCounter++;  // position unchanged this step
        } else {
            stuckCounter = 0;  // moved — reset
        }
        prevX = currX;
        prevY = currY;
        state[15] = Math.min(1f, (float) stuckCounter / (float) STUCK_RECOVERY);

        // ── Ammo available ──
        state[16] = GameState.hasAmmoForCurrentWeapon() ? 1f : 0f;
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

        // Kill reward
        float killDelta = curr[13] - prev[13];
        if (killDelta > 0) reward += killDelta * 100f;  // +1.0 per kill

        // Secret discovery
        float secretDelta = curr[14] - prev[14];
        if (secretDelta > 0) reward += secretDelta * 10f;  // +1.0 per secret

        // Damage penalty
        if (curr[9] > prev[9]) reward -= 0.05f;

        // Death penalty
        if (curr[10] > 0 && prev[10] == 0) reward -= 1.0f;

        // Health loss
        float healthDelta = curr[0] - prev[0];
        if (healthDelta < 0) reward += healthDelta * 0.5f;

        // ── Stuck penalty (continuous) ──
        if (curr[15] > 0.5f) reward -= curr[15] * 0.15f;  // grows with stuck time

        // ── Fire effectiveness ──
        if (action == 7) {
            // FIRE action
            if (curr[16] < 0.5f) {
                reward -= 0.05f;  // firing with no ammo = bad
            } else if (killDelta > 0) {
                reward += 0.02f;  // fire that killed something = good
            }
        }

        // ── Action shaping ──
        if (action == 0) reward -= 0.005f;        // NOOP
        if (action >= 1 && action <= 6) reward += 0.003f;  // movement
        if (action >= 10) reward += 0.005f;       // weapon switch (exploration)

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
