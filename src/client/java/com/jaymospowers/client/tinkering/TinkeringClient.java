package com.jaymospowers.client.tinkering;

import com.jaymospowers.tinkering.Tinkering;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;

/** The Part Builder's and Tool Station's screens. */
public final class TinkeringClient {
	private TinkeringClient() {
	}

	public static void init() {
		MenuScreens.register(Tinkering.PART_BUILDER_MENU, PartBuilderScreen::new);
		MenuScreens.register(Tinkering.TOOL_STATION_MENU, ToolStationScreen::new);
		Tinkering.traitDetails = () -> Minecraft.getInstance().hasShiftDown();
	}
}
