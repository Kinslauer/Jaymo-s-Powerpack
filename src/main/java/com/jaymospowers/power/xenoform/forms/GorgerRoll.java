package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.core.Debris;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.power.xenoform.XenoformPower;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Gorger's rolling ball, on top of its Roll Up (hold B). While rolled:
 * <ul>
 * <li>V sets the ball bowling along your look for {@link #ROLL_TICKS} ticks: it ploughs through a line of creatures,
 * hitting each once, and bounces off walls; each rebound hits harder ({@link #BOUNCE_BONUS} a bounce, up to
 * {@link #MAX_BOUNCES}) and lets it hit the same creatures again;</li>
 * <li>G is the rising strike: the ball springs up with an uppercut that throws everything near into the air, and comes
 * down as Gorger's slam;</li>
 * <li>projectiles coming at the ball are knocked away: fireballs are batted back, arrows and the like sent off.</li>
 * </ul>
 */
public final class GorgerRoll {
	// ---- tuning ----
	/** The bowl: how long it lasts (ticks), how fast (blocks a tick), its cooldown. */
	public static final int ROLL_TICKS = 60, ROLL_COOLDOWN = 100;
	public static final double ROLL_SPEED = 0.85;
	/** What it hits: damage, tossed ahead and up. */
	public static final float ROLL_DAMAGE = 5.0F;
	public static final double ROLL_TOSS = 0.9, ROLL_LIFT = 0.45;
	/** Each rebound adds this share to the hit's damage, up to this many counted. */
	public static final float BOUNCE_BONUS = 0.35F;
	public static final int MAX_BOUNCES = 5;
	/** Ticks between rebounds counted. */
	public static final int BOUNCE_GAP = 4;
	/** The rising strike: its spring, the uppercut's reach, damage and toss, its cooldown (G's). */
	public static final double RISE_UP = 1.0, RISE_RADIUS = 3.2, RISE_TOSS = 0.95;
	public static final float RISE_DAMAGE = 8.0F;
	public static final int RISE_COOLDOWN = 100;
	/** Projectiles within this many blocks of the ball are knocked away. */
	public static final double DEFLECT_RANGE = 3.0;
	public static final int COLOR = 0xB06A54;

	private static final class Roll {
		Vec3 dir = Vec3.ZERO;
		int age, bounces;
		long bouncedAt = -100L;
		boolean bowling, rising;
		final IntSet hit = new IntOpenHashSet();
	}

	private static final Map<UUID, Roll> ROLLS = new HashMap<>();

	private GorgerRoll() {
	}

	/** Whether the ball is bowling now. */
	public static boolean bowling(ServerPlayer player) {
		Roll roll = ROLLS.get(player.getUUID());
		return roll != null && roll.bowling;
	}

	/** V while rolled: the ball starts bowling along the look. */
	public static boolean bowl(ServerPlayer player) {
		Vec3 dir = player.getLookAngle().horizontal();
		if (dir.lengthSqr() < 1.0E-4) {
			return false;
		}
		Roll roll = ROLLS.computeIfAbsent(player.getUUID(), id -> new Roll());
		roll.dir = dir.normalize();
		roll.age = 0;
		roll.bounces = 0;
		roll.bowling = true;
		roll.rising = false;
		roll.hit.clear();
		player.removeEffect(MobEffects.SLOWNESS);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMADILLO_ROLL, SoundSource.PLAYERS, 1.2F, 0.6F);
		MagicFx.ring(player.level(), player.position(), 1.8F, COLOR);
		return true;
	}

	/** G while rolled: the rising strike. */
	public static boolean rise(ServerPlayer player) {
		if (!player.onGround()) {
			return false;
		}
		ServerLevel level = player.level();
		Roll roll = ROLLS.computeIfAbsent(player.getUUID(), id -> new Roll());
		float damage = RISE_DAMAGE * mult(roll);
		roll.bowling = false;
		roll.rising = true;
		player.removeEffect(MobEffects.SLOWNESS);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RISE_RADIUS), e -> valid(player, e))) {
			e.hurtServer(level, player.damageSources().playerAttack(player), damage);
			e.setDeltaMovement(e.getDeltaMovement().x * 0.5, RISE_TOSS, e.getDeltaMovement().z * 0.5);
			e.syncVelocity = true;
			level.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 10, 0.3, 0.4, 0.3, 0.3);
		}
		player.setDeltaMovement(roll.dir.x * 0.3, RISE_UP, roll.dir.z * 0.3);
		player.syncVelocity = true;
		MagicFx.blast(level, player.position().add(0.0, 0.5, 0.0), (float)RISE_RADIUS * 0.8F, COLOR);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 1.2F, 1.2F);
		Shakes.heavy(player, player.position(), 10.0, 0.4F);
		return true;
	}

	/**
	 * Every tick while rolled. True if the ball is bowling (the stationary roll's slowness and standing still are then
	 * left off).
	 */
	public static boolean tick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		deflect(player, level);
		Roll roll = ROLLS.get(player.getUUID());
		if (roll == null) {
			return false;
		}
		if (roll.rising) {
			if (!player.onGround() && !player.isInWater()) {
				// in the air after the spring: it comes down as the slam
				live.hornSlam = true;
				live.hornFrom = Math.max(live.hornFrom, player.getY());
				roll.age = 1;
			} else if (roll.age > 0) {
				roll.rising = false;
				roll.age = 0;
			}
			return false;
		}
		if (!roll.bowling) {
			return false;
		}
		if (++roll.age > ROLL_TICKS) {
			roll.bowling = false;
			return false;
		}
		long now = level.getGameTime();
		if (player.horizontalCollision && now - roll.bouncedAt >= BOUNCE_GAP) {
			rebound(player, roll, level, now);
		}
		player.setDeltaMovement(roll.dir.x * ROLL_SPEED, player.getDeltaMovement().y, roll.dir.z * ROLL_SPEED);
		player.syncVelocity = true;
		float damage = ROLL_DAMAGE * mult(roll);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(0.5, 0.0, 0.5), e -> valid(player, e)
			&& !roll.hit.contains(e.getId()))) {
			roll.hit.add(e.getId());
			e.hurtServer(level, player.damageSources().playerAttack(player), damage);
			e.setDeltaMovement(roll.dir.x * ROLL_TOSS, ROLL_LIFT, roll.dir.z * ROLL_TOSS);
			e.syncVelocity = true;
			level.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 8, 0.3, 0.4, 0.3, 0.3);
			level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.RAVAGER_ATTACK, SoundSource.PLAYERS, 1.0F, 1.1F);
		}
		return true;
	}

	/** The ball is not rolled any more (or Gorger is done): nothing of the bowl is left. */
	public static void clear(ServerPlayer player) {
		ROLLS.remove(player.getUUID());
	}

	/** Damage multiplier from the rebounds so far. */
	private static float mult(Roll roll) {
		return 1.0F + BOUNCE_BONUS * Math.min(roll.bounces, MAX_BOUNCES);
	}

	private static boolean valid(ServerPlayer player, LivingEntity e) {
		return e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && !e.isPassengerOfSameVehicle(player)
			&& !(e instanceof OwnableEntity pet && pet.getOwner() == player) && !player.isAlliedTo(e);
	}

	/** Off a wall: the side it struck is mirrored; the ball can hit again, harder. */
	private static void rebound(ServerPlayer player, Roll roll, ServerLevel level, long now) {
		double reach = player.getBbWidth() * 0.5 + 0.35;
		boolean x = solid(level, player.getX() + Math.signum(roll.dir.x) * reach, player.getY() + 0.5, player.getZ());
		boolean z = solid(level, player.getX(), player.getY() + 0.5, player.getZ() + Math.signum(roll.dir.z) * reach);
		if (!x && !z) {
			x = z = true;
		}
		roll.dir = new Vec3(x ? -roll.dir.x : roll.dir.x, 0.0, z ? -roll.dir.z : roll.dir.z);
		roll.bounces++;
		roll.bouncedAt = now;
		roll.hit.clear();
		Vec3 wall = player.position().add(roll.dir.scale(-reach)).add(0.0, 0.5, 0.0);
		BlockPos at = BlockPos.containing(wall);
		BlockState state = level.getBlockState(at);
		if (!state.isAir()) {
			Debris.puff(level, at, state, 1.0F);
		}
		float power = Math.min(roll.bounces, MAX_BOUNCES) / (float)MAX_BOUNCES;
		MagicFx.ring(level, wall, null, 1.2F + power * 1.5F, COLOR, 8);
		level.playSound(null, wall.x, wall.y, wall.z, SoundEvents.ARMADILLO_HURT_REDUCED, SoundSource.PLAYERS, 1.0F, 1.2F - power * 0.4F);
		level.playSound(null, wall.x, wall.y, wall.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 0.6F + power, 1.0F);
		Shakes.heavy(player, wall, 14.0, 0.3F + 0.5F * power);
	}

	private static boolean solid(ServerLevel level, double x, double y, double z) {
		BlockPos pos = BlockPos.containing(x, y, z);
		return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	/** Shots coming at the ball are knocked away: fireballs are batted back along the look, the rest sent off. */
	private static void deflect(ServerPlayer player, ServerLevel level) {
		Vec3 centre = player.getBoundingBox().getCenter();
		List<Projectile> shots = level.getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(DEFLECT_RANGE),
			p -> p.isAlive() && p.getOwner() != player && p.getDeltaMovement().lengthSqr() > 0.01 && p.getDeltaMovement().dot(centre.subtract(p.position())) > 0.0);
		for (Projectile shot : shots) {
			if (shot instanceof AbstractHurtingProjectile) {
				shot.hurtServer(level, player.damageSources().playerAttack(player), 1.0F);
			} else {
				shot.setDeltaMovement(shot.getDeltaMovement().scale(-0.6));
			}
			shot.setOwner(player);
			shot.syncVelocity = true;
			shot.needsSync = true;
			level.sendParticles(ParticleTypes.CRIT, shot.getX(), shot.getY(), shot.getZ(), 6, 0.2, 0.2, 0.2, 0.2);
			level.playSound(null, shot.getX(), shot.getY(), shot.getZ(), SoundEvents.ARMADILLO_SCUTE_DROP, SoundSource.PLAYERS, 1.0F, 1.5F);
		}
	}
}
