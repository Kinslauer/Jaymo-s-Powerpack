package com.jaymospowers.client.wardrobe;

import java.util.UUID;
import net.conczin.mca.client.gui.VillagerEditorScreen;
import net.minecraft.client.gui.screens.Screen;

/**
 * The wardrobe's Looks tab: MCA's character editor on yourself, as {@code /mca editor} opens it (General, Body and
 * Traits). Only touched while MCA is loaded ({@link WardrobeClient}).
 */
final class WardrobeLooks {
	private WardrobeLooks() {
	}

	static Screen editor(UUID self) {
		return new VillagerEditorScreen(self, self);
	}

	/** Whether {@code screen} is the wardrobe's own editor (not the creator, the needle or comb, which are subclasses). */
	static boolean is(Object screen) {
		return screen.getClass() == VillagerEditorScreen.class;
	}

	/** Leaving the tab: the edits go to the server, as the editor's Done button sends them. */
	static void keep(Screen screen) {
		if (is(screen) && ((VillagerEditorScreen)screen).hasVillagerData()) {
			((VillagerEditorScreen)screen).syncVillagerData();
		}
	}
}
