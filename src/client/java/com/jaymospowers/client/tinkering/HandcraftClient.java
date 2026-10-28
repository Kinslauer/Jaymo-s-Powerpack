package com.jaymospowers.client.tinkering;

import com.jaymospowers.ground.knapping.Knapping;
import com.jaymospowers.tinkering.fletching.Fletching;
import net.minecraft.client.gui.screens.MenuScreens;

/** The knapping grid's and the Fletching Station's screens. */
final class HandcraftClient {
	private HandcraftClient() {
	}

	static void init() {
		MenuScreens.register(Knapping.MENU, KnappingScreen::new);
		MenuScreens.register(Fletching.MENU, FletchingScreen::new);
	}
}
