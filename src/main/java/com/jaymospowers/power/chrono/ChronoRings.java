package com.jaymospowers.power.chrono;

import com.jaymospowers.core.fx.MagicFx;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Running rings: a speedster who runs a tight closed circle (a radius of {@link #MIN_RADIUS} to {@link #MAX_RADIUS} blocks,
 * round in under {@link #MAX_LOOP_TICKS} ticks) raises a vortex inside it for {@link #VORTEX_TICKS} ticks. The vortex lifts
 * every creature in the ring but you up to {@link #MAX_RISE} blocks, turns them round, and puts out fire: burning creatures
 * and fire blocks inside it. A ring of gold light is laid on the ground and the air spirals up. Overclock and Velocity both.
 */
public final class ChronoRings {
	// ---- tuning ----
	/** The circle: radius range (blocks), the most it may stray from round (share of its radius), the end meeting the start (blocks). */
	static final double MIN_RADIUS = 2.0, MAX_RADIUS = 6.0, ROUNDNESS = 0.4, CLOSE = 1.9;
	/** Least and most ticks round, the history kept, and the turn that counts as a full circle (radians). */
	static final int MIN_LOOP_TICKS = 14, MAX_LOOP_TICKS = 55, HISTORY = 60;
	static final double FULL_TURN = Math.PI * 2.0 * 0.85;
	/** Ticks between rings by one runner. */
	static final int COOLDOWN = 120;
	/** The vortex: ticks it spins, how high it lifts (blocks), the lift a tick, how fast things are swept round, and the pull in. */
	static final int VORTEX_TICKS = 60;
	static final double MAX_RISE = 3.5, LIFT = 0.16, SWEEP = 0.3, PULL = 0.05;
	static final int GOLD = 0xFFD070;

	private static final class Vortex {
		final ServerLevel level;
		final UUID owner;
		final Vec3 centre;
		final double radius;
		int left = VORTEX_TICKS;

		Vortex(ServerLevel level, UUID owner, Vec3 centre, double radius) {
			this.level = level;
			this.owner = owner;
			this.centre = centre;
			this.radius = radius;
		}
	}

	private static final Map<UUID, ArrayDeque<Vec3>> PATHS = new HashMap<>();
	private static final Map<UUID, Integer> READY = new HashMap<>();
	private static final List<Vortex> VORTICES = new ArrayList<>();

	private ChronoRings() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ChronoRings::tickVortices);
	}

	/** Each tick (server) for every speedster: its path is remembered, and a closed circle raises a vortex. */
	static void tick(ServerPlayer player, ServerLevel level, boolean overclocked) {
		if (!overclocked || !player.isSprinting() || !player.onGround() || player.isPassenger()) {
			PATHS.remove(player.getUUID());
			return;
		}
		ArrayDeque<Vec3> path = PATHS.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
		path.addLast(player.position());
		while (path.size() > HISTORY) {
			path.removeFirst();
		}
		if (path.size() < MIN_LOOP_TICKS + 2 || player.tickCount % 2 != 0 || READY.getOrDefault(player.getUUID(), 0) > player.tickCount) {
			return;
		}
		List<Vec3> points = new ArrayList<>(path);
		Vec3 now = points.get(points.size() - 1);
		for (int i = points.size() - 1 - MIN_LOOP_TICKS; i >= 0 && points.size() - 1 - i <= MAX_LOOP_TICKS; i--) {
			Vec3 then = points.get(i);
			if (Math.hypot(now.x - then.x, now.z - then.z) <= CLOSE) {
				Vec3 centre = circle(points.subList(i, points.size()));
				if (centre != null) {
					double radius = radius(points.subList(i, points.size()), centre);
					PATHS.remove(player.getUUID());
					READY.put(player.getUUID(), player.tickCount + COOLDOWN);
					raise(player, level, centre, radius);
				}
				return;
			}
		}
	}

	/** The centre of {@code loop} if it is a circle (round enough, one full turn the same way), else null. */
	private static Vec3 circle(List<Vec3> loop) {
		double cx = 0.0, cz = 0.0, cy = 0.0;
		for (Vec3 p : loop) {
			cx += p.x;
			cz += p.z;
			cy += p.y;
		}
		cx /= loop.size();
		cz /= loop.size();
		cy /= loop.size();
		Vec3 centre = new Vec3(cx, cy, cz);
		double mean = radius(loop, centre);
		if (mean < MIN_RADIUS || mean > MAX_RADIUS) {
			return null;
		}
		double turn = 0.0, last = Double.NaN;
		for (Vec3 p : loop) {
			if (Math.abs(Math.hypot(p.x - cx, p.z - cz) - mean) > mean * ROUNDNESS) {
				return null;
			}
			double angle = Math.atan2(p.z - cz, p.x - cx);
			if (!Double.isNaN(last)) {
				double d = angle - last;
				while (d > Math.PI) {
					d -= Math.PI * 2.0;
				}
				while (d < -Math.PI) {
					d += Math.PI * 2.0;
				}
				turn += d;
			}
			last = angle;
		}
		return Math.abs(turn) >= FULL_TURN ? centre : null;
	}

	private static double radius(List<Vec3> loop, Vec3 centre) {
		double sum = 0.0;
		for (Vec3 p : loop) {
			sum += Math.hypot(p.x - centre.x, p.z - centre.z);
		}
		return sum / loop.size();
	}

	private static void raise(ServerPlayer player, ServerLevel level, Vec3 centre, double radius) {
		VORTICES.add(new Vortex(level, player.getUUID(), centre, radius));
		MagicFx.ring(level, centre.add(0.0, 0.1, 0.0), (float)radius, GOLD);
		level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.4F, 0.7F);
	}

	private static void tickVortices(MinecraftServer server) {
		for (Iterator<Vortex> it = VORTICES.iterator(); it.hasNext();) {
			Vortex v = it.next();
			if (--v.left <= 0 || v.level.getServer() != server) {
				it.remove();
				continue;
			}
			spin(v);
		}
	}

	private static void spin(Vortex v) {
		ServerLevel level = v.level;
		Vec3 c = v.centre;
		AABB box = new AABB(c.x - v.radius, c.y - 1.0, c.z - v.radius, c.x + v.radius, c.y + MAX_RISE + 3.0, c.z + v.radius);
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.getUUID().equals(v.owner) && !e.isSpectator())) {
			double rx = living.getX() - c.x, rz = living.getZ() - c.z;
			double d = Math.hypot(rx, rz);
			if (d > v.radius) {
				continue;
			}
			double tx = d < 0.4 ? 1.0 : -rz / d, tz = d < 0.4 ? 0.0 : rx / d;
			double inx = d < 0.4 ? 0.0 : -rx / d * PULL, inz = d < 0.4 ? 0.0 : -rz / d * PULL;
			double rise = living.getY() - c.y < MAX_RISE ? LIFT : Math.min(0.0, living.getDeltaMovement().y);
			living.setDeltaMovement(tx * SWEEP + inx, rise, tz * SWEEP + inz);
			living.syncVelocity = true;
			if (living.isOnFire()) {
				living.clearFire();
			}
		}
		if (v.left % 4 == 0) {
			int r = (int)Math.ceil(v.radius);
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (dx * dx + dz * dz > v.radius * v.radius) {
						continue;
					}
					for (int dy = -1; dy <= 4; dy++) {
						BlockPos pos = BlockPos.containing(c.x + dx, c.y + dy, c.z + dz);
						if (level.getBlockState(pos).is(BlockTags.FIRE)) {
							level.removeBlock(pos, false);
							level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 1.4F);
						}
					}
				}
			}
		}
		if (v.left % 10 == 0) {
			MagicFx.sigil(level, c.add(0.0, 0.05, 0.0), (float)v.radius, GOLD, 14);
		}
		// the air spirals up round the ring
		double t = (VORTEX_TICKS - v.left) / (double)VORTEX_TICKS;
		ParticleOptions mote = MagicFx.mote(GOLD, 1.0F);
		for (int arm = 0; arm < 3; arm++) {
			double a = (VORTEX_TICKS - v.left) * 0.5 + arm * Math.PI * 2.0 / 3.0;
			double r = v.radius * (0.35 + 0.65 * ((t * 3.0 + arm / 3.0) % 1.0));
			level.sendParticles(mote, c.x + Math.cos(a) * r, c.y + 0.2 + ((t * 4.0 + arm / 3.0) % 1.0) * MAX_RISE, c.z + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}
}
