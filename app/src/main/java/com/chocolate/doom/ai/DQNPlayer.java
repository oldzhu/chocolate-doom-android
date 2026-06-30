package com.chocolate.doom.ai;

import java.util.Random;

/**
 * DQN agent with experience replay, epsilon-greedy exploration,
 * and Double Q-Learning-style target network.
 *
 * Learning loop:
 *   1. Observe state s
 *   2. Choose action a = argmax Q(s) or random (epsilon-greedy)
 *   3. Execute action, observe reward r and next state s'
 *   4. Store (s, a, r, s', done) in replay buffer
 *   5. Every 4 steps: sample batch, compute Bellman targets, train network
 *   6. Every 1000 steps: sync target network
 */
public class DQNPlayer {
    // ── Network ──
    private final NeuralNet onlineNet;   // trained every step
    private final NeuralNet targetNet;   // synced periodically for stability
    private final int numActions;

    // ── Replay Buffer ──
    private final float[][] replayStates;
    private final float[][] replayNextStates;
    private final int[] replayActions;
    private final float[] replayRewards;
    private final boolean[] replayDones;
    private int replayPos = 0;
    private int replaySize = 0;
    private final int replayCapacity;
    private final int batchSize;

    // ── Hyperparameters ──
    private float epsilon = 1.0f;
    private final float epsilonMin = 0.05f;
    private final float epsilonDecay = 0.9995f;  // per step
    private final float gamma = 0.99f;
    private int trainFrequency = 4;     // train every N steps
    private int targetSyncFrequency = 1000;
    private int stepCount = 0;

    private final Random rng = new Random();

    /**
     * @param stateSize     number of state features
     * @param numActions    number of discrete actions
     * @param hiddenLayers  hidden layer sizes (e.g. {128, 64})
     * @param replayCap     replay buffer capacity
     * @param batchSize     training batch size
     */
    public DQNPlayer(int stateSize, int numActions, int[] hiddenLayers,
                     int replayCap, int batchSize) {
        this.numActions = numActions;
        this.replayCapacity = replayCap;
        this.batchSize = batchSize;

        // Build layer sizes: input → hidden... → output
        int[] layerSizes = new int[2 + hiddenLayers.length];
        layerSizes[0] = stateSize;
        System.arraycopy(hiddenLayers, 0, layerSizes, 1, hiddenLayers.length);
        layerSizes[layerSizes.length - 1] = numActions;

        onlineNet = new NeuralNet(layerSizes);
        targetNet = new NeuralNet(layerSizes);
        copyWeights(onlineNet, targetNet);

        // Allocate replay buffer
        replayStates = new float[replayCap][stateSize];
        replayNextStates = new float[replayCap][stateSize];
        replayActions = new int[replayCap];
        replayRewards = new float[replayCap];
        replayDones = new boolean[replayCap];
    }

    // ──────── Action Selection ────────

    /**
     * Choose action: epsilon-greedy.
     * @param state current state features (normalized 0-1)
     * @return action index 0..numActions-1
     */
    public int chooseAction(float[] state) {
        if (rng.nextFloat() < epsilon) {
            return rng.nextInt(numActions);
        }
        float[] qValues = onlineNet.forward(state);
        return argmax(qValues);
    }

    /**
     * Choose action without exploration (for evaluation).
     */
    public int chooseBestAction(float[] state) {
        float[] qValues = onlineNet.forward(state);
        return argmax(qValues);
    }

    // ──────── Experience Storage ────────

    /**
     * Store a transition in the replay buffer.
     */
    public void storeExperience(float[] state, int action, float reward,
                                 float[] nextState, boolean done) {
        int idx = replayPos;
        System.arraycopy(state, 0, replayStates[idx], 0, state.length);
        System.arraycopy(nextState, 0, replayNextStates[idx], 0, nextState.length);
        replayActions[idx] = action;
        replayRewards[idx] = reward;
        replayDones[idx] = done;

        replayPos = (replayPos + 1) % replayCapacity;
        if (replaySize < replayCapacity) replaySize++;
    }

