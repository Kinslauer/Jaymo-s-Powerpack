package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebParkour;
import com.jaymospowers.power.web.WebPose;
import com.jaymospowers.power.web.WebPower;
import com.jaymospowers.power.web.WebSymbiote;
import com.jaymospowers.power.web.WebSymbioteState;
import com.jaymospowers.power.web.WebWallRun;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The local player's parkour (see {@link WebParkour} for the moves and the numbers). Runs after {@link WebSwing} and
 * {@link WebWallRunClient} each tick and stays out of the way of a web, a zip, the wings, a wall run and the symbiote's
 * own wall crawl.
 */
final class WebParkourClient {
	/** How far from the body (blocks) a wall is felt for a cling or a wall jump. */
	private static final double REACH = 0.35;
	/** Pressed into the wall this hard each tick while clinging, so you stay on it. */
	private static final double HUG = 0.02;
	/** Moving at least this fast across the ground (blocks/tick) to vault or slide. */
	private static final double MOVE_MIN = 0.15;
	/** The flips a jump out of a slide turns into, one at random. */
	private static final int[] SLIDE_FLIPS = {WebPose.FRONT_FLIP, WebPose.CORKSCREW, WebPose.CARTWHEEL};

	private int kind = WebParkour.NONE;
	/** The wall's side for a cling (null otherwise), and the slide's heading and speed. */
	private @Nullable Direction wall;
	private Vec3 heading = Vec3.ZERO;
	private double speed;
	private int ticks, cooldown;
	/** The wall face last kicked off: not again until you land (a different wall is fine, so a chimney can be climbed). */
	private @Nullable Direction lastKick;
	private boolean jumpWasDown, shiftWasDown, wasSprinting;

	void reset() {
		stop();
		lastKick = null;
		cooldown = 0;
	}

	boolean active() {
		return kind != WebParkour.NONE;
	}

	void tick(LocalPlayer player, WebSwing swing, WebWallRunClient wallRun) {
		Input input = player.input.keyPresses;
		boolean jumpPressed = input.jump() && !jumpWasDown;
		boolean shiftPressed = input.shift() && !shiftWasDown;
		jumpWasDown = input.jump();
		shiftWasDown = input.shift();
		boolean sprinting = player.isSprinting() || wasSprinting;
		wasSprinting = player.isSprinting();
		if (cooldown > 0) {
			cooldown--;
		}
		if (player.onGround() || player.isInWater() || player.isInLava() || player.onClimbable()) {
			lastKick = null;
		}
		boolean free = player.isAlive() && !player.isSpectator() && !player.isPassenger() && !player.getAbilities().flying && !player.isFallFlying()
			&& !player.isInWater() && !player.isInLava() && !player.onClimbable() && !swing.attached() && swing.pull == null && swing.wingsTicks == 0
			&& !wallRun.running() && !WebSymbioteClient.driving() && !WebPower.ragdolled(player)
			&& !WebSymbiote.isToggled(player, WebSymbioteState.CRAWL);
		if (!free) {
			if (kind == WebParkour.CLING || kind == WebParkour.SLIDE) {
				stop();
			}
			return;
		}
		switch (kind) {
			case WebParkour.CLING -> tickCling(player, input, jumpPressed);
			case WebParkour.SLIDE -> tickSlide(player, input, jumpPressed);
			case WebParkour.VAULT, WebParkour.WALL_JUMP -> {
				// the pose plays out on the server's clock; here the move is done once it has its push
				if (++ticks > 2) {
					kind = WebParkour.NONE;
				}
			}
			default -> {
				if (player.onGround()) {
					if (sprinting && input.forward() && cooldown == 0 && shiftPressed) {
						trySlide(player);
					} else if (sprinting && input.forward() && !input.shift() && cooldown == 0) {
						tryVault(player);
					}
				} else if (jumpPressed) {
					tryWallJump(player, input, null);
				} else if (input.shift()) {
					tryCling(player);
				}
			}
		}
	}

	// ---- vault -------------------------------------------------------------------------------------------------------

	/** Sprinting into a one-block obstacle with room above it: a hand on it and the legs swung over. */
	private void tryVault(LocalPlayer player) {
		// (running into it stops you dead, so it's the bump that counts, not the speed)
		if (!player.horizontalCollision) {
			return;
		}
		Vec3 flat = player.getDeltaMovement().horizontal();
		Vec3 dir = Vec3.directionFromRotation(0.0F, player.getYRot());
		AABB box = player.getBoundingBox();
		AABB knee = new AABB(box.minX, box.minY + 0.2, box.minZ, box.maxX, box.minY + 0.95, box.maxZ).move(dir.x * 0.4, 0.0, dir.z * 0.4);
		AABB over = box.move(dir.x * 0.9, 1.05, dir.z * 0.9);
		AABB rise = box.move(0.0, 1.05, 0.0);
		if (player.level().noCollision(player, knee) || !player.level().noCollision(player, over) || !player.level().noCollision(player, rise)) {
			return;
		}
		double forward = Math.max(WebParkour.VAULT_FORWARD, flat.length());
		player.setDeltaMovement(dir.scale(forward).add(0.0, WebParkour.VAULT_UP, 0.0));
		start(player, WebParkour.VAULT, null);
		cooldown = WebParkour.VAULT_COOLDOWN;
	}

