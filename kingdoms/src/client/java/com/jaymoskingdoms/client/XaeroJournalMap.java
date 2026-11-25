package com.jaymoskingdoms.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import xaero.map.WorldMapSession;
import xaero.map.gui.GuiMap;

/**
 * The journal map page's "World map" button when Xaero's World Map is installed: its full map, opened the way its own key
 * opens it, with the journal behind it, so closing it (Escape, or its map key) goes back to the journal. Loaded only when
 * the World Map is ({@link JournalScreen#XAERO_WORLD_MAP}).
 */
final class XaeroJournalMap {
	private XaeroJournalMap() {
	}

	/** Opens Xaero's World Map over {@code journal}; false (nothing opened) while its map isn't ready, e.g. still loading. */
	static boolean open(Screen journal) {
		Minecraft minecraft = Minecraft.getInstance();
		WorldMapSession session = WorldMapSession.getCurrentSession();
		if (session == null || !session.isUsable() || session.getMapProcessor() == null || minecraft.getCameraEntity() == null) {
			return false;
		}
		minecraft.gui.setScreen(new GuiMap(journal, journal, session.getMapProcessor(), minecraft.getCameraEntity()));
		return true;
	}
}
