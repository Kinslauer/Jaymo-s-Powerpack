package com.jaymospowers.client.fighting;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.hud.HudStyle;
import com.jaymospowers.client.hud.HudZones;
import com.jaymospowers.fighting.ArrowPick;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.ProjectileWeaponItem;

/**
 * Pick your arrow, this side ({@link ArrowPick}): the wheel while a bow or crossbow is drawn asks the server for the next
 * kind of arrow (and is not the hotbar's or the stances'), and the pick is drawn beside the hotbar, in the left dock, with
 * how many of it are carried, while a weapon that has a pick is in hand.
 */
public final class ArrowPickClient {
	/** Ignore a wheel turn that follows the last within this many milliseconds (a touchpad sends many small ones). */
	private static final long REPEAT_MILLIS = 120L;
	private static final int SLOT = 18;

	private static long last;

	private ArrowPickClient() {
	}

	public static void init() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosPowers.id("arrow_pick"), ArrowPickClient::render);
	}

	/** The wheel turned: true if it was taken (a ranged weapon is being drawn), so the hotbar and the rest don't have it. */
	public static boolean scroll(double amount) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || amount == 0.0 || minecraft.gui.screen() != null || !player.isUsingItem() || player.isSpectator()
			|| !(player.getUseItem().getItem() instanceof ProjectileWeaponItem)) {
			return false;
		}
		long now = System.currentTimeMillis();
		if (now - last >= REPEAT_MILLIS) {
			last = now;
			ClientPlayNetworking.send(new ArrowPick.Scroll(amount > 0.0 ? -1 : 1));
		}
		return true;
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || player.isSpectator()) {
			return;
		}
		ItemStack weapon = player.isUsingItem() ? player.getUseItem() : player.getMainHandItem();
		if (!(weapon.getItem() instanceof ProjectileWeaponItem)) {
			weapon = player.getOffhandItem();
		}
		ItemStackTemplate chosen = weapon.getItem() instanceof ProjectileWeaponItem ? weapon.get(ArrowPick.CHOSEN) : null;
		if (chosen == null) {
			return;
		}
		int[] at = HudZones.place(HudZones.DOCK_LEFT, "jaymospowers:arrow_pick", 10, SLOT, SLOT);
		if (at == null) {
			return;
		}
		ItemStack arrow = chosen.create();
		int count = ArrowPick.count(player, arrow);
		graphics.fill(at[0], at[1], at[0] + SLOT, at[1] + SLOT, HudStyle.PLATE);
		graphics.item(arrow, at[0] + 1, at[1] + 1);
		graphics.itemDecorations(minecraft.font, arrow.copyWithCount(Math.max(count, 1)), at[0] + 1, at[1] + 1, count > 0 ? null : "0");
	}
}
