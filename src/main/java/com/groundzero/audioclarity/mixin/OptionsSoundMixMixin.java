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
        // VOICE is the narrator, which speaks through the OS's text-to-speech, and MUSIC plays
        // straight to the sound card (MusicRoute) - neither passes through the master bus, so
        // both keep the Master slider the vanilla way.
        if (MasterBus.active() != null && source != SoundSource.VOICE && !audioclarity$musicBypasses(source)) {
            volume = source == SoundSource.MASTER ? 1f : getSoundSourceVolume(source);
        } else {
            volume = cir.getReturnValueF();
        }
        float result = volume * ClarityConfig.mix(source.getName());
        // Music skips the chain, so the compressor's Output gain would never reach it and raising
        // Output would bury the music under the game. Give music the same Output gain, so Output
        // is the final volume for everything and the game/music balance stays as tuned.
        if (MasterBus.active() != null && audioclarity$musicBypasses(source)) {
            result *= (float) Math.pow(10.0, ClarityConfig.compressor().outputDb() / 20.0);
        }
        cir.setReturnValue(result);
    }

    @Unique
    private static boolean audioclarity$musicBypasses(SoundSource source) {
        return source == SoundSource.MUSIC && ClarityConfig.musicSkipsCompressor();
    }
}
