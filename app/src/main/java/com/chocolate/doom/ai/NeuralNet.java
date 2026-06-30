package com.chocolate.doom.ai;

import java.io.*;
import java.util.Random;

/**
 * Pure-Java feedforward neural network for DQN.
 *
 * Architecture: input → hidden[0] → ... → hidden[k] → output
 * Activation: ReLU (hidden), Linear (output)
 * Loss: MSE (Huber loss variant for RL stability)
 * Optimizer: SGD with momentum
 *
 * All weights stored as float[] arrays — no external dependencies.
 */
public class NeuralNet {
    private final int[] layerSizes;     // sizes of each layer (incl. input/output)
    private final float[][] weights;    // weights[l][i * nextSize + j]
    private final float[][] biases;     // biases[l][j]
    private final float[][] momentumW;  // momentum buffers for SGD
    private final float[][] momentumB;
    private final float[][] activations; // cached forward-pass activations
    private final float[][] zValues;     // pre-activation values (for backprop)

    private final Random rng = new Random();
    private float learningRate = 0.001f;
    private float momentum = 0.9f;
    private float l2Reg = 0.0001f;

    /**
     * @param layerSizes e.g. {13, 128, 64, 14} for 13 input → 128 → 64 → 14 actions
     */
    public NeuralNet(int... layerSizes) {
        this.layerSizes = layerSizes;
        int numLayers = layerSizes.length - 1; // weight matrices between layers

        weights = new float[numLayers][];
        biases = new float[numLayers][];
        momentumW = new float[numLayers][];
        momentumB = new float[numLayers][];
        activations = new float[layerSizes.length][];
        zValues = new float[layerSizes.length][];

        for (int l = 0; l < numLayers; l++) {
            int inSize = layerSizes[l];
            int outSize = layerSizes[l + 1];
            weights[l] = new float[inSize * outSize];
            biases[l] = new float[outSize];
            momentumW[l] = new float[inSize * outSize];
            momentumB[l] = new float[outSize];
            activations[l] = new float[inSize];   // l=0 is input
            zValues[l + 1] = new float[outSize];
        }
        activations[layerSizes.length - 1] = new float[layerSizes[layerSizes.length - 1]];

        initWeights();
    }

    /** Xavier/Glorot initialization */
    private void initWeights() {
        for (int l = 0; l < weights.length; l++) {
            int inSize = layerSizes[l];
            int outSize = layerSizes[l + 1];
            float limit = (float) Math.sqrt(6.0 / (inSize + outSize));
            float[] w = weights[l];
            for (int i = 0; i < w.length; i++) {
                w[i] = (rng.nextFloat() * 2f - 1f) * limit;
            }
            // biases start at 0
        }
    }

    // ──────── Forward Pass ────────

    /**
     * Forward pass: input → hidden layers (ReLU) → output (linear).
     * @return output layer activations (Q-values)
     */
    public float[] forward(float[] input) {
        if (input.length != layerSizes[0])
            throw new IllegalArgumentException("Input size mismatch: " + input.length + " vs " + layerSizes[0]);

        System.arraycopy(input, 0, activations[0], 0, input.length);

        for (int l = 0; l < weights.length; l++) {
            int inSize = layerSizes[l];
            int outSize = layerSizes[l + 1];
            float[] w = weights[l];
            float[] b = biases[l];
            float[] aPrev = activations[l];
            float[] z = zValues[l + 1];

            // z = W * aPrev + b
            for (int j = 0; j < outSize; j++) {
                float sum = b[j];
                for (int i = 0; i < inSize; i++) {
                    sum += w[i * outSize + j] * aPrev[i];
                }
                z[j] = sum;
            }

            // activation: ReLU for hidden, linear for output
            float[] a = activations[l + 1];
            if (l < weights.length - 1) {
                for (int j = 0; j < outSize; j++) a[j] = Math.max(0f, z[j]); // ReLU
            } else {
                System.arraycopy(z, 0, a, 0, outSize); // linear output
            }
        }

        return activations[activations.length - 1];
    }

    // ──────── Training ────────

