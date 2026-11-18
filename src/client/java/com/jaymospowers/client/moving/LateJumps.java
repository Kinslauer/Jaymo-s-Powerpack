package com.jaymospowers.client.moving;

import com.jaymospowers.client.freerun.FreerunClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Late jumps: a jump pressed just after walking off a ledge still works, and holding jump under a low ceiling keeps
 * jumping instead of waiting out vanilla's pause after each bump.
 */
public final class LateJumps {
	/** Ticks after leaving the ground that a fresh jump press still jumps; fewer in heavy armour. */
	static final int GRACE_TICKS = 4, HEAVY_GRACE_TICKS = 2;
	/** Armour points from which the armour counts as heavy. */
	static final int HEAVY_ARMOUR = 15;
	/** A head bump this recent, standing again with jump held, jumps at once. */
	static final int BUMP_MEMORY_TICKS = 12;
	/** Ticks between two of those repeated jumps. */
	static final int REPEAT_TICKS = 3;

	/** Ticks since the feet last stood on something (0 on the ground). */
	private static int sinceGround;
	private static boolean jumpWasDown;
	/** Ticks since the head last hit a ceiling in the air. */
	private static int sinceBump = Integer.MAX_VALUE / 2;
	private static int repeatWait;

	private LateJumps() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(LateJumps::tick);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			sinceGround = 0;
			return;
		}
		boolean jump = minecraft.options.keyJump.isDown() && minecraft.gui.screen() == null;
		boolean pressed = jump && !jumpWasDown;
		jumpWasDown = jump;
		sinceBump++;
		if (repeatWait > 0) {
			repeatWait--;
		}
		if (player.onGround()) {
			sinceGround = 0;
		} else {
			sinceGround++;
			if (player.verticalCollision && !player.verticalCollisionBelow) {
				sinceBump = 0;
			}
		}
		if (!jump || !free(player)) {
			return;
		}
		Vec3 motion = player.getDeltaMovement();
		int grace = player.getArmorValue() >= HEAVY_ARMOUR ? HEAVY_GRACE_TICKS : GRACE_TICKS;
		if (pressed && !player.onGround() && sinceGround <= grace && motion.y <= 0.0) {
			player.jumpFromGround();
			sinceGround = grace + 1;
		} else if (player.onGround() && sinceBump <= BUMP_MEMORY_TICKS && repeatWait == 0) {
			player.jumpFromGround();
			repeatWait = REPEAT_TICKS;
		}
	}

	/** Not swimming, climbing, riding, flying or in a move of their own. */
	private static boolean free(LocalPlayer player) {
		return !player.isPassenger() && !player.getAbilities().flying && !player.isInWater() && !player.isInLava()
			&& !player.onClimbable() && !player.isFallFlying() && !player.isSpectator() && !player.isSleeping()
			&& !FreerunClient.active();
	}
}
