package com.jaymospowers.power.cryo;

import com.jaymospowers.core.Powers;
import com.jaymospowers.power.smoke.SmokeField;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (0.14.0) G (hold), Frost Breath: a cone of bitter cold from your mouth, {@link #RANGE} blocks along your look (a wall
 * stops it), for as long as you hold G, up to {@link #MAX_TICKS}. What it catches is slowed and freezes the way powder
 * snow freezes you ({@code ticksFrozen}); held in it long enough a creature is frozen through and stands stiff (barely
 * moving, too numb to strike). Water it breathes on freezes over (melting back after {@link #ICE_TICKS}), burning things
 * stop burning, and smoke it touches turns to freezing fog. Snow mode: a gust of snow that slows and blinds and never
 * freezes or hurts. The cooldown grows with how long you blew.
 */
public final class CryoBreath {
	/** Reach (blocks), width at the mouth and how much wider a block further. */
	static final double RANGE = 8.0, MOUTH = 0.3, SPREAD = 0.3;
	/** Longest breath (ticks), and the cooldown after the shortest and the longest. */
	public static final int MAX_TICKS = 100;
	static final int COOLDOWN_MIN = 30;
	static final int COOLDOWN_MAX = 140;
	/** Freezing added a tick to what's in the cone, and how far past frozen through it can go. */
	static final int FREEZE = 5;
	static final int FREEZE_OVER = 80;
	/** Slowness II this long, refreshed while in it; frozen through: held stiff this long. */
	static final int SLOW_TICKS = 30;
	static final int STIFF_TICKS = 20;
	/** Snow mode: blinded this long. */
	static final int SNOW_BLIND_TICKS = 40;
	/** Every how many ticks water freezes and smoke turns to fog along the cone, and how long the ice lasts (30 s). */
	static final int ICE_EVERY = 4;
	static final int ICE_TICKS = 600;
	/** Hunger a tick; at or below this food it stops. */
	static final float EXHAUSTION = 0.02F;
	static final int MIN_FOOD = 6;

	private static final Map<UUID, Long> HOLDS = new HashMap<>();

	private CryoBreath() {
	}

	/** Blowing now. */
	public static boolean breathing(ServerPlayer player) {
		return HOLDS.containsKey(player.getUUID());
	}

	static void start(ServerPlayer player) {
		if (HOLDS.containsKey(player.getUUID()) || !CryoPower.ready(player, CryoState.BREATH) || CryoPower.spent(player, MIN_FOOD)) {
			return;
		}
		long now = player.level().getGameTime();
		HOLDS.put(player.getUUID(), now);
		CryoPower.set(player, CryoPower.state(player).withBreath(now));
		CryoMoves.begin(player, CryoMoves.BREATH);
		player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.POWDER_SNOW_BREAK, SoundSource.PLAYERS, 1.0F, 0.6F);
	}

	/** Lets go; with {@code cooldown}, the cooldown starts (longer the longer it blew). */
	static void stop(ServerPlayer player, boolean cooldown) {
		Long from = HOLDS.remove(player.getUUID());
		if (from == null) {
			return;
		}
		CryoMoves.end(player, CryoMoves.BREATH);
		if (Powers.has(player, CryoPower.INSTANCE)) {
			CryoPower.set(player, CryoPower.state(player).withBreath(-1L));
			if (cooldown) {
				float held = Mth.clamp((player.level().getGameTime() - from) / (float)MAX_TICKS, 0.0F, 1.0F);
				CryoPower.cooldown(player, CryoState.BREATH, (int)Mth.lerp(held, COOLDOWN_MIN, COOLDOWN_MAX));
			}
		}
	}

	static void tick(ServerPlayer player) {
		Long from = HOLDS.get(player.getUUID());
		if (from == null) {
			return;
		}
		ServerLevel level = player.level();
		long held = level.getGameTime() - from;
		if (!player.isAlive() || player.isSpectator() || held >= MAX_TICKS || CryoPower.spent(player, MIN_FOOD)) {
			stop(player, true);
			return;
		}
		float strength = CryoPower.strength(player);
		boolean snow = CryoPower.snow(player);
		Vec3 look = player.getViewVector(1.0F);
		Vec3 from3 = player.getEyePosition().add(0.0, -0.15, 0.0).add(look.scale(0.4));
		double range = RANGE * strength;
		HitResult wall = level.clip(new ClipContext(from3, from3.add(look.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		double reach = wall.getType() == HitResult.Type.MISS ? range : wall.getLocation().distanceTo(from3);
		// the cold, seen by everyone
		ParticleOptions flake = snow ? ParticleTypes.ITEM_SNOWBALL : ParticleTypes.SNOWFLAKE;
		for (int i = 0; i < 6; i++) {
			double t = level.getRandom().nextDouble() * reach;
			Vec3 p = from3.add(look.scale(t));
			double w = MOUTH + t * SPREAD * 0.5;
			level.sendParticles(flake, p.x, p.y, p.z, 2, w, w, w, 0.02);
		}
		if (held % 2 == 0) {
			Vec3 p = from3.add(look.scale(reach * 0.5));
			level.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 1, 0.3, 0.3, 0.3, 0.01);
		}
		if (held % 10 == 0) {
			level.playSound(null, from3.x, from3.y, from3.z, SoundEvents.POWDER_SNOW_STEP, SoundSource.PLAYERS, 0.8F, 0.5F + level.getRandom().nextFloat() * 0.2F);
		}
		// what it catches
		AABB box = new AABB(from3, from3.add(look.scale(reach))).inflate(MOUTH + reach * SPREAD);
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, box,
			e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand))) {
			Vec3 c = living.getBoundingBox().getCenter().subtract(from3);
			double t = c.dot(look);
			if (t < 0.0 || t > reach || c.subtract(look.scale(t)).length() > MOUTH + t * SPREAD + living.getBbWidth() * 0.5) {
				continue;
			}
			chill(player, living, snow, strength);
		}
		// water freezes over, and smoke turns to freezing fog, all along it
		if (held % ICE_EVERY == 0) {
			for (double t = 1.0; t <= reach + 0.5; t += 1.0) {
				Vec3 p = from3.add(look.scale(Math.min(t, reach)));
				double w = MOUTH + t * SPREAD;
				if (!snow) {
					SmokeField.freeze(level, p, w + 0.5);
				}
				if (!snow) {
					BlockPos at = BlockPos.containing(p);
					for (int down = 0; down <= 2; down++) {
						if (CryoIce.place(level, at.below(down), Blocks.ICE.defaultBlockState(), (int)(ICE_TICKS * strength), CryoIce.Mode.WATER)) {
							break;
						}
					}
				}
			}
		}
		player.causeFoodExhaustion(EXHAUSTION);
	}

	/** The cold on one creature: slowed and freezing (frozen through, held stiff); in snow mode slowed and blinded only. */
	static void chill(ServerPlayer player, LivingEntity living, boolean snow, float strength) {
		living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOW_TICKS, 1), player);
		if (snow) {
			living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, SNOW_BLIND_TICKS, 0), player);
			return;
		}
		if (living.isOnFire()) {
			living.clearFire();
		}
		if (living.canFreeze()) {
			living.setTicksFrozen(Math.min(living.getTicksRequiredToFreeze() + FREEZE_OVER, living.getTicksFrozen() + Math.round(FREEZE * strength)));
		}
		if (living.isFullyFrozen()) {
			// frozen stiff
			living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STIFF_TICKS, 6, false, false), player);
			living.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, STIFF_TICKS, 4, false, false), player);
			living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, STIFF_TICKS, 4, false, false), player);
		}
	}

	static void clear() {
		HOLDS.clear();
	}
}
