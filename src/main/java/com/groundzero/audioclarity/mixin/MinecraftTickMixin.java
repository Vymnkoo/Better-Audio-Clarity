package com.groundzero.audioclarity.mixin;

import com.groundzero.audioclarity.ClarityConfig;
import com.groundzero.audioclarity.audio.MasterBus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands the Master slider to the compressor every client tick (it is applied as the final gain),
 * and lets the config notice a saved file.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftTickMixin {

    @Shadow
    @Final
    public Options options;

    @Inject(method = "tick", at = @At("HEAD"))
    private void audioclarity$masterVolume(CallbackInfo ci) {
        if (options != null) {
            MasterBus.masterVolume = options.getSoundSourceVolume(SoundSource.MASTER);
        }
        ClarityConfig.poll();
    }
}
