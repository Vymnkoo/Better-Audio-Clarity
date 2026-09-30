package com.groundzero.audioclarity.mixin;

import com.groundzero.audioclarity.gui.CompressorScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A "Compressor..." button in the top-left corner of Music &amp; Sound: opens the settings on the Compressor tab. */
@Mixin(OptionsSubScreen.class)
public abstract class SoundOptionsCompressorButtonMixin extends Screen {

    private SoundOptionsCompressorButtonMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void audioclarity$compressorButton(CallbackInfo ci) {
        if ((Object) this instanceof SoundOptionsScreen) {
            Screen self = this;
            addRenderableWidget(Button.builder(Component.literal("Compressor..."),
                    b -> com.groundzero.audioclarity.gui.Screens.open(new CompressorScreen(self))).bounds(6, 6, 90, 20).build());
        }
    }
}
