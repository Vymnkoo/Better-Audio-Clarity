package com.groundzero.audioclarity.gui;

import com.groundzero.audioclarity.ClarityConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Gains tab: the gain each sound actually gets on its way to the compressor. The category
 * mix (each category's level with its vanilla slider at 100%) is adjustable here and heard at
 * once; the chain gains and per-sound adjustments are shown for reference.
 */
public class GainsScreen extends TabbedScreen {

    private static final int SLIDER_W = 150;
    private static final int ROW = 22;
    private static final Map<String, String> NAMES = Map.ofEntries(
            Map.entry("record", "Jukebox"), Map.entry("weather", "Weather"), Map.entry("block", "Blocks"),
            Map.entry("hostile", "Hostile"), Map.entry("neutral", "Friendly"), Map.entry("player", "Players"),
            Map.entry("ambient", "Ambient"), Map.entry("voice", "Voice"), Map.entry("ui", "UI"),
            Map.entry("music", "Music"), Map.entry("master", "Server"));   // Master category = sounds servers play

    public GainsScreen(Screen parent) {
        super(parent);
    }

    @Override
    protected void init() {
        addTabs();
        int left = width / 2 - SLIDER_W - 5;
        int right = width / 2 + 5;
        int i = 0;
        for (String category : ClarityConfig.mixLevels().keySet()) {
            int x = i % 2 == 0 ? left : right;
            int y = TOP + (i / 2) * ROW;
            addTuning(new MixSlider(x, y, category));
            i++;
        }
        // Loud-sound taming sits in the next free slot.
        addTuning(new LevelingSlider(i % 2 == 0 ? left : right, TOP + (i / 2) * ROW));
        // The per-sound list pages when it doesn't fit above the buttons.
        int n = ClarityConfig.soundAdjustments().size();
        perColumn = Math.max(1, (height - 46 - listTop()) / LINE);
        pages = Math.max(1, (n + perColumn * 2 - 1) / (perColumn * 2));
        page = Math.min(page, pages - 1);
        if (pages > 1) {
            int by = headingY();
            addRenderableWidget(Button.builder(Component.literal("<"), b -> { page = (page + pages - 1) % pages; })
                    .bounds(width / 2 - SLIDER_W - 5, by, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> { page = (page + 1) % pages; })
                    .bounds(width / 2 + SLIDER_W - 15, by, 20, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Reset mix to defaults"), b -> {
            ClarityConfig.resetMix();
            refreshAll();
            rebuildWidgets();
        }).bounds(left, height - 28, SLIDER_W, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(right, height - 28, SLIDER_W, 20).build());
    }

    private static final int LINE = 11;
    private int page, pages = 1, perColumn = 1;

    private int rows() {
        return (ClarityConfig.mixLevels().size() + 2) / 2;   // + the leveling slider
    }

    private int headingY() {
        return TOP + rows() * ROW + 18;
    }

    private int listTop() {
        return headingY() + 24;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        int y = TOP + rows() * ROW + 4;
        ClarityConfig.Compressor c = ClarityConfig.compressor();
        g.centeredText(font, String.format(Locale.ROOT, "After the mix:  make-up %+.1f dB  >  output %+.1f dB (Master Volume)",
                c.makeupDb(), c.outputDb()), width / 2, y, 0xFFA0A0A0);

        List<Map.Entry<String, Float>> all = new ArrayList<>(ClarityConfig.soundAdjustments().entrySet());
        String heading = "Per-sound adjustments: " + all.size()
                + (pages > 1 ? "   (page " + (page + 1) + "/" + pages + ")" : "");
        g.centeredText(font, heading, width / 2, headingY() + 6, 0xFFE0E0E0);
        y = listTop();
        int from = page * perColumn * 2;
        List<Map.Entry<String, Float>> rules = all.subList(Math.min(from, all.size()), Math.min(from + perColumn * 2, all.size()));
        int colW = SLIDER_W + 5;
        for (int r = 0; r < rules.size(); r++) {
            Map.Entry<String, Float> e = rules.get(r);
            int x = r < perColumn ? width / 2 - colW - 5 : width / 2 + 5;
            int ry = y + (r % perColumn) * LINE;
            String name = e.getKey().replace("minecraft:", "");
            String value = e.getValue() <= ClarityConfig.MUTE_DB ? "muted" : String.format(Locale.ROOT, "%+.1f dB", e.getValue());
            int room = colW - font.width(value) - 6;
            if (font.width(name) > room) {
                name = font.plainSubstrByWidth(name, room - font.width("...")) + "...";
            }
            g.text(font, name, x, ry, 0xFFA0A0A0);
            g.text(font, value, x + colW - font.width(value), ry, e.getValue() < 0 ? 0xFFFF8A80 : 0xFF7CFC7C);
        }
        if (rules.isEmpty()) {
            g.centeredText(font, "none", width / 2, y, 0xFF808080);
        }
    }

    /** Makes sounds that are already playing take the new level too. */
    private static void refresh(String category) {
        for (SoundSource s : SoundSource.values()) {
            if (s.getName().equals(category)) {
                net.minecraft.client.Minecraft.getInstance().getSoundManager().refreshCategoryVolume(s);
            }
        }
    }

    private static void refreshAll() {
        ClarityConfig.mixLevels().keySet().forEach(GainsScreen::refresh);
    }

    private static String dbText(float level) {
        return level <= 0.0001f ? "-inf" : String.format(Locale.ROOT, "%.1f dB", 20.0 * Math.log10(level));
    }

    /** A category's level, 0-100% in 0.1% steps, shown with its gain in dB. */
    /** How much of a loud sound's excess is taken off (SoundLeveler), 0-100%. */
    private static class LevelingSlider extends AbstractSliderButton {
        LevelingSlider(int x, int y) {
            super(x, y, SLIDER_W, 20, Component.empty(), ClarityConfig.loudTaming());
            updateMessage();
        }

        private float amount() {
            return Math.round((float) value * 20f) / 20f;   // 5% steps
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(amount() == 0f ? "Tame loud sounds: off" : String.format(Locale.ROOT, "Tame loud sounds: %.0f%%", amount() * 100f)));
        }

        @Override
        protected void applyValue() {
            ClarityConfig.setLoudTaming(amount());
        }
    }

    private static class MixSlider extends AbstractSliderButton {
        private final String category;

        MixSlider(int x, int y, String category) {
            super(x, y, SLIDER_W, 20, Component.empty(), ClarityConfig.mix(category));
            this.category = category;
            updateMessage();
        }

        private float level() {
            return Math.round((float) value * 1000f) / 1000f;
        }

        @Override
        protected void updateMessage() {
            String name = NAMES.getOrDefault(category, category);
            setMessage(Component.literal(String.format(Locale.ROOT, "%s: %.1f%% (%s)", name, level() * 100f, dbText(level()))));
        }

        @Override
        protected void applyValue() {
            ClarityConfig.setMix(category, level());
            refresh(category);
        }
    }
}
