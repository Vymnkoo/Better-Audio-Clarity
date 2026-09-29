package com.groundzero.audioclarity.mixin;

import com.groundzero.audioclarity.ClarityConfig;
import com.groundzero.audioclarity.audio.CompressorMeter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the compressor meter on top of Music &amp; Sound (which doesn't override this method itself). */
@Mixin(Screen.class)
public abstract class ScreenMeterMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void audioclarity$drawMeter(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        if (self instanceof SoundOptionsScreen && ClarityConfig.showMeter()) {
            CompressorMeter.draw(graphics, self.getFont(), self.width);
        }
    }
}