	// ---- cling and wall jump ----------------------------------------------------------------------------------------

	/** In the air, Sneak against a wall in front: hang on it. */
	private void tryCling(LocalPlayer player) {
		Direction facing = player.getDirection();
		if (player.getDeltaMovement().y > 0.3 || !WebParkour.canCling(player) || !WebWallRun.wallAt(player.level(), player, facing, REACH)) {
			return;
		}
		player.setDeltaMovement(Vec3.ZERO);
		start(player, WebParkour.CLING, facing);
		WebSwing.play(player, SoundEvents.SPIDER_STEP, 0.6F, 1.3F);
	}

	private void tickCling(LocalPlayer player, Input input, boolean jumpPressed) {
		Direction face = wall;
		if (face != null && input.forward() && input.shift() && !jumpPressed && !player.onGround()
			&& !WebWallRun.wallAt(player.level(), player, face, REACH) && player.level().noCollision(player, player.getBoundingBox().move(0.0, 0.6, 0.0))) {
			// climbed to the top of the wall: pull up and over it
			player.setDeltaMovement(new Vec3(face.getStepX(), 0.0, face.getStepZ()).scale(WebParkour.MANTLE_IN).add(0.0, WebParkour.MANTLE_UP, 0.0));
			player.resetFallDistance();
			stop();
			WebSwing.play(player, SoundEvents.SPIDER_STEP, 0.6F, 0.9F);
			return;
		}
		if (face == null || player.onGround() || !input.shift() && !jumpPressed || ++ticks > WebParkour.CLING_MAX_TICKS
			|| !WebWallRun.wallAt(player.level(), player, face, REACH)) {
			stop();
			return;
		}
		if (jumpPressed) {
			tryWallJump(player, input, face);
			return;
		}
		// shimmy along the wall with A/D; after the hold you start to slip
		Direction left = face.getCounterClockWise();
		double strafe = (input.left() ? 1.0 : 0.0) - (input.right() ? 1.0 : 0.0);
		// W climbs; the suit's tendrils never slip, fists do after the hold
		int hold = WebParkour.CLING_HOLD_TICKS[WebWallRun.tier(player)];
		boolean tendrils = WebSymbiote.suitShown(player);
		double down = input.forward() ? WebParkour.CLING_CLIMB : ticks > hold && !tendrils ? -WebParkour.CLING_SLIP : 0.0;
		Vec3 into = new Vec3(face.getStepX(), 0.0, face.getStepZ()).scale(HUG);
		Vec3 side = new Vec3(left.getStepX(), 0.0, left.getStepZ()).scale(strafe * WebParkour.CLING_SHIMMY);
		player.setDeltaMovement(side.add(into).add(0.0, down, 0.0));
		player.resetFallDistance();
		if (down < 0.0 && ticks % 4 == 0) {
			Vec3 at = player.position().add(face.getStepX() * 0.35, 1.2, face.getStepZ() * 0.35);
			player.level().addParticle(ParticleTypes.CLOUD, at.x, at.y, at.z, 0.0, 0.02, 0.0);
		}
	}

