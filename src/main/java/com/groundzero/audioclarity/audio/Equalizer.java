package com.groundzero.audioclarity.audio;

import com.groundzero.audioclarity.ClarityConfig;

import java.util.List;

/**
 * A global EQ on the game mix, after the compressor and before the limiter (so a boost can't
 * clip): a chain of biquad filters (RBJ Audio EQ Cookbook), each band one of highpass / lowpass /
 * lowshelf / highshelf / peak. Driven frame by frame from {@link Compressor}, on OpenAL's mixing
 * thread.
 *
 * <p>Coefficients are only recomputed when the config hands over a new settings object, so the
 * per-block path never allocates. Filter memory is kept across a re-tune when the band layout
 * is unchanged, so moving a slider-like value in the file doesn't click.
 */
public final class Equalizer {

    private final double sampleRate;
    private ClarityConfig.Eq applied;
    private boolean active;
    private int bands;
    // Per band: normalised coefficients, and the filter memory of each channel (transposed direct form II).
    private double[] b0 = new double[0], b1 = b0, b2 = b0, a1 = b0, a2 = b0;
    private double[] l1 = b0, l2 = b0, r1 = b0, r2 = b0;

    public Equalizer(float sampleRate) {
        this.sampleRate = sampleRate;
    }

    /** Left / right result of the last {@link #filter} call. */
    public double outL, outR;

    /** Call once per block before filtering. Returns false when the EQ is off (skip {@link #filter}). */
    public boolean begin() {
        ClarityConfig.Eq eq = ClarityConfig.eq();
        if (eq != applied) {
            configure(eq);
        }
        return active;
    }

    /** Filters one stereo frame; the result is in {@link #outL} / {@link #outR}. */
    public void filter(double l, double r) {
        for (int b = 0; b < bands; b++) {
            double yl = b0[b] * l + l1[b];
            l1[b] = b1[b] * l - a1[b] * yl + l2[b];
            l2[b] = b2[b] * l - a2[b] * yl;
            l = yl;
            double yr = b0[b] * r + r1[b];
            r1[b] = b1[b] * r - a1[b] * yr + r2[b];
            r2[b] = b2[b] * r - a2[b] * yr;
            r = yr;
        }
        outL = l;
        outR = r;
    }

    /** Call once per block after filtering. */
    public void end() {
        // Denormals: a decayed filter tail can crawl through tiny numbers very slowly on some CPUs.
        for (int b = 0; b < bands; b++) {
            if (Math.abs(l1[b]) < 1e-20) l1[b] = 0;
            if (Math.abs(l2[b]) < 1e-20) l2[b] = 0;
            if (Math.abs(r1[b]) < 1e-20) r1[b] = 0;
            if (Math.abs(r2[b]) < 1e-20) r2[b] = 0;
        }
    }

    private void configure(ClarityConfig.Eq eq) {
        List<ClarityConfig.Band> list = eq.bands();
        boolean sameLayout = applied != null && applied.bands().size() == list.size();
        if (sameLayout) {
            for (int i = 0; i < list.size(); i++) {
                if (!applied.bands().get(i).type().equals(list.get(i).type())) {
                    sameLayout = false;
                    break;
                }
            }
        }
        applied = eq;
        int n = list.size();
        if (!sameLayout) {
            b0 = new double[n]; b1 = new double[n]; b2 = new double[n]; a1 = new double[n]; a2 = new double[n];
            l1 = new double[n]; l2 = new double[n]; r1 = new double[n]; r2 = new double[n];
        }
        for (int i = 0; i < n; i++) {
            ClarityConfig.Band band = list.get(i);
            double f = Math.max(10.0, Math.min(band.freqHz(), sampleRate * 0.45));
            double w0 = 2 * Math.PI * f / sampleRate;
            double cos = Math.cos(w0);
            double alpha = Math.sin(w0) / (2 * Math.max(0.1, band.q()));
            double a = Math.pow(10, band.gainDb() / 40.0);
            double sqA2alpha = 2 * Math.sqrt(a) * alpha;
            double nb0, nb1, nb2, na0, na1, na2;
            switch (band.type()) {
                case "highpass" -> {
                    nb0 = (1 + cos) / 2; nb1 = -(1 + cos); nb2 = (1 + cos) / 2;
                    na0 = 1 + alpha; na1 = -2 * cos; na2 = 1 - alpha;
                }
                case "lowpass" -> {
                    nb0 = (1 - cos) / 2; nb1 = 1 - cos; nb2 = (1 - cos) / 2;
                    na0 = 1 + alpha; na1 = -2 * cos; na2 = 1 - alpha;
                }
                case "lowshelf" -> {
                    nb0 = a * ((a + 1) - (a - 1) * cos + sqA2alpha);
                    nb1 = 2 * a * ((a - 1) - (a + 1) * cos);
                    nb2 = a * ((a + 1) - (a - 1) * cos - sqA2alpha);
                    na0 = (a + 1) + (a - 1) * cos + sqA2alpha;
                    na1 = -2 * ((a - 1) + (a + 1) * cos);
                    na2 = (a + 1) + (a - 1) * cos - sqA2alpha;
                }
                case "highshelf" -> {
                    nb0 = a * ((a + 1) + (a - 1) * cos + sqA2alpha);
                    nb1 = -2 * a * ((a - 1) + (a + 1) * cos);
                    nb2 = a * ((a + 1) + (a - 1) * cos - sqA2alpha);
                    na0 = (a + 1) - (a - 1) * cos + sqA2alpha;
                    na1 = 2 * ((a - 1) - (a + 1) * cos);
                    na2 = (a + 1) - (a - 1) * cos - sqA2alpha;
                }
                default -> { // peak
                    nb0 = 1 + alpha * a; nb1 = -2 * cos; nb2 = 1 - alpha * a;
                    na0 = 1 + alpha / a; na1 = -2 * cos; na2 = 1 - alpha / a;
                }
            }
            b0[i] = nb0 / na0; b1[i] = nb1 / na0; b2[i] = nb2 / na0;
            a1[i] = na1 / na0; a2[i] = na2 / na0;
        }
        bands = n;
        active = eq.enabled() && n > 0;
    }
}
