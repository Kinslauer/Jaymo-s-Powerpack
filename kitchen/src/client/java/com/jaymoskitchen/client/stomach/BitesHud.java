package com.jaymoskitchen.client.stomach;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.stomach.Bites;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * Finer hunger ({@link Bites}) on the hunger bar: the top shank drawn eaten down from the top by the bites gone from it,
 * a third of a point at a time. Also hands the bites to the client's own hunger, so a bitten point lets you start eating.
 */
public final class BitesHud {
	private static final Identifier EMPTY = Identifier.withDefaultNamespace("hud/food_empty"),
		FULL = Identifier.withDefaultNamespace("hud/food_full"),
		EMPTY_HUNGER = Identifier.withDefaultNamespace("hud/food_empty_hunger"),
		FULL_HUNGER = Identifier.withDefaultNamespace("hud/food_full_hunger");
	/** A shank's size, and the hunger bar's place from the bottom of the screen (vanilla's). */
	static final int SHANK = 9, FROM_BOTTOM = 39;

	private BitesHud() {
	}

	public static void register() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.FOOD_BAR, JaymosKitchen.id("hunger_bites"), BitesHud::render);
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			LocalPlayer player = minecraft.player;
			if (player != null) {
				((Bites.Holder)(Object)player.getFoodData()).jaymoskitchen$setBites(Bites.of(player));
			}
		});
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || player.isCreative() || player.isSpectator() || player.getVehicle() instanceof LivingEntity) {
			return;
		}
		int bites = Bites.of(player);
		int food = player.getFoodData().getFoodLevel();
		if (bites <= 0 || food <= 0) {
			return;
		}
		int shank = (food - 1) / 2;
		// what's left of the top shank, in thirds of a point: 2 points to a shank
		int left = (food - shank * 2) * Bites.PER_POINT - bites;
		int x = graphics.guiWidth() / 2 + 91 - shank * 8 - 9;
		int y = graphics.guiHeight() - FROM_BOTTOM;
		boolean hungry = player.hasEffect(MobEffects.HUNGER);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, hungry ? EMPTY_HUNGER : EMPTY, x, y, SHANK, SHANK);
		int shown = Math.round(SHANK * left / (2.0F * Bites.PER_POINT));
		if (shown > 0) {
			graphics.enableScissor(x, y + SHANK - shown, x + SHANK, y + SHANK);
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, hungry ? FULL_HUNGER : FULL, x, y, SHANK, SHANK);
			graphics.disableScissor();
		}
	}
}
