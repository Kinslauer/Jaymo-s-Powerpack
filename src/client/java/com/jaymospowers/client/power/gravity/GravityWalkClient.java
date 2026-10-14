package com.jaymospowers.client.power.gravity;

import com.jaymospowers.power.gravity.GravityPower;
import com.jaymospowers.power.gravity.GravityWalk;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The local player's wall walk (see {@link GravityWalk} for the rules and numbers). Sneak while facing a wall you're
 * against, or with your head against a ceiling, and you stand on it; W/A/S/D walk you across it relative to where
 * you look (look up a wall and W climbs it), Jump pushes off, Sneak again lets go. Walking off the top of a wall steps
 * you up onto the ground above it; walking off any other edge, you fall.
 */
final class GravityWalkClient {
	/** How far a surface is felt: to step onto it, and to stay on it. */
	private static final double REACH_START = 0.12, REACH_WALK = 0.2;
	/** Facing a wall at least this squarely (look · its side) to step onto it. */
	private static final double FACING = 0.35;

	private static @Nullable Direction face;
	private static boolean sneakWasDown, jumpWasDown;
	/** Ticks on the surface (the server's word, its attachment, may take a few to arrive). */
	private static int ticks;

	private GravityWalkClient() {
	}

	static void tick(LocalPlayer player) {
		Input input = player.input.keyPresses;
		boolean sneakPressed = input.shift() && !sneakWasDown, jumpPressed = input.jump() && !jumpWasDown;
		sneakWasDown = input.shift();
		jumpWasDown = input.jump();
		boolean free = player.isAlive() && !player.isSpectator() && !player.isPassenger() && !player.isInWater() && !player.isInLava()
			&& !player.isFallFlying() && !player.getAbilities().flying && player.getAttached(GravityPower.ROCK_FLIGHT) == null
			&& player.getAttached(com.jaymospowers.power.gravity.GravityForward.FALLING) == null && GravityWalk.allowed(player);
		if (face == null) {
			if (free && sneakPressed) {
				Direction surface = find(player);
				if (surface != null) {
					face = surface;
					ticks = 0;
					ClientPlayNetworking.send(new GravityWalk.Walk(surface.get3DDataValue()));
				}
			}
			return;
		}
		Vec3 n = new Vec3(-face.getStepX(), -face.getStepY(), -face.getStepZ());
		Vec3 into = n.scale(-1.0);
		if (++ticks > 10 && player.getAttached(GravityWalk.SURFACE) == null) {
			// the server let you go (no surface there, by its reckoning)
			face = null;
			return;
		}
		if (!free || sneakPressed) {
			stop();
			return;
		}
		if (jumpPressed) {
			player.setDeltaMovement(n.scale(GravityWalk.JUMP_OUT).add(0.0, face == Direction.UP ? -GravityWalk.JUMP_UP : GravityWalk.JUMP_UP, 0.0));
			stop();
			return;
		}
		if (!GravityWalk.surfaceAt(player.level(), player, face, REACH_WALK)) {
			// off the edge: over the top of a wall you step up onto it; anywhere else you fall
			if (face != Direction.UP && player.getY() - player.yo > 0.01) {
				player.setDeltaMovement(into.scale(GravityWalk.MANTLE_IN).add(0.0, GravityWalk.MANTLE_UP, 0.0));
			}
			stop();
			return;
		}
		// the way you look, laid flat on the surface; W/S along it, A/D across it
		Vec3 look = player.getLookAngle();
		Vec3 forward = flatten(look, n);
		if (forward.lengthSqr() < 0.01) {
			forward = face == Direction.UP ? flatten(look.horizontal(), n) : new Vec3(0.0, 1.0, 0.0);
		}
		forward = forward.normalize();
		Vec3 right = forward.cross(n).normalize();
		// A and D go the way they look on screen (the view stays upright)
		Vec3 screenRight = new Vec3(-look.z, 0.0, look.x);
		if (right.dot(screenRight) < 0.0) {
			right = right.scale(-1.0);
		}
		double f = (input.forward() ? 1.0 : 0.0) - (input.backward() ? 1.0 : 0.0);
		double s = (input.right() ? 1.0 : 0.0) - (input.left() ? 1.0 : 0.0);
		Vec3 move = forward.scale(f).add(right.scale(s));
		if (move.lengthSqr() > 1.0) {
			move = move.normalize();
		}
		double speed = player.isSprinting() || input.sprint() ? GravityWalk.SPRINT_SPEED : GravityWalk.SPEED;
		// (still crouched from the sneak that stepped on: hang just under a ceiling until you can stand up against it)
		double stick = face == Direction.UP && player.isCrouching() ? 0.0 : GravityWalk.STICK;
		player.setDeltaMovement(move.scale(speed).add(into.scale(stick)));
		player.resetFallDistance();
		if (move.lengthSqr() > 0.0 && player.tickCount % 5 == 0) {
			Vec3 at = player.position().add(0.0, face == Direction.UP ? player.getBbHeight() : player.getBbHeight() * 0.5, 0.0).add(into.scale(0.35));
			player.level().addParticle(ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z, n.x * 0.02, n.y * 0.02, n.z * 0.02);
		}
	}

	/** {@code v} with its part along {@code n} taken out. */
	private static Vec3 flatten(Vec3 v, Vec3 n) {
		return v.subtract(n.scale(v.dot(n)));
	}

	/** The surface to step onto: a ceiling your head is against (looking up, or off the ground), else the wall you face. */
	private static @Nullable Direction find(LocalPlayer player) {
		boolean ceiling = GravityWalk.surfaceAt(player.level(), player, Direction.UP, REACH_START);
		if (ceiling && (!player.onGround() || player.getXRot() < -30.0F)) {
			return Direction.UP;
		}
		Vec3 look = player.getLookAngle().horizontal();
		if (look.lengthSqr() < 1.0E-4) {
			return ceiling ? Direction.UP : null;
		}
		look = look.normalize();
		Direction best = null;
		double bestDot = FACING;
		for (Direction side : Direction.Plane.HORIZONTAL) {
			double dot = look.dot(new Vec3(side.getStepX(), 0.0, side.getStepZ()));
			if (dot > bestDot && GravityWalk.surfaceAt(player.level(), player, side, REACH_START)) {
				best = side;
				bestDot = dot;
			}
		}
		return best != null ? best : ceiling ? Direction.UP : null;
	}

	static void stop() {
		if (face != null) {
			face = null;
			if (ClientPlayNetworking.canSend(GravityWalk.Walk.TYPE)) {
				ClientPlayNetworking.send(new GravityWalk.Walk(-1));
			}
		}
	}

	/** Disconnected, or the world changed: forget the surface without telling anyone. */
	static void reset() {
		face = null;
	}
}
