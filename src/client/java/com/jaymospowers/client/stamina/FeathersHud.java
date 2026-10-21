package com.jaymospowers.client.stamina;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.stamina.Feathers;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * (0.15.4) The feathers row ({@link Feathers}), in vanilla's place for such things: right above the hunger bar (above
 * the air bubbles when they show, above a mount's hearts when riding), ten icons draining from the left as hunger
 * does. Blue feathers, golden ones over them (a power's bonus), red strain once the row is empty, grey ones held back
 * by armour or heat at the left end, pale and shaking while exhausted; the empty slots flash for a moment when
 * feathers are spent. Survival and adventure only. Art: {@code tools/gen_feathers.py}.
 */
public final class FeathersHud {
	private static final Identifier TEXTURE = JaymosPowers.id("textures/gui/feathers.png");
	private static final int TEX_W = 99, TEX_H = 9, ICON = 9;
	private static final int CONTAINER = 0, FULL = 1, HALF = 2, GOLD = 3, GOLD_HALF = 4, STRAIN = 5, STRAIN_HALF = 6, HELD = 7, PALE = 8,
		PALE_HALF = 9, FLASH = 10;
	/** How long (ticks) the empty slots flash after a spend. */
	private static final int FLASH_TICKS = 6;

	private static int lastSpendable = -1;
	private static int flash;

	private FeathersHud() {
	}

	public static void init() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.FOOD_BAR, JaymosPowers.id("feathers"), FeathersHud::render);
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (flash > 0) {
				flash--;
			}
			LocalPlayer player = minecraft.player;
			if (player == null) {
				lastSpendable = -1;
				return;
			}
			int spendable = Feathers.view(player).spendable();
			if (lastSpendable >= 0 && spendable < lastSpendable) {
				flash = FLASH_TICKS;
			}
			lastSpendable = spendable;
		});
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || player.isCreative() || player.isSpectator()) {
			return;
		}
		Feathers.View view = Feathers.view(player);
		int right = graphics.guiWidth() / 2 + 91;
		int y = graphics.guiHeight() - 49;
		if (player.getVehicle() instanceof LivingEntity mount) {
			// a mount's hearts take the hunger bar's place, ten to a row
			int hearts = Mth.ceil(mount.getMaxHealth() / 2.0F);
			y -= (Math.max(1, Mth.ceil(Math.min(hearts, 30) / 10.0F)) - 1) * 10;
		}
		if (player.isEyeInFluid(FluidTags.WATER) || player.getAirSupply() < player.getMaxAirSupply()) {
			y -= 10;
		}
		boolean flashing = flash > 0 && flash % 2 == 0;
		for (int i = 0; i < Feathers.MAX / 2; i++) {
			int x = right - i * 8 - ICON;
			int at = y;
			if (view.exhausted()) {
				// out of breath: the row trembles, as hunger's does
				at += (player.tickCount * 3 + i * 7) % 3 - 1;
			}
			int unit = i * 2;
			if (unit >= view.cap()) {
				icon(graphics, x, at, HELD);
			} else {
				icon(graphics, x, at, flashing ? FLASH : CONTAINER);
			}
			if (unit < view.cap() && view.feathers() > unit) {
				boolean whole = view.feathers() >= unit + 2;
				icon(graphics, x, at, view.exhausted() ? (whole ? PALE : PALE_HALF) : (whole ? FULL : HALF));
			} else if (view.strain() > unit) {
				icon(graphics, x, at, view.strain() >= unit + 2 ? STRAIN : STRAIN_HALF);
			}
			if (view.bonus() > unit) {
				icon(graphics, x, at, view.bonus() >= unit + 2 ? GOLD : GOLD_HALF);
			}
		}
	}

	private static void icon(GuiGraphicsExtractor graphics, int x, int y, int column) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, column * ICON, 0, ICON, ICON, TEX_W, TEX_H);
	}
}
