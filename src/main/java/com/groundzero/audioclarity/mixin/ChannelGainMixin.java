package com.groundzero.audioclarity.mixin;

import com.groundzero.audioclarity.audio.MusicRoute;
import com.mojang.blaze3d.audio.Channel;
import org.lwjgl.openal.AL10;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Two jobs on Minecraft's OpenAL channels:
 * <ul>
 *   <li>Boosts: OpenAL caps a source at 100% (AL_MAX_GAIN = 1) unless told otherwise, which
 *       would swallow a boost from the sound mixer - the cap is raised for boosted sources only;
 *       the compressor's limiter keeps the result from clipping.</li>
 *   <li>Music around the compressor ({@link MusicRoute}): a music channel lives on the real
 *       sound card's context, so each of its calls runs with that context current.</li>
 * </ul>
 */
@Mixin(Channel.class)
public abstract class ChannelGainMixin implements MusicRoute.MusicChannel {

    @Shadow
    @Final
    private int source;

    @Unique
    private boolean audioclarity$music;

    @Override
    public boolean audioclarity$isMusic() {
        return audioclarity$music;
    }

    @Override
    public void audioclarity$markMusic() {
        audioclarity$music = true;
    }

    // ---- creating a music channel on the sound card

    @Inject(method = "create", at = @At("HEAD"))
    private static void audioclarity$createOnSoundCard(CallbackInfoReturnable<Channel> cir) {
        if (MusicRoute.creatingMusic()) {
            MusicRoute.enter();
        }
    }

    @Inject(method = "create", at = @At("RETURN"))
    private static void audioclarity$createdOnSoundCard(CallbackInfoReturnable<Channel> cir) {
        if (MusicRoute.creatingMusic()) {
            if (cir.getReturnValue() instanceof MusicRoute.MusicChannel m) {
                m.audioclarity$markMusic();
            }
            MusicRoute.exit();
        }
    }

    // ---- every other call on a music channel runs on the sound card's context

    @Inject(method = {"destroy", "play", "pause", "unpause", "stop", "setSelfPosition", "setPitch", "setLooping",
            "disableAttenuation", "linearAttenuation", "setRelative", "attachStaticBuffer", "attachBufferStream",
            "updateStream"}, at = @At("HEAD"))
    private void audioclarity$enter(CallbackInfo ci) {
        if (audioclarity$music) {
            MusicRoute.enter();
        }
    }

    @Inject(method = {"destroy", "play", "pause", "unpause", "stop", "setSelfPosition", "setPitch", "setLooping",
            "disableAttenuation", "linearAttenuation", "setRelative", "attachStaticBuffer", "attachBufferStream",
            "updateStream"}, at = @At("RETURN"))
    private void audioclarity$exit(CallbackInfo ci) {
        if (audioclarity$music) {
            MusicRoute.exit();
        }
    }

    @Inject(method = {"playing", "stopped"}, at = @At("HEAD"))
    private void audioclarity$enterQuery(CallbackInfoReturnable<Boolean> cir) {
        if (audioclarity$music) {
            MusicRoute.enter();
        }
    }

    @Inject(method = {"playing", "stopped"}, at = @At("RETURN"))
    private void audioclarity$exitQuery(CallbackInfoReturnable<Boolean> cir) {
        if (audioclarity$music) {
            MusicRoute.exit();
        }
    }

    // ---- setVolume: context + boost cap (one handler, so the order is guaranteed)

    @Inject(method = "setVolume", at = @At("HEAD"))
    private void audioclarity$volumeHead(float volume, CallbackInfo ci) {
        if (audioclarity$music) {
            MusicRoute.enter();
        }
        if (volume > 1f) {
            AL10.alSourcef(source, AL10.AL_MAX_GAIN, volume);
        }
    }

    @Inject(method = "setVolume", at = @At("RETURN"))
    private void audioclarity$volumeReturn(float volume, CallbackInfo ci) {
        if (audioclarity$music) {
            MusicRoute.exit();
        }
    }
}