	/**
	 * Jump off a wall: the one you cling to ({@code from}), else one beside or in front (not the one last kicked off).
	 * From a cling, holding forward climbs: more up, less out.
	 */
	private void tryWallJump(LocalPlayer player, Input input, @Nullable Direction from) {
		Direction face = from;
		if (face == null) {
			Direction facing = player.getDirection();
			if (facing != lastKick && WebWallRun.wallAt(player.level(), player, facing, REACH)) {
				face = facing;
			} else {
				for (Direction side : Direction.Plane.HORIZONTAL) {
					if (side != lastKick && WebWallRun.wallAt(player.level(), player, side, REACH)) {
						face = side;
						break;
					}
				}
			}
		}
		if (face == null || face == lastKick && from == null) {
			return;
		}
		boolean climb = from != null && input.forward();
		int tier = WebWallRun.tier(player);
		Vec3 out = new Vec3(-face.getStepX(), 0.0, -face.getStepZ()).scale(climb ? WebParkour.CLIMB_OUT : WebParkour.WALL_JUMP_OUT);
		Vec3 normal = new Vec3(face.getStepX(), 0.0, face.getStepZ());
		Vec3 flat = player.getDeltaMovement().horizontal();
		Vec3 along = flat.subtract(normal.scale(flat.dot(normal))).scale(WebParkour.WALL_JUMP_KEEP);
		double up = WebParkour.WALL_JUMP_UP[tier] + (climb ? WebParkour.CLIMB_UP_BONUS : 0.0);
		player.setDeltaMovement(along.add(out).add(0.0, up, 0.0));
		player.resetFallDistance();
		lastKick = face;
		kind = WebParkour.NONE;
		start(player, WebParkour.WALL_JUMP, face);
		WebSwing.play(player, SoundEvents.SLIME_JUMP, 0.7F, 1.4F);
		Vec3 at = player.position().add(face.getStepX() * 0.4, 0.6, face.getStepZ() * 0.4);
		for (int i = 0; i < 5; i++) {
			player.level().addParticle(ParticleTypes.CLOUD, at.x, at.y, at.z, out.x * 0.1, 0.02, out.z * 0.1);
		}
	}

	// ---- slide -------------------------------------------------------------------------------------------------------

	/** Sprinting, Sneak: drop into a slide. */
	private void trySlide(LocalPlayer player) {
		Vec3 flat = player.getDeltaMovement().horizontal();
		if (flat.length() < MOVE_MIN) {
			return;
		}
		heading = flat.normalize();
		speed = Math.min(0.75, Math.max(WebParkour.SLIDE_MIN, flat.length() * WebParkour.SLIDE_BOOST));
		player.setDeltaMovement(heading.scale(speed).add(0.0, player.getDeltaMovement().y, 0.0));
		start(player, WebParkour.SLIDE, null);
	}

	private void tickSlide(LocalPlayer player, Input input, boolean jumpPressed) {
		Vec3 moved = new Vec3(player.getX() - player.xo, 0.0, player.getZ() - player.zo);
		speed *= WebParkour.SLIDE_KEEP;
		boolean blocked = ticks > 1 && moved.dot(heading) < speed * 0.3;
		if (jumpPressed || !player.onGround() && input.jump() && player.getDeltaMovement().y > 0.0) {
			// a jump out of it: a leap higher and further than a plain jump, the slide's speed and then some
			double out = Math.min(WebParkour.SLIDE_JUMP_MAX, speed * WebParkour.SLIDE_JUMP_KEEP + WebParkour.SLIDE_JUMP_ADD);
			player.setDeltaMovement(heading.scale(out).add(0.0, Math.max(player.getDeltaMovement().y, WebParkour.SLIDE_JUMP_UP), 0.0));
			stop();
			cooldown = WebParkour.SLIDE_COOLDOWN;
			// and flips out of it (Andy: every slide)
			ClientPlayNetworking.send(new com.jaymospowers.power.web.WebTricks.Trick(
				SLIDE_FLIPS[player.getRandom().nextInt(SLIDE_FLIPS.length)], player.getRandom().nextBoolean() ? 1 : -1));
			WebSwing.play(player, SoundEvents.SLIME_JUMP, 0.7F, 1.2F);
			for (int i = 0; i < 6; i++) {
				player.level().addParticle(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1, player.getZ(), -heading.x * 0.12, 0.04, -heading.z * 0.12);
			}
			return;
		}
		if (!player.onGround() || jumpPressed || !input.shift() || ++ticks > WebParkour.SLIDE_TICKS || speed < WebParkour.SLIDE_STOP || blocked) {
			// a drop off a ledge keeps the speed: vanilla carries it from here
			stop();
			cooldown = WebParkour.SLIDE_COOLDOWN;
			return;
		}
		player.setDeltaMovement(heading.scale(speed).add(0.0, player.getDeltaMovement().y, 0.0));
		if (ticks % 2 == 0) {
			player.level().addParticle(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1, player.getZ(), -heading.x * 0.05, 0.02, -heading.z * 0.05);
		}
	}

	// ------------------------------------------------------------------------------------------------------------------

	private void start(LocalPlayer player, int move, @Nullable Direction face) {
		kind = move;
		wall = face;
		ticks = 0;
		ClientPlayNetworking.send(new WebParkour.Move(move, face == null ? -1 : face.get3DDataValue()));
	}

	private void stop() {
		if (kind != WebParkour.NONE) {
			boolean held = kind == WebParkour.CLING || kind == WebParkour.SLIDE;
			kind = WebParkour.NONE;
			wall = null;
			if (held && ClientPlayNetworking.canSend(WebParkour.Move.TYPE)) {
				ClientPlayNetworking.send(new WebParkour.Move(WebParkour.NONE, -1));
			}
		}
	}
}
