package com.groundzero.audioclarity.audio;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Locale;

/**
 * A small read-only meter for the master compressor, drawn in the top-right corner of Music &
 * Sound: input level, gain reduction and output level. Look only - nothing to adjust.
 */
public final class CompressorMeter {

    private static final int BAR_W = 90;

    private static float shownIn = -60f, shownOut = -60f, shownGr;
    private static long lastFrame = System.nanoTime();

    private CompressorMeter() {}

    public static void draw(GuiGraphicsExtractor g, Font font, int screenWidth) {
        MasterBus bus = MasterBus.active();
        if (bus == null) {
            return;
        }
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        Compressor c = bus.compressor();
        // Peaks jump up at once and fall back at 20 dB/s, like a hardware meter.
        shownIn = Math.max(c.meterInDb, shownIn - 20f * dt);
        shownOut = Math.max(c.meterOutDb, shownOut - 20f * dt);
        shownGr = Math.min(c.meterGrDb, shownGr + 20f * dt);

        int x = screenWidth - BAR_W - 70;
        int y = 6;
        bar(g, font, x, y, "IN", shownIn, -60f, 0f, 0xFF4CAF50, false);
        bar(g, font, x, y + 8, "GR", shownGr, -24f, 0f, 0xFFE53935, true);
        bar(g, font, x, y + 16, "OUT", shownOut, -60f, 0f, 0xFF42A5F5, false);
    }

    private static void bar(GuiGraphicsExtractor g, Font font, int x, int y, String label, float db, float min, float max,
                            int color, boolean fromRight) {
        int bx = x + 22;
        g.text(font, label, x, y - 1, 0xFFB0B0B0);
        g.fill(bx, y, bx + BAR_W, y + 6, 0xFF202020);
        float t = Math.max(0f, Math.min(1f, (db - min) / (max - min)));
        int len = fromRight ? (int) ((1f - t) * BAR_W) : (int) (t * BAR_W);
        if (fromRight) {
            g.fill(bx + BAR_W - len, y, bx + BAR_W, y + 6, color);
        } else {
            g.fill(bx, y, bx + len, y + 6, color);
        }
        g.text(font, db <= -119f ? "-inf" : String.format(Locale.ROOT, "%.1f", db), bx + BAR_W + 5, y - 1, 0xFFB0B0B0);
    }
}
