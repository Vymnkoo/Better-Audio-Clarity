package com.groundzero.audioclarity.gui;

import com.groundzero.audioclarity.ClarityConfig;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.network.chat.Component;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Locale;

/**
 * The Output slider that takes Master's place at the top of Music &amp; Sound: the overall volume,
 * shown relative to the tuned level (0 dB = as tuned), from OFF up to +6.5 dB. Heard at once and
 * saved when the screen closes (and by Done in the mod's own settings).
 */
public class OutputVolumeSlider extends AbstractSliderButton {

    private static final float LOWEST = -40f;                                  // above OFF
    private static final float TUNED = ClarityConfig.DEFAULT_COMPRESSOR.outputDb();
    private static final float HIGHEST = 12f - TUNED;                          // Output tops out at +12 dB

    public OutputVolumeSlider(int x, int y, int width) {
        super(x, y, width, 20, Component.empty(), toSlider(ClarityConfig.compressor().outputDb() - TUNED));
        updateMessage();
    }

    private static double toSlider(float relativeDb) {
        return Math.max(0.0, Math.min(1.0, (relativeDb - LOWEST) / (HIGHEST - LOWEST)));
    }

    /** Relative dB in 0.5 dB steps; the very bottom is OFF. */
    private float relativeDb() {
        return Math.round((LOWEST + (HIGHEST - LOWEST) * (float) value) * 2f) / 2f;
    }

    private boolean off() {
        return relativeDb() <= LOWEST;
    }

    @Override
    protected void updateMessage() {
        float db = relativeDb();
        setMessage(Component.literal(off() ? "Master Volume: OFF"
                : db == 0f ? "Master Volume: 0.0 dB" : String.format(Locale.ROOT, "Master Volume: %+.1f dB", db)));
    }

    @Override
    protected void applyValue() {
        ClarityConfig.setOutputDb(off() ? ClarityConfig.OUTPUT_OFF_DB : TUNED + relativeDb());
    }

    // ---- adding it to an options list: addBig(AbstractWidget) is 26.2+, 26.1 only has addSmall(List)

    private static final MethodHandle ADD_BIG = findAddBig();

    private static MethodHandle findAddBig() {
        try {
            return MethodHandles.publicLookup().findVirtual(OptionsList.class, "addBig",
                    MethodType.methodType(void.class, AbstractWidget.class));
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    public static void addTo(OptionsList list) {
        OutputVolumeSlider slider = new OutputVolumeSlider(0, 0, 310);
        if (ADD_BIG != null) {
            try {
                ADD_BIG.invoke(list, (AbstractWidget) slider);
                return;
            } catch (Throwable t) {
                throw new IllegalStateException("Could not add the Output slider", t);
            }
        }
        list.addSmall(List.<AbstractWidget>of(slider));
    }
}
