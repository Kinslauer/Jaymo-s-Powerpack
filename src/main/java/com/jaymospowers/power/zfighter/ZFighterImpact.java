package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.Shakes;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.16.10, Andy: "the way this unarmed combat goes", a Viltrumite-style fight) What happens after a Z Fighter's
 * finisher: the foe it sent flying slams into the wall or the ground it meets (a burst of that block's debris, a
 * shockwave ring of dust, a thud everyone near feels, and a little more hurt), and the fighter rushes after it to keep
 * the string going ({@link #rush}). Shared by the players' hold combo ({@link ZFighterCombo}) and the rogue fighters
 * ({@link ZFighterRogue}). Blocks are only shown, never broken.
 */
public final class ZFighterImpact {
	/** How long after the finisher a slam still counts. */
	static final int FLIGHT_TICKS = 40;
	/** Ticks before a slam into the ground counts (it's still lifting off). */
	static final int LIFT_OFF_TICKS = 3;
	/** Blocks a tick it must have been flying at to slam: sideways into a wall, down into the ground. */
	static final double WALL_SPEED = 0.4;
	static final double GROUND_SPEED = 0.5;
	/** The shockwave ring: this many puffs, thrown out this fast. */
	static final int RING_PUFFS = 16;
	static final double RING_SPEED = 0.35;
	static final float SHAKE = 0.7F;
	static final double SHAKE_RANGE = 16.0;
	/** A rush stops this short of the foe (and never goes faster than what's left of the gap). */
	static final double RUSH_STOP = 0.6;

	/** One foe in flight. */
	private static final class Flight {
		final @Nullable Entity by;
		final float damage;
		final long from;
		Vec3 last;
		Vec3 before = Vec3.ZERO;

		Flight(@Nullable Entity by, float damage, long from, Vec3 at) {
			this.by = by;
			this.damage = damage;
			this.from = from;
			this.last = at;
		}
	}

	private static final Map<LivingEntity, Flight> FLYING = new IdentityHashMap<>();

	private ZFighterImpact() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> tick());
	}

	/** A finisher just sent {@code target} flying: it slams into whatever it meets for {@code damage} more. */
	static void launched(LivingEntity target, @Nullable Entity by, float damage) {
		FLYING.put(target, new Flight(by, damage, target.level().getGameTime(), target.position()));
	}

	private static void tick() {
		Iterator<Map.Entry<LivingEntity, Flight>> it = FLYING.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<LivingEntity, Flight> entry = it.next();
			LivingEntity entity = entry.getKey();
			Flight flight = entry.getValue();
			if (entity.isRemoved() || !entity.isAlive() || !(entity.level() instanceof ServerLevel level)
				|| level.getGameTime() - flight.from > FLIGHT_TICKS) {
				it.remove();
				continue;
			}
			// read off where it went, not its motion: a player's motion lives on his own client
			Vec3 moved = entity.position().subtract(flight.last);
			Vec3 was = flight.before;
			flight.before = moved;
			flight.last = entity.position();
			double sideways = Math.sqrt(was.x * was.x + was.z * was.z);
			boolean wall = entity.horizontalCollision && sideways > WALL_SPEED;
			boolean ground = entity.onGround() && was.y < -GROUND_SPEED && level.getGameTime() - flight.from > LIFT_OFF_TICKS;
			if (wall || ground) {
				it.remove();
				slam(level, entity, flight, wall ? new Vec3(was.x, 0.0, was.z).normalize() : new Vec3(0.0, -1.0, 0.0));
			}
		}
	}

	/** It hits: the block it met bursts into debris, a ring of dust goes out, everyone near feels it. */
	private static void slam(ServerLevel level, LivingEntity entity, Flight flight, Vec3 into) {
		Vec3 centre = entity.getBoundingBox().getCenter();
		Vec3 at = into.y < 0.0 ? entity.position() : centre.add(into.scale(entity.getBbWidth() * 0.5));
		BlockState state = struck(level, at, into);
		if (state != null) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), true, false, at.x, at.y, at.z, 40, 0.5, 0.5, 0.5, 0.15);
			level.playSound(null, at.x, at.y, at.z, state.getSoundType().getBreakSound(), SoundSource.PLAYERS, 1.2F, 0.6F);
		}
		level.sendParticles(ParticleTypes.EXPLOSION, true, false, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		// the ring lies flat on the ground, or stands up against a wall
		Vec3 a = into.y < 0.0 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
		Vec3 b = into.y < 0.0 ? new Vec3(0.0, 0.0, 1.0) : into.cross(a).normalize();
		for (int i = 0; i < RING_PUFFS; i++) {
			double angle = Math.PI * 2.0 * i / RING_PUFFS;
			Vec3 out = a.scale(Mth.cos((float)angle)).add(b.scale(Mth.sin((float)angle)));
			// count 0: the offsets are the puff's motion
			level.sendParticles(ParticleTypes.CLOUD, true, false, at.x + out.x * 0.4, at.y + out.y * 0.4 + 0.1, at.z + out.z * 0.4, 0,
				out.x, out.y, out.z, RING_SPEED);
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6F, 1.3F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.6F);
		Shakes.around(level, at, SHAKE_RANGE, SHAKE);
		if (flight.damage > 0.0F) {
			Entity by = flight.by;
			DamageSource source = by instanceof Player player ? level.damageSources().playerAttack(player)
				: by instanceof LivingEntity living ? level.damageSources().mobAttack(living) : level.damageSources().flyIntoWall();
			entity.setInvulnerableTime(0);
			entity.hurtServer(level, source, flight.damage);
		}
	}

	/** The block it slammed into: just ahead along {@code into}, else under it. */
	private static @Nullable BlockState struck(ServerLevel level, Vec3 at, Vec3 into) {
		for (double d = 0.0; d <= 1.0; d += 0.25) {
			BlockPos pos = BlockPos.containing(at.add(into.scale(d + 0.05)));
			BlockState state = level.getBlockState(pos);
			if (!state.isAir()) {
				return state;
			}
		}
		BlockState below = level.getBlockState(BlockPos.containing(at.x, at.y - 0.5, at.z));
		return below.isAir() ? null : below;
	}

	/**
	 * One tick of a rush after a foe (through the air, at up to {@code speed} blocks a tick), with a streak behind;
	 * true once he's within {@code reach} of it (centre to its edge), and he stops there.
	 */
	static boolean rush(LivingEntity mover, LivingEntity target, double speed, double reach) {
		Vec3 to = target.getBoundingBox().getCenter().subtract(mover.getBoundingBox().getCenter());
		double gap = to.length() - target.getBbWidth() * 0.5;
		mover.resetFallDistance();
		if (gap <= reach || to.lengthSqr() < 1.0E-4) {
			mover.setDeltaMovement(mover.getDeltaMovement().scale(0.2));
			mover.syncVelocity = true;
			return true;
		}
		double step = Math.min(speed, Math.max(0.1, gap - reach + RUSH_STOP));
		mover.setDeltaMovement(to.normalize().scale(step));
		mover.syncVelocity = true;
		if (mover.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.CLOUD, true, false, mover.getX(), mover.getY() + mover.getBbHeight() * 0.5, mover.getZ(), 2, 0.15,
				0.3, 0.15, 0.0);
		}
		return false;
	}

	/** The rush's whoosh off the mark. */
	static void rushSound(LivingEntity mover) {
		mover.level().playSound(null, mover.getX(), mover.getY(), mover.getZ(), SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.8F, 1.4F);
	}
}