    // ──────── Training Step ────────

    /**
     * Should be called every game step (every 4th frame with frame-skip).
     * Updates epsilon, trains if enough data.
     */
    public void step() {
        stepCount++;
        decayEpsilon();

        if (replaySize < batchSize) return;
        if (stepCount % trainFrequency != 0) return;

        // Sample random batch
        for (int b = 0; b < batchSize; b++) {
            int idx = rng.nextInt(replaySize);

            float[] state = replayStates[idx];
            float[] nextState = replayNextStates[idx];
            int action = replayActions[idx];
            float reward = replayRewards[idx];
            boolean done = replayDones[idx];

            // Compute Bellman target: Q_target = r + gamma * max_a Q_target(s', a)
            float targetQ;
            if (done) {
                targetQ = reward;
            } else {
                float[] nextQ = targetNet.forward(nextState);
                targetQ = reward + gamma * max(nextQ);
            }

            // Get current Q-values and set target for the chosen action
            float[] currentQ = onlineNet.forward(state);
            float[] targetVec = currentQ.clone(); // keep other Q-values as-is
            targetVec[action] = targetQ;

            // Only train the chosen action dimension
            boolean[] mask = new boolean[numActions];
            mask[action] = true;

            onlineNet.train(state, targetVec, mask);
        }

        // Periodically sync target network
        if (stepCount % targetSyncFrequency == 0) {
            copyWeights(onlineNet, targetNet);
        }
    }

    // ──────── Helpers ────────

    private void decayEpsilon() {
        epsilon = Math.max(epsilonMin, epsilon * epsilonDecay);
    }

    public float getEpsilon() { return epsilon; }
    public int getStepCount() { return stepCount; }
    public int getReplaySize() { return replaySize; }

    private static void copyWeights(NeuralNet src, NeuralNet dst) {
        // Hack: save to temp, reload into dst
        // (In production, expose getWeights/setWeights on NeuralNet)
        try {
            java.io.File tmp = java.io.File.createTempFile("dqn_sync", ".tmp");
            src.save(tmp.getAbsolutePath());
            NeuralNet loaded = NeuralNet.load(tmp.getAbsolutePath());
            // Copy weights manually via reflection or just use load result
            // Simplified: use the loaded network's weights
            // This is inefficient but works for the prototype
            copyWeightsInternal(loaded, dst);
            tmp.delete();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Direct weight copy between networks of identical architecture
    private static void copyWeightsInternal(NeuralNet src, NeuralNet dst) {
        try {
            java.lang.reflect.Field wField = NeuralNet.class.getDeclaredField("weights");
            java.lang.reflect.Field bField = NeuralNet.class.getDeclaredField("biases");
            wField.setAccessible(true);
            bField.setAccessible(true);
            float[][] srcW = (float[][]) wField.get(src);
            float[][] srcB = (float[][]) bField.get(src);
            float[][] dstW = (float[][]) wField.get(dst);
            float[][] dstB = (float[][]) bField.get(dst);
            for (int l = 0; l < srcW.length; l++) {
                System.arraycopy(srcW[l], 0, dstW[l], 0, srcW[l].length);
                System.arraycopy(srcB[l], 0, dstB[l], 0, srcB[l].length);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static int argmax(float[] arr) {
        int best = 0;
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] > arr[best]) best = i;
        }
        return best;
    }

    private static float max(float[] arr) {
        float m = arr[0];
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] > m) m = arr[i];
        }
        return m;
    }

    // ──────── Persistence ────────

    public void saveModel(String path) throws java.io.IOException {
        onlineNet.save(path);
    }

    public void loadModel(String path) throws java.io.IOException {
        NeuralNet loaded = NeuralNet.load(path);
        copyWeightsInternal(loaded, onlineNet);
        copyWeightsInternal(loaded, targetNet);
    }
}
