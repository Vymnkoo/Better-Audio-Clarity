package com.groundzero.audioclarity.gui;

import com.groundzero.audioclarity.ClarityConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The General tab, opened from Mod Menu: the overall Output level (to match speakers or
 * headphones) and the main on/off switches. The Compressor and Gains tabs hold the tuning.
 */
public class SettingsScreen extends TabbedScreen {

    private static final int W = 310;
    private static final int HALF = 150;
    private static final int ROW = 24;
    private static final float MIN_DB = -12f, MAX_DB = 12f;

    public SettingsScreen(Screen parent) {
        super(parent);
    }

    @Override
    protected void init() {
        addTabs();
        int left = width / 2 - W / 2;
        int right = left + HALF + 10;
        int y = TOP + 4;
        addRenderableWidget(new OutputSlider(left, y));
        y += ROW + 6;
        toggle(left, y, "Compressor", () -> ClarityConfig.compressor().enabled(), ClarityConfig::setCompressorEnabled);
        toggle(right, y, "EQ", () -> ClarityConfig.eq().enabled(), ClarityConfig::setEqEnabled);
        y += ROW;
        toggle(left, y, "Music skips compressor", ClarityConfig::musicSkipsCompressorSetting, ClarityConfig::setMusicSkipsCompressor);
        toggle(right, y, "UI skips compressor", ClarityConfig::uiSkipsCompressorSetting, ClarityConfig::setUiSkipsCompressor);
        y += ROW;
        toggle(left, y, "Meter in Music & Sound", ClarityConfig::showMeter, ClarityConfig::setShowMeter);

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(width / 2 - 100, height - 28, 200, 20).build());
    }

    private void toggle(int x, int y, String name, Supplier<Boolean> get, Consumer<Boolean> set) {
        addRenderableWidget(Button.builder(label(name, get.get()), b -> {
            set.accept(!get.get());
            b.setMessage(label(name, get.get()));
        }).bounds(x, y, HALF, 20).build());
    }

    private static Component label(String name, boolean on) {
        return Component.literal(name + ": " + (on ? "ON" : "OFF"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        int y = TOP + 4 + ROW * 4 + 16;
        g.centeredText(font, "Output = overall volume of the finished mix (raise it for quiet headphones).", width / 2, y, 0xFFA0A0A0);
        g.centeredText(font, "Everything is saved in config/better-audio-clarity.json", width / 2, y + 12, 0xFF808080);
    }

    /** Output gain in dB, 0.1 dB steps. */
    private static class OutputSlider extends AbstractSliderButton {
        OutputSlider(int x, int y) {
            super(x, y, W, 20, Component.empty(), toSlider(ClarityConfig.compressor().outputDb()));
            updateMessage();
        }

        private static double toSlider(float db) {
            return (Math.max(MIN_DB, Math.min(MAX_DB, db)) - MIN_DB) / (MAX_DB - MIN_DB);
        }

        private float db() {
            return Math.round((MIN_DB + (MAX_DB - MIN_DB) * (float) value) * 10f) / 10f;
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(String.format(Locale.ROOT, "Output: %+.1f dB", db())));
        }

        @Override
        protected void applyValue() {
            ClarityConfig.setOutputDb(db());
        }
    }
}
