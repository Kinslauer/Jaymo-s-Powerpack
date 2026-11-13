package com.jaymoskingdoms.client;

import com.jaymoskingdoms.MagicMenu;
import com.jaymoskingdoms.Scrying;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The scrying bowl: a round view of the ground round the one being watched, drawn from what the server sends each second, with
 * the people and monsters about them as dots. Closing the screen lets go of the spell.
 */
final class ScryScreen extends Screen {
	private static final int CELL = 4, BORDER = 6;
	private static final int[] DOT = {0xFFFFFFFF, 0xFF4AA3FF, 0xFFFF4A4A, 0xFF8AE06A};
	private Scrying.View view;
	private boolean quiet;

	ScryScreen(Scrying.View view) {
		super(Component.translatable("gui.jaymoskingdoms.scry.title", view.who()));
		this.view = view;
	}

	void update(Scrying.View next) {
		this.view = next;
	}

	/** The server ended the spell: close without telling it again. */
	void closeQuietly() {
		this.quiet = true;
		onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		if (!this.quiet) {
			ClientPlayNetworking.send(new MagicMenu.Pick("scry_stop", ""));
		}
		super.onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int n = (int) Math.round(Math.sqrt(this.view.colours().length));
		int size = n * CELL;
		int left = (this.width - size) / 2, top = (this.height - size) / 2;
		int r = n / 2;
		graphics.fill(left - BORDER, top - BORDER, left + size + BORDER, top + size + BORDER, 0xFF2A2018);
		graphics.fill(left - 2, top - 2, left + size + 2, top + size + 2, 0xFF101820);
		int[] colours = this.view.colours();
		for (int z = 0; z < n; z++) {
			for (int x = 0; x < n; x++) {
				int dx = x - r, dz = z - r;
				if (dx * dx + dz * dz > r * r) {
					continue;
				}
				int c = colours[z * n + x];
				if (this.view.night()) {
					c = ((c >> 16 & 255) * 2 / 5) << 16 | ((c >> 8 & 255) * 2 / 5) << 8 | (c & 255) * 3 / 5;
				}
				int x0 = left + x * CELL, y0 = top + z * CELL;
				graphics.fill(x0, y0, x0 + CELL, y0 + CELL, 0xFF000000 | c);
			}
		}
		int[] dots = this.view.dots();
		for (int i = 0; i + 2 < dots.length; i += 3) {
			int x0 = left + (dots[i] + r) * CELL, y0 = top + (dots[i + 1] + r) * CELL;
			int kind = dots[i + 2];
			int s = kind == 0 ? CELL + 2 : CELL;
			graphics.fill(x0 - 1, y0 - 1, x0 + s - 1, y0 + s - 1, DOT[Math.clamp(kind, 0, 3)]);
		}
		graphics.centeredText(this.font, this.title, this.width / 2, top - BORDER - 12, Ui.GOLD);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
