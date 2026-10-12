package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebPose;
import com.jaymospowers.power.web.WebPower;
import com.jaymospowers.power.web.WebTricks;
import com.jaymospowers.power.web.WebWallRun;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The local player's wall run (see {@link WebWallRun} for the numbers): in the air, holding forward, moving fast along a
 * wall at your side, you run along it; Jump kicks off it, up and away from the wall. Runs after {@link WebSwing} each
 * tick and only takes over while you're on the wall, so a web, a zip or the wings always win.
 */
final class WebWallRunClient {
	/** How far beside the body (blocks) a wall is felt: to step onto it, and to stay on it. */
	private static final double REACH_ENTRY = 0.35, REACH_RUN = 0.3;
	/** Pressed into the wall this hard each tick, so the run hugs it. */
	private static final double HUG = 0.06;
	/** The chance a kick-off throws a cartwheel. */
	private static final double KICK_TRICK_CHANCE = 0.5;

	/** The wall's side while running along it (null off the wall), and which way along it you run. */
	private @Nullable Direction face;
	private Vec3 along = Vec3.ZERO;
	private int ticks;
	private int maxTicks;
	/** The run's own speed along the wall and up (vanilla's air drag and gravity don't apply on the wall). */
	private double speed, up;
	/** The wall face last run on: no running it again until you land (another wall is fine, so walls can chain). */
	private @Nullable Direction lastFace;
	private boolean jumpWasDown;

	void reset() {
		stop();
		lastFace = null;
	}

	boolean running() {
		return face != null;
	}

	void tick(LocalPlayer player, WebSwing swing) {
		Input input = player.input.keyPresses;
		boolean jump = input.jump();
		boolean jumpPressed = jump && !jumpWasDown;
		jumpWasDown = jump;
		if (player.onGround() || player.isInWater() || player.isInLava() || player.onClimbable()) {
			lastFace = null;
		}
		boolean free = !player.onGround() && !player.isInWater() && !player.isInLava() && !player.onClimbable() && !player.isFallFlying()
			&& !player.getAbilities().flying && !player.isPassenger() && !player.isSpectator() && player.isAlive()
			&& !swing.attached() && swing.pull == null && swing.wingsTicks == 0 && !WebSymbioteClient.driving()
			&& !WebPower.ragdolled(player);
		if (face == null) {
			if (free && input.forward() && !input.shift()) {
				tryStart(player);
			}
			return;
		}
		if (!free || !input.forward() || input.shift() || ++ticks > maxTicks || !WebWallRun.wallAt(player.level(), player, face, REACH_RUN)) {
			// off the wall: the run's speed carries on into the air
			stop();
			return;
		}
		if (jumpPressed) {
			kick(player);
			return;
		}
		Vec3 moved = new Vec3(player.getX() - player.xo, 0.0, player.getZ() - player.zo);
		if (ticks > 1 && moved.dot(along) < speed * 0.3) {
			// ran into something ahead
			stop();
			return;
		}
		up = Math.max(-WebWallRun.RUN_FALL_MAX, up - WebWallRun.RUN_GRAVITY);
		Vec3 into = new Vec3(face.getStepX(), 0.0, face.getStepZ()).scale(HUG);
		player.setDeltaMovement(along.scale(speed).add(into).add(0.0, up, 0.0));
		if (ticks % 3 == 0) {
			Vec3 at = player.position().add(face.getStepX() * 0.35, 0.2, face.getStepZ() * 0.35);
			player.level().addParticle(ParticleTypes.CLOUD, at.x, at.y, at.z, -along.x * 0.05, 0.01, -along.z * 0.05);
		}
	}

	/** Step onto a wall at your side, if you're moving along it fast enough. */
	private void tryStart(LocalPlayer player) {
		Vec3 v = player.getDeltaMovement();
		Vec3 flat = v.horizontal();
		if (flat.length() < WebWallRun.ENTRY_SPEED || v.y < -WebWallRun.ENTRY_FALL_MAX) {
			return;
		}
		Vec3 heading = flat.normalize();
		Direction best = null;
		double bestInto = WebWallRun.ENTRY_INTO;
		for (Direction side : Direction.Plane.HORIZONTAL) {
			if (side == lastFace) {
				continue;
			}
			double into = heading.dot(new Vec3(side.getStepX(), 0.0, side.getStepZ()));
			// beside you, not behind: you may angle in towards it, never away
			if (Math.abs(into) <= bestInto && into > -0.3 && WebWallRun.wallAt(player.level(), player, side, REACH_ENTRY)) {
				best = side;
				bestInto = Math.abs(into);
			}
		}
		if (best == null) {
			return;
		}
		Vec3 tangent = new Vec3(-best.getStepZ(), 0.0, best.getStepX());
		along = heading.dot(tangent) >= 0.0 ? tangent : tangent.scale(-1.0);
		face = best;
		ticks = 0;
		maxTicks = WebWallRun.RUN_TICKS[WebWallRun.tier(player)];
		speed = Math.min(WebWallRun.RUN_SPEED_MAX, Math.max(WebWallRun.RUN_SPEED_MIN, flat.length()));
		up = Math.min(WebWallRun.RUN_LIFT_MAX, Math.max(v.y, WebWallRun.RUN_LIFT));
		player.setDeltaMovement(along.scale(speed).add(0.0, up, 0.0));
		ClientPlayNetworking.send(new WebWallRun.Run(face.get3DDataValue()));
		WebSwing.play(player, SoundEvents.PLAYER_ATTACK_NODAMAGE, 0.4F, 1.6F);
	}

	/** Jump off the wall: up (higher with mastery, highest ascended), out from it, still carrying the run's speed. */
	private void kick(LocalPlayer player) {
		Direction wall = face;
		Vec3 out = new Vec3(-wall.getStepX(), 0.0, -wall.getStepZ()).scale(WebWallRun.JUMP_OUT);
		double keep = speed * WebWallRun.JUMP_KEEP;
		int tier = WebWallRun.tier(player);
		player.setDeltaMovement(along.scale(keep).add(out).add(0.0, WebWallRun.JUMP_UP[tier], 0.0));
		stop();
		lastFace = wall;
		WebSwing.play(player, SoundEvents.SLIME_JUMP, 0.7F, 1.3F);
		if (tier == WebWallRun.JUMP_UP.length - 1) {
			WebSwing.play(player, SoundEvents.WIND_CHARGE_BURST.value(), 0.5F, 1.2F);
		}
		Vec3 at = player.position().add(wall.getStepX() * 0.4, 0.3, wall.getStepZ() * 0.4);
		for (int i = 0; i < 5; i++) {
			player.level().addParticle(ParticleTypes.CLOUD, at.x, at.y, at.z, out.x * 0.1, 0.02, out.z * 0.1);
		}
		if (player.getRandom().nextDouble() < KICK_TRICK_CHANCE) {
			// turn the cartwheel away from the wall
			Vec3 right = along.cross(new Vec3(0.0, 1.0, 0.0));
			int side = right.dot(out) > 0.0 ? 1 : -1;
			ClientPlayNetworking.send(new WebTricks.Trick(WebPose.CARTWHEEL, side));
		}
	}

	private void stop() {
		if (face != null) {
			face = null;
			if (ClientPlayNetworking.canSend(WebWallRun.Run.TYPE)) {
				ClientPlayNetworking.send(new WebWallRun.Run(-1));
			}
		}
	}
}
