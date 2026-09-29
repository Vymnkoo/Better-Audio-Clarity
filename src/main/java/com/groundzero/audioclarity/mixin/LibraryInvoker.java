package com.groundzero.audioclarity.mixin;

import com.mojang.blaze3d.audio.Library;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Library's private device helpers, so the master bus can open the real device the vanilla way. */
@Mixin(Library.class)
public interface LibraryInvoker {

    @Invoker("openDeviceOrFallback")
    static long audioclarity$openDeviceOrFallback(String preferred, String fallback) {
        throw new AssertionError();
    }

    @Invoker("queryDeviceName")
    static String audioclarity$queryDeviceName(long device) {
        throw new AssertionError();
    }
}
