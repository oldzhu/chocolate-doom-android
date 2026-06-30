package com.chocolate.doom.ai;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
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

    // ── State features (v1.0: heuristic, no JNI) ──
    // state[0] = time alive / 600 (normalized 0-1)
    // state[1] = 1.0 if recently fired, 0.0 otherwise
    // state[2] = 1.0 if recently used, 0.0 otherwise
    // state[3] = movement direction encoded (0=none, 0.25=fwd, 0.5=back, 0.75=left, 1.0=right)
    private static final int STATE_SIZE = 4;

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

    // ──────── State Extraction (heuristic v1.0) ────────

    /**
     * Extract normalized state features.
     * v1.0: Heuristic — time-based features only.
     * v1.5: Replace with JNI game state extraction (health, ammo, kills, etc.)
     */
    private void extractState(float[] state) {
        long elapsed = (System.currentTimeMillis() - startTime) / 1000; // seconds
        state[0] = Math.min(1.0f, elapsed / 600f);  // time alive (caps at 10 min)

        // Action-based features from previous step
        if (prevAction == 7) state[1] = 1.0f;  // recently fired
        else state[1] *= 0.8f;                  // decay

        if (prevAction == 8) state[2] = 1.0f;  // recently used
        else state[2] *= 0.8f;

        // Movement direction encoding
        switch (prevAction) {
            case 1: state[3] = 0.25f; break; // forward
            case 2: state[3] = 0.50f; break; // backward
            case 3: state[3] = 0.75f; break; // left
            case 4: state[3] = 1.00f; break; // right
            default: state[3] *= 0.5f;         // decay toward 0
        }
    }

    // ──────── Reward Function ────────

    /**
     * Heuristic reward: +0.01 per frame alive, -0.02 for standing still,
     * small bonuses for firing and using.
     */
    private float calculateReward(float[] prev, float[] curr, int action) {
        float reward = 0.01f; // alive bonus

        // Penalty for NOOP (encourages doing something)
        if (action == 0) reward -= 0.005f;

        // Bonus for firing
        if (action == 7) reward += 0.02f;

        // Bonus for moving
        if (action >= 1 && action <= 6) reward += 0.005f;

        // Bonus for weapon switching (exploration)
        if (action >= 10) reward += 0.01f;

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
