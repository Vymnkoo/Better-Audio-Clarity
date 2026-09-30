package com.groundzero.audioclarity.audio;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Locale;

/**
 * A read-only meter for the master compressor at the bottom of Music &amp; Sound, above the Done
 * button: input level, gain reduction and output level, full width like a mixing desk. Look only -
 * nothing to adjust.
 */
public final class CompressorMeter {

    /** Room the meter needs above the Done button (MusicSoundFooterMixin makes the footer this much taller). */
    public static final int HEIGHT = 42;
    private static final int BAR_W = 200;   // same as the Done button
    private static final int BAR_H = 10;
    private static final int ROW = 14;

    private static float shownIn = -60f, shownOut = -60f, shownGr;
    private static long lastFrame = System.nanoTime();

    private CompressorMeter() {}

    /** @param top y of the meter's first bar */
    public static void draw(GuiGraphicsExtractor g, Font font, int screenWidth, int top) {
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

        // The bars are centred and as wide as the Done button below them; labels hang off the
        // left, values off the right.
        int bx = (screenWidth - BAR_W) / 2;
        bar(g, font, bx, top, "IN", shownIn, -60f, 0f, 0xFF4CAF50, false);
        bar(g, font, bx, top + ROW, "GR", shownGr, -24f, 0f, 0xFFE53935, true);
        bar(g, font, bx, top + ROW * 2, "OUT", shownOut, -60f, 0f, 0xFF42A5F5, false);
    }

    /** A horizontal level bar; gain reduction grows from the right like a GR meter on a desk. */
    private static void bar(GuiGraphicsExtractor g, Font font, int bx, int y, String label, float db, float min, float max,
                            int color, boolean fromRight) {
        int bw = BAR_W;
        g.text(font, label, bx - 6 - font.width(label), y + 1, 0xFFE0E0E0);
        g.fill(bx, y, bx + bw, y + BAR_H, 0xFF202020);
        float t = Math.max(0f, Math.min(1f, (db - min) / (max - min)));
        int len = fromRight ? (int) ((1f - t) * bw) : (int) (t * bw);
        if (fromRight) {
            g.fill(bx + bw - len, y, bx + bw, y + BAR_H, color);
        } else {
            g.fill(bx, y, bx + len, y + BAR_H, color);
        }
        String text = db <= -119f ? "-inf" : String.format(Locale.ROOT, "%.1f dB", db);
        g.text(font, text, bx + bw + 6, y + 1, 0xFFE0E0E0);
    }
}
