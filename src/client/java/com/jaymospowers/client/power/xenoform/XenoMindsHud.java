package com.jaymospowers.client.power.xenoform;

import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoForms;
import com.jaymospowers.power.xenoform.XenoMinds;
import com.jaymospowers.power.xenoform.minds.LumenShapes;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The small meters of the mind and shape moves, above the alien's timer: Lumen's charge of light with its picked shape and the
 * heat gauge of the beam, and Cortex's read of whoever last struck it (name, health, whom it is after, what it is about to do).
 */
final class XenoMindsHud {
	private static final int OVER_COLOR = 0xFF5030, HOT_COLOR = 0xFFD070;

	private XenoMindsHud() {
	}

	static void draw(GuiGraphicsExtractor graphics, Font font, LocalPlayer player, XenoForm form, int x, int y, int width, float time) {
		if (form == XenoForms.LUMEN) {
			lumen(graphics, font, player, x, y, width, time);
		} else if (form == XenoForms.CORTEX) {
			cortex(graphics, font, player, x, y, width);
		}
	}

	/** Text kept on screen: shifted left when it would run off the right edge. */
	private static int fit(GuiGraphicsExtractor graphics, Font font, String text, int x) {
		return Math.max(2, Math.min(x, graphics.guiWidth() - font.width(text) - 2));
	}

	private static void lumen(GuiGraphicsExtractor graphics, Font font, LocalPlayer player, int x, int y, int width, float time) {
		XenoMinds.LumenHud hud = player.getAttached(XenoMinds.LUMEN);
		if (hud == null) {
			return;
		}
		LumenShapes.Shape[] shapes = LumenShapes.Shape.values();
		LumenShapes.Shape shape = shapes[Mth.clamp(hud.shape(), 0, shapes.length - 1)];
		boolean low = shape.cost > hud.charge();
		String title = shape.title().getString() + (shape.cost > 0 ? " " + shape.cost : "");
		graphics.text(font, title, fit(graphics, font, title, x), y - 14, 0xFF000000 | (low ? 0x8090A0 : LumenShapes.COLOR));
		float share = Mth.clamp(hud.charge() / (float)LumenShapes.MAX, 0.0F, 1.0F);
		graphics.fill(x - 1, y - 5, x + width + 1, y - 1, 0xB0000000);
		graphics.fill(x, y - 4, x + Mth.ceil(width * share), y - 2, 0xFF000000 | LumenShapes.COLOR);
		if (hud.heat() > 0) {
			float heat = Mth.clamp(hud.heat() / (float)LumenShapes.HEAT_FULL, 0.0F, 1.0F);
			int color = hud.over() ? (((int)(time * 0.5F) & 1) == 0 ? OVER_COLOR : 0xFFFFFF) : HOT_COLOR;
			graphics.fill(x - 1, y, x + width + 1, y + 3, 0xB0000000);
			graphics.fill(x, y + 1, x + Mth.ceil(width * heat), y + 2, 0xFF000000 | color);
		}
	}

	private static void cortex(GuiGraphicsExtractor graphics, Font font, LocalPlayer player, int x, int y, int width) {
		XenoMinds.FoeHud foe = player.getAttached(XenoMinds.FOE);
		if (foe == null) {
			return;
		}
		if (foe.staring()) {
			String stare = Component.translatable("xenoform_minds.hud.stare").getString();
			graphics.text(font, stare, fit(graphics, font, stare, x), y - 42, 0xFF000000 | 0xB070E0);
		}
		if (foe.name().isEmpty()) {
			return;
		}
		String name = foe.name() + " " + Math.round(foe.health()) + "/" + Math.round(foe.max());
		graphics.text(font, name, fit(graphics, font, name, x), y - 30, 0xFFFFFFFF);
		float share = Mth.clamp(foe.health() / Math.max(1.0F, foe.max()), 0.0F, 1.0F);
		graphics.fill(x - 1, y - 20, x + width + 1, y - 15, 0xB0000000);
		graphics.fill(x, y - 19, x + Mth.ceil(width * share), y - 16, 0xFF000000 | (share > 0.5F ? 0x50D070 : share > 0.25F ? 0xE0B040 : 0xE05040));
		String line = Component.translatable("xenoform_minds.intent." + foe.intent()).getString();
		if (!foe.target().isEmpty()) {
			line += " - " + Component.translatable("xenoform_minds.hud.after", foe.target()).getString();
		}
		graphics.text(font, line, fit(graphics, font, line, x), y - 12, 0xFF000000 | 0x4FB8B0);
	}
}
