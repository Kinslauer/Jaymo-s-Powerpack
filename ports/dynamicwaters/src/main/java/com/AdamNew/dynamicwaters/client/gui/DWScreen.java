package com.AdamNew.dynamicwaters.client.gui;

import com.AdamNew.dynamicwaters.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

public abstract class DWScreen extends Screen {
    protected DWScreen(Component title) {
        super(title);
    }

    public static float panoramaTurn() {
        return Minecraft.getInstance().getDeltaTracker().getRealtimeDeltaTicks();
    }

    public static boolean customPanorama() {
        return Config.CUSTOM_MAIN_MENU_PANORAMA != null && Config.CUSTOM_MAIN_MENU_PANORAMA.get();
    }

    /** Screens whose background was the river panorama upstream: config screens, by their class name. */
    public static boolean configLike(Screen screen) {
        final String name = screen.getClass().getSimpleName();
        return name.contains("Config") || name.contains("Configuration");
    }

    /** Whether the panorama drawn this frame should be the river one instead of vanilla's. */
    public static boolean wantsRiverPanorama() {
        final Screen screen = Minecraft.getInstance().gui.screen();
        if (screen == null) return false;
        if (screen instanceof DWConfigScreen) return true;
        return customPanorama() && (screen instanceof TitleScreen || configLike(screen));
    }
}
