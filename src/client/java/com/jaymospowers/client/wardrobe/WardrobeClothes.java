package com.jaymospowers.client.wardrobe;

import java.util.UUID;
import net.conczin.mca.client.gui.NeedleScreen;
import net.minecraft.client.gui.screens.Screen;

/**
 * The wardrobe's Clothes tab: MCA's character editor held to its clothing page (its Needle and Thread screen), so a
 * wardrobe changes clothes and nothing else of how you look (Andy: "they should only ever change clothes not the looks
 * or anything else from the editor"). No needle is worn down, and the wardrobe's tabs sit where MCA's page row would be.
 * Only touched while MCA is loaded ({@link WardrobeClient}).
 */
final class WardrobeClothes extends NeedleScreen {
	/** The tab row's place: where the full editor's page row is (MCA lays it at the middle less this). */
	private static final int TABS_ABOVE_MIDDLE = 103;

	WardrobeClothes(UUID self) {
		super(self);
	}

	static Screen open(UUID self) {
		return new WardrobeClothes(self);
	}

	/** Leaving the tab: the clothes go to the server, as the editor's Done button sends them. */
	static void keep(Screen screen) {
		if (screen instanceof WardrobeClothes clothes && clothes.hasVillagerData()) {
			clothes.syncVillagerData();
		}
	}

	@Override
	protected void eventCallback(String event) {
		// (the needle's wear: a wardrobe has none)
	}

	@Override
	protected void setPage(String page) {
		super.setPage(page);
		// MCA clears every widget on each page change, so the tabs go back on after it
		if (WardrobeClient.showing(WardrobeClient.Tab.CLOTHES) && this.minecraft != null && this.minecraft.gui.screen() == this) {
			WardrobeClient.tabs(this.width / 2, this.height / 2 - TABS_ABOVE_MIDDLE, WardrobeClient.Tab.CLOTHES, this::addRenderableWidget);
		}
	}
}
