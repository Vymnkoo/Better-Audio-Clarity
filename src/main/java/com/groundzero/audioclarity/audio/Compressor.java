package com.groundzero.audioclarity.audio;

import com.groundzero.audioclarity.ClarityConfig;
import org.lwjgl.system.MemoryUtil;

/**
 * A stereo-linked, feed-forward compressor with a soft knee, followed by a safety limiter with
 * lookahead ({@link LookaheadLimiter}) - the classic master-bus chain - with the global
 * {@link Equalizer} in between. Works in place on interleaved 32-bit float stereo straight in
 * native memory.
 *
 * <p>Runs on OpenAL's mixing thread for every block the speakers need, so it must never
 * allocate or block: a GC pause here is an audible dropout.
 */
public final class Compressor {

    private static final float DB_PER_NEPER = 8.685889638f;   // 20 / ln(10)
    private static final float NEPER_PER_DB = 0.11512925465f; // ln(10) / 20
    private static final float SILENCE_DB = -120f;
    public static final float MAX_LOOKAHEAD_MS = 20f;

    private final float sampleRate;
    private final Equalizer eq;
    public final HarshnessTamer tamer;     // presence band (~3.5 kHz)
    public final HarshnessTamer sizzle;    // sizzle band (~10 kHz)
    private final LookaheadLimiter limiter;
    private float grDb;             // current gain reduction, <= 0
    private float masterGain = 1f;  // last block's master volume, ramped from to avoid zipper noise
    private float staticGain = 1f;  // last block's make-up x output, ramped the same way

    // Meters for the Music & Sound screen: loudest input/output and deepest reduction of the last block.
    public volatile float meterInDb = SILENCE_DB;
    public volatile float meterOutDb = SILENCE_DB;
    public volatile float meterGrDb;

    public Compressor(float sampleRate) {
        this.sampleRate = sampleRate;
        this.eq = new Equalizer(sampleRate);
        this.tamer = new HarshnessTamer(sampleRate, ClarityConfig::tamer);
        this.sizzle = new HarshnessTamer(sampleRate, ClarityConfig::sizzle);
        this.limiter = new LookaheadLimiter((int) Math.ceil(MAX_LOOKAHEAD_MS * 0.001 * sampleRate));
    }

    /**
     * @param master the game's Master volume slider, applied last - after the compressor and
     *               limiter - so it only sets how loud the finished mix is.
     */
    public void process(long address, int frames, float master) {
        ClarityConfig.Compressor p = ClarityConfig.compressor();
        float masterStart = masterGain;
        float masterStep = frames > 0 ? (master - masterStart) / frames : 0f;
        masterGain = master;
        boolean on = p.enabled();
        float threshold = p.thresholdDb();
        float slope = 1f - 1f / p.ratio();
        float knee = p.kneeDb();
        float halfKnee = knee * 0.5f;
        float attack = coefficient(p.attackMs());
        float release = coefficient(p.releaseMs());
        // Make-up and output ramp across the block too, so moving them never clicks.
        float staticTarget = dbToGain(on ? p.makeupDb() : 0f) * dbToGain(p.outputDb());
        float staticStart = staticGain;
        float staticStep = frames > 0 ? (staticTarget - staticStart) / frames : 0f;
        staticGain = staticTarget;
        boolean limit = p.limiter();
        float limiterRelease = coefficient(80f);
        if (limit) {
            limiter.setDelay(Math.round(p.lookaheadMs() * 0.001f * sampleRate));
        }
        boolean eqOn = eq.begin();
        boolean tamerOn = tamer.begin();
        boolean sizzleOn = sizzle.begin();

        float peakIn = 0f, peakOut = 0f, deepestGr = 0f;
        for (int i = 0; i < frames; i++) {
            long at = address + ((long) i << 3);
            float l = MemoryUtil.memGetFloat(at);
            float r = MemoryUtil.memGetFloat(at + 4);
            float peak = Math.max(Math.abs(l), Math.abs(r));
            if (peak > peakIn) {
                peakIn = peak;
            }

            float gainDb = 0f;
            if (on) {
                float levelDb = peak > 1e-6f ? (float) Math.log(peak) * DB_PER_NEPER : SILENCE_DB;
                float over = levelDb - threshold;
                float target;
                if (over <= -halfKnee) {
                    target = 0f;
                } else if (over < halfKnee) {
                    float x = over + halfKnee;
                    target = -slope * x * x / (2f * knee);
                } else {
                    target = -slope * over;
                }
                // Attack while reduction deepens, release while it recovers.
                float c = target < grDb ? attack : release;
                grDb = target + c * (grDb - target);
                if (grDb < deepestGr) {
                    deepestGr = grDb;
                }
                gainDb = grDb;
            }
            float g = (float) Math.exp(gainDb * NEPER_PER_DB) * (staticStart + staticStep * (i + 1));
            l *= g;
            r *= g;

            // Tone shaping on the compressed mix, ahead of the limiter.
            if (eqOn) {
                eq.filter(l, r);
                l = (float) eq.outL;
                r = (float) eq.outR;
            }
            // Harsh bands are tamed after the EQ, so a presence boost can't make a harsh sound worse.
            if (tamerOn) {
                tamer.process(l, r);
                l = (float) tamer.outL;
                r = (float) tamer.outR;
            }
            if (sizzleOn) {
                sizzle.process(l, r);
                l = (float) sizzle.outL;
                r = (float) sizzle.outR;
            }

            if (limit) {
                limiter.process(l, r, limiterRelease);
                l = limiter.outL;
                r = limiter.outR;
            }
            // Never hand the device anything past full scale.
            l = Math.max(-1f, Math.min(1f, l));
            r = Math.max(-1f, Math.min(1f, r));

            // The OUT meter shows the processed mix before the Master slider, like a desk's
            // bus meter ahead of the monitor volume.
            float out = Math.max(Math.abs(l), Math.abs(r));
            if (out > peakOut) {
                peakOut = out;
            }
            float m = masterStart + masterStep * (i + 1);
            l *= m;
            r *= m;
            MemoryUtil.memPutFloat(at, l);
            MemoryUtil.memPutFloat(at + 4, r);
        }
        if (eqOn) {
            eq.end();
        }
        if (tamerOn) {
            tamer.end();
        }
        if (sizzleOn) {
            sizzle.end();
        }
        meterInDb = toDb(peakIn);
        meterOutDb = toDb(peakOut);
        meterGrDb = deepestGr;
    }

    /** One-pole smoothing coefficient for a time constant in milliseconds. */
    private float coefficient(float ms) {
        return (float) Math.exp(-1.0 / (Math.max(0.01f, ms) * 0.001 * sampleRate));
    }

    private static float dbToGain(float db) {
        return (float) Math.exp(db * NEPER_PER_DB);
    }

    private static float toDb(float linear) {
        return linear > 1e-6f ? (float) Math.log(linear) * DB_PER_NEPER : SILENCE_DB;
    }
}
