package com.jaymospowers.client.bits;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.hud.HudStyle;
import com.jaymospowers.client.hud.HudZones;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;

/**
 * Use bar: eating, drinking, brushing, sounding a horn, drawing a bow, loading a crossbow and winding up a trident put a thin
 * bar under the crosshair (the HUD's BELOW zone, the same flat frame, track and fill as every charge bar) that fills as the
 * use goes on. A drawn bow, a loaded crossbow and a wound-up trident turn it white. Holding a shield up, looking down a
 * spyglass and the other uses that go on as long as you hold them have no end to fill towards, so they show nothing.
 */
public final class UseBar {
	// ===== the knobs ================================================================================================
	/** The BELOW zone row (under the crosshair, above the guard and finisher bars) and the bar's width. */
	static final int ROW = 5, WIDTH = HudStyle.CENTRE_BAR_W;
	/** Ticks a bow takes to draw fully and a trident to wind up (vanilla's own thresholds). */
	static final int BOW_TICKS = net.minecraft.world.item.BowItem.MAX_DRAW_DURATION, TRIDENT_TICKS = net.minecraft.world.item.TridentItem.THROW_THRESHOLD_TIME;
	/** Colours: food, drink, and the rest of the timed uses; a charge that is full. */
	static final int FOOD = 0xFFD54A, DRINK = 0x6FB7FF, OTHER = 0xB0B0C8, FULL = 0xFFFFFF;
	// ================================================================================================================

	private UseBar() {
	}

	public static void init() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, JaymosPowers.id("use_bar"), UseBar::draw);
	}

	private static void draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || !player.isUsingItem() || minecraft.gui.screen() != null) {
			return;
		}
		ItemStack stack = player.getUseItem();
		ItemUseAnimation how = stack.getUseAnimation();
		int total;
		switch (how) {
			case EAT, DRINK, BRUSH, TOOT_HORN -> total = stack.getUseDuration(player);
			case BOW -> total = BOW_TICKS;
			case CROSSBOW -> total = CrossbowItem.getChargeDuration(stack, player);
			case TRIDENT -> total = TRIDENT_TICKS;
			default -> total = 0;
		}
		if (total <= 0) {
			return;
		}
		float share = Mth.clamp((player.getTicksUsingItem() + delta.getGameTimeDeltaPartialTick(false)) / total, 0.0F, 1.0F);
		boolean charge = how == ItemUseAnimation.BOW || how == ItemUseAnimation.CROSSBOW || how == ItemUseAnimation.TRIDENT;
		int color = charge && share >= 1.0F ? FULL : how == ItemUseAnimation.EAT ? FOOD : how == ItemUseAnimation.DRINK ? DRINK : OTHER;
		int frame = 2;
		int[] at = HudZones.place(HudZones.BELOW, "jaymospowers:use_bar", ROW, WIDTH + frame, HudStyle.BAR_H + frame);
		if (at == null) {
			return;
		}
		HudStyle.bar(graphics, at[0] + 1, at[1] + 1, WIDTH, HudStyle.BAR_H, share, color);
	}
}
