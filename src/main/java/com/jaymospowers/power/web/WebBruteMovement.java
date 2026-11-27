package com.jaymospowers.power.web;

import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.wounds.Tripped;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The big suit's leap, climb and unarmed attack rhythm. Inputs come from the movement and attack packets. */
public final class WebBruteMovement {
	private static final double LEAP_RANGE = 40.0;
	private static final int FLIGHT_LIMIT = 64, CLIMB_BEAT = 14;
	private static final Map<UUID, Motion> MOTION = new HashMap<>();

	private static final class Motion {
		final ServerLevel level;
		int charge, hooks, climbTicks;
		boolean wasGround, jump, aimed, airborne;
		long launch, wallGrace, hookAt, hopAt;
		Vec3 wall = Vec3.ZERO;
		double peak;

		Motion(ServerPlayer player) {
			level = player.level();
			wasGround = player.onGround();
		}
	}

	private WebBruteMovement() {
	}

	static void register() {
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!(player instanceof ServerPlayer server) || !(entity instanceof LivingEntity foe)
				|| !WebBruteArts.able(server) || !player.getMainHandItem().isEmpty()
				|| WebHeldGrab.holding(player) || player.getAttached(WebTendrils.GRIPS) != null
				|| !foe.isAlive() || foe.isSpectator() || player.isAlliedTo(foe)
				|| !player.isWithinEntityInteractionRange(foe, 0.0) || !player.hasLineOfSight(foe)) {
				return InteractionResult.PASS;
			}
			if (WebBruteArts.busy(server)) {
				return InteractionResult.FAIL;
			}
			Motion motion = motion(server);
			long now = level.getGameTime();
			if (now < motion.hookAt) {
				return InteractionResult.FAIL;
			}
			boolean lunge = ++motion.hooks % 4 == 0;
			motion.hookAt = now + (lunge ? 17 : 12);
			WebBruteArts.pose(server, lunge ? WebPose.BRUTE_HOOK_LUNGE : WebPose.BRUTE_HOOK, motion.hooks % 2 == 0 ? -1 : 1);
			if (lunge) {
				Vec3 step = foe.position().subtract(player.position()).horizontal().normalize().scale(0.46);
				WebBruteArts.velocity(server, step.add(0, 0.11, 0));
			}
			return InteractionResult.PASS;
		});
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (!(source.getEntity() instanceof ServerPlayer player) || source.getDirectEntity() != player || !(taken > 0)
				|| !WebBruteArts.able(player) || !player.getMainHandItem().isEmpty()) {
				return;
			}
			WebPose pose = player.getAttached(WebPower.POSE);
			if (pose == null || pose.kind() != WebPose.BRUTE_HOOK_LUNGE || pose.start() != player.level().getGameTime()
				|| WebBruteArts.busy(player)) {
				return;
			}
			Vec3 out = entity.position().subtract(player.position()).horizontal().normalize();
			WebBruteArts.velocity(entity, entity.getDeltaMovement().add(out.scale(0.9)).add(0, 0.22, 0));
			if (WebPin.pinnable(player, entity) && !Tripped.sweep(entity, 18)) {
				entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 18, 3));
			}
		});
	}

	private static Motion motion(ServerPlayer player) {
		return MOTION.compute(player.getUUID(), (id, last) -> last != null && last.level == player.level() ? last : new Motion(player));
	}

	static void forget(ServerPlayer player) {
		if (MOTION.remove(player.getUUID()) != null) {
			WebPose pose = player.getAttached(WebPower.POSE);
			if (pose != null && pose.kind() >= WebPose.BRUTE_COIL && pose.kind() <= WebPose.BRUTE_WALL_LEDGE) {
				player.removeAttached(WebPower.POSE);
			}
		}
	}

	static void clear() {
		MOTION.clear();
	}

	static boolean special(ServerPlayer player) {
		Motion motion = MOTION.get(player.getUUID());
		return motion != null && (motion.aimed || motion.climbTicks > 0 || motion.charge > 0
			|| player.level().getGameTime() < motion.wallGrace);
	}

	static float brace(ServerPlayer player) {
		Motion motion = MOTION.get(player.getUUID());
		return motion == null || motion.level != player.level() || !player.onGround()
			? 1.0F : 1.0F - motion.charge * (0.55F / WebLeap.CHARGE_FULL);
	}

	static void tick(ServerPlayer player) {
		if (!WebBruteArts.able(player) || player.isPassenger() || player.isInWater() || player.isInLava()
			|| player.getAbilities().flying || player.isFallFlying()) {
			forget(player);
			return;
		}
		Motion motion = motion(player);
		boolean grounded = player.onGround();
		if (WebBruteArts.busy(player)) {
			motion.charge = 0;
			motion.aimed = false;
			motion.climbTicks = 0;
			motion.wasGround = grounded;
			motion.jump = player.getLastClientInput().jump();
			return;
		}
		Input input = player.getLastClientInput();
		long now = player.level().getGameTime();
		boolean aimed = motion.aimed;
		boolean wall = !aimed && crawl(player, motion, input, now);
		if (!wall) {
			leap(player, motion, input, now);
		}
		if (grounded && !motion.wasGround && !wall) {
			WebBruteArts.pose(player, WebPose.BRUTE_LAND, 1);
			if (!aimed) {
				Shakes.heavy(player, player.position(), 8.0, 0.28F);
				WebBruteArts.dust(player.level(), player.position(), 6, 0.4);
			}
		}
		motion.wasGround = grounded;
		motion.jump = input.jump();
	}

	private static void leap(ServerPlayer player, Motion motion, Input input, long now) {
		if (motion.aimed) {
			int age = (int)(now - motion.launch);
			motion.peak = Math.max(motion.peak, player.getY());
			motion.airborne |= !player.onGround();
			if (motion.airborne && player.onGround()) {
				motion.aimed = false;
				WebSlam.slam(player, (float)(motion.peak - player.getY()));
			} else if (age > FLIGHT_LIMIT || motion.airborne && player.horizontalCollision) {
				motion.aimed = false;
			}
			return;
		}
		if (player.onGround() && input.jump() && (motion.charge > 0 || !motion.jump)
			&& now >= motion.wallGrace && player.getAttached(WebBrute.MOVE) == null
			&& !WebHeldGrab.holding(player) && !WebPower.swingingNow(player)) {
			if (motion.charge == 0) {
				WebBruteArts.pose(player, WebPose.BRUTE_COIL, 1);
			}
			motion.charge = Math.min(WebLeap.CHARGE_FULL, motion.charge + 1);
			Vec3 velocity = player.getDeltaMovement();
			if (velocity.horizontalDistanceSqr() > 0.01) {
				WebBruteArts.velocity(player, velocity.multiply(0.42, 1, 0.42));
			}
			return;
		}
		if (motion.charge == 0) {
			return;
		}
		if (input.jump() || !player.onGround()) {
			motion.charge = 0;
			return;
		}

		float charge = motion.charge / (float)WebLeap.CHARGE_FULL;
		motion.charge = 0;
		LivingEntity quarry = charge >= 1 ? WebBruteArts.aim(player, LEAP_RANGE) : null;
		Vec3 aimed = quarry == null ? null : WebLeap.aimedVelocity(player, quarry.position());
		Vec3 takeoff = aimed == null
			? WebBruteArts.heading(player).scale(WebLeap.CHARGE_FORWARD_MAX * charge).add(0, WebLeap.speed(charge), 0)
			: aimed;
		motion.aimed = aimed != null;
		motion.airborne = false;
		motion.launch = now;
		motion.peak = player.getY();
		motion.wallGrace = now + 8;
		WebBruteArts.pose(player, WebPose.BRUTE_AIR, 1);
		WebBruteArts.velocity(player, takeoff);
		Shakes.heavy(player, player.position(), 8.0, 0.4F);
	}


	private static boolean crawl(ServerPlayer player, Motion motion, Input input, long now) {
		if (!WebSymbiote.crawlTendrils(player) || player.getAttached(WebSymbiote.WALL) != null
			|| WebPower.swingingNow(player) || WebStamina.tired(player) || now < motion.wallGrace) {
			motion.climbTicks = 0;
			return false;
		}
		AABB box = player.getBoundingBox();
		Vec3 out = wall(player, box);
		if (motion.climbTicks > 0 && input.shift()) {
			motion.climbTicks = 0;
			motion.wallGrace = now + 14;
			WebBruteArts.pose(player, WebPose.BRUTE_WALL_LEAP, 1);
			WebBruteArts.velocity(player, motion.wall.scale(0.21).add(0, -0.22, 0));
			return true;
		}
		if (motion.climbTicks > 0 && motion.jump && !input.jump()) {
			motion.climbTicks = 0;
			motion.charge = 0;
			motion.wallGrace = now + 14;
			Vec3 look = player.getLookAngle();
			Vec3 away = motion.wall;
			Vec3 across = look.horizontal();
			double into = across.dot(away);
			if (into < 0.25) {
				across = across.add(away.scale(0.25 - into));
			}
			WebBruteArts.pose(player, WebPose.BRUTE_WALL_LEAP, 1);
			WebBruteArts.velocity(player, across.scale(0.9).add(0, Math.max(0.32, 0.62 + look.y * 0.45), 0));
			return true;
		}
		if (out == null) {
			if (motion.climbTicks > 0 && input.jump() && ledge(player, box, motion.wall)) {
				motion.climbTicks = 0;
				motion.wallGrace = now + 12;
				WebBruteArts.pose(player, WebPose.BRUTE_WALL_LEDGE, 1);
				WebBruteArts.velocity(player, motion.wall.scale(-0.44).add(0, 0.48, 0));
				return true;
			}
			motion.climbTicks = 0;
			return false;
		}
		if (input.shift() || motion.climbTicks == 0 && !input.jump()) {
			return false;
		}
		motion.wall = out;
		motion.charge = 0;
		int beat = motion.climbTicks++ % CLIMB_BEAT;
		int sideways = (input.right() ? 1 : 0) - (input.left() ? 1 : 0);
		Vec3 along = new Vec3(out.z, 0, -out.x).scale(sideways);
		if (motion.climbTicks == 1) {
			WebBruteArts.pose(player, WebPose.BRUTE_WALL_GRAB, 1);
		} else if (sideways != 0 && now >= motion.hopAt && beat < 7) {
			motion.hopAt = now + 10;
			WebBruteArts.pose(player, WebPose.BRUTE_WALL_SIDE, sideways);
		} else if (beat == 7) {
			WebBruteArts.pose(player, WebPose.BRUTE_WALL_HEAVE, 1);
		} else if (beat == 0) {
			WebBruteArts.pose(player, WebPose.BRUTE_WALL_HOLD, 1);
		}
		double rise = input.jump() && beat >= 7 ? 0.46 : 0.015;
		WebBruteArts.velocity(player, along.scale(now < motion.hopAt - 5 ? 0.31 : 0.055)
			.add(out.scale(-0.13)).add(0, rise, 0));
		player.resetFallDistance();
		return true;
	}

	private static @Nullable Vec3 wall(ServerPlayer player, AABB box) {
		Vec3 facing = WebBruteArts.heading(player);
		Vec3 best = null;
		double score = Double.NEGATIVE_INFINITY;
		for (Direction side : Direction.Plane.HORIZONTAL) {
			Vec3 toward = side.getUnitVec3();
			Vec3 moved = player.position().add(toward.scale(player.getBbWidth() * 0.5 + 0.35));
			if (!player.level().hasChunkAt(BlockPos.containing(moved))
				|| player.level().noCollision(player, box.deflate(0, 0.08, 0).move(toward.scale(0.27)))) {
				continue;
			}
			double dot = toward.dot(facing);
			if (dot > score) {
				best = toward.scale(-1);
				score = dot;
			}
		}
		return best;
	}

	private static boolean ledge(ServerPlayer player, AABB box, Vec3 out) {
		Vec3 over = out.scale(-(player.getBbWidth() * 0.5 + 0.35));
		AABB landing = box.move(over).move(0, 0.42, 0);
		return player.level().hasChunkAt(BlockPos.containing(player.position().add(over)))
			&& player.level().noCollision(player, landing)
			&& !player.level().noCollision(player, new AABB(landing.minX, landing.minY - 0.95, landing.minZ,
				landing.maxX, landing.minY, landing.maxZ));
	}
}
