package com.jaymoskitchen.client.hands;

import com.jaymoskitchen.net.Payloads.HandsSync;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** The readout of each running kind (what {@code Run#fill} sends: ints and floats, laid out per kind). */
final class Readouts {
	private Readouts() {
	}

	/** Just the dish and how many are at it. */
	private static final HandsHud.Drawer PLAIN = new HandsHud.Drawer() {
		@Override
		public int height(HandsSync sync) {
			return HandsHud.LINE;
		}

		@Override
		public void draw(GuiGraphicsExtractor graphics, Font font, HandsSync sync, int x, int y, float time) {
			com.jaymoskitchen.client.hud.HudStyle.text(graphics, font, HandsHud.dishName(sync.dish(), sync.kind()), x, y,
				com.jaymoskitchen.client.hud.HudStyle.TEXT);
		}
	};

	static HandsHud.Drawer of(int kind) {
		return PLAIN;
	}
}
