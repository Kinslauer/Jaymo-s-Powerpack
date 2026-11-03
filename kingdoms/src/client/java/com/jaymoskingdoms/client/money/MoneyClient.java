package com.jaymoskingdoms.client.money;

import com.jaymoskingdoms.money.MoneyItems;
import net.minecraft.client.gui.screens.MenuScreens;

/** (0.7.486) The coinage's client side: the Coin Press's screen. */
public final class MoneyClient {
	private MoneyClient() {
	}

	public static void register() {
		MenuScreens.register(MoneyItems.PRESS_MENU, CoinPressScreen::new);
	}
}
