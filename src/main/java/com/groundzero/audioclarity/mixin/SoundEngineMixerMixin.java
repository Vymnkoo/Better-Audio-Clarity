package com.groundzero.audioclarity.mixin;

import com.groundzero.audioclarity.ClarityConfig;
import com.groundzero.audioclarity.audio.MasterBus;
import com.groundzero.audioclarity.audio.MusicRoute;
import com.mojang.blaze3d.audio.Library;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

import static com.groundzero.audioclarity.AudioClarity.LOGGER;

/**
 * Per-sound adjustments: every sound's volume is scaled by {@link ClarityConfig#soundGain}.
 *
 * <p>Two paths set a sound's volume in 26.2, and both need it:
 * <ul>
 *   <li>starting a sound - play() works the volume out with calculateVolume(float, SoundSource)
 *       directly, so play() notes which sound it is and that call applies its gain;</li>
 *   <li>sounds that keep updating while they play (minecarts, music...) - the tick loop uses
 *       calculateVolume(SoundInstance).</li>
 * </ul>
 */
@Mixin(SoundEngine.class)
public abstract class SoundEngineMixerMixin {

    /** The sound play() is starting, until its volume has been worked out. Render thread only. */
    @Unique
    private SoundInstance audioclarity$starting;

    /** The sound play() is handling, for the whole call (music routing needs it late in play()). */
    @Unique
    private SoundInstance audioclarity$playing;

    @Inject(method = "play", at = @At("HEAD"))
    private void audioclarity$noteStarting(SoundInstance sound, CallbackInfoReturnable<?> cir) {
        audioclarity$starting = sound;
        audioclarity$playing = sound;
    }

    @Inject(method = "play", at = @At("RETURN"))
    private void audioclarity$doneStarting(SoundInstance sound, CallbackInfoReturnable<?> cir) {
        audioclarity$starting = null;
        audioclarity$playing = null;
    }

    /**
     * Sounds that skip the chain (music, and UI) must be streamed: a streamed sound decodes into
     * its own buffers, which can live on the real sound card, while fully loaded sounds share
     * buffers that belong to the loopback mix. Music is streamed anyway; UI sounds are switched
     * to streaming here (both calls in play() - the stream/load choice and the channel pool).
     */
    @Redirect(method = "play", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/sounds/Sound;shouldStream()Z"))
    private boolean audioclarity$streamIfSkippingChain(Sound resolved) {
        SoundInstance sound = audioclarity$playing;
        if (sound != null && MasterBus.active() != null && audioclarity$skips(sound)) {
            return true;
        }
        return resolved.shouldStream();
    }

    /** Music, UI, and single sounds listed in skip_compressor_sounds go around the chain. */
    @Unique
    private static boolean audioclarity$skips(SoundInstance sound) {
        return ClarityConfig.skipsChain(sound.getSource())
                || ClarityConfig.skipsChainAsSound(sound.getIdentifier().toString(), sound.getSource().getName());
    }

    /**
     * A single sound that skips the chain although its category goes through it (a server's
     * button-click "tick" in Blocks) misses what the chain gives the rest of its category -
     * make-up, output and the Master slider - so it gets them here and keeps its usual loudness.
     */
    @Unique
    private static float audioclarity$aroundGain(SoundInstance sound) {
        if (MasterBus.active() == null || ClarityConfig.skipsChain(sound.getSource())
                || !ClarityConfig.skipsChainAsSound(sound.getIdentifier().toString(), sound.getSource().getName())) {
            return 1f;
        }
        ClarityConfig.Compressor c = ClarityConfig.compressor();
        float db = (c.enabled() ? c.makeupDb() : 0f) + c.outputDb();
        return (float) Math.pow(10.0, db / 20.0) * MasterBus.masterVolume;
    }

    /**
     * Streamed sounds that skip the chain get their channel on the real sound card instead of the
     * loopback mix, so the master compressor never hears them (see {@link MusicRoute}).
     */
    @Redirect(method = "play", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/sounds/ChannelAccess;createHandle(Lcom/mojang/blaze3d/audio/Library$Pool;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<ChannelAccess.ChannelHandle> audioclarity$aroundCompressor(ChannelAccess access, Library.Pool pool) {
        SoundInstance sound = audioclarity$playing;
        boolean around = sound != null && pool == Library.Pool.STREAMING && MasterBus.active() != null && audioclarity$skips(sound);
        if (sound != null && ClarityConfig.logSounds()) {
            LOGGER.info("[sound] {} ({}) -> {}", sound.getIdentifier(), sound.getSource().getName(),
                    around ? "sound card, around the compressor" : "through the compressor (" + pool + ")");
        }
        if (around) {
            return MusicRoute.createOnSoundCard(((ChannelAccessAccessor) access).audioclarity$executor(), () -> access.createHandle(pool));
        }
        return access.createHandle(pool);
    }

    @Inject(method = "calculateVolume(FLnet/minecraft/sounds/SoundSource;)F", at = @At("RETURN"), cancellable = true)
    private void audioclarity$startGain(float volume, net.minecraft.sounds.SoundSource source, CallbackInfoReturnable<Float> cir) {
        SoundInstance sound = audioclarity$starting;
        if (sound != null) {
            audioclarity$starting = null; // once per play()
            float g = ClarityConfig.soundGain(sound.getIdentifier().toString(), sound.getSource().getName())
                    * audioclarity$aroundGain(sound);
            if (g != 1f) {
                cir.setReturnValue(cir.getReturnValueF() * g);
            }
        }
    }

    @Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F",
            at = @At("RETURN"), cancellable = true)
    private void audioclarity$tickGain(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        float g = ClarityConfig.soundGain(sound.getIdentifier().toString(), sound.getSource().getName())
                * audioclarity$aroundGain(sound);
        if (g != 1f) {
            cir.setReturnValue(cir.getReturnValueF() * g);
        }
    }
}
