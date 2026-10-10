package com.jaymospowers.power.gravity;

import com.jaymospowers.core.BlockCluster;
import com.jaymospowers.core.Fate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Collapsing Horizon (ascended and corrupted) is so dense that the world comes apart around it, blessing and curse
 * both. Every {@code TEAR_EVERY} a block within {@code TEAR_RADIUS} may tear loose (any block, wherever and however it
 * was placed: bases too; heavy ones, stone and harder, only {@code HEAVY_SHARE} as often). It trembles and cracks for
 * {@code STRUGGLE_TICKS}, comes free, drifts in (faster as it nears) and joins the {@code MAX_ORBIT} at most that
 * circle you. Lift (R) can take one from the ring; right click while holding it pockets it. Charging Crush (B) draws
 * the ring to the target, and the release flings them outward in a shockwave that hurts ({@code SHOCK_PER_BLOCK} for
 * each). Crush released inside your own Gravity Well (the cave-in) rains them instead: one every {@code RAIN_EVERY},
 * each lining up over the nearest creature the well caught and plummeting onto it.
 */
final class GravityOrbit {
	static final double TEAR_RADIUS = 5.0;
	static final int TEAR_EVERY = 30, STRUGGLE_TICKS = 25, MAX_ORBIT = 3;
	static final float TEAR_CHANCE = 0.6F, HEAVY_SHARE = 0.1F;
	/** Heavy: at least this hard (stone is 1.5), and not wood. */
	static final float HEAVY_HARDNESS = 1.5F;
	/** The ring: its radius and height about you, how fast it turns (radians/tick); the drift in. */
	static final double ORBIT_RADIUS = 2.6, ORBIT_HEIGHT = 1.2, ORBIT_TURN = 0.06, DRIFT = 0.08, DRIFT_MAX = 0.9;
	/** The Crush shockwave: hurt per orbiting block, to everything in the blast, and how hard they're thrown out. */
	static final float SHOCK_PER_BLOCK = 2.0F;
	static final double SHOCK_THROW = 1.4;
	/** The rain (G+B): one block let go this often (ticks), how long it takes to line up over its target, how high above it, how fast it comes down. */
	static final int RAIN_EVERY = 8, RAIN_ALIGN_TICKS = 5;
	static final double RAIN_HEIGHT = 5.0, RAIN_SPEED = 2.2, RAIN_HOVER = 3.0;

	/** A block coming loose: where, and since when. */
	private record Struggle(BlockPos pos, long since) {
	}

	private static final class Ring {
		final List<Integer> orbiting = new ArrayList<>();
		final List<Integer> drifting = new ArrayList<>();
		Struggle struggle;
		/** Being drawn to a Crush's target (B held). */
		Vec3 crushAt;
	}

	private static final Map<UUID, Ring> RINGS = new HashMap<>();

	/** A ring raining down on a cave-in, one block at a time. */
	private static final class Rain {
		final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
		final UUID owner;
		final Vec3 center;
		final double radius;
		final List<Integer> queue;
		long next;
		/** The block lining up now (-1: none), since when, and from where. */
		int aligning = -1;
		long alignFrom;
		Vec3 alignStart = Vec3.ZERO;

		Rain(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, UUID owner, Vec3 center, double radius, List<Integer> queue, long next) {
			this.dimension = dimension;
			this.owner = owner;
			this.center = center;
			this.radius = radius;
			this.queue = queue;
			this.next = next;
		}
	}

	private static final List<Rain> RAINS = new ArrayList<>();

	private GravityOrbit() {
	}

	static boolean collapsingHorizon(ServerPlayer player) {
		return Fate.isAscended(player) && Fate.isCorrupted(player);
	}

