package com.groundzero.audioclarity.mixin;

import net.minecraft.client.sounds.ChannelAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.Executor;

/** The sound engine's own thread, so music channels are created on it in order. */
@Mixin(ChannelAccess.class)
public interface ChannelAccessAccessor {

    @Accessor("executor")
    Executor audioclarity$executor();
}
