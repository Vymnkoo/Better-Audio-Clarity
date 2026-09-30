package com.groundzero.audioclarity.gui;

import com.groundzero.audioclarity.ClarityConfig;
import com.groundzero.audioclarity.ClarityConfig.Compressor;
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
 * The Compressor tab: every compressor setting as a slider, heard live, with input /
 * gain-reduction / output meters and a bypass for A/B listening.
 */
public class CompressorScreen extends TabbedScreen {

    private static final int SLIDER_W = 150;
    private static final int ROW = 24;

    private Compressor p = ClarityConfig.compressor();
    private float shownIn = -60f, shownOut = -60f, shownGr;
    private long lastFrame = System.nanoTime();

    public CompressorScreen(Screen parent) {
        super(parent);
    }

    @Override
    protected void init() {
        addTabs();
        p = ClarityConfig.compressor();
        int left = width / 2 - SLIDER_W - 5;
        int right = width / 2 + 5;
        int y = TOP;
        addTuning(new ParamSlider(left, y, "Threshold", -60f, 0f, false, p.thresholdDb(), v -> fmt("%.1f dB", v),
                (q, v) -> with(q, v, q.ratio(), q.attackMs(), q.releaseMs(), q.kneeDb(), q.makeupDb(), q.outputDb())));
        addTuning(new ParamSlider(right, y, "Ratio", 1f, 20f, true, p.ratio(), v -> fmt("%.2f:1", v),
                (q, v) -> with(q, q.thresholdDb(), v, q.attackMs(), q.releaseMs(), q.kneeDb(), q.makeupDb(), q.outputDb())));
        y += ROW;
        addTuning(new ParamSlider(left, y, "Attack", 0.1f, 200f, true, p.attackMs(), v -> fmt(v < 10 ? "%.2f ms" : "%.0f ms", v),
                (q, v) -> with(q, q.thresholdDb(), q.ratio(), v, q.releaseMs(), q.kneeDb(), q.makeupDb(), q.outputDb())));
        addTuning(new ParamSlider(right, y, "Release", 10f, 2000f, true, p.releaseMs(), v -> fmt("%.0f ms", v),
                (q, v) -> with(q, q.thresholdDb(), q.ratio(), q.attackMs(), v, q.kneeDb(), q.makeupDb(), q.outputDb())));
        y += ROW;
        addTuning(new ParamSlider(left, y, "Knee", 0f, 24f, false, p.kneeDb(), v -> fmt("%.1f dB", v),
                (q, v) -> with(q, q.thresholdDb(), q.ratio(), q.attackMs(), q.releaseMs(), v, q.makeupDb(), q.outputDb())));
        addTuning(new ParamSlider(right, y, "Make-up", 0f, 24f, false, p.makeupDb(), v -> fmt("+%.1f dB", v),
                (q, v) -> with(q, q.thresholdDb(), q.ratio(), q.attackMs(), q.releaseMs(), q.kneeDb(), v, q.outputDb())));
        y += ROW;
        addTuning(new ParamSlider(left, y, "Output", -24f, 12f, false, p.outputDb(), v -> fmt("%+.1f dB", v),
                (q, v) -> with(q, q.thresholdDb(), q.ratio(), q.attackMs(), q.releaseMs(), q.kneeDb(), q.makeupDb(), v)));
        addTuning(Button.builder(limiterLabel(), b -> {
            update(new Compressor(p.enabled(), p.thresholdDb(), p.ratio(), p.attackMs(), p.releaseMs(), p.kneeDb(), p.makeupDb(), p.outputDb(), !p.limiter(), p.latency(), p.lookaheadMs()));
            b.setMessage(limiterLabel());
        }).bounds(right, y, SLIDER_W, 20).build());
        y += ROW;
        addTuning(Button.builder(enabledLabel(), b -> {
            update(new Compressor(!p.enabled(), p.thresholdDb(), p.ratio(), p.attackMs(), p.releaseMs(), p.kneeDb(), p.makeupDb(), p.outputDb(), p.limiter(), p.latency(), p.lookaheadMs()));
            b.setMessage(enabledLabel());
        }).bounds(left, y, SLIDER_W, 20).build());
        addTuning(Button.builder(latencyLabel(), b -> {
            update(new Compressor(p.enabled(), p.thresholdDb(), p.ratio(), p.attackMs(), p.releaseMs(), p.kneeDb(), p.makeupDb(), p.outputDb(), p.limiter(), p.latency().next(), p.lookaheadMs()));
            b.setMessage(latencyLabel());
        }).bounds(right, y, SLIDER_W, 20).build());
        y += ROW;
        addTuning(new ParamSlider(left, y, "Lookahead", 0f, 20f, false, p.lookaheadMs(),
                v -> v < 0.05f ? "off" : fmt("%.1f ms", v),
                (q, v) -> new Compressor(q.enabled(), q.thresholdDb(), q.ratio(), q.attackMs(), q.releaseMs(), q.kneeDb(),
                        q.makeupDb(), q.outputDb(), q.limiter(), q.latency(), v)));

        addRenderableWidget(Button.builder(Component.literal("Reset to defaults"), b -> {
            Compressor d = ClarityConfig.DEFAULT_COMPRESSOR;
            update(new Compressor(d.enabled(), d.thresholdDb(), d.ratio(), d.attackMs(), d.releaseMs(), d.kneeDb(),
                    d.makeupDb(), d.outputDb(), d.limiter(), p.latency(), d.lookaheadMs()));
            rebuildWidgets();
        }).bounds(left, height - 28, SLIDER_W, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(right, height - 28, SLIDER_W, 20).build());
    }

    private static Compressor with(Compressor q, float thr, float ratio, float atk, float rel, float knee, float makeup, float out) {
        return new Compressor(q.enabled(), thr, ratio, atk, rel, knee, makeup, out, q.limiter(), q.latency(), q.lookaheadMs());
    }

    private void update(Compressor next) {
        p = next;
        ClarityConfig.setCompressor(next);
    }

    private Component enabledLabel() {
        return Component.literal("Compressor: " + (p.enabled() ? "ON" : "BYPASS"));
    }

    private Component limiterLabel() {
        return Component.literal("Safety limiter: " + (p.limiter() ? "ON" : "OFF"));
    }

    private Component latencyLabel() {
        String ms = switch (p.latency()) {
            case LOW -> "~10 ms";
            case NORMAL -> "~20 ms";
            case SAFE -> "~40 ms";
        };
        String name = p.latency().name();
        return Component.literal("Latency: " + name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT) + " (" + ms + ")");
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);

