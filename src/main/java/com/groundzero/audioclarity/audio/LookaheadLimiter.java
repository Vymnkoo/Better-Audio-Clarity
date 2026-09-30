package com.groundzero.audioclarity.audio;

/**
 * The safety limiter at the end of the chain, with lookahead: the mix is held back by the
 * lookahead time, so the limiter sees each peak coming and lowers the gain smoothly before it
 * arrives, instead of chopping it the instant it hits (which crackles).
 *
 * <p>The gain it applies is the moving average (over the lookahead) of the lowest gain any
 * sample in the window needs, so it has fully reached a peak's gain by the time that peak comes
 * out - nothing can slip past the ceiling. With no lookahead it falls back to an instant limiter.
 *
 * <p>Runs per sample on OpenAL's mixing thread: no allocation, all buffers sized up front.
 */
public final class LookaheadLimiter {

    public static final float CEILING = 0.966f;   // -0.3 dBFS

    private final int maxDelay;
    private final float[] delayL, delayR;   // the held-back mix
    private final float[] box;              // window minimums over the last `delay` samples
    private final long[] minIdx;            // monotonic deque of (sample index, needed gain)
    private final float[] minVal;
    private int delay = -1;
    private int pos;
    private double boxSum;
    private int qHead, qSize;
    private long sampleIndex;
    private float gain = 1f;

    /** Result of the last {@link #process} call. */
    public float outL, outR;

    public LookaheadLimiter(int maxDelaySamples) {
        this.maxDelay = Math.max(1, maxDelaySamples);
        this.delayL = new float[maxDelay];
        this.delayR = new float[maxDelay];
        this.box = new float[maxDelay];
        this.minIdx = new long[maxDelay + 2];
        this.minVal = new float[maxDelay + 2];
    }

    /** Sets the lookahead in samples; changing it clears the buffers (a tiny gap is fine). */
    public void setDelay(int d) {
        d = Math.max(0, Math.min(maxDelay, d));
        if (d == delay) {
            return;
        }
        delay = d;
        pos = 0;
        for (int i = 0; i < maxDelay; i++) {
            delayL[i] = 0f;
            delayR[i] = 0f;
            box[i] = 1f;
        }
        boxSum = d;
        qHead = 0;
        qSize = 0;
        gain = 1f;
    }

    /** Current gain, for tests and meters (1 = not limiting). */
    public float gain() {
        return gain;
    }

    /**
     * @param releaseCoef one-pole coefficient for the gain recovering after a peak
     */
    public void process(float l, float r, float releaseCoef) {
        float peak = Math.max(Math.abs(l), Math.abs(r));
        float need = peak > CEILING ? CEILING / peak : 1f;
        if (delay <= 0) {
            gain = need < gain ? need : need + releaseCoef * (gain - need);
            outL = l * gain;
            outR = r * gain;
            return;
        }
        float windowMin = pushWindowMin(need);
        boxSum += windowMin - box[pos];
        box[pos] = windowMin;
        float smooth = (float) Math.min(1.0, boxSum / delay);
        gain = smooth < gain ? smooth : smooth + releaseCoef * (gain - smooth);
        float heldL = delayL[pos];
        float heldR = delayR[pos];
        delayL[pos] = l;
        delayR[pos] = r;
        pos = pos + 1 == delay ? 0 : pos + 1;
        outL = heldL * gain;
        outR = heldR * gain;
    }

    /** Adds this sample's needed gain; returns the lowest among the last delay + 1 samples. */
    private float pushWindowMin(float need) {
        int cap = minVal.length;
        long idx = sampleIndex++;
        while (qSize > 0) {
            int back = (qHead + qSize - 1) % cap;
            if (minVal[back] >= need) {
                qSize--;
            } else {
                break;
            }
        }
        int slot = (qHead + qSize) % cap;
        minIdx[slot] = idx;
        minVal[slot] = need;
        qSize++;
        while (minIdx[qHead] < idx - delay) {
            qHead = (qHead + 1) % cap;
            qSize--;
        }
        return minVal[qHead];
    }
}
