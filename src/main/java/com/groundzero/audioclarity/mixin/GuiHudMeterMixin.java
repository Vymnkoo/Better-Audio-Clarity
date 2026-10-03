package com.groundzero.audioclarity.mixin;

import com.groundzero.audioclarity.audio.HudMeter;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/**
 * The HUD meter on 26.1, where Gui itself draws the HUD with this method; from 26.2 the method
 * is gone from Gui (HudMeterMixin hooks Hud instead), so this injection simply doesn't apply.
 */
@Mixin(Gui.class)
public abstract class GuiHudMeterMixin {

    /** Options.hideGui (F1) - only on 26.1, so looked up by name. */
    @Unique
    private static Field audioclarity$hideGui;
    @Unique
    private static boolean audioclarity$looked;

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("TAIL"), require = 0)
    private void audioclarity$hudMeter(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        HudMeter.draw(graphics, audioclarity$hidden());
    }

    @Unique
    private static boolean audioclarity$hidden() {
        if (!audioclarity$looked) {
            audioclarity$looked = true;
            try {
                audioclarity$hideGui = net.minecraft.client.Options.class.getField("hideGui");
            } catch (NoSuchFieldException e) {
                audioclarity$hideGui = null;
            }
        }
        try {
            return audioclarity$hideGui != null && audioclarity$hideGui.getBoolean(Minecraft.getInstance().options);
        } catch (IllegalAccessException e) {
            return false;
        }
    }
}
