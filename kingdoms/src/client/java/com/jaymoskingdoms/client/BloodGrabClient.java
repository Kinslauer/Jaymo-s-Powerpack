package com.jaymoskingdoms.client;

import com.jaymoskingdoms.BloodGrab;
import com.jaymoskingdoms.NightGifts;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

/**
 * (0.7.614) The grab-drink's keys ({@link BloodGrab}): two taps of use on the same prey in quick succession
 * ({@link BloodGrab#DOUBLE_TAP_TICKS}) by a vampire with an empty hand grabs it; letting go of use while holding it throws
 * it. The use key itself is left alone (the first tap is the usual bite); the server decides who can be grabbed.
 */
public final class BloodGrabClient {
	private static boolean wasDown, released;
	private static long lastTap = Long.MIN_VALUE;
	private static int lastTarget = -1;

	private BloodGrabClient() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(BloodGrabClient::tick);
	}

	private static void tick(Minecraft minecraft) {
		if (minecraft.player == null || minecraft.level == null) {
			wasDown = false;
			return;
		}
		boolean down = minecraft.options.keyUse.isDown();
		boolean pressed = down && !wasDown;
		wasDown = down;
		if (minecraft.player.hasAttached(BloodGrab.GRABBING)) {
			// let go of use: the throw (once)
			if (!down && !released && ClientPlayNetworking.canSend(BloodGrab.Release.TYPE)) {
				ClientPlayNetworking.send(new BloodGrab.Release());
				released = true;
			}
			return;
		}
		released = false;
		if (!pressed || minecraft.gui.screen() != null || !minecraft.player.getMainHandItem().isEmpty()) {
			return;
		}
		NightGifts.Gift gift = NightGifts.gift(minecraft.player);
		if (gift == null || !gift.blood() || !(minecraft.crosshairPickEntity instanceof LivingEntity target)) {
			lastTap = Long.MIN_VALUE;
			return;
		}
		long now = minecraft.level.getGameTime();
		if (target.getId() == lastTarget && now - lastTap <= BloodGrab.DOUBLE_TAP_TICKS) {
			if (ClientPlayNetworking.canSend(BloodGrab.Start.TYPE)) {
				ClientPlayNetworking.send(new BloodGrab.Start(target.getId()));
			}
			lastTap = Long.MIN_VALUE;
			return;
		}
		lastTap = now;
		lastTarget = target.getId();
	}
}