    /**
     * Single training step: forward → compute loss → backward → update weights.
     * Uses Huber loss for RL stability (smooth L1).
     *
     * @param input   state features
     * @param target  target Q-values (from Bellman update)
     * @param mask    which output dimensions to train (null = all)
     */
    public void train(float[] input, float[] target, boolean[] mask) {
        // Forward pass
        forward(input);

        int lastLayer = weights.length - 1;
        int outSize = layerSizes[layerSizes.length - 1];

        // Output layer gradients: dL/dz = (pred - target) clipped for Huber
        float[] outputGrad = new float[outSize];
        float[] pred = activations[activations.length - 1];
        int masked = 0;
        for (int j = 0; j < outSize; j++) {
            if (mask == null || mask[j]) {
                float diff = pred[j] - target[j];
                // Huber loss: clip gradient at 1.0
                outputGrad[j] = Math.max(-1f, Math.min(1f, diff));
                masked++;
            } else {
                outputGrad[j] = 0f;
            }
        }
        if (masked == 0) return; // nothing to train

        // Backpropagate through hidden layers
        float[][] layerGrads = new float[weights.length][];
        layerGrads[lastLayer] = outputGrad;

        for (int l = lastLayer - 1; l >= 0; l--) {
            int inSize = layerSizes[l];
            int nextSize = layerSizes[l + 1];
            float[] nextGrad = layerGrads[l + 1];
            float[] w = weights[l];
            float[] z = zValues[l + 1];
            float[] grad = new float[inSize];

            // grad[i] = sum_j(W[i][j] * nextGrad[j]) * ReLU'(z[i]) for this layer's input
            // Wait, the grad computed here is for the output of layer l.
            // Actually, we need dL/d(activation of layer l) = sum_j W[i][j] * nextGrad[j]
            // Then for the previous layer (l-1), we need dL/dz[l] = dL/da[l] * ReLU'(z[l])

            // Compute dL/da[l] (gradient w.r.t. this layer's output/input to next)
            for (int i = 0; i < inSize; i++) {
                float sum = 0f;
                for (int j = 0; j < nextSize; j++) {
                    sum += w[i * nextSize + j] * nextGrad[j];
                }
                grad[i] = sum;
            }

            // For layer l (which takes input from layer l-1):
            // dL/dz[l] = dL/da[l] * ReLU'(z[l])
            if (l > 0) {
                // layer l has zValues[l] (pre-activation for layer l's output)
                float[] zPrev = zValues[l];
                for (int i = 0; i < inSize; i++) {
                    grad[i] *= (zPrev[i] > 0 ? 1f : 0f); // ReLU derivative
                }
            }
            // else l==0: input layer, no activation, just pass through

            layerGrads[l] = grad;
        }

        // Update weights and biases
        for (int l = 0; l < weights.length; l++) {
            int inSize = layerSizes[l];
            int outSz = layerSizes[l + 1];
            float[] w = weights[l];
            float[] b = biases[l];
            float[] mw = momentumW[l];
            float[] mb = momentumB[l];
            float[] a = activations[l];
            float[] grad = layerGrads[l]; // gradient w.r.t. z[l+1]

            for (int j = 0; j < outSz; j++) {
                float g = grad[j] / Math.max(1f, masked); // scale by batch size proxy
                // Bias update
                mb[j] = momentum * mb[j] - learningRate * g;
                b[j] += mb[j] - learningRate * l2Reg * b[j];

                // Weight update
                for (int i = 0; i < inSize; i++) {
                    int idx = i * outSize + j;
                    float dw = g * a[i];
                    mw[idx] = momentum * mw[idx] - learningRate * dw;
                    w[idx] += mw[idx] - learningRate * l2Reg * w[idx];
                }
            }
        }
    }

    // ──────── Persistence ────────

    /** Save model weights to file (simple binary format) */
    public void save(String path) throws IOException {
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(path)))) {
            out.writeInt(layerSizes.length);
            for (int s : layerSizes) out.writeInt(s);
            out.writeFloat(learningRate);
            out.writeFloat(momentum);
            for (float[] w : weights) {
                out.writeInt(w.length);
                for (float v : w) out.writeFloat(v);
            }
            for (float[] b : biases) {
                out.writeInt(b.length);
                for (float v : b) out.writeFloat(v);
            }
        }
    }

    /** Load model weights from file */
    public static NeuralNet load(String path) throws IOException {
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new FileInputStream(path)))) {
            int numLayers = in.readInt();
            int[] sizes = new int[numLayers];
            for (int i = 0; i < numLayers; i++) sizes[i] = in.readInt();
            NeuralNet net = new NeuralNet(sizes);
            net.learningRate = in.readFloat();
            net.momentum = in.readFloat();
            for (float[] w : net.weights) {
                int len = in.readInt();
                for (int i = 0; i < len; i++) w[i] = in.readFloat();
            }
            for (float[] b : net.biases) {
                int len = in.readInt();
                for (int i = 0; i < len; i++) b[i] = in.readFloat();
            }
            return net;
        }
    }

    // ──────── Accessors ────────

    public void setLearningRate(float lr) { this.learningRate = lr; }
    public float getLearningRate() { return learningRate; }
    public int getInputSize() { return layerSizes[0]; }
    public int getOutputSize() { return layerSizes[layerSizes.length - 1]; }
    public int getNumParams() {
        int n = 0;
        for (float[] w : weights) n += w.length;
        for (float[] b : biases) n += b.length;
        return n;
    }
}
