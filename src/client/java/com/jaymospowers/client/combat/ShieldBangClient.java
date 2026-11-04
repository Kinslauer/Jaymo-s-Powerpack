package com.jaymospowers.client.combat;

import com.jaymospowers.combat.ShieldBang;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * (0.20.3) The shield bang on this side ({@link ShieldBang}): a left click while a shield is raised in the off hand and a
 * weapon is in the main hand. Vanilla eats attack clicks while you block, so the press is spotted from the key itself;
 * {@code HeldInput} keeps Better Combat from swinging on it instead.
 */
public final class ShieldBangClient {
	private static boolean attackWasDown;
	private static int wait;

	private ShieldBangClient() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(ShieldBangClient::tick);
	}

	private static void tick(Minecraft minecraft) {
		if (wait > 0) {
			wait--;
		}
		boolean down = minecraft.options.keyAttack.isDown();
		boolean pressed = down && !attackWasDown;
		attackWasDown = down;
		if (pressed && wait <= 0 && minecraft.gui.screen() == null && holdsAttack(minecraft)) {
			ClientPlayNetworking.send(ShieldBang.Bang.INSTANCE);
			wait = ShieldBang.COOLDOWN;
		}
	}

	/** Whether the attack key is the bang's right now (a shield up, a weapon out), not a swing's. */
	public static boolean holdsAttack(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		return player != null && ShieldBang.ready(player);
	}
}
