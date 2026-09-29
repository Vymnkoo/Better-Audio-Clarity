package com.groundzero.audioclarity.mixin;

import com.groundzero.audioclarity.audio.MusicRoute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sound Physics Remastered puts its reverb/occlusion filters on every source as it starts. Music
 * plays straight to the sound card (MusicRoute), where those filter objects - which belong to the
 * compressed game mix - don't exist, so OpenAL rejected them and the log filled with
 * "Set environment ... AL_INVALID_NAME". Music shouldn't get cave reverb anyway: skip it.
 *
 * <p>Optional: does nothing if Sound Physics isn't installed.
 */
@Pseudo
@Mixin(targets = "com.sonicether.soundphysics.SoundPhysics", remap = false)
public abstract class SoundPhysicsSkipMusicMixin {

    @Inject(method = {
            "setEnvironment(IFFFFFFFFFF)V",
            "setDefaultEnvironment(IZ)V",
            "setSoundPos(ILnet/minecraft/world/phys/Vec3;)V"},
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void audioclarity$skipMusic(CallbackInfo ci) {
        if (MusicRoute.inMusicContext()) {
            ci.cancel();
        }
    }
}
