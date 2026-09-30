package com.groundzero.audioclarity.audio;

/**
 * A loudness meter to ITU-R BS.1770-4 / EBU R128, the standard behind "LUFS" targets (streaming
 * uses -14 LUFS; the tuning screen aims the game itself at a more natural -18). Measures the finished stereo mix:
 * <ul>
 *   <li>K-weighting (the standard's two filters: a high shelf for how the head shapes sound, and
 *       a high-pass that ignores deep rumble), coefficients worked out for any sample rate;</li>
 *   <li>momentary loudness (last 400 ms), short-term (last 3 s);</li>
 *   <li>integrated loudness since the last reset: 400 ms blocks every 100 ms, gated at -70 LUFS
 *       and then 10 LU below the ungated average, as the standard specifies;</li>
 *   <li>sample peak since the last reset.</li>
 * </ul>
 * Runs per sample on OpenAL's mixing thread - no allocation: blocks go into a fixed histogram
 * (0.1 LU bins), so it can measure for hours.
 */
public final class LoudnessMeter {

    private static final double MIN_LUFS = -70.0;
    private static final double MAX_LUFS = 10.0;
    private static final double BIN = 0.1;
    private static final int BINS = (int) ((MAX_LUFS - MIN_LUFS) / BIN);

    // K-weighting: stage 1 (high shelf) and stage 2 (high-pass), transposed direct form II per channel.
    private final double s1b0, s1b1, s1b2, s1a1, s1a2;
    private final double s2a1, s2a2;
    private double l1a, l1b, l2a, l2b, r1a, r1b, r2a, r2b;

    private final int subBlockFrames;        // 100 ms
    private double subSum;                   // sum of squares of the current 100 ms (both channels)
    private int subCount;
    private final double[] subBlocks = new double[30];   // last 3 s of 100 ms mean squares
    private int subPos, subFilled;

    private final long[] histogram = new long[BINS];
    private float peak;

    public volatile float momentaryLufs = (float) MIN_LUFS - 1;
    public volatile float shortTermLufs = (float) MIN_LUFS - 1;
    public volatile float integratedLufs = (float) MIN_LUFS - 1;
    public volatile float peakDb = -120f;
    /** Set from the GUI; the audio thread clears the measurement at its next block. */
    public volatile boolean resetRequested;

    public LoudnessMeter(double fs) {
        // Stage 1 - the "head" high shelf (same formulas as libebur128).
        double f0 = 1681.974450955533, g = 3.999843853973347, q = 0.7071752369554196;
        double k = Math.tan(Math.PI * f0 / fs);
        double vh = Math.pow(10.0, g / 20.0);
        double vb = Math.pow(vh, 0.4996667741545416);
        double a0 = 1.0 + k / q + k * k;
        s1b0 = (vh + vb * k / q + k * k) / a0;
        s1b1 = 2.0 * (k * k - vh) / a0;
        s1b2 = (vh - vb * k / q + k * k) / a0;
        s1a1 = 2.0 * (k * k - 1.0) / a0;
        s1a2 = (1.0 - k / q + k * k) / a0;
        // Stage 2 - the RLB high-pass (numerator 1, -2, 1).
        f0 = 38.13547087602444;
        q = 0.5003270373238773;
        k = Math.tan(Math.PI * f0 / fs);
        a0 = 1.0 + k / q + k * k;
        s2a1 = 2.0 * (k * k - 1.0) / a0;
        s2a2 = (1.0 - k / q + k * k) / a0;
        subBlockFrames = Math.max(1, (int) Math.round(fs / 10.0));
    }

    /** Feeds one stereo frame of the finished mix. */
    public void add(float l, float r) {
        if (resetRequested) {
            reset();
        }
        float p = Math.max(Math.abs(l), Math.abs(r));
        if (p > peak) {
            peak = p;
        }
        double kl = kWeight(l, true);
        double kr = kWeight(r, false);
        subSum += kl * kl + kr * kr;
        if (++subCount >= subBlockFrames) {
            endSubBlock();
        }
    }

    private double kWeight(double x, boolean left) {
        // Stage 1.
        double y;
        if (left) {
            y = s1b0 * x + l1a;
            l1a = s1b1 * x - s1a1 * y + l1b;
            l1b = s1b2 * x - s1a2 * y;
        } else {
            y = s1b0 * x + r1a;
            r1a = s1b1 * x - s1a1 * y + r1b;
            r1b = s1b2 * x - s1a2 * y;
        }
        // Stage 2 (b = 1, -2, 1).
        double z;
        if (left) {
            z = y + l2a;
            l2a = -2.0 * y - s2a1 * z + l2b;
            l2b = y - s2a2 * z;
        } else {
            z = y + r2a;
            r2a = -2.0 * y - s2a1 * z + r2b;
            r2b = y - s2a2 * z;
        }
        return z;
    }

    private void endSubBlock() {
        subBlocks[subPos] = subSum / subCount;
        subPos = (subPos + 1) % subBlocks.length;
        if (subFilled < subBlocks.length) {
            subFilled++;
        }
        subSum = 0;
        subCount = 0;

        double momentary = mean(4);
        if (subFilled >= 4) {
            momentaryLufs = (float) lufs(momentary);
            // Every 100 ms a new 400 ms block (75% overlap) goes to the integrated measurement.
            double block = lufs(momentary);
            if (block > MIN_LUFS && block < MAX_LUFS) {
                histogram[(int) ((block - MIN_LUFS) / BIN)]++;
            }
            integratedLufs = (float) integrated();
        }
        if (subFilled >= 30) {
            shortTermLufs = (float) lufs(mean(30));
        }
        peakDb = peak > 1e-6f ? (float) (20.0 * Math.log10(peak)) : -120f;
    }

    /** Mean square of the last n sub-blocks. */
    private double mean(int n) {
        n = Math.min(n, subFilled);
        double sum = 0;
        for (int i = 1; i <= n; i++) {
            sum += subBlocks[(subPos - i + subBlocks.length) % subBlocks.length];
        }
        return n > 0 ? sum / n : 0;
    }

    /** Gated integrated loudness from the histogram (absolute gate, then relative gate -10 LU). */
    private double integrated() {
        double energy = 0;
        long count = 0;
        for (int i = 0; i < BINS; i++) {
            if (histogram[i] > 0) {
                energy += histogram[i] * energyOfBin(i);
                count += histogram[i];
            }
        }
        if (count == 0) {
            return MIN_LUFS - 1;
        }
        double relativeGate = lufs(energy / count) - 10.0;
        energy = 0;
        count = 0;
        int start = Math.max(0, (int) ((relativeGate - MIN_LUFS) / BIN));
        for (int i = start; i < BINS; i++) {
            if (histogram[i] > 0) {
                energy += histogram[i] * energyOfBin(i);
                count += histogram[i];
            }
        }
        return count > 0 ? lufs(energy / count) : MIN_LUFS - 1;
    }

    private static double energyOfBin(int i) {
        double centre = MIN_LUFS + (i + 0.5) * BIN;
        return Math.pow(10.0, (centre + 0.691) / 10.0);
    }

    private static double lufs(double meanSquare) {
        return meanSquare > 1e-12 ? -0.691 + 10.0 * Math.log10(meanSquare) : MIN_LUFS - 1;
    }

    private void reset() {
        resetRequested = false;
        java.util.Arrays.fill(histogram, 0);
        subPos = 0;
        subFilled = 0;
        subSum = 0;
        subCount = 0;
        peak = 0;
        momentaryLufs = shortTermLufs = integratedLufs = (float) MIN_LUFS - 1;
        peakDb = -120f;
    }
}
