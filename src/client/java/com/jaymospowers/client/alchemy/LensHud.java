package com.jaymospowers.client.alchemy;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.alchemy.Essence;
import com.jaymospowers.alchemy.Knowledge;
import com.jaymospowers.alchemy.codex.Codex;
import com.jaymospowers.alchemy.codex.Lens;
import com.jaymospowers.alchemy.codex.LensItem;
import com.jaymospowers.client.UiStyle;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import org.jspecify.annotations.Nullable;

/**
 * While a Scrying Lens is held: a small panel by the crosshair with what you're aiming at and its essences. Essences
 * the player has discovered show their symbol, name and amount; the rest show "?". A thin bar fills while the lens is
 * held on it, and the whole panel is the same reading the server will do ({@link Lens#aim}).
 */
final class LensHud {
	private static final int ACCENT = 0xFFC9B8FF, PANEL = 0x90081018, ROW = 11, ICON = 10, MAX_W = 130, GAP = 14;

	private static Lens.@Nullable Thing thing;

	private LensHud() {
	}

	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			LocalPlayer player = minecraft.player;
			thing = null;
			if (player == null || player.isSpectator() || minecraft.gui.screen() != null) {
				return;
			}
			InteractionHand hand = player.getMainHandItem().is(Codex.LENS) ? InteractionHand.MAIN_HAND
				: player.getOffhandItem().is(Codex.LENS) ? InteractionHand.OFF_HAND : null;
			if (hand != null) {
				thing = Lens.aim(player, hand);
			}
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, JaymosPowers.id("lens_panel"), LensHud::render);
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		Lens.Thing now = thing;
		if (player == null || now == null) {
			return;
		}
		Font font = minecraft.font;
		List<Map.Entry<Essence, Integer>> rows = Lens.sorted(now.essences());
		int width = Math.min(MAX_W, Math.max(60, font.width(now.name().getString()) + 12));
		for (Map.Entry<Essence, Integer> row : rows) {
			String text = Knowledge.hasSeen(player, row.getKey()) ? Component.translatable(row.getKey().key()).getString() + " " + row.getValue() : "?";
			width = Math.max(width, Math.min(MAX_W, font.width(text) + ICON + 14));
		}
		int lines = Math.max(1, rows.size());
		int height = 6 + 10 + 4 + lines * ROW;
		int x = graphics.guiWidth() / 2 + GAP;
		int y = graphics.guiHeight() / 2 - height / 2;
		graphics.fill(x, y, x + width, y + height, PANEL);
		graphics.fill(x, y, x + 2, y + height, ACCENT);
		UiStyle.line(graphics, font, now.name(), x + 6, y + 4, width - 10, UiStyle.TEXT);
		if (player.isUsingItem() && player.getUseItem().is(Codex.LENS)) {
			UiStyle.bar(graphics, x + 6, y + 14, width - 12, 1, (float) player.getTicksUsingItem() / LensItem.USE_TICKS, ACCENT);
		}
		int ry = y + 18;
		if (rows.isEmpty()) {
			graphics.text(font, Component.translatable("gui.jaymospowers.lens.empty"), x + 6, ry + 1, UiStyle.HINT);
			return;
		}
		for (Map.Entry<Essence, Integer> row : rows) {
			Essence e = row.getKey();
			if (Knowledge.hasSeen(player, e)) {
				CodexIcons.draw(graphics, e, x + 5, ry, ICON);
				UiStyle.line(graphics, font, Component.translatable(e.key()).append(" " + row.getValue()), x + 6 + ICON + 3, ry + 1,
					width - ICON - 14, 0xFF000000 | CodexIcons.colour(e));
			} else {
				graphics.text(font, "?", x + 5 + (ICON - font.width("?")) / 2, ry + 1, UiStyle.HINT);
			}
			ry += ROW;
		}
	}
}
