package com.groundzero.audioclarity.mixin;

import com.groundzero.audioclarity.ClarityConfig;
import com.groundzero.audioclarity.audio.CompressorMeter;
import com.groundzero.audioclarity.audio.MasterBus;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes the bottom bar of Music &amp; Sound taller and keeps the Done button at its bottom, so the
 * compressor meter (drawn by ScreenMeterMixin) fits full-width between the options and Done.
 * The options list above simply scrolls in the slightly smaller space.
 */
@Mixin(OptionsSubScreen.class)
public abstract class MusicSoundFooterMixin extends Screen {

    @Shadow
    @Final
    public HeaderAndFooterLayout layout;

    private MusicSoundFooterMixin(Component title) {
        super(title);
    }

    /** Music &amp; Sound's Output slider (SoundOptionsOutputMixin) lives in our config: save it on close. */
    @Inject(method = "removed", at = @At("HEAD"))
    private void audioclarity$saveOutput(CallbackInfo ci) {
        if ((Object) this instanceof SoundOptionsScreen) {
            ClarityConfig.saveNow();
        }
    }

    @Inject(method = "addFooter", at = @At("HEAD"), cancellable = true)
    private void audioclarity$roomForMeter(CallbackInfo ci) {
        if ((Object) this instanceof SoundOptionsScreen && ClarityConfig.showMeter() && MasterBus.active() != null) {
            layout.setFooterHeight(HeaderAndFooterLayout.DEFAULT_HEADER_AND_FOOTER_HEIGHT + CompressorMeter.HEIGHT);
            // Same Done button as vanilla, pinned to the bottom of the taller bar.
            layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).width(200).build(),
                    s -> s.alignVerticallyBottom().paddingBottom(6));
            ci.cancel();
        }
    }
}
