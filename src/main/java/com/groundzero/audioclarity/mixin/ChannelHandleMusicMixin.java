package com.groundzero.audioclarity.mixin;

import com.groundzero.audioclarity.audio.MusicRoute;
import com.mojang.blaze3d.audio.Channel;
import net.minecraft.client.sounds.ChannelAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Consumer;

/**
 * Commands queued for a channel (play, position, volume...) run with a music channel's sound
 * card context already current - before the channel method itself starts, so other mods'
 * hooks inside those methods (e.g. Sound Physics on play) also see the right context.
 */
@Mixin(ChannelAccess.ChannelHandle.class)
public abstract class ChannelHandleMusicMixin {

    @ModifyVariable(method = "execute", at = @At("HEAD"), argsOnly = true)
    private Consumer<Channel> audioclarity$onMusicContext(Consumer<Channel> action) {
        return MusicRoute.wrap(action);
    }
}
