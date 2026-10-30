package com.jaymospowers.power.gravity;

import com.jaymospowers.core.Debris;
import com.jaymospowers.core.Shakes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * (0.18.48, Weapon Forms patch 6) Comet Ram (sneak + the fourth key): you wrap yourself in gravity and ram forward like a
 * comet. For up to {@link #TICKS} you are driven along the way you looked when you set off (a little steering toward where you
 * look now), faster each tick up to {@link #MAX_SPEED}; creatures in your path are caught up and carried ahead of you (up to
 * {@link #MAX_CARRIED}). The ram ends at a wall, the ground, or the end of its run, and the carried are slammed down: heavy damage
 * to each, the rest around the spot thrown and hurt, the ground cracked and the view shaken.
 * <p>
 * Fall rules still apply: nothing here waives fall damage (a ram that ends in the air leaves you to fall). Hitting a wall or
 * the ground at speed with nothing in front to take it hurts you, by speed squared ({@link #CRASH_K}); the carried take the blow
 * for you. Hungry work ({@link #EXHAUSTION}). Server-driven: the velocity is pushed to your client each tick.
 */
public final class GravityCometRam {
	/** The run: how long (ticks), the speed it starts at and tops out at (blocks a tick), and how fast it gathers. */
	public static final int TICKS = 22;
	public static final double START_SPEED = 0.8, MAX_SPEED = 2.0, ACCEL = 0.12;
	/** How much of the way you look now is steered into the way you go each tick. */
	public static final double STEER = 0.12;
	/** Wait before another (ticks), and hunger it costs. */
	public static final int COOLDOWN = 240;
	public static final float EXHAUSTION = 3.0F;
	/** How many it carries, how far round you it catches them, and how far ahead of you they are held. */
	public static final int MAX_CARRIED = 6;
	public static final double CATCH_RADIUS = 1.4, HOLD_AHEAD = 1.3;
	/** Hit on being caught, the slam at the end (before the speed bonus), and the slam's reach and damage to the rest around. */
	public static final float CATCH_DAMAGE = 3.0F, SLAM_DAMAGE = 9.0F, SPEED_DAMAGE = 3.0F, AREA_DAMAGE = 5.0F;
	public static final double SLAM_RADIUS = 4.0, SLAM_THROW = 0.9, SLAM_DOWN = 1.1;
	/** The crash into a wall or the ground with nothing carried: damage = speed squared times this (capped). */
	public static final float CRASH_K = 1.5F, CRASH_MAX = 8.0F;
	/** Running into something solid at least this fast (blocks a tick) is a crash. */
	public static final double IMPACT_SPEED = 0.7;

	private static final class Run {
		final Vec3 dir;
		Vec3 heading;
		int ticks;
		double speed = START_SPEED;
		Vec3 last;
		final List<UUID> carried = new ArrayList<>();
		final Set<UUID> seen = new HashSet<>();

		Run(Vec3 dir, Vec3 at) {
			this.dir = dir;
			this.heading = dir;
			this.last = at;
		}
	}

	private static final Map<UUID, Run> RUNS = new HashMap<>();
	private static final Map<UUID, Long> READY_AT = new HashMap<>();

	private GravityCometRam() {
	}

	/** Whether {@code player} is ramming now. */
	public static boolean ramming(ServerPlayer player) {
		return RUNS.containsKey(player.getUUID());
	}

	/** Sneak + the fourth key. False (nothing happens) while one is going or not yet ready. */
	static boolean start(ServerPlayer player) {
		long now = player.level().getGameTime();
		long left = READY_AT.getOrDefault(player.getUUID(), 0L) - now;
		if (RUNS.containsKey(player.getUUID()) || player.isPassenger() || player.isInWater()) {
			return false;
		}
		if (left > 0 && !player.isCreative()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.gravity_ram.wait", (left + 19) / 20).withStyle(ChatFormatting.GRAY));
			return false;
		}
		if (player.getFoodData().getFoodLevel() <= 0 && !player.isCreative()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.gravity_ram.hungry").withStyle(ChatFormatting.GRAY));
			return false;
		}
		Vec3 look = player.getLookAngle();
		// a dive is fine; straight up is not a ram
		Vec3 dir = new Vec3(look.x, Math.max(-0.7, Math.min(0.5, look.y)), look.z).normalize();
		RUNS.put(player.getUUID(), new Run(dir, player.position()));
		READY_AT.put(player.getUUID(), now + TICKS + COOLDOWN);
		player.causeFoodExhaustion(EXHAUSTION);
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 0.8F, 1.4F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.9F, 0.5F);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.4, 0.6, 0.4, 0.2);
		drive(player, dir.scale(START_SPEED));
		return true;
	}

	private static void drive(ServerPlayer player, Vec3 velocity) {
		player.setDeltaMovement(velocity);
		player.needsSync = true;
		player.syncVelocity = true;
	}

	/** From Gravity's player tick. */
	static void tick(ServerPlayer player) {
		Run run = RUNS.get(player.getUUID());
		if (run == null) {
			return;
		}
		ServerLevel level = player.level();
		if (!player.isAlive() || player.isSpectator() || player.isInWater() || player.isPassenger()) {
			end(player, run, false);
			return;
		}
		run.ticks++;
		Vec3 now = player.position();
		double moved = now.distanceTo(run.last);
		run.last = now;
		// the way you go bends a little toward the way you look
		Vec3 look = player.getLookAngle();
		run.heading = run.heading.scale(1.0 - STEER).add(look.scale(STEER)).normalize();
		run.speed = Math.min(MAX_SPEED, run.speed + ACCEL);
		Vec3 velocity = run.heading.scale(run.speed);
		boolean crash = run.ticks > 2 && Math.max(run.speed * 0.6, moved) >= IMPACT_SPEED
			&& (player.horizontalCollision || player.verticalCollision && run.heading.y < -0.1 || player.onGround() && run.heading.y < -0.25);
		if (!crash) {
			catchFoes(level, player, run, now);
			drive(player, velocity);
			hold(player, run, now, velocity);
			trail(level, player, run);
		}
		if (crash) {
			end(player, run, true);
		} else if (run.ticks >= TICKS) {
			end(player, run, false);
		}
	}

	/** Creatures in the path are caught up (hurt a little) and carried. */
	private static void catchFoes(ServerLevel level, ServerPlayer player, Run run, Vec3 at) {
		if (run.carried.size() >= MAX_CARRIED) {
			return;
		}
		Vec3 front = at.add(run.heading.scale(HOLD_AHEAD * 0.5)).add(0.0, player.getBbHeight() * 0.5, 0.0);
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(front, front).inflate(CATCH_RADIUS),
			e -> e != player && e.isAlive() && !e.isSpectator() && !run.seen.contains(e.getUUID()) && !e.isAlliedTo(player)
				&& !(e instanceof net.minecraft.world.entity.OwnableEntity pet && player.getUUID().equals(pet.getOwnerReference() == null ? null : pet.getOwnerReference().getUUID())))) {
			if (run.carried.size() >= MAX_CARRIED) {
				break;
			}
			run.seen.add(living.getUUID());
			run.carried.add(living.getUUID());
			GravityPower.hurtByWeight(living, player, CATCH_DAMAGE);
			level.playSound(null, living.getX(), living.getY(), living.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.7F);
		}
	}

	/** The carried are held out ahead of you, moving as you do. */
	private static void hold(ServerPlayer player, Run run, Vec3 at, Vec3 velocity) {
		ServerLevel level = player.level();
		int index = 0;
		for (UUID id : run.carried) {
			if (!(level.getEntity(id) instanceof LivingEntity living) || !living.isAlive()) {
				continue;
			}
			// held in a loose fan in front of the chest
			double fan = (index - (run.carried.size() - 1) / 2.0) * 0.45;
			Vec3 side = run.heading.cross(new Vec3(0.0, 1.0, 0.0));
			side = side.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
			Vec3 spot = at.add(0.0, player.getBbHeight() * 0.35, 0.0).add(run.heading.scale(HOLD_AHEAD + 0.2 * (index / 3))).add(side.scale(fan));
			Vec3 pull = spot.subtract(living.position());
			living.setDeltaMovement(velocity.add(pull.scale(0.5)));
			living.needsSync = true;
			living.syncVelocity = true;
			index++;
		}
	}

	private static void trail(ServerLevel level, ServerPlayer player, Run run) {
		Vec3 back = player.position().subtract(run.heading.scale(0.8)).add(0.0, player.getBbHeight() * 0.5, 0.0);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, back.x, back.y, back.z, 6, 0.25, 0.3, 0.25, 0.15);
		level.sendParticles(ParticleTypes.END_ROD, back.x, back.y, back.z, 2, 0.15, 0.2, 0.15, 0.02);
		if (run.ticks % 3 == 0) {
			level.sendParticles(ParticleTypes.SMOKE, back.x, back.y, back.z, 4, 0.3, 0.3, 0.3, 0.02);
		}
	}

	/** The end of the run: the carried are slammed down (and, if it ended on a crash, the blow lands on whoever is in front). */
	private static void end(ServerPlayer player, Run run, boolean crashed) {
		RUNS.remove(player.getUUID());
		ServerLevel level = player.level();
		double speed = Math.max(run.speed, 0.0);
		Vec3 spot = player.position().add(run.heading.scale(HOLD_AHEAD));
		BlockPos at = BlockPos.containing(spot.x, player.getY() - 0.5, spot.z);
		if (!crashed) {
			// out of road: the dive is cut short, no more drive
			Vec3 now = player.getDeltaMovement();
			player.setDeltaMovement(now.scale(0.4));
			player.needsSync = true;
			player.syncVelocity = true;
		}
		boolean any = false;
		for (UUID id : run.carried) {
			if (level.getEntity(id) instanceof LivingEntity living && living.isAlive()) {
				any = true;
				float damage = SLAM_DAMAGE + (float)(speed / MAX_SPEED) * SPEED_DAMAGE;
				GravityPower.hurtByWeight(living, player, damage);
				living.setDeltaMovement(run.heading.x * SLAM_THROW * 0.5, -SLAM_DOWN, run.heading.z * SLAM_THROW * 0.5);
				living.needsSync = true;
				living.syncVelocity = true;
			}
		}
		if (!any && !crashed && speed < MAX_SPEED * 0.5) {
			// a fizzle: nothing caught, nothing struck
			return;
		}
		Vec3 centre = any || crashed ? spot : player.position();
		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(centre, centre).inflate(SLAM_RADIUS),
			e -> e != player && e.isAlive() && !e.isSpectator() && !run.carried.contains(e.getUUID()))) {
			Vec3 away = near.position().subtract(centre).multiply(1.0, 0.0, 1.0);
			away = away.lengthSqr() < 1.0E-4 ? run.heading.multiply(1.0, 0.0, 1.0) : away.normalize();
			GravityPower.hurtByWeight(near, player, AREA_DAMAGE);
			near.setDeltaMovement(away.x * SLAM_THROW, 0.45, away.z * SLAM_THROW);
			near.needsSync = true;
			near.syncVelocity = true;
		}
		if (crashed && !any) {
			// nothing carried to take it: you do
			float hurt = Math.min(CRASH_MAX, (float)(speed * speed) * CRASH_K);
			if (!player.isCreative()) {
				player.hurtServer(level, level.damageSources().flyIntoWall(), hurt);
			}
		}
		level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.2F, 0.7F);
		level.sendParticles(ParticleTypes.EXPLOSION, centre.x, centre.y + 0.5, centre.z, 2, 0.5, 0.3, 0.5, 0.0);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, centre.x, centre.y + 0.5, centre.z, 40, 1.0, 0.6, 1.0, 0.3);
		Debris.puff(level, at, level.getBlockState(at), 2.0F);
		Debris.ring(level, at, 3, 1.0F);
		Shakes.heavy(player, centre, 16.0, (float)Mth.clamp(speed * 0.45, 0.3, 0.9));
	}

	static void stop(ServerPlayer player) {
		RUNS.remove(player.getUUID());
	}

	static void forget(ServerPlayer player) {
		RUNS.remove(player.getUUID());
		READY_AT.remove(player.getUUID());
	}

	static void clear() {
		RUNS.clear();
		READY_AT.clear();
	}
}
