package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.FormattedCharSequence;

/**
 * What is said over your own head (a blade with a will that speaks to you) is drawn small above the hotbar, because your own
 * head is out of sight in first person and the line would never be read.
 */
public final class OwnSpeechHud {
	/** The gap above the hotbar and the height of a row (gui pixels). */
	static final int ABOVE = 62, ROW = 10;

	private OwnSpeechHud() {
	}

	public static void register() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, JaymosKingdoms.id("own_speech"), OwnSpeechHud::draw);
	}

	private static void draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || !minecraft.options.getCameraType().isFirstPerson()) {
			return;
		}
		int y = graphics.guiHeight() - ABOVE;
		for (Map.Entry<List<FormattedCharSequence>, Integer> line : SpeechBubbles.own(minecraft.player.getId())) {
			for (FormattedCharSequence row : line.getKey()) {
				int w = minecraft.font.width(row);
				int x = (graphics.guiWidth() - w) / 2;
				graphics.fill(x - 2, y - 1, x + w + 2, y + ROW - 1, 0x90000000);
				graphics.text(minecraft.font, row, x, y, 0xFF000000 | line.getValue());
				y += ROW;
			}
		}
	}
}
