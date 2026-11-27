package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebPose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

/**
 * Barrel rolls in a dive. Andy's pick from Legends: Superheroes, "diving after a release, hold use and left or right to
 * barrel-roll that way, steering the dive as you spin". After you let go of a web and are on the way down, holding the
 * use key with A or D lays the body out along the dive and rolls it about its own length ({@link WebPose#DIVE_ROLL},
 * sent again each turn to keep it going, drawn for everyone by {@link WebBodyPose}), and bends the dive's heading that
 * way, a little each tick, at the speed you were going: a spiral you steer with the roll. Let go of use, or of the
 * side, and it ends after the turn in hand.
 */
final class WebDiveRoll {
	// ===== feel: the knobs to tweak after playtesting ===============================================================
	/** Falling at least this fast (blocks/tick) to be in a dive. */
	static final double DIVE_VY = -0.12;
	/** The dive's heading turns this far each tick (radians) toward the side rolled. */
	static final double STEER = 0.075;
	/** One turn of the roll is sent again this many ticks after the last (the pose lasts a tick longer, so they run into each other). */
	static final int PERIOD = WebPose.ticks(WebPose.DIVE_ROLL) - 1;
	// ================================================================================================================

	private int side;
	private int nextAt;
	private boolean rolling;

	void reset() {
		rolling = false;
		side = 0;
	}

	boolean rolling() {
		return rolling;
	}

	/** Every tick after the swing has moved you. */
	void tick(Minecraft minecraft, LocalPlayer player, WebSwing swing) {
		Input input = player.input.keyPresses;
		int want = input.left() == input.right() ? 0 : input.right() ? 1 : -1;
		boolean diving = swing.flying && !swing.attached() && swing.pull == null && swing.wingsTicks == 0 && !player.onGround() && !player.isInWater()
			&& !player.isInLava() && !player.onClimbable() && !player.isFallFlying() && !player.isPassenger() && !WebSymbioteClient.driving()
			&& !WebSymbioteWingsClient.flying(player) && !swing.lineRide.active() && player.getDeltaMovement().y <= DIVE_VY;
		boolean held = minecraft.options.keyUse.isDown() && minecraft.gui.screen() == null;
		WebTrickClient tricks = swing.tricks();
		if (!diving || !held || want == 0) {
			rolling = false;
			side = 0;
			return;
		}
		boolean fresh = !rolling || want != side;
		if (fresh && !tricks.mayInterrupt(player)) {
			return;
		}
		if (fresh || player.tickCount >= nextAt) {
			// the side is the way the roll goes; a roll that carries on is sent with a side of 2 so its pose doesn't ease in again
			tricks.sendMove(WebPose.DIVE_ROLL, fresh ? want : want * 2);
			nextAt = player.tickCount + PERIOD;
			if (fresh) {
				WebSwing.play(player, SoundEvents.PLAYER_ATTACK_SWEEP, 0.5F, 1.7F);
			}
		}
		rolling = true;
		side = want;
		tricks.busy(player, 3);
		// the dive steers toward the side rolled (the right of heading (x, z) is (-z, x))
		Vec3 v = player.getDeltaMovement();
		double a = STEER * want;
		double cos = Math.cos(a), sin = Math.sin(a);
		player.setDeltaMovement(v.x * cos - v.z * sin, v.y, v.z * cos + v.x * sin);
	}
}
