package com.groundzero.audioclarity;

import com.groundzero.audioclarity.gui.SettingsScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Puts a config button on Better Audio Clarity in Mod Menu's mod list. Only loaded when Mod Menu is installed. */
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return SettingsScreen::new;
    }
}
