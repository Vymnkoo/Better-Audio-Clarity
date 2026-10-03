package com.groundzero.audioclarity.mixin;

import com.groundzero.audioclarity.audio.HudMeter;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The HUD meter on 26.2 and later, where the HUD is drawn by net.minecraft.client.gui.Hud.
 * Pseudo: 26.1 has no Hud class (GuiHudMeterMixin covers it there).
 */
@Pseudo
@Mixin(targets = "net.minecraft.client.gui.Hud")
public abstract class HudMeterMixin {

    @Shadow
    private boolean isHidden;   // F1

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("TAIL"), require = 0)
    private void audioclarity$hudMeter(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        HudMeter.draw(graphics, isHidden);
    }
}