        MasterBus bus = MasterBus.active();
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        float in = bus != null ? bus.compressor().meterInDb : -120f;
        float out = bus != null ? bus.compressor().meterOutDb : -120f;
        float gr = bus != null ? bus.compressor().meterGrDb : 0f;
        // Peaks jump up at once and fall back at 20 dB/s, like a hardware meter.
        shownIn = Math.max(in, shownIn - 20f * dt);
        shownOut = Math.max(out, shownOut - 20f * dt);
        shownGr = Math.min(gr, shownGr + 20f * dt);

        int x = width / 2 - SLIDER_W - 5;
        int w = SLIDER_W * 2 + 10;
        int y = TOP + ROW * 6 + 6;
        meter(g, x, y, w, "IN", shownIn, -60f, 0f, 0xFF4CAF50, false);
        meter(g, x, y + 16, w, "GR", shownGr, -24f, 0f, 0xFFE53935, true);
        meter(g, x, y + 32, w, "OUT", shownOut, -60f, 0f, 0xFF42A5F5, false);

        if (bus == null) {
            g.centeredText(font, "Master bus not active - audio is running the normal way (see the log)", width / 2, y + 50, 0xFFFF8A80);
            return;
        }
        g.centeredText(font, String.format(Locale.ROOT, "%d Hz, sound card period %.1f ms", bus.sampleRate(), bus.periodMs()),
                width / 2, y + 50, 0xFF808080);
    }

    /** A horizontal level bar; gain reduction grows from the right like a GR meter on a desk. */
    private void meter(GuiGraphicsExtractor g, int x, int y, int w, String label, float db, float min, float max, int color, boolean fromRight) {
        int labelW = 24;
        int bx = x + labelW;
        int bw = w - labelW - 52;
        g.text(font, label, x, y + 1, 0xFFE0E0E0);
        g.fill(bx, y, bx + bw, y + 10, 0xFF202020);
        float t = Math.max(0f, Math.min(1f, (db - min) / (max - min)));
        int len = fromRight ? (int) ((1f - t) * bw) : (int) (t * bw);
        if (fromRight) {
            g.fill(bx + bw - len, y, bx + bw, y + 10, color);
        } else {
            g.fill(bx, y, bx + len, y + 10, color);
        }
        String text = db <= -119f ? "-inf" : String.format(Locale.ROOT, "%.1f dB", db);
        g.text(font, text, bx + bw + 6, y + 1, 0xFFE0E0E0);
    }

    private static String fmt(String pattern, float v) {
        return String.format(Locale.ROOT, pattern, v);
    }

    /** A slider over a real parameter range, optionally logarithmic (ratio, attack, release). */
    private class ParamSlider extends AbstractSliderButton {
        private final String name;
        private final float min, max;
        private final boolean log;
        private final Function<Float, String> format;
        private final BiFunction<Compressor, Float, Compressor> apply;

        ParamSlider(int x, int y, String name, float min, float max, boolean log, float initial,
                    Function<Float, String> format, BiFunction<Compressor, Float, Compressor> apply) {
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
            update(apply.apply(p, fromSlider()));
        }
    }
}
