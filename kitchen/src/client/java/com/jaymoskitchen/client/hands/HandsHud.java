package com.jaymoskitchen.client.hands;

import com.jaymoskitchen.ModItems;
import com.jaymoskitchen.client.hud.HudStyle;
import com.jaymoskitchen.client.hud.HudZones;
import com.jaymoskitchen.cooking.Dish;
import com.jaymoskitchen.cooking.Quality;
import com.jaymoskitchen.cooking.hands.HandsOn;
import com.jaymoskitchen.cooking.hands.Pick;
import com.jaymoskitchen.net.Payloads.HandsSync;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * What the station you're at shows, in a small plate under the crosshair: what lies on it and the dish it would make
 * (with the tool to start it), or the running dish's readout (one drawer per kind, below), or how it came out.
 */
final class HandsHud {
	static final int WIDTH = 150, PAD = 4, LINE = 10, SLOT = 16;

	private HandsHud() {
	}

	/** One drawer's plate: its height, and what it draws at {@code (x, y)} (width {@link #WIDTH}). */
	interface Drawer {
		int height(HandsSync sync);

		void draw(GuiGraphicsExtractor graphics, Font font, HandsSync sync, int x, int y, float time);
	}

	static void draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!HandsOn.clientOn || minecraft.player == null || minecraft.gui.screen() != null) {
			return;
		}
		HandsClient.View view = HandsClient.current(minecraft);
		if (view == null) {
			return;
		}
		HandsSync sync = view.sync();
		Font font = minecraft.font;
		float time = HandsClient.clock + delta.getGameTimeDeltaPartialTick(false);
		Drawer drawer = drawerFor(sync);
		int height = PAD * 2 + drawer.height(sync);
		int[] at = HudZones.place(HudZones.BELOW, "jaymoskitchen:hands", 0, WIDTH, height);
		if (at == null) {
			return;
		}
		HudStyle.plate(graphics, at[0], at[1], WIDTH, height, HudStyle.PLATE, HudStyle.RIM_DIM);
		drawer.draw(graphics, font, sync, at[0] + PAD, at[1] + PAD, time);
	}

	static Component dishName(int dish, int kind) {
		Dish found = Dish.byIndex(dish);
		if (found != null) {
			return Component.translatable(ModItems.dish(found).getDescriptionId());
		}
		Pick.Kind k = Pick.Kind.byIndex(kind);
		return Component.translatable("hands.pick." + (k == null ? "none" : k.name().toLowerCase()));
	}

	private static Drawer drawerFor(HandsSync sync) {
		return switch (sync.phase()) {
			case HandsSync.PHASE_RUNNING -> Readouts.of(sync.kind());
			case HandsSync.PHASE_JUDGED -> JUDGED;
			default -> STAGING;
		};
	}

	/** The things lying on the station, and what they'd make. */
	private static final Drawer STAGING = new Drawer() {
		@Override
		public int height(HandsSync sync) {
			return LINE + 2 + SLOT;
		}

		@Override
		public void draw(GuiGraphicsExtractor graphics, Font font, HandsSync sync, int x, int y, float time) {
			Component line;
			if (sync.picks() == 0) {
				line = Component.translatable("hud.jaymoskitchen.hands.nothing_yet");
			} else {
				Pick.Kind kind = Pick.Kind.byIndex(sync.kind());
				line = Component.translatable("hud.jaymoskitchen.hands.pick", dishName(sync.dish(), sync.kind()), sync.pick() + 1, sync.picks(),
					Component.translatable(kind == null ? "tool.jaymoskitchen.hand" : kind.tool().translationKey()));
			}
			HudStyle.text(graphics, font, line, x, y, sync.picks() == 0 ? HudStyle.DIM : HudStyle.GOLD);
			int px = x;
			for (ItemStack stack : sync.placed()) {
				if (px + SLOT > x + WIDTH - PAD * 2) {
					break;
				}
				graphics.item(stack, px, y + LINE + 2);
				px += SLOT;
			}
		}
	};

	/** How a dish came out (a Quality ordinal), for a moment. */
	private static final Drawer JUDGED = new Drawer() {
		@Override
		public int height(HandsSync sync) {
			return LINE;
		}

		@Override
		public void draw(GuiGraphicsExtractor graphics, Font font, HandsSync sync, int x, int y, float time) {
			Quality[] all = Quality.values();
			boolean dish = sync.dish() >= 0 && sync.quality() >= 0 && sync.quality() < all.length;
			Component what = dish
				? Component.translatable(all[sync.quality()].translationKey(), dishName(sync.dish(), sync.kind())).withStyle(all[sync.quality()].color())
				: Component.translatable("hud.jaymoskitchen.hands.done." + Math.max(0, Math.min(2, sync.quality())));
			int color = HudStyle.TEXT;
			HudStyle.text(graphics, font, what, x, y, color);
		}
	};
}
