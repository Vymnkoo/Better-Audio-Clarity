package com.groundzero.audioclarity.mixin;

import com.groundzero.audioclarity.ClarityConfig;
import com.groundzero.audioclarity.audio.MasterBus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.groundzero.audioclarity.AudioClarity.LOGGER;

/**
 * Every client tick: hands the Master slider to the compressor (it is applied as the final gain)
 * and lets the config notice a saved file.
 *
 * <p>On the very first tick with the mod, it also sets every category slider except Master and
 * Music to 100% - once. The built-in mix sits under the sliders, so a slider someone had lowered
 * in vanilla would otherwise lower that category a second time. Master and Music are personal
 * listening choices and are left alone; anything changed afterwards is never touched again.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftTickMixin {

    @Shadow
    @Final
    public Options options;

    @Inject(method = "tick", at = @At("HEAD"))
    private void audioclarity$tick(CallbackInfo ci) {
        if (options != null) {
            MasterBus.masterVolume = options.getSoundSourceVolume(SoundSource.MASTER);
            if (!ClarityConfig.sliderResetDone()) {
                audioclarity$resetCategorySliders();
            }
            if (!ClarityConfig.masterMovedToOutput() && MasterBus.active() != null) {
                audioclarity$moveMasterToOutput();
            }
        }
        ClarityConfig.poll();
        com.groundzero.audioclarity.audio.MusicDuck.tick((Minecraft) (Object) this);
    }

    /**
     * Music &amp; Sound shows Output in place of Master (SoundOptionsOutputMixin), so Master must not
     * stay below 100% where nobody can reach it. Once, fold its level into Output instead - the
     * game sounds exactly as loud as before.
     */
    @Unique
    private void audioclarity$moveMasterToOutput() {
        float master = options.getSoundSourceVolume(SoundSource.MASTER);
        if (master < 1f) {
            float out = ClarityConfig.compressor().outputDb();
            ClarityConfig.setOutputDb(master <= 0f ? ClarityConfig.OUTPUT_OFF_DB : out + (float) (20.0 * Math.log10(master)));
            options.getSoundSourceOptionInstance(SoundSource.MASTER).set(1.0);
            options.save();
            LOGGER.info("Master was {}%: moved into Output ({} dB -> {} dB), Master is now 100%",
                    Math.round(master * 100f), out, ClarityConfig.compressor().outputDb());
        }
        ClarityConfig.markMasterMovedToOutput();
    }

    @Unique
    private void audioclarity$resetCategorySliders() {
        int changed = 0;
        for (SoundSource source : SoundSource.values()) {
            if (source == SoundSource.MASTER || source == SoundSource.MUSIC) {
                continue;
            }
            if (options.getSoundSourceVolume(source) != 1f) {
                options.getSoundSourceOptionInstance(source).set(1.0);
                changed++;
            }
        }
        if (changed > 0) {
            options.save();
        }
        ClarityConfig.markSliderResetDone();
        LOGGER.info("First start: set {} sound slider(s) to 100% - the built-in mix is under the sliders", changed);
    }
}
