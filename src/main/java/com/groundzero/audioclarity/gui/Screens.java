package com.groundzero.audioclarity.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Opens a screen on every supported version: 26.2 and later have Minecraft.gui.setScreen,
 * 26.1 has Minecraft.setScreen. Looked up once; Minecraft 26.x runs under these same names.
 */
public final class Screens {

    private static final MethodHandle SET_SCREEN = find();

    private Screens() {}

    private static MethodHandle find() {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        MethodType type = MethodType.methodType(void.class, Screen.class);
        try {
            Class<?> gui = Class.forName("net.minecraft.client.gui.Gui");
            MethodHandle onGui = lookup.findVirtual(gui, "setScreen", type);
            MethodHandle guiField = lookup.findGetter(Minecraft.class, "gui", gui);
            // (Minecraft, Screen) -> minecraft.gui.setScreen(screen)
            return MethodHandles.filterArguments(onGui, 0, guiField);
        } catch (ReflectiveOperationException newerApiMissing) {
            try {
                return lookup.findVirtual(Minecraft.class, "setScreen", type);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("No setScreen method found on this Minecraft version", e);
            }
        }
    }

    public static void open(Screen screen) {
        try {
            SET_SCREEN.invoke(Minecraft.getInstance(), screen);
        } catch (Throwable t) {
            throw new IllegalStateException("Could not open " + screen, t);
        }
    }
}
