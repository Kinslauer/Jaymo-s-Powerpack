package com.jaymospowers.client.combat;

import com.jaymospowers.combat.ShieldBang;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * (0.20.3) The shield bang and bash on this side ({@link ShieldBang}): the attack key while a shield is raised in the off
 * hand and a weapon is in the main hand. Vanilla eats attack clicks while you block, so the key is read directly. A short
 * click bangs; held past {@link ShieldBang#BASH_MIN} ticks a charge bar fills (the usual {@code |||...} bar, so
 * {@code HeldInput} keeps every other swing off it), and letting go steps you in and throws the bash.
 */
public final class ShieldBangClient {
	/** The bar's length (characters), and how hard the step in is at no charge and full (blocks a tick). */
	static final int BAR = 12;
	static final double STEP_MIN = 0.25, STEP_MAX = 0.6;

	private static boolean holding;
	private static int held, wait;

	private ShieldBangClient() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(ShieldBangClient::tick);
	}

	private static void tick(Minecraft minecraft) {
		if (wait > 0) {
			wait--;
		}
		LocalPlayer player = minecraft.player;
		boolean down = minecraft.options.keyAttack.isDown();
		boolean ready = player != null && minecraft.gui.screen() == null && holdsAttack(minecraft);
		if (!ready) {
			// the shield came down (or a screen opened): the hold is off
			holding = false;
			return;
		}
		if (down && !holding) {
			holding = wait <= 0;
			held = 0;
			return;
		}
		if (!holding) {
			return;
		}
		if (down) {
			held++;
			if (held == ShieldBang.BASH_MIN) {
				ClientPlayNetworking.send(new ShieldBang.Bash(true));
			}
			if (held >= ShieldBang.BASH_MIN) {
				int filled = Math.round(BAR * Math.min(1.0F, charge()));
				player.sendOverlayMessage(Component.literal("|".repeat(filled) + ".".repeat(BAR - filled)));
			}
			return;
		}
		// let go
		holding = false;
		wait = ShieldBang.COOLDOWN;
		if (held < ShieldBang.BASH_MIN) {
			ClientPlayNetworking.send(ShieldBang.Bang.INSTANCE);
			return;
		}
		ClientPlayNetworking.send(new ShieldBang.Bash(false));
		player.sendOverlayMessage(Component.empty());
		if (player.onGround() && !player.isPassenger()) {
			double step = STEP_MIN + (STEP_MAX - STEP_MIN) * Math.min(1.0F, charge());
			Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot());
			player.setDeltaMovement(look.x * step, player.getDeltaMovement().y, look.z * step);
		}
	}

	private static float charge() {
		return (held - ShieldBang.BASH_MIN) / (float) (ShieldBang.BASH_FULL - ShieldBang.BASH_MIN);
	}

	/** Whether the attack key is the bang's right now (a shield up, a weapon out), not a swing's. */
	public static boolean holdsAttack(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		return player != null && !player.isShiftKeyDown() && ShieldBang.ready(player);
	}
}
