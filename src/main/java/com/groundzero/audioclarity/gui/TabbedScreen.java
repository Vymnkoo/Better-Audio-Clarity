package com.groundzero.audioclarity.gui;

import com.groundzero.audioclarity.ClarityConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Function;

/**
 * The settings screens share one tab bar at the top: General, Compressor and Gains. Switching
 * tabs saves and opens the other tab over the same parent, so Done always goes back to where
 * the settings were opened from. Every change is heard at once.
 */
abstract class TabbedScreen extends Screen {

    static final int TOP = 36;   // where a tab's content starts, under the tab bar
    private static final int TAB_W = 100;

    private enum Tab {
        GENERAL("General", SettingsScreen.class, SettingsScreen::new),
        COMPRESSOR("Compressor", CompressorScreen.class, CompressorScreen::new),
        GAINS("Gains", GainsScreen.class, GainsScreen::new);

        final String label;
        final Class<? extends TabbedScreen> type;
        final Function<Screen, TabbedScreen> open;

        Tab(String label, Class<? extends TabbedScreen> type, Function<Screen, TabbedScreen> open) {
            this.label = label;
            this.type = type;
            this.open = open;
        }
    }

    protected final Screen parent;
    /** The latency when the settings were opened: the sound card only takes a new one on an audio restart. */
    private ClarityConfig.Latency latencyAtOpen = ClarityConfig.compressor().latency();

    protected TabbedScreen(Screen parent) {
        super(Component.literal("Better Audio Clarity"));
        this.parent = parent;
    }

    /** Adds the tab bar; the current tab's button is greyed out. */
    protected void addTabs() {
        Tab[] tabs = Tab.values();
        int x = width / 2 - tabs.length * TAB_W / 2;
        for (Tab tab : tabs) {
            Button b = Button.builder(Component.literal(tab.label), btn -> switchTo(tab))
                    .bounds(x, 8, TAB_W - 2, 20).build();
            b.active = tab.type != getClass();
            addRenderableWidget(b);
            x += TAB_W;
        }
    }

    private void switchTo(Tab tab) {
        ClarityConfig.saveNow();
        TabbedScreen next = tab.open.apply(parent);
        next.latencyAtOpen = latencyAtOpen;
        minecraft.gui.setScreen(next);
    }

    @Override
    public void onClose() {
        ClarityConfig.saveNow();
        if (ClarityConfig.compressor().latency() != latencyAtOpen) {
            // The sound card's period is fixed when its context is made - restart the audio.
            minecraft.getSoundManager().reload();
        }
        minecraft.gui.setScreen(parent);
    }

    /** Keep the game (and its sound) running while tuning, even in singleplayer. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
