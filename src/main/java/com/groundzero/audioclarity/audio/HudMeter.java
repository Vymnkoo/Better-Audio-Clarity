package com.groundzero.audioclarity.audio;

import com.groundzero.audioclarity.ClarityConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Locale;

/**
 * A small live meter in the top-left corner while playing (General tab: "Meter on HUD"): input,
 * compressor reduction, limiter reduction and output. Hidden with the HUD (F1) and while the
 * debug screen (F3) is open. Look only.
 */
public final class HudMeter {

    private static final int X = 4, Y = 4;
    private static final int LABEL_W = 22, BAR_W = 64, VALUE_W = 38, ROW = 9, BAR_H = 4;

    private static float shownIn = -60f, shownOut = -60f, shownGr, shownLim;
    private static long lastFrame = System.nanoTime();

    private HudMeter() {}

    /** @param hudHidden the HUD is hidden (F1) */
    public static void draw(GuiGraphicsExtractor g, boolean hudHidden) {
        Minecraft mc = Minecraft.getInstance();
        MasterBus bus = MasterBus.active();
        if (!ClarityConfig.hudMeter() || hudHidden || bus == null || mc.getDebugOverlay().showDebugScreen()) {
            return;
        }
        Compressor c = bus.compressor();
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        // Peaks jump at once and fall back at 20 dB/s, like the meters in the settings.
        shownIn = Math.max(c.meterInDb, shownIn - 20f * dt);
        shownOut = Math.max(c.meterOutDb, shownOut - 20f * dt);
        shownGr = Math.min(c.meterGrDb, shownGr + 20f * dt);
        shownLim = Math.min(c.meterLimDb, shownLim + 20f * dt);

        Font font = mc.font;
        int w = LABEL_W + BAR_W + VALUE_W + 4;
        g.fill(X - 2, Y - 2, X + w, Y + ROW * 4, 0x90000000);
        row(g, font, 0, "IN", shownIn, -60f, false, 0xFF4CAF50);
        row(g, font, 1, "GR", shownGr, -24f, true, 0xFFE53935);
        row(g, font, 2, "LIM", shownLim, -12f, true, 0xFFFFA726);
        row(g, font, 3, "OUT", shownOut, -60f, false, 0xFF42A5F5);
    }

    /** One bar; reductions (GR, LIM) grow from the right like a gain-reduction meter. */
    private static void row(GuiGraphicsExtractor g, Font font, int i, String label, float db, float min, boolean fromRight, int color) {
        int y = Y + i * ROW;
        int bx = X + LABEL_W;
        g.text(font, label, X, y, 0xFFE0E0E0);
        g.fill(bx, y + 2, bx + BAR_W, y + 2 + BAR_H, 0xFF303030);
        float t = fromRight ? Math.max(0f, Math.min(1f, db / min)) : Math.max(0f, Math.min(1f, (db - min) / -min));
        int len = (int) (t * BAR_W);
        if (fromRight) {
            g.fill(bx + BAR_W - len, y + 2, bx + BAR_W, y + 2 + BAR_H, color);
        } else {
            g.fill(bx, y + 2, bx + len, y + 2 + BAR_H, color);
        }
        String text = db <= -119f ? "-inf" : String.format(Locale.ROOT, "%.1f", db);
        g.text(font, text, bx + BAR_W + 4, y, 0xFFE0E0E0);
    }
}