	/** Every tick, for a Collapsing Horizon. */
	static void tick(ServerPlayer player) {
		Ring ring = RINGS.computeIfAbsent(player.getUUID(), k -> new Ring());
		ServerLevel level = player.level();
		long now = level.getGameTime();
		var random = player.getRandom();
		// a block straining against your pull
		if (ring.struggle != null) {
			BlockPos pos = ring.struggle.pos();
			long held = now - ring.struggle.since();
			BlockState state = level.getBlockState(pos);
			if (!BlockCluster.takeable(level, pos, 50.0F, null) || pos.distToCenterSqr(player.position()) > (TEAR_RADIUS + 2.0) * (TEAR_RADIUS + 2.0)) {
				level.destroyBlockProgress(crackId(player), pos, -1);
				ring.struggle = null;
			} else if (held >= STRUGGLE_TICKS) {
				level.destroyBlockProgress(crackId(player), pos, -1);
				ring.struggle = null;
				BlockCluster block = BlockCluster.of(level, List.of(pos), Vec3.ZERO, null);
				if (block != null) {
					block.script(true, true);
					ring.drifting.add(block.getId());
					level.playSound(null, pos, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0F, 0.6F);
				}
			} else {
				level.destroyBlockProgress(crackId(player), pos, (int)(held * 9 / STRUGGLE_TICKS));
				if (held % 5 == 0) {
					level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, state),
						pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.05);
					level.playSound(null, pos, state.getSoundType().getHitSound(), SoundSource.BLOCKS, 0.8F, 0.5F);
				}
			}
		} else if (now % TEAR_EVERY == 0 && ring.orbiting.size() + ring.drifting.size() < MAX_ORBIT && random.nextFloat() < TEAR_CHANCE) {
			BlockPos pos = player.blockPosition().offset(random.nextInt(11) - 5, random.nextInt(6) - 2, random.nextInt(11) - 5);
			BlockState state = level.getBlockState(pos);
			if (pos.distToCenterSqr(player.position()) <= TEAR_RADIUS * TEAR_RADIUS && BlockCluster.takeable(level, pos, 50.0F, null)
				&& (!heavy(level, pos, state) || random.nextFloat() < HEAVY_SHARE)
				&& !pos.equals(player.blockPosition().below())) {
				ring.struggle = new Struggle(pos.immutable(), now);
			}
		}
		// drifting in, faster as they come
		Vec3 me = player.position().add(0.0, ORBIT_HEIGHT, 0.0);
		for (Iterator<Integer> it = ring.drifting.iterator(); it.hasNext(); ) {
			Entity block = level.getEntity(it.next());
			if (!(block instanceof BlockCluster cluster) || !cluster.scripted()) {
				it.remove();
				continue;
			}
			Vec3 to = me.subtract(block.position());
			double d = to.length();
			if (d <= ORBIT_RADIUS + 0.3) {
				it.remove();
				ring.orbiting.add(block.getId());
				continue;
			}
			double speed = Math.min(DRIFT_MAX, DRIFT + 2.0 / Math.max(1.0, d * d) + DRIFT * (TEAR_RADIUS - Math.min(d, TEAR_RADIUS)));
			move(block, block.position().add(to.scale(speed / d)));
		}
		// the ring (or, Crush charging, a swarm over its target)
		int n = ring.orbiting.size();
		int i = 0;
		for (Iterator<Integer> it = ring.orbiting.iterator(); it.hasNext(); ) {
			Entity block = level.getEntity(it.next());
			if (!(block instanceof BlockCluster cluster) || !cluster.scripted()) {
				// taken by Lift, or gone
				it.remove();
				continue;
			}
			double a = now * ORBIT_TURN + i * Math.PI * 2.0 / Math.max(1, n);
			Vec3 spot;
			if (ring.crushAt != null) {
				spot = ring.crushAt.add(Math.cos(a * 3.0) * 1.5, 2.5 + Math.sin(a * 2.0) * 0.5, Math.sin(a * 3.0) * 1.5);
			} else {
				spot = me.add(Math.cos(a) * ORBIT_RADIUS, Math.sin(a * 2.0 + i) * 0.4, Math.sin(a) * ORBIT_RADIUS);
			}
			Vec3 at = block.position();
			move(block, at.add(spot.subtract(at).scale(ring.crushAt != null ? 0.25 : 0.5)));
			i++;
		}
	}

	private static boolean heavy(ServerLevel level, BlockPos pos, BlockState state) {
		return state.getDestroySpeed(level, pos) >= HEAVY_HARDNESS && !state.is(BlockTags.LOGS) && !state.is(BlockTags.PLANKS);
	}

	private static void move(Entity block, Vec3 to) {
		Vec3 was = block.position();
		block.setPos(to);
		block.setDeltaMovement(to.subtract(was));
	}

	private static int crackId(ServerPlayer player) {
		return player.getId() * 41 + 2000;
	}

	/** Crush charging toward {@code at} (null: let them come back to you). */
	static void drawTo(ServerPlayer player, Vec3 at) {
		Ring ring = RINGS.get(player.getUUID());
		if (ring != null) {
			ring.crushAt = at;
		}
	}

	/**
	 * Crush released at {@code at} in {@code radius}: the ring flies outward from there and everything in the blast is
	 * hurt for each block. Returns how many blocks were spent.
	 */
	static int shockwave(ServerPlayer player, Vec3 at, double radius) {
		Ring ring = RINGS.get(player.getUUID());
		if (ring == null) {
			return 0;
		}
		ring.crushAt = null;
		ServerLevel level = player.level();
		int spent = 0;
		var random = player.getRandom();
		for (int id : ring.orbiting) {
			Entity block = level.getEntity(id);
			if (block instanceof BlockCluster cluster) {
				cluster.script(false, false);
				double a = random.nextDouble() * Math.PI * 2.0;
				cluster.setDeltaMovement(Math.cos(a) * SHOCK_THROW, 0.4, Math.sin(a) * SHOCK_THROW);
				cluster.launch(player);
				spent++;
			}
		}
		ring.orbiting.clear();
		if (spent == 0) {
			return 0;
		}
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius + 1.5),
			e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand))) {
			living.setInvulnerableTime(0);
			GravityPower.touch(living, player);
			living.hurtServer(level, level.damageSources().playerAttack(player), SHOCK_PER_BLOCK * spent);
			Vec3 away = living.position().subtract(at).horizontal();
			away = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
			living.setDeltaMovement(away.scale(0.9).add(0.0, 0.4, 0.0));
			living.syncVelocity = true;
		}
		for (int k = 0; k < 32; k++) {
			double a = k / 32.0 * Math.PI * 2.0;
			level.sendParticles(ParticleTypes.CLOUD, at.x + Math.cos(a) * radius, at.y + 0.3, at.z + Math.sin(a) * radius, 2, 0.1, 0.1, 0.1, 0.05);
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.5F, 0.8F);
		return spent;
	}

	/**
	 * Crush released inside your own Gravity Well ({@code center}, {@code radius}): the ring (orbiting and drifting
	 * alike) is let go one block at a time, each onto the nearest creature the well caught (see {@link #tickRain}).
	 */
	static void rain(ServerPlayer player, Vec3 center, double radius) {
		Ring ring = RINGS.get(player.getUUID());
		if (ring == null) {
			return;
		}
		ring.crushAt = null;
		List<Integer> queue = new ArrayList<>(ring.orbiting);
		queue.addAll(ring.drifting);
		ring.orbiting.clear();
		ring.drifting.clear();
		if (queue.isEmpty()) {
			return;
		}
		ServerLevel level = player.level();
		RAINS.add(new Rain(level.dimension(), player.getUUID(), center, radius, queue, level.getGameTime() + RAIN_EVERY));
		level.playSound(null, center.x, center.y + RAIN_HOVER, center.z, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 1.2F, 0.5F);
	}

	static void tickRain(net.minecraft.server.MinecraftServer server) {
		if (RAINS.isEmpty()) {
			return;
		}
		for (Iterator<Rain> it = RAINS.iterator(); it.hasNext(); ) {
			Rain rain = it.next();
			ServerLevel level = server.getLevel(rain.dimension);
			if (level == null) {
				it.remove();
				continue;
			}
			long now = level.getGameTime();
			ServerPlayer owner = server.getPlayerList().getPlayer(rain.owner);
			// the rest hang over the well, waiting their turn
			rain.queue.removeIf(id -> !(level.getEntity(id) instanceof BlockCluster cluster) || !cluster.scripted());
			int n = rain.queue.size(), i = 0;
			for (int id : rain.queue) {
				Entity block = level.getEntity(id);
				double a = now * ORBIT_TURN * 2.0 + i * Math.PI * 2.0 / Math.max(1, n);
				Vec3 spot = rain.center.add(Math.cos(a) * 1.5, RAIN_HOVER + Math.sin(a * 2.0) * 0.4, Math.sin(a) * 1.5);
				move(block, block.position().add(spot.subtract(block.position()).scale(0.25)));
				i++;
			}
			if (rain.aligning >= 0) {
				Entity block = level.getEntity(rain.aligning);
				if (!(block instanceof BlockCluster cluster) || !cluster.scripted()) {
					rain.aligning = -1;
				} else {
					// over the nearest creature still in the well (or the middle, if none)
					LivingEntity target = nearest(level, rain, owner, block.position());
					Vec3 over = above(level, target != null ? target.position() : rain.center, target != null ? target.getBbHeight() : 0.0);
					double p = Math.min(1.0, (now - rain.alignFrom) / (double)RAIN_ALIGN_TICKS);
					p = p * p * (3.0 - 2.0 * p);
					move(block, rain.alignStart.lerp(over, p));
					if (now - rain.alignFrom >= RAIN_ALIGN_TICKS) {
						// and straight down onto it
						cluster.script(false, false);
						cluster.setDeltaMovement(0.0, -RAIN_SPEED, 0.0);
						cluster.needsSync = true;
						cluster.syncVelocity = true;
						cluster.launch(owner);
						if (target != null && owner != null) {
							GravityPower.touch(target, owner);
						}
						level.sendParticles(ParticleTypes.REVERSE_PORTAL, over.x, over.y + 0.5, over.z, 8, 0.3, 0.3, 0.3, 0.05);
						level.playSound(null, over.x, over.y, over.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 0.6F, 1.8F);
						rain.aligning = -1;
					}
				}
			}
			if (rain.aligning < 0 && now >= rain.next && !rain.queue.isEmpty()) {
				int id = rain.queue.remove(0);
				Entity block = level.getEntity(id);
				rain.aligning = id;
				rain.alignFrom = now;
				rain.alignStart = block.position();
				rain.next = now + RAIN_EVERY;
				level.playSound(null, block.getX(), block.getY(), block.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.8F, 0.6F);
			}
			if (rain.aligning < 0 && rain.queue.isEmpty()) {
				it.remove();
			}
		}
	}

	/** The living thing in the rain's well nearest {@code from}, not its owner. */
	private static LivingEntity nearest(ServerLevel level, Rain rain, ServerPlayer owner, Vec3 from) {
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(rain.center, rain.center).inflate(rain.radius),
			e -> e != owner && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && e.position().distanceTo(rain.center) <= rain.radius)) {
			double d = living.position().distanceToSqr(from);
			if (d < bestD) {
				bestD = d;
				best = living;
			}
		}
		return best;
	}

	/** Up to {@code RAIN_HEIGHT} over the top of something {@code height} tall at {@code at}, under any ceiling. */
	private static Vec3 above(ServerLevel level, Vec3 at, double height) {
		double y = at.y + height + 1.0;
		double top = at.y + height + RAIN_HEIGHT;
		while (y + 1.0 < top) {
			BlockPos pos = BlockPos.containing(at.x, y + 1.5, at.z);
			if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
				break;
			}
			y += 1.0;
		}
		return new Vec3(at.x, y, at.z);
	}

	/** The form is gone (or the player): the ring falls apart. */
	static void drop(ServerPlayer player) {
		Ring ring = RINGS.remove(player.getUUID());
		if (ring == null) {
			return;
		}
		ServerLevel level = player.level();
		if (ring.struggle != null) {
			level.destroyBlockProgress(crackId(player), ring.struggle.pos(), -1);
		}
		List<Integer> all = new ArrayList<>(ring.orbiting);
		all.addAll(ring.drifting);
		for (int id : all) {
			if (level.getEntity(id) instanceof BlockCluster cluster) {
				cluster.script(false, false);
			}
		}
	}

	/** Lift took this block out of the ring. */
	static void taken(ServerPlayer player, Entity block) {
		Ring ring = RINGS.get(player.getUUID());
		if (ring != null) {
			ring.orbiting.remove((Integer)block.getId());
			ring.drifting.remove((Integer)block.getId());
		}
		if (block instanceof BlockCluster cluster) {
			cluster.script(false, false);
		}
	}

	static void clear() {
		RINGS.clear();
		RAINS.clear();
	}

	/** (for HUD/debug) how many circle this player. */
	static int count(ServerPlayer player) {
		Ring ring = RINGS.get(player.getUUID());
		return ring == null ? 0 : ring.orbiting.size();
	}
}
