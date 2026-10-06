package com.groundzero.audioclarity.mixin;

import com.groundzero.audioclarity.ClarityConfig;
import com.groundzero.audioclarity.audio.MasterBus;
import com.mojang.blaze3d.audio.Library;
import net.minecraft.client.Minecraft;
import org.lwjgl.openal.ALC10;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.IntBuffer;

import static com.groundzero.audioclarity.AudioClarity.LOGGER;

/**
 * Routes Minecraft's audio through {@link MasterBus}: the game gets a loopback device to mix
 * into, and the real sound card is fed from it through the compressor.
 *
 * <p>Only the device handle, the device name and the context attributes are swapped; the rest of
 * Library.init runs untouched. The attributes are changed with {@code @ModifyArg} (not a redirect)
 * because Sound Physics Remastered also edits that same alcCreateContext call.
 */
@Mixin(Library.class)
public abstract class LibraryMasterBusMixin {

    @Unique
    private MasterBus audioclarity$bus;
    @Unique
    private static boolean audioclarity$failedThisSession;

    @Redirect(method = "init", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/audio/Library;openDeviceOrFallback(Ljava/lang/String;Ljava/lang/String;)J"))
    private long audioclarity$openThroughMasterBus(String preferred, String fallback) {
        ClarityConfig.onSoundEngineStart(); // F3+T re-reads the config
        long real = LibraryInvoker.audioclarity$openDeviceOrFallback(preferred, fallback);
        audioclarity$bus = audioclarity$failedThisSession ? null : MasterBus.tryCreate(real);
        return audioclarity$bus != null ? audioclarity$bus.loopDevice() : real;
    }

    /** Keep reporting the real card's name - Minecraft compares it to the chosen device. */
    @Redirect(method = "init", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/audio/Library;queryDeviceName(J)Ljava/lang/String;"))
    private String audioclarity$realDeviceName(long device) {
        return LibraryInvoker.audioclarity$queryDeviceName(audioclarity$bus != null ? audioclarity$bus.realDevice() : device);
    }

    /**
     * Vanilla keeps only up to 8 streaming channels (the rest are for fully loaded sounds). Music
     * and UI sounds stream while they skip the chain, and quick UI sounds - a sword tick spammed
     * in PvP - could run out; 16 is plenty and costs the loaded pool (~250) nothing noticeable.
     *
     * <p>The first clamp in init is the streaming count. Up to 26.4-snapshot-2 it is
     * Mth.clamp(int, int, int); from snapshot-3 Mojang uses java.lang.Math.clamp(long, int, int)
     * there. One redirect per form, each optional, so whichever the game has is hooked.
     */
    @Redirect(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(III)I", ordinal = 0),
            require = 0)
    private int audioclarity$moreStreamingChannels(int value, int min, int max) {
        return audioclarity$streaming(Math.max(min, Math.min(max, value)));
    }

    @Redirect(method = "init", at = @At(value = "INVOKE", target = "Ljava/lang/Math;clamp(JII)I", ordinal = 0),
            require = 0)
    private int audioclarity$moreStreamingChannelsLong(long value, int min, int max) {
        return audioclarity$streaming((int) Math.max(min, Math.min(max, value)));
    }

    @Unique
    private int audioclarity$streaming(int vanilla) {
        return audioclarity$bus != null ? Math.max(vanilla, 16) : vanilla;
    }

    @ModifyArg(method = "init", index = 1, at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/openal/ALC10;alcCreateContext(JLjava/nio/IntBuffer;)J"))
    private IntBuffer audioclarity$loopbackFormat(IntBuffer attributes) {
        return audioclarity$bus != null ? audioclarity$bus.withLoopbackFormat(attributes) : attributes;
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void audioclarity$startMasterBus(CallbackInfo ci) {
        if (audioclarity$bus != null && !audioclarity$bus.start()) {
            // Game audio is already bound to the loopback device, which nothing would play.
            // Reload the sound engine once, this time the vanilla way.
            audioclarity$failedThisSession = true;
            LOGGER.warn("Reloading the sound engine without the master compressor");
            Minecraft.getInstance().execute(() -> Minecraft.getInstance().getSoundManager().reload());
        }
    }

    /**
     * After Minecraft has freed its channels (music channels live on the sound card, so they must
     * go first), before it closes the loopback device the bus renders from.
     */
    @Inject(method = "cleanup", at = @At(value = "INVOKE", target = "Lorg/lwjgl/openal/ALC10;alcDestroyContext(J)V"))
    private void audioclarity$stopMasterBus(CallbackInfo ci) {
        if (audioclarity$bus != null) {
            audioclarity$bus.close();
            audioclarity$bus = null;
        }
    }

    /** Unplugging the headphones disconnects the real card, never the loopback device. */
    @Redirect(method = "isCurrentDeviceDisconnected", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/openal/ALC11;alcGetInteger(JI)I"))
    private int audioclarity$realDeviceConnected(long device, int param) {
        return ALC10.alcGetInteger(audioclarity$bus != null ? audioclarity$bus.realDevice() : device, param);
    }
}
