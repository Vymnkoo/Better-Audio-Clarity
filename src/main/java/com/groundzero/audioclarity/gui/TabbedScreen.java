package com.groundzero.audioclarity.gui;

import com.groundzero.audioclarity.ClarityConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The settings screens share one tab bar at the top: General, Dynamics (the compressor) and Gains. Switching
 * tabs saves and opens the other tab over the same parent, so Done always goes back to where
 * the settings were opened from. Every change is heard at once.
 *
 * <p>The tuning controls (Dynamics and Gains) start greyed out. Clicking one asks first, since
 * they change how the mod sounds; after a yes they stay unlocked until the game restarts.
 */
abstract class TabbedScreen extends Screen {

    static final int TOP = 36;   // where a tab's content starts, under the tab bar
    private static final int TAB_W = 110;

    /** Set once the player has confirmed they want to change the tuning (this game session only). */
    private static boolean tuningUnlocked;

    private enum Tab {
        GENERAL("General", SettingsScreen.class, SettingsScreen::new),
        COMPRESSOR("Dynamics (Comp)", CompressorScreen.class, CompressorScreen::new),
        GAINS("Gains", GainsScreen.class, GainsScreen::new),
        HARSHNESS("Harshness", HarshnessScreen.class, HarshnessScreen::new);

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
    private final List<AbstractWidget> tuning = new ArrayList<>();

    protected TabbedScreen(Screen parent) {
        super(Component.literal("Better Audio Clarity"));
        this.parent = parent;
    }

    /** Adds the tab bar; the current tab's button is greyed out. */
    protected void addTabs() {
        tuning.clear();
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

    /** Adds a tuning control: greyed out until the player confirms they want to change the tuning. */
    protected <T extends AbstractWidget> T addTuning(T widget) {
        widget.active = tuningUnlocked;
        tuning.add(widget);
        return addRenderableWidget(widget);
    }

    protected static boolean tuningLocked() {
        return !tuningUnlocked;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!tuningUnlocked) {
            for (AbstractWidget w : tuning) {
                if (event.x() >= w.getX() && event.x() < w.getX() + w.getWidth()
                        && event.y() >= w.getY() && event.y() < w.getY() + w.getHeight()) {
                    askToUnlock();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        if (!tuningUnlocked && !tuning.isEmpty()) {
            g.centeredText(font, "Locked: click a setting to change the tuning", width / 2, height - 40, 0xFFFFD580);
        }
    }

    private void askToUnlock() {
        Screens.open(new ConfirmScreen(yes -> {
            if (yes) {
                tuningUnlocked = true;
            }
            Screens.open(this);   // re-opens this tab, now with the controls unlocked
        }, Component.literal("Change the tuning?"),
                Component.literal("Tinkering with these settings changes how Better Audio Clarity sounds. "
                        + "The defaults were tuned by ear, and \"Reset to defaults\" always brings them back.\n\n"
                        + "Are you sure you want to change them?"),
                Component.literal("Yes, let me tune"), Component.literal("Cancel")));
    }

    private void switchTo(Tab tab) {
        ClarityConfig.saveNow();
        TabbedScreen next = tab.open.apply(parent);
        next.latencyAtOpen = latencyAtOpen;
        Screens.open(next);
    }

    @Override
    public void onClose() {
        ClarityConfig.saveNow();
        if (ClarityConfig.compressor().latency() != latencyAtOpen) {
            // The sound card's period is fixed when its context is made - restart the audio.
            minecraft.getSoundManager().reload();
        }
        Screens.open(parent);
    }

    /** Keep the game (and its sound) running while tuning, even in singleplayer. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
