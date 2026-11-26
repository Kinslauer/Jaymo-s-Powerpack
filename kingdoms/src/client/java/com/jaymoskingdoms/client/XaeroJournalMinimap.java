package com.jaymoskingdoms.client;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import xaero.common.HudMod;
import xaero.common.core.IGuiGraphics;
import xaero.hud.gui.util.GuiUtils;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.render.MinimapPipRenderState;
import xaero.lib.XaeroLib;

/**
 * The traveller's journal's map page when Xaero's Minimap is installed: its minimap, drawn in the page's map box the way
 * its own HUD draws it (its size, zoom, shape and waypoints are the player's Xaero settings). Loaded only when the
 * Minimap is ({@link JournalScreen#XAERO_MINIMAP}); without it the journal draws its own map.
 */
final class XaeroJournalMinimap {
	/** Our own render state, so a HUD minimap drawn in the same frame (Xaero's "hide under screens" off) keeps its own. */
	private static final MinimapPipRenderState STATE = new MinimapPipRenderState();

	private XaeroJournalMinimap() {
	}

	/** The minimap's width on screen (gui px), or -1 while it can't be drawn (no session yet, or blocked by the server). */
	static int width() {
		MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
		if (session == null || HudMod.INSTANCE == null || session.getProcessor().getNoMinimapMessageReceived()) {
			return -1;
		}
		return session.getWidth(Minecraft.getInstance().getWindow().getGuiScale());
	}

	/** Draws the minimap with its top-left corner at ({@code x}, {@code y}); false (nothing drawn) while it can't be. */
	static boolean draw(GuiGraphicsExtractor graphics, int x, int y, float partial) {
		MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
		if (session == null || HudMod.INSTANCE == null || session.getProcessor().getNoMinimapMessageReceived()) {
			return false;
		}
		Window window = Minecraft.getInstance().getWindow();
		int screenW = window.getGuiScaledWidth(), screenH = window.getGuiScaledHeight();
		double guiScale = window.getGuiScale();
		float minimapScale = GuiUtils.getMinimapScale(HudMod.INSTANCE.getHudConfigs().getClientConfigManager());
		int configured = session.getConfiguredWidth(), w = session.getWidth(guiScale);
		STATE.update(x, y, screenW, screenH, guiScale, minimapScale, configured, w, partial, XaeroLib.INSTANCE.getClient().getBufferProvider());
		((IGuiGraphics)graphics).xaero_mm_getGuiRenderState().addPicturesInPictureState(STATE);
		session.getProcessor().getRenderer().renderOutsidePip(session, x, y, screenW, screenH, guiScale, minimapScale, configured, partial, graphics);
		return true;
	}
}
