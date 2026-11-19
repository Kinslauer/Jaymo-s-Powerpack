package com.jaymospowers.client.alchemy;

import com.jaymospowers.alchemy.picks.MettleDraught;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The Draught of Mettle's one more jump in the air ({@link MettleDraught}): while it works, a fresh press of Jump with
 * nothing underfoot, after leaving the ground, kicks upward once; touching the ground (or water, or a ladder) gives it
 * back. Kept to this one class, apart from any powers' own jumps, so a shared double-jump system can take it over.
 */
public final class MettleJump {
	/** Upward speed of the extra jump (blocks per tick; a plain jump is 0.42). */
	private static final double LIFT = 0.5;

	private static boolean spent;
	private static boolean jumpWasDown;

	private MettleJump() {
	}

	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(MettleJump::tick);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			spent = false;
			jumpWasDown = false;
			return;
		}
		boolean down = minecraft.options.keyJump.isDown();
		boolean pressed = down && !jumpWasDown;
		jumpWasDown = down;
		if (player.onGround() || player.isInWater() || player.onClimbable() || player.getAbilities().flying || player.isPassenger()) {
			spent = false;
			return;
		}
		if (pressed && !spent && minecraft.gui.screen() == null && !player.isFallFlying() && MettleDraught.active(player)) {
			spent = true;
			Vec3 motion = player.getDeltaMovement();
			player.setDeltaMovement(motion.x, LIFT, motion.z);
		}
	}
}
