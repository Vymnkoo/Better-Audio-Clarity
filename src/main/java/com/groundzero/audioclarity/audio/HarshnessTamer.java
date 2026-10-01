package com.groundzero.audioclarity.audio;

import com.groundzero.audioclarity.ClarityConfig;

/**
 * A dynamic EQ for harsh sounds, after the global EQ and before the limiter. A band-pass filter
 * (RBJ, constant 0 dB peak) picks out the harsh region; its level is compared with the level of
 * the whole mix, and only while the band stands out by more than the threshold is part of it
 * subtracted again: {@code out = in - cut * band}, a peaking cut of {@code cut} at the centre that
 * is 0 dB - no change at all - the rest of the time. Stereo-linked, so the image never shifts.
 *
 * <p>Comparing with the whole mix rather than with a fixed level makes it judge the sound's
 * colour, not its loudness: a loud but balanced sound passes, a quiet but piercing one is tamed.
 * Runs per frame on OpenAL's mixing thread: no allocation.
 */
public final class HarshnessTamer {

    private static final double FLOOR = 1e-9;   // ~ -90 dBFS mean square: below this nothing is judged

    private final double sampleRate;
    private ClarityConfig.Tamer applied;
    private boolean active;
    private double b0, b2, a1, a2;              // band-pass: b1 = 0, b2 = -b0
    private double l1, l2, r1, r2;              // filter memory (transposed direct form II)
    private double envC, attackC, releaseC;
    private double bandEnv, fullEnv;            // smoothed mean squares
    private double threshold, slope, maxCut;
    private double cutDb;                       // current reduction, <= 0

    /** Deepest cut of the last block, for the meter. */
    public volatile float meterCutDb;
    /** How far the band stood above the whole mix (dB) at its peak in the last block, for the meter. */
    public volatile float meterBandDb = -60f;
    private float blockCut, blockBand;

    public double outL, outR;

    public HarshnessTamer(float sampleRate) {
        this.sampleRate = sampleRate;
    }

    /** Once per block. Returns false when the tamer is off (skip {@link #process}). */
    public boolean begin() {
        ClarityConfig.Tamer t = ClarityConfig.tamer();
        if (t != applied) {
            configure(t);
        }
        blockCut = 0f;
        blockBand = -60f;
        return active;
    }

    public void process(double l, double r) {
        double bl = b0 * l + l1;
        l1 = -a1 * bl + l2;
        l2 = -b0 * l - a2 * bl;
        double br = b0 * r + r1;
        r1 = -a1 * br + r2;
        r2 = -b0 * r - a2 * br;

        // ~5 ms RMS of the band and of the whole mix.
        double bandPow = (bl * bl + br * br) * 0.5;
        double fullPow = (l * l + r * r) * 0.5;
        bandEnv = bandPow + envC * (bandEnv - bandPow);
        fullEnv = fullPow + envC * (fullEnv - fullPow);

        double target = 0;
        if (fullEnv > FLOOR) {
            // The band against the rest of the sound: a piercing sound reads well above 0 dB.
            double rest = Math.max(fullEnv - bandEnv, fullEnv * 1e-3);
            double rel = 10 * Math.log10(Math.max(bandEnv, FLOOR * 1e-3) / rest);
            if (rel > blockBand) {
                blockBand = (float) rel;
            }
            double over = rel - threshold;
            if (over > 0) {
                target = Math.max(-maxCut, -over * slope);
            }
        }
        double c = target < cutDb ? attackC : releaseC;
        cutDb = target + c * (cutDb - target);
        if (cutDb < blockCut) {
            blockCut = (float) cutDb;
        }
        double amount = cutDb > -0.001 ? 0 : 1 - Math.pow(10, cutDb / 20);   // 0 = untouched
        outL = l - amount * bl;
        outR = r - amount * br;
    }

    /** Once per block, after processing. */
    public void end() {
        if (Math.abs(l1) < 1e-20) l1 = 0;
        if (Math.abs(l2) < 1e-20) l2 = 0;
        if (Math.abs(r1) < 1e-20) r1 = 0;
        if (Math.abs(r2) < 1e-20) r2 = 0;
        if (bandEnv < 1e-30) bandEnv = 0;
        if (fullEnv < 1e-30) fullEnv = 0;
        meterCutDb = blockCut;
        meterBandDb = blockBand;
    }

    private void configure(ClarityConfig.Tamer t) {
        applied = t;
        double f = Math.max(200.0, Math.min(t.freqHz(), sampleRate * 0.45));
        double w0 = 2 * Math.PI * f / sampleRate;
        double alpha = Math.sin(w0) / (2 * Math.max(0.1, t.q()));
        double a0 = 1 + alpha;
        b0 = alpha / a0;
        b2 = -b0;
        a1 = -2 * Math.cos(w0) / a0;
        a2 = (1 - alpha) / a0;
        envC = Math.exp(-1.0 / (0.005 * sampleRate));
        attackC = Math.exp(-1.0 / (Math.max(0.1, t.attackMs()) * 0.001 * sampleRate));
        releaseC = Math.exp(-1.0 / (Math.max(1, t.releaseMs()) * 0.001 * sampleRate));
        threshold = t.thresholdDb();
        slope = 1 - 1 / Math.max(1, t.ratio());
        maxCut = t.maxCutDb();
        active = t.enabled();
        if (!active) {
            cutDb = 0;
            meterCutDb = 0;
        }
    }
}
