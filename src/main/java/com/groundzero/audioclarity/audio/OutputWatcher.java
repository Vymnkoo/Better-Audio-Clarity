package com.groundzero.audioclarity.audio;

import com.groundzero.audioclarity.ClarityConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;

/**
 * Music, UI and the other sounds that skip the chain get the Output gain as part of their own
 * volume (OptionsSoundMixMixin), which a playing sound only picks up when the game recalculates
 * it. So whenever Output changes - from any slider or the config file - recalculate everything
 * that is playing; the chain itself follows Output on its own.
 */
public final class OutputWatcher {

    private static float lastOutputDb = Float.NaN;

    private OutputWatcher() {}

    public static void tick(Minecraft mc) {
        float out = ClarityConfig.compressor().outputDb();
        if (out == lastOutputDb) {
            return;
        }
        boolean first = Float.isNaN(lastOutputDb);
        lastOutputDb = out;
        if (!first) {
            for (SoundSource source : SoundSource.values()) {
                if (source != SoundSource.MASTER) {
                    mc.getSoundManager().refreshCategoryVolume(source);
                }
            }
        }
    }
}
