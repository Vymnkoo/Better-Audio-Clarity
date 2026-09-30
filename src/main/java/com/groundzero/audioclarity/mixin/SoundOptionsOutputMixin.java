package com.groundzero.audioclarity.mixin;

import com.groundzero.audioclarity.ClarityConfig;
import com.groundzero.audioclarity.audio.MasterBus;
import com.groundzero.audioclarity.gui.OutputVolumeSlider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Music &amp; Sound: the big Master Volume slider at the top becomes the mod's Output slider, so
 * there is one overall volume, in dB, like in the mod's settings. Master's old level was folded
 * into Output once (MinecraftTickMixin). Without the audio chain running, Output would do
 * nothing, so Master stays.
 */
@Mixin(SoundOptionsScreen.class)
public abstract class SoundOptionsOutputMixin {

    @Redirect(method = "addOptions", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/OptionsList;addBig(Lnet/minecraft/client/OptionInstance;)V"))
    private void audioclarity$outputInsteadOfMaster(OptionsList list, OptionInstance<?> option) {
        boolean master = option == Minecraft.getInstance().options.getSoundSourceOptionInstance(SoundSource.MASTER);
        if (master && MasterBus.active() != null && ClarityConfig.masterMovedToOutput()) {
            OutputVolumeSlider.addTo(list);
        } else {
            list.addBig(option);
        }
    }
}
