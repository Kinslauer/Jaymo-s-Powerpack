package com.jaymospowers.client.alchemy;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.alchemy.Essence;
import com.jaymospowers.alchemy.EssenceList;
import com.jaymospowers.alchemy.Knowledge;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

/**
 * The essence symbols (16x16 textures under {@code textures/gui/alchemy/essence/}), for any alchemy screen or HUD:
 * {@link #draw} an essence's symbol at any size, or a "?" for one the player hasn't discovered.
 */
public final class CodexIcons {
	private static final Map<Essence, Identifier> TEXTURES = new EnumMap<>(Essence.class);

	private CodexIcons() {
	}

	private static Identifier texture(Essence essence) {
		return TEXTURES.computeIfAbsent(essence, e -> JaymosPowers.id("textures/gui/alchemy/essence/" + e.id() + ".png"));
	}

	/** An essence's symbol, {@code size} pixels square (it is drawn at 16 and scaled). */
	public static void draw(GuiGraphicsExtractor graphics, Essence essence, int x, int y, int size) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture(essence), x, y, 0.0F, 0.0F, size, size, 16, 16, 16, 16);
	}

	/** The symbol if the player has discovered the essence, else a grey "?" in its place. */
	public static void drawKnown(GuiGraphicsExtractor graphics, Font font, Player player, Essence essence, int x, int y, int size) {
		if (Knowledge.hasSeen(player, essence)) {
			draw(graphics, essence, x, y, size);
		} else {
			graphics.text(font, "?", x + (size - font.width("?")) / 2, y + (size - font.lineHeight) / 2 + 1, 0xFF707080);
		}
	}

	/** An essence's colour as text (0xRRGGBB), lightened when it is too dark to read on the panel. */
	public static int colour(Essence essence) {
		int c = essence.colour;
		int r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
		if ((r + g + b) / 3 < 100) {
			r += (255 - r) * 45 / 100;
			g += (255 - g) * 45 / 100;
			b += (255 - b) * 45 / 100;
		}
		return r << 16 | g << 8 | b;
	}

	/** The name of an essence in its own colour, or "?" for one the player hasn't discovered. */
	public static Component name(Player player, Essence essence) {
		return Knowledge.hasSeen(player, essence) ? Component.translatable(essence.key()).withColor(colour(essence))
			: Component.literal("?").withColor(0x707080);
	}

	/** Whether the local player can read this list: every essence in it discovered. */
	public static boolean readable(Player player, EssenceList list) {
		for (Essence e : list.amounts().keySet()) {
			if (!Knowledge.hasSeen(player, e)) {
				return false;
			}
		}
		return true;
	}

	/** The local player, or null in a menu. */
	public static Player player() {
		return Minecraft.getInstance().player;
	}
}
