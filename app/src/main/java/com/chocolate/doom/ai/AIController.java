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

    // ── Auto-play state machine ──
    // STATE_IDLE:     AI on, waiting for user to start game (5s timeout)
    // STATE_PLAYING:  AI actively controlling the game
    // STATE_DEAD:     Player died, handling death screens
    // STATE_RESTART:  Waiting before restarting
    private static final int AP_IDLE = 0;
    private static final int AP_PLAYING = 1;
    private static final int AP_DEAD = 2;
    private static final int AP_RESTART = 3;
    private int autoPlayState = AP_IDLE;
    private long stateEnterTime = 0;
    private int currentEpisode = 1;
    private int currentSkill = 3;  // 0=ITYTD, 1=HNTR, 2=HMP, 3=UV, 4=NM
    private int episodeCount = 0;  // total episodes played
    private int totalDeaths = 0;
    private boolean wasDeadLastStep = false;
    private static final long AUTO_START_TIMEOUT_MS = 5000;   // 5s before auto-start
    private static final long DEAD_RESTART_DELAY_MS = 5000;   // 5s after death before restart
    private long lastModelSave = 0;
    private static final long MODEL_SAVE_INTERVAL_MS = 60000; // save model every 60s

    // ── Stuck detection ──
    private int prevX = 0, prevY = 0;
    private int currX = 0, currY = 0;  // current position (for exploration bonus)
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
        currX = 0; currY = 0;
        prevAction = 0;

        // Auto-play: start in IDLE, wait 5s for user, then auto-start
        autoPlayState = AP_IDLE;
        stateEnterTime = System.currentTimeMillis();
        wasDeadLastStep = false;
        lastModelSave = System.currentTimeMillis();

        running = true;
        aiThread = new Thread(this::aiLoop, "AI-Player");
        aiThread.setPriority(Thread.MIN_PRIORITY);
        aiThread.start();

        Log.i(TAG, "AI ENABLED (auto-play ready, waiting for game start)");
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

    // ──────── AI Loop (with auto-play state machine) ────────

    private void aiLoop() {
        try {
            Thread.sleep(1000); // wait for game to settle

            while (running && enabled) {
                long loopStart = System.currentTimeMillis();

                // ── Auto-play state machine ──
                switch (autoPlayState) {
                    case AP_IDLE:
                        loopIdle();
                        break;
                    case AP_PLAYING:
                        loopPlaying();
                        break;
                    case AP_DEAD:
                        loopDead();
                        break;
                    case AP_RESTART:
                        loopRestart();
                        break;
                }

                // Sleep for frame-skip duration
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

    // ── IDLE: waiting for game to start ──

    private void loopIdle() throws InterruptedException {
        // Auto-start timeout reached?
        long elapsed = System.currentTimeMillis() - stateEnterTime;
        if (elapsed >= AUTO_START_TIMEOUT_MS) {
            // Pick random episode (1-4) and skill (2=HMP, 3=UV) for variety
            currentEpisode = 1 + rng.nextInt(4);
            currentSkill = 2 + rng.nextInt(2);  // HMP or UV only (not ITYTD/HNTR for challenge, not NM to avoid cheat block)
            startNewGame(currentEpisode, currentSkill);
            episodeCount++;
            autoPlayState = AP_PLAYING;
            stateEnterTime = System.currentTimeMillis();
            startTime = System.currentTimeMillis();
            Log.i(TAG, "Auto-start: episode " + currentEpisode + " skill " + currentSkill
                    + " (episode #" + episodeCount + ")");
            return;
        }

        // Still waiting — do a NOOP
        executeAction(0);
    }

    // ── PLAYING: normal AI loop ──

    private void loopPlaying() throws InterruptedException {
        // Settle period: skip first ~1 second after game start to let level load
        if (totalSteps == 0) {
            long settleElapsed = System.currentTimeMillis() - stateEnterTime;
            if (settleElapsed < 1500) {
                return; // just wait, don't act yet
            }
        }

        // 1. Observe state
        extractState(currentState);

        // 2. Check for death
        if (currentState[10] > 0.5f && !wasDeadLastStep) {
            // Just died!
            totalDeaths++;
            Log.i(TAG, "Player died! (death #" + totalDeaths + ", episode #" + episodeCount
                    + ", steps=" + totalSteps + ", kills=" + (int)(currentState[13] * 100) + ")");
            // Store this as a terminal transition
            if (totalSteps > 0) {
                dqn.storeExperience(prevState, prevAction,
                        calculateReward(prevState, currentState, prevAction),
                        currentState.clone(), true);
                dqn.step();
            }
            releaseAllKeys();
            autoPlayState = AP_DEAD;
            stateEnterTime = System.currentTimeMillis();
            wasDeadLastStep = true;
            return;
        }
        wasDeadLastStep = currentState[10] > 0.5f;

        // 3. Compute reward from previous step
        float reward = calculateReward(prevState, currentState, prevAction);

        // 4. Store experience (if we have a previous transition)
        if (totalSteps > 0) {
            dqn.storeExperience(prevState, prevAction, reward,
                    currentState.clone(), false);
        }

        // 5. Choose action (override if stuck)
        int action;
        if (stuckCounter >= STUCK_RECOVERY) {
            int[] turnActions = {3, 4, 5, 6};
            action = turnActions[rng.nextInt(turnActions.length)];
        } else {
            action = dqn.chooseAction(currentState);
        }

        // 6. Execute action
        executeAction(action);

        // 7. Train
        dqn.step();

        // 8. Update tracking
        System.arraycopy(currentState, 0, prevState, 0, STATE_SIZE);
        prevAction = action;
        totalSteps++;

        if (action == 7) totalFires++;

        notifyStep();

        // Safety: periodically cancel any quit dialog (every ~30s / 260 steps)
        if (totalSteps > 0 && totalSteps % 260 == 0) {
            cancelQuitDialog();
        }

        // Periodic model save (every 60s)
        long now = System.currentTimeMillis();
        if (now - lastModelSave > MODEL_SAVE_INTERVAL_MS) {
            saveModelToFile();
            lastModelSave = now;
        }
    }

    // ── DEAD: handling death screens ──

    private void loopDead() throws InterruptedException {
        long elapsed = System.currentTimeMillis() - stateEnterTime;

        if (elapsed < 2000) {
            // Wait for death animation to finish
            return;
        }

        if (elapsed < 3000) {
            // Press SPACE to advance past death screen
            injectKey(62, 80);
            Thread.sleep(500);
            return;
        }

        if (elapsed < 4000) {
            // Press ENTER to advance past intermission
            injectKey(66, 80);
            Thread.sleep(500);
            return;
        }

        // Wait for title screen, then transition to restart
        if (elapsed >= 5000) {
            autoPlayState = AP_RESTART;
            stateEnterTime = System.currentTimeMillis();
        }
    }

    // ── RESTART: wait then start new game ──

    private void loopRestart() throws InterruptedException {
        long elapsed = System.currentTimeMillis() - stateEnterTime;

        if (elapsed >= DEAD_RESTART_DELAY_MS) {
            // Restart with SAME episode and skill
            startNewGame(currentEpisode, currentSkill);
            episodeCount++;
            autoPlayState = AP_PLAYING;
            stateEnterTime = System.currentTimeMillis();
            startTime = System.currentTimeMillis();
            totalSteps = 0;
            totalFires = 0;
            stuckCounter = 0;
            prevX = 0; prevY = 0;
            currX = 0; currY = 0;
            prevAction = 0;
            wasDeadLastStep = false;
            Log.i(TAG, "Restart: episode " + currentEpisode + " skill " + currentSkill
                    + " (episode #" + episodeCount + ", deaths=" + totalDeaths + ")");
            return;
        }
    }

    // ──────── Menu Navigation & Auto-Start ────────

    /**
     * Inject a single key press: keyDown → hold → keyUp.
     */
    private void injectKey(int keycode, int holdMs) {
        SDLActivity.onNativeKeyDown(keycode);
        try { Thread.sleep(holdMs); } catch (InterruptedException e) {}
        SDLActivity.onNativeKeyUp(keycode);
    }

    /**
     * Cancel any quit-confirmation dialog ("Press Y to quit").
     * Safe to call anytime — 'N' is a no-op in normal gameplay.
     * Keycode 49 = KEYCODE_N.
     */
    private void cancelQuitDialog() {
        injectKey(49, 80);  // Press 'N' to cancel quit prompt
        try { Thread.sleep(200); } catch (InterruptedException e) {}
    }

    /**
     * Navigate DOOM menus to start a new game at given episode and skill.
     * Works from title screen, demo loop, or any menu state.
     *
     * Menu flow: any key (title→menu) → Enter (New Game) → arrows + Enter (episode)
     *            → arrows + Enter (skill) → game starts
     *
     * Uses long delays to ensure menu transitions complete on slow devices.
     * Retries if game doesn't start within timeout.
     */
    private boolean startNewGame(int episode, int skill) {
        Log.i(TAG, "Starting new game: episode=" + episode + " skill=" + skill);
        releaseAllKeys();

        // Cancel any quit confirmation dialog that might be open
        cancelQuitDialog();

        int maxRetries = 3;
        for (int retry = 0; retry < maxRetries; retry++) {
            try {
                if (retry > 0) {
                    Log.i(TAG, "  Retry #" + retry + " — waiting before re-attempt...");
                    Thread.sleep(2000);
                }

                // 1. Ensure we're at the main menu.
                //    ESC toggles the menu: if open → closes it; if closed → opens it.
                //    Strategy: press ESC to close any menu → press Enter at title screen
                //    ("press any key" shows main menu) → then Enter for "New Game".
                injectKey(111, 150);   // ESC — close any open menu/submenu
                Thread.sleep(400);
                injectKey(66, 150);    // ENTER — at title screen, this shows main menu
                Thread.sleep(600);

                // 2. Press Enter to select "New Game"
                //    On the main menu, "New Game" is the first item (default highlight)
                injectKey(66, 150);    // ENTER
                Thread.sleep(1000);    // Longer wait for episode select screen to appear

                // 3. Select episode: press DOWN (episode-1) times, then Enter
                for (int i = 1; i < episode; i++) {
                    injectKey(20, 120);  // DOWN
                    Thread.sleep(200);
                }
                injectKey(66, 150);    // ENTER to confirm episode
                Thread.sleep(1000);    // Wait for skill select screen

                // 4. Select skill: press DOWN (skill) times, then Enter
                for (int i = 0; i < skill; i++) {
                    injectKey(20, 120);  // DOWN
                    Thread.sleep(200);
                }
                injectKey(66, 150);    // ENTER to confirm skill
                Thread.sleep(3000);    // Wait for level to load (longer for slow devices)

                // 5. Verify game started by checking health after a moment
                Thread.sleep(1000);
                try {
                    int health = GameState.getPlayerHealth();
                    if (health > 0) {
                        Log.i(TAG, "New game confirmed: ep=" + episode + " skill=" + skill
                                + " health=" + health);
                        return true;
                    }
                    Log.w(TAG, "Game may not have started (health=" + health + "), retrying...");
                } catch (Exception e) {
                    Log.w(TAG, "JNI check failed, assuming game started");
                    return true; // JNI not ready yet, proceed anyway
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        Log.e(TAG, "Failed to start game after " + maxRetries + " retries. Proceeding anyway.");
        return false;
    }

    /**
     * Save DQN model to app's internal storage.
     * Called periodically during auto-play to preserve training progress.
     */
    private void saveModelToFile() {
        try {
            // Use a fixed path in app files dir
            java.io.File dir = new java.io.File("/data/data/com.chocolate.doom/files/");
            if (!dir.exists()) dir.mkdirs();
            java.io.File modelFile = new java.io.File(dir, "dqn_model.bin");
            dqn.saveModel(modelFile.getAbsolutePath());
            Log.i(TAG, "Model saved: " + modelFile.getAbsolutePath()
                    + " (steps=" + totalSteps + " ε=" + String.format("%.3f", dqn.getEpsilon()) + ")");
        } catch (Exception e) {
            Log.w(TAG, "Failed to save model: " + e.getMessage());
        }
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
        state[12] = Math.min(1.0f, elapsed / 120f);  // saturates at 2 min (was 600f/10min)

        state[13] = Math.min(1.0f, GameState.getKillCount() / 100f);
        state[14] = Math.min(1.0f, GameState.getSecretCount() / 10f);

        // ── Stuck detection (only increment on movement actions: 1=FWD..6=STRAFER) ──
        currX = GameState.getPlayerX();
        currY = GameState.getPlayerY();
        boolean isMovementAction = (prevAction >= 1 && prevAction <= 6);
        if (currX == prevX && currY == prevY) {
            if (isMovementAction) {
                stuckCounter++;  // movement action but no position change = stuck
            }
            // else: FIRE/USE/weapon-switch/NOOP — don't count as stuck
        } else {
            stuckCounter = 0;  // moved — reset regardless of action type
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
     * Reward based on real game outcomes (v2.3 — balanced).
     * +kills, +secrets, +exploration, -damage, -stuck, -NOOP.
     * Alive bonus reduced to prevent passive strategy.
     */
    private float calculateReward(float[] prev, float[] curr, int action) {
        float reward = 0.003f; // alive bonus (was 0.01f — too large, encouraged passivity)

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

        // ── Stuck penalty (increased: -0.20 * stuckLevel, was -0.15) ──
        if (curr[15] > 0.5f) reward -= curr[15] * 0.20f;

        // ── Fire effectiveness ──
        if (action == 7) {
            // FIRE action
            if (curr[16] < 0.5f) {
                reward -= 0.05f;  // firing with no ammo = bad
            } else if (killDelta > 0) {
                reward += 0.02f;  // fire that killed something = good
            }
        }

        // ── Action shaping (rebalanced) ──
        if (action == 0) reward -= 0.01f;         // NOOP penalty (was 0.005f — too weak)
        if (action >= 1 && action <= 6) reward += 0.003f;  // movement
        if (action >= 10) reward += 0.005f;       // weapon switch (exploration)

        // ── Exploration bonus: reward significant position change ──
        // Position is tracked via prevX/prevY in extractState; if we moved, encourage it
        if (currX != prevX || currY != prevY) {
            reward += 0.002f;  // small bonus for any position change
        }

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
