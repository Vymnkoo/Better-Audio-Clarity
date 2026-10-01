package com.groundzero.audioclarity.gui;

import com.groundzero.audioclarity.ClarityConfig;
import com.groundzero.audioclarity.ClarityConfig.Tamer;
import com.groundzero.audioclarity.audio.HarshnessTamer;
import com.groundzero.audioclarity.audio.MasterBus;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The Harshness tab: the dynamic EQ that turns down a harsh band only while it stands out. A
 * meter shows how far the band sits relative to the whole mix (with the threshold marked) and
 * how much is being taken out.
 */
public class HarshnessScreen extends TabbedScreen {

    private static final int SLIDER_W = 150;
    private static final int ROW = 24;
    private static final float BAND_MIN = -10f, BAND_MAX = 20f;

    /** Which band the controls edit: 0 = presence (~3.5 kHz), 1 = sizzle (~10 kHz). Kept while the game runs. */
    private static int band;

    private Tamer t = settings();
    private float shownBand = BAND_MIN, shownCut;
    private long lastFrame = System.nanoTime();

    public HarshnessScreen(Screen parent) {
        super(parent);
    }

    @Override
    protected void init() {
        addTabs();
        t = settings();
        int left = width / 2 - SLIDER_W - 5;
        int right = width / 2 + 5;
        int y = TOP;
        // Not a tuning control: switching which band is shown changes nothing, so it is never locked.
        addRenderableWidget(Button.builder(bandLabel(), b -> {
            band = 1 - band;
            rebuildWidgets();
        }).bounds(left, y, SLIDER_W * 2 + 10, 20).build());
        y += ROW;
        addTuning(Button.builder(enabledLabel(), b -> {
            update(new Tamer(!t.enabled(), t.freqHz(), t.q(), t.thresholdDb(), t.ratio(), t.maxCutDb(), t.attackMs(), t.releaseMs()));
            b.setMessage(enabledLabel());
        }).bounds(left, y, SLIDER_W, 20).build());
        addTuning(new Slider(right, y, "Frequency", 1000f, 16000f, true, t.freqHz(),
                v -> v < 10000 ? fmt("%.2f kHz", v / 1000f) : fmt("%.1f kHz", v / 1000f),
                (q, v) -> new Tamer(q.enabled(), v, q.q(), q.thresholdDb(), q.ratio(), q.maxCutDb(), q.attackMs(), q.releaseMs())));
        y += ROW;
        addTuning(new Slider(left, y, "Threshold", -10f, 20f, false, t.thresholdDb(), v -> fmt("%+.1f dB", v),
                (q, v) -> new Tamer(q.enabled(), q.freqHz(), q.q(), v, q.ratio(), q.maxCutDb(), q.attackMs(), q.releaseMs())));
        addTuning(new Slider(right, y, "Ratio", 1f, 20f, true, t.ratio(), v -> fmt("%.1f:1", v),
                (q, v) -> new Tamer(q.enabled(), q.freqHz(), q.q(), q.thresholdDb(), v, q.maxCutDb(), q.attackMs(), q.releaseMs())));
        y += ROW;
        addTuning(new Slider(left, y, "Max cut", 0f, 24f, false, t.maxCutDb(), v -> fmt("%.1f dB", v),
                (q, v) -> new Tamer(q.enabled(), q.freqHz(), q.q(), q.thresholdDb(), q.ratio(), v, q.attackMs(), q.releaseMs())));
        addTuning(new Slider(right, y, "Width (Q)", 0.3f, 4f, true, t.q(), v -> fmt("%.2f", v),
                (q, v) -> new Tamer(q.enabled(), q.freqHz(), v, q.thresholdDb(), q.ratio(), q.maxCutDb(), q.attackMs(), q.releaseMs())));
        y += ROW;
        addTuning(new Slider(left, y, "Attack", 0.1f, 50f, true, t.attackMs(), v -> fmt(v < 10 ? "%.1f ms" : "%.0f ms", v),
                (q, v) -> new Tamer(q.enabled(), q.freqHz(), q.q(), q.thresholdDb(), q.ratio(), q.maxCutDb(), v, q.releaseMs())));
        addTuning(new Slider(right, y, "Release", 5f, 1000f, true, t.releaseMs(), v -> fmt("%.0f ms", v),
                (q, v) -> new Tamer(q.enabled(), q.freqHz(), q.q(), q.thresholdDb(), q.ratio(), q.maxCutDb(), q.attackMs(), v)));

        addRenderableWidget(Button.builder(Component.literal("Reset to defaults"), b -> {
            update(band == 0 ? ClarityConfig.DEFAULT_TAMER : ClarityConfig.DEFAULT_SIZZLE);
            rebuildWidgets();
        }).bounds(left, height - 28, SLIDER_W, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(right, height - 28, SLIDER_W, 20).build());
    }

    private static Tamer settings() {
        return band == 0 ? ClarityConfig.tamer() : ClarityConfig.sizzle();
    }

    private void update(Tamer next) {
        t = next;
        if (band == 0) {
            ClarityConfig.setTamer(next);
        } else {
            ClarityConfig.setSizzle(next);
        }
    }

    private static Component bandLabel() {
        return Component.literal(band == 0 ? "Band: Presence (harsh, ~3.5 kHz)  >" : "Band: Sizzle (coins, ~10 kHz)  >");
    }

    private Component enabledLabel() {
        return Component.literal("This band: " + (t.enabled() ? "ON" : "OFF"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        MasterBus bus = MasterBus.active();
        HarshnessTamer tamer = bus == null ? null : band == 0 ? bus.compressor().tamer : bus.compressor().sizzle;
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        float level = tamer != null && t.enabled() ? tamer.meterBandDb : BAND_MIN;
        float cut = tamer != null && t.enabled() ? tamer.meterCutDb : 0f;
        shownBand = Math.max(level, shownBand - 20f * dt);
        shownCut = Math.min(cut, shownCut + 20f * dt);

        int x = width / 2 - SLIDER_W - 5;
        int w = SLIDER_W * 2 + 10;
        int y = TOP + ROW * 5 + 6;
        int labelW = 34;
        int bx = x + labelW;
        int bw = w - labelW - 52;

        // BAND: how far the harsh band stands above the rest of the sound; the white tick is the threshold.
        g.text(font, "BAND", x, y + 1, 0xFFE0E0E0);
        g.fill(bx, y, bx + bw, y + 10, 0xFF202020);
        float tb = clamp01((shownBand - BAND_MIN) / (BAND_MAX - BAND_MIN));
        boolean over = shownBand > t.thresholdDb();
        g.fill(bx, y, bx + (int) (tb * bw), y + 10, over ? 0xFFFFA726 : 0xFF4CAF50);
        int tick = bx + (int) (clamp01((t.thresholdDb() - BAND_MIN) / (BAND_MAX - BAND_MIN)) * bw);
        g.fill(tick, y - 2, tick + 1, y + 12, 0xFFFFFFFF);
        g.text(font, shownBand <= BAND_MIN ? "--" : fmt("%+.1f dB", shownBand), bx + bw + 6, y + 1, 0xFFE0E0E0);

        // CUT: how much of the band is being taken out, growing from the right like a GR meter.
        int cy = y + 16;
        g.text(font, "CUT", x, cy + 1, 0xFFE0E0E0);
        g.fill(bx, cy, bx + bw, cy + 10, 0xFF202020);
        int len = (int) (clamp01(-shownCut / 24f) * bw);
        g.fill(bx + bw - len, cy, bx + bw, cy + 10, 0xFFE53935);
        g.text(font, fmt("%.1f dB", shownCut), bx + bw + 6, cy + 1, 0xFFE0E0E0);

        g.centeredText(font, "Turns the harsh band down only while it sticks out (orange = over the threshold).",
                width / 2, cy + 20, 0xFFA0A0A0);
        g.centeredText(font, "Normal sounds pass untouched.", width / 2, cy + 32, 0xFF808080);
        if (bus == null) {
            g.centeredText(font, "Master bus not active - the tamer only works with the audio chain on", width / 2, cy + 44, 0xFFFF8A80);
        }
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static String fmt(String pattern, float v) {
        return String.format(Locale.ROOT, pattern, v);
    }

    /** A slider over a real parameter range, optionally logarithmic. */
    private class Slider extends AbstractSliderButton {
        private final String name;
        private final float min, max;
        private final boolean log;
        private final Function<Float, String> format;
        private final BiFunction<Tamer, Float, Tamer> apply;

        Slider(int x, int y, String name, float min, float max, boolean log, float initial,
               Function<Float, String> format, BiFunction<Tamer, Float, Tamer> apply) {
            super(x, y, SLIDER_W, 20, Component.empty(), 0.0);
            this.name = name;
            this.min = min;
            this.max = max;
            this.log = log;
            this.format = format;
            this.apply = apply;
            this.value = toSlider(initial);
            updateMessage();
        }

        private double toSlider(float v) {
            v = Math.max(min, Math.min(max, v));
            return log ? Math.log(v / min) / Math.log(max / min) : (v - min) / (max - min);
        }

        private float fromSlider() {
            return (float) (log ? min * Math.pow(max / min, value) : min + (max - min) * value);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(name + ": " + format.apply(fromSlider())));
        }

        @Override
        protected void applyValue() {
            update(apply.apply(t, fromSlider()));
        }
    }
}
