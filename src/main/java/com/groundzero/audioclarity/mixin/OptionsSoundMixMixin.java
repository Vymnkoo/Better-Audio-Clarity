package com.groundzero.audioclarity.mixin;

import com.groundzero.audioclarity.ClarityConfig;
import com.groundzero.audioclarity.audio.MasterBus;
import net.minecraft.client.Options;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * getFinalSoundSourceVolume (master x category slider) is where every sound's loudness comes
 * from. Two changes:
 * <ul>
 *   <li>the category's level from the config's category_mix is multiplied in, so a slider at
 *       100% plays at the tuned mix and players can still turn it down from there;</li>
 *   <li>while the master compressor is running, the Master slider is left out here - it is
 *       applied after the compressor instead, as the final output gain, so turning the game down
 *       never changes how hard the compressor works.</li>
 * </ul>
 */
@Mixin(Options.class)
public abstract class OptionsSoundMixMixin {

    @Shadow
    public abstract float getSoundSourceVolume(SoundSource source);

    @Inject(method = "getFinalSoundSourceVolume", at = @At("RETURN"), cancellable = true)
    private void audioclarity$applySoundMix(SoundSource source, CallbackInfoReturnable<Float> cir) {
        float volume;
        // VOICE is the narrator, which speaks through the OS's text-to-speech, and MUSIC and UI play
        // straight to the sound card (MusicRoute) - none of them pass through the master bus, so
        // they keep the Master slider the vanilla way.
        if (MasterBus.active() != null && source != SoundSource.VOICE && !audioclarity$skipsChain(source)) {
            volume = source == SoundSource.MASTER ? 1f : getSoundSourceVolume(source);
        } else {
            volume = cir.getReturnValueF();
        }
        float result = volume * ClarityConfig.mix(source.getName());
        // Music and UI skip the chain, so the compressor's Output gain would never reach them and
        // raising Output would bury them under the game. Give them the same Output gain, so Output
        // is the final volume for everything and the balance stays as tuned.
        if (MasterBus.active() != null && audioclarity$skipsChain(source)) {
            result *= (float) Math.pow(10.0, ClarityConfig.compressor().outputDb() / 20.0);
        }
        cir.setReturnValue(result);
    }

    @Unique
    private static boolean audioclarity$skipsChain(SoundSource source) {
        return ClarityConfig.skipsChain(source);
    }
}
