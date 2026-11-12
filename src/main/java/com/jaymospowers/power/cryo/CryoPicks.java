package com.jaymospowers.power.cryo;

import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.core.wounds.BodyPart;
import com.jaymospowers.core.wounds.Bleeding;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Cold picks for the frost users who are not Z Fighters: the Cryokinesis power and Rime (which borrows its kit through
 * {@link CryoLent}). What already existed: Frost Breath (the cone, G / Rime's R), Ice Shards (a fan that slows), Flash
 * Freeze, Ice Wall and the Blizzard. Added, as sneak + the breath key, sneak + the shards key and sneak + the fourth key:
 * <ul>
 * <li><b>Cold gaze</b> (hold): two thin beams from the eyes. A creature kept in them for {@link #FREEZE_TICKS} (6 s) in
 * a row is frozen stiff for as long as the gaze stays on it. Snow mode only chills.</li>
 * <li><b>Icicle volley</b>: {@link #VOLLEY_COUNT} icicles in quick succession; each hurts and opens a bleeding cut
 * ({@link Bleeding}).</li>
 * <li><b>Ice ball</b>: a thrown ball of ice that bursts on what it meets: whatever it catches is rooted for
 * {@link #ROOT_TICKS} and a ring of ice (real blocks, melting again through {@link CryoIce}) closes round the feet.</li>
 * </ul>
 * Cooldowns are kept here (no HUD chip). Moves are server side; the projectiles are not entities.
 */
public final class CryoPicks {
	// ---- tuning ----
	public static final int EYES_COOLDOWN = 80, VOLLEY_COOLDOWN = 120, BALL_COOLDOWN = 160;
	/** Gaze: reach, longest hold, ticks on one target before it freezes, grace when the aim slips, damage a second, hunger a tick. */
	static final double EYES_REACH = 20.0;
	static final int EYES_MAX = 200, FREEZE_TICKS = 120, EYES_GRACE = 6, EYES_HIT_EVERY = 10, MIN_FOOD = 6;
	static final float EYES_DAMAGE = 0.5F, EXHAUSTION = 0.01F;
	/** Held stiff this long once frozen (refreshed while it stays on target). */
	static final int STIFF_TICKS = 24;
	/** Volley: how many, ticks apart, spread (radians), speed, life, damage, slow ticks. */
	static final int VOLLEY_COUNT = 7, VOLLEY_GAP = 2, VOLLEY_LIFE = 30, VOLLEY_SLOW = 40;
	static final double VOLLEY_SPREAD = 0.05, VOLLEY_SPEED = 2.0;
	static final float VOLLEY_DAMAGE = 2.5F;
	/** Ice ball: speed, drop a tick, life, burst radius, damage, root ticks, the ring of ice. */
	static final double BALL_SPEED = 1.3, BALL_DROP = 0.02, BALL_RADIUS = 2.5;
	static final int BALL_LIFE = 50, ROOT_TICKS = 80;
	static final float BALL_DAMAGE = 3.0F;
	static final int COLOR = 0x8FD3F5, WHITE = 0xEAF8FF;

	private static final class Gaze {
		UUID target;
		int onTarget, grace;
		final long start;

		Gaze(long start) {
			this.start = start;
		}
	}

	private static final class Shot {
		final ServerLevel level;
		final ServerPlayer owner;
		final boolean ball;
		Vec3 pos, velocity;
		int age;

		Shot(ServerLevel level, ServerPlayer owner, boolean ball, Vec3 pos, Vec3 velocity) {
			this.level = level;
			this.owner = owner;
			this.ball = ball;
			this.pos = pos;
			this.velocity = velocity;
		}
	}

	private static final class Volley {
		final ServerPlayer owner;
		int left;
		long next;

		Volley(ServerPlayer owner, int left, long next) {
			this.owner = owner;
			this.left = left;
			this.next = next;
		}
	}

	private static final Map<UUID, Gaze> GAZES = new HashMap<>();
	private static final List<Shot> SHOTS = new ArrayList<>();
	private static final List<Volley> VOLLEYS = new ArrayList<>();
	private static final Map<UUID, long[]> COOLDOWNS = new HashMap<>();
	private static final int EYES = 0, VOLLEY = 1, BALL = 2;

	private CryoPicks() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(CryoPicks::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			GAZES.clear();
			SHOTS.clear();
			VOLLEYS.clear();
			COOLDOWNS.clear();
		});
	}

	/** Cold things the player may hurt: not themselves, their pets, or players they may not harm. */
	private static boolean foe(ServerPlayer owner, Entity entity) {
		return entity instanceof LivingEntity && entity != owner && entity.isAlive() && !entity.isSpectator()
			&& !(entity instanceof TamableAnimal pet && pet.isOwnedBy(owner)) && (!(entity instanceof Player other) || owner.canHarmPlayer(other));
	}

	private static boolean ready(ServerPlayer player, int slot) {
		return player.level().getGameTime() >= COOLDOWNS.computeIfAbsent(player.getUUID(), k -> new long[3])[slot];
	}

	private static void cooldown(ServerPlayer player, int slot, int ticks) {
		COOLDOWNS.computeIfAbsent(player.getUUID(), k -> new long[3])[slot] = player.level().getGameTime() + ticks;
	}

	public static boolean gazing(ServerPlayer player) {
		return GAZES.containsKey(player.getUUID());
	}

	/** Every cold pick ends (the power is lost, the lend ends, the player goes down). */
	public static void end(ServerPlayer player) {
		GAZES.remove(player.getUUID());
		VOLLEYS.removeIf(v -> v.owner == player);
	}

	// ---- the moves ----

	/** The cold gaze, held while {@code down}. */
	public static void eyes(ServerPlayer player, boolean down) {
		if (down) {
			if (ready(player, EYES) && !GAZES.containsKey(player.getUUID())) {
				GAZES.put(player.getUUID(), new Gaze(player.level().getGameTime()));
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.POWDER_SNOW_BREAK, SoundSource.PLAYERS, 1.0F, 1.6F);
			}
		} else if (GAZES.remove(player.getUUID()) != null) {
			cooldown(player, EYES, EYES_COOLDOWN);
		}
	}

	/** A volley of bleeding icicles. */
	public static boolean volley(ServerPlayer player) {
		if (!ready(player, VOLLEY)) {
			return false;
		}
		VOLLEYS.add(new Volley(player, VOLLEY_COUNT, player.level().getGameTime()));
		cooldown(player, VOLLEY, VOLLEY_COOLDOWN);
		return true;
	}

	/** A ball of ice that roots what it hits. */
	public static boolean ball(ServerPlayer player) {
		if (!ready(player, BALL)) {
			return false;
		}
		ServerLevel level = player.level();
		Vec3 from = player.getEyePosition().add(player.getLookAngle().scale(0.8)).subtract(0.0, 0.2, 0.0);
		SHOTS.add(new Shot(level, player, true, from, player.getLookAngle().scale(BALL_SPEED)));
		level.playSound(null, from.x, from.y, from.z, SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 1.0F, 0.6F);
		cooldown(player, BALL, BALL_COOLDOWN);
		return true;
	}

	// ---- ticking ----

	private static void tick(MinecraftServer server) {
		for (Iterator<Map.Entry<UUID, Gaze>> it = GAZES.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Gaze> entry = it.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
			if (owner == null || !owner.isAlive() || owner.isSpectator() || owner.level().getGameTime() - entry.getValue().start >= EYES_MAX
				|| CryoPower.spent(owner, MIN_FOOD)) {
				it.remove();
				if (owner != null) {
					cooldown(owner, EYES, EYES_COOLDOWN);
				}
			} else {
				gaze(owner, entry.getValue());
			}
		}
		for (Iterator<Volley> it = VOLLEYS.iterator(); it.hasNext();) {
			Volley volley = it.next();
			long now = volley.owner.level().getGameTime();
			if (!volley.owner.isAlive()) {
				it.remove();
			} else if (now >= volley.next) {
				icicle(volley.owner);
				volley.next = now + VOLLEY_GAP;
				if (--volley.left <= 0) {
					it.remove();
				}
			}
		}
		for (Iterator<Shot> it = SHOTS.iterator(); it.hasNext();) {
			if (step(it.next())) {
				it.remove();
			}
		}
	}

	private static void gaze(ServerPlayer owner, Gaze gaze) {
		ServerLevel level = owner.level();
		long now = level.getGameTime();
		Vec3 eye = owner.getEyePosition();
		Vec3 look = owner.getLookAngle();
		Vec3 to = eye.add(look.scale(EYES_REACH));
		HitResult clip = level.clip(new ClipContext(eye, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
		Vec3 end = clip.getType() == HitResult.Type.MISS ? to : clip.getLocation();
		LivingEntity target = null;
		double best = Double.MAX_VALUE;
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(1.0), e -> foe(owner, e))) {
			var hit = living.getBoundingBox().inflate(0.3).clip(eye, end);
			if (hit.isPresent() && eye.distanceToSqr(hit.get()) < best) {
				best = eye.distanceToSqr(hit.get());
				target = living;
			}
		}
		if (target != null) {
			end = eye.add(look.scale(Math.sqrt(best)));
		}
		Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
		if (right.lengthSqr() > 1.0E-4) {
			right = right.normalize().scale(0.14);
		}
		Vec3 base = eye.add(look.scale(0.4)).subtract(0.0, 0.05, 0.0);
		MagicFx.beam(level, base.add(right), end, 0.05F, WHITE, 3);
		MagicFx.beam(level, base.subtract(right), end, 0.05F, COLOR, 3);
		owner.causeFoodExhaustion(EXHAUSTION);
		if (target == null) {
			if (++gaze.grace > EYES_GRACE) {
				gaze.target = null;
				gaze.onTarget = 0;
			}
			return;
		}
		gaze.grace = 0;
		if (!target.getUUID().equals(gaze.target)) {
			gaze.target = target.getUUID();
			gaze.onTarget = 0;
		}
		gaze.onTarget++;
		boolean snow = CryoPower.snow(owner);
		float strength = CryoPower.strength(owner);
		if (now % EYES_HIT_EVERY == 0) {
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 1), owner);
			if (target.isOnFire()) {
				target.clearFire();
			}
			if (target.canFreeze() && !snow) {
				target.setTicksFrozen(Math.min(target.getTicksRequiredToFreeze() + 20, target.getTicksFrozen() + Math.round(18 * strength)));
			}
			if (!snow) {
				target.hurtServer(level, owner.damageSources().playerAttack(owner), EYES_DAMAGE * strength);
			}
			level.sendParticles(ParticleTypes.SNOWFLAKE, end.x, end.y, end.z, 4, 0.2, 0.3, 0.2, 0.02);
		}
		if (!snow && gaze.onTarget >= FREEZE_TICKS) {
			if (gaze.onTarget == FREEZE_TICKS) {
				MagicFx.blast(level, target.getBoundingBox().getCenter(), 1.4F, WHITE);
				level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 1.2F, 0.7F);
			}
			stiffen(owner, target);
		}
	}

	/** Frozen stiff: held still, too numb to strike. */
	private static void stiffen(ServerPlayer owner, LivingEntity target) {
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STIFF_TICKS, 9, false, false), owner);
		target.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, STIFF_TICKS, 4, false, false), owner);
		target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, STIFF_TICKS, 4, false, false), owner);
		if (target.canFreeze()) {
			target.setTicksFrozen(Math.max(target.getTicksFrozen(), target.getTicksRequiredToFreeze() + 20));
		}
	}

	private static void icicle(ServerPlayer owner) {
		ServerLevel level = owner.level();
		Vec3 look = owner.getLookAngle();
		RandomSource random = level.getRandom();
		Vec3 aim = look.add((random.nextDouble() - 0.5) * 2.0 * VOLLEY_SPREAD, (random.nextDouble() - 0.5) * 2.0 * VOLLEY_SPREAD,
			(random.nextDouble() - 0.5) * 2.0 * VOLLEY_SPREAD).normalize();
		Vec3 from = owner.getEyePosition().add(look.scale(0.8)).add((random.nextDouble() - 0.5) * 0.6, -0.3 + (random.nextDouble() - 0.5) * 0.4,
			(random.nextDouble() - 0.5) * 0.6);
		SHOTS.add(new Shot(level, owner, false, from, aim.scale(VOLLEY_SPEED)));
		level.playSound(null, from.x, from.y, from.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.5F, 1.8F);
	}

	/** One tick of a shot; true once it is gone. */
	private static boolean step(Shot shot) {
		ServerLevel level = shot.level;
		shot.age++;
		if (shot.owner.level() != level || shot.age > (shot.ball ? BALL_LIFE : VOLLEY_LIFE)) {
			return true;
		}
		if (shot.ball) {
			shot.velocity = shot.velocity.subtract(0.0, BALL_DROP, 0.0);
		}
		Vec3 from = shot.pos;
		Vec3 to = from.add(shot.velocity);
		HitResult clip = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shot.owner));
		Vec3 end = clip.getType() == HitResult.Type.MISS ? to : clip.getLocation();
		LivingEntity struck = null;
		double best = Double.MAX_VALUE;
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(0.6), e -> foe(shot.owner, e))) {
			var hit = living.getBoundingBox().inflate(shot.ball ? 0.4 : 0.15).clip(from, end);
			if (hit.isPresent() && from.distanceToSqr(hit.get()) < best) {
				best = from.distanceToSqr(hit.get());
				struck = living;
			}
		}
		if (struck != null) {
			end = from.add(shot.velocity.normalize().scale(Math.sqrt(best)));
		}
		if (shot.ball) {
			MagicFx.orb(level, end, 0.7F, COLOR, 3);
			level.sendParticles(ParticleTypes.SNOWFLAKE, end.x, end.y, end.z, 2, 0.15, 0.15, 0.15, 0.01);
		} else {
			MagicFx.stroke(level, from, end, 0.06F, WHITE);
		}
		shot.pos = end;
		if (struck == null && clip.getType() == HitResult.Type.MISS) {
			return false;
		}
		if (shot.ball) {
			burstBall(shot, end);
		} else if (struck != null) {
			level.playSound(null, end.x, end.y, end.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.6F, 1.5F);
			struck.hurtServer(level, shot.owner.damageSources().playerAttack(shot.owner), VOLLEY_DAMAGE * CryoPower.strength(shot.owner));
			struck.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, VOLLEY_SLOW, 0), shot.owner);
			Bleeding.open(struck, BodyPart.BODY, false);
		}
		return true;
	}

	private static void burstBall(Shot shot, Vec3 at) {
		ServerLevel level = shot.level;
		MagicFx.blast(level, at, (float)BALL_RADIUS, COLOR);
		MagicFx.ring(level, at, (float)BALL_RADIUS * 1.4F, WHITE);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.2F, 0.7F);
		float strength = CryoPower.strength(shot.owner);
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(BALL_RADIUS), e -> foe(shot.owner, e))) {
			living.hurtServer(level, shot.owner.damageSources().playerAttack(shot.owner), BALL_DAMAGE * strength);
			living.clearFire();
			stiffenFor(shot.owner, living, ROOT_TICKS);
			shell(level, living, ROOT_TICKS);
		}
	}

	private static void stiffenFor(ServerPlayer owner, LivingEntity target, int ticks) {
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 9, false, false), owner);
		if (target.canFreeze()) {
			target.setTicksFrozen(Math.max(target.getTicksFrozen(), target.getTicksRequiredToFreeze() + 20));
		}
	}

	/** A ring of ice round the feet, only into air, melting again (the same helper the Ice Wall and Flash Freeze use). */
	private static void shell(ServerLevel level, LivingEntity target, int ticks) {
		BlockPos feet = target.blockPosition();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) {
					continue;
				}
				for (int dy = 0; dy <= 1; dy++) {
					CryoIce.place(level, feet.offset(dx, dy, dz), Blocks.PACKED_ICE.defaultBlockState(), ticks, CryoIce.Mode.AIR);
				}
			}
		}
	}
}
