package com.jaymospowers.power.gravity;

import com.jaymospowers.core.BlockCluster;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Rock flight, the earthboard (hold V once Weightless is yours; a tap still toggles Weightless). You stomp, and a
 * ragged crater of the ground under you ({@link #CRATER_RADIUS} across, like a creeper's blast) tears up and clumps
 * together under your feet; you ride the slab, steering it like a wind's flight (the client flies it:
 * {@code GravityRockFlightClient}). It can climb {@link #MAX_ALTITUDE} over the ground, struggling and shuddering in
 * the last {@link #STRUGGLE_BAND}, and is always sinking a little, like falling debris. Dust and grit pour from its
 * underside. Until the power is fully mastered (or ascended, or corrupted) you ride only while V is held; after that,
 * once it's up you stay on it with V let go, and press V again to step off. Stepping off, the board drops to the
 * ground whole and lies there for {@link #BOARD_LINGER} ticks (it can be stood on, and holding V on it rides it
 * again), then crumbles away. The rider stands in a surfing pose, leaning into turns ({@code GravityBoardPose}). The
 * slab is {@link GravityPower#ROCK_FLIGHT}'s chunk, carried under you.
 */
final class GravityRockFlight {
	/** How high over the ground it can go, and the band under that where it fights to stay up. */
	public static final double MAX_ALTITUDE = 19.0, STRUGGLE_BAND = 3.0;
	static final float EXHAUSTION = 0.02F, HARDEST = 50.0F;
	/** The crater it tears up: how wide (blocks from the middle), how deep (blocks), how ragged, and at most how many blocks. */
	static final double CRATER_RADIUS = 2.8, CRATER_DEPTH_WEIGHT = 1.6, CRATER_RAGGED = 1.0;
	static final int CRATER_DEPTH = 2, CRATER_MAX = 45;
	/** Stepped off, it lies whole this long (ticks), then crumbles. */
	static final int BOARD_LINGER = 160;

	private GravityRockFlight() {
	}

	/** Whether V toggles the board (let go and keep riding) rather than riding only while it's held. */
	static boolean toggles(ServerPlayer player) {
		return com.jaymospowers.core.Mastery.unlocked(player) >= com.jaymospowers.core.Mastery.ALL
			|| com.jaymospowers.core.Fate.isAscended(player) || com.jaymospowers.core.Fate.isCorrupted(player);
	}

	/** The stomp: a board lying underfoot comes back up, or the ground comes up under you. Returns false if there was nothing to take. */
	static boolean start(ServerPlayer player) {
		ServerLevel level = player.level();
		BlockCluster slab = boardUnder(player);
		if (slab != null) {
			// the same board again
			slab.removeAttached(BlockCluster.BOARD);
			slab.refreshBox();
		} else {
			slab = tearUp(player);
			if (slab == null) {
				return false;
			}
		}
		slab.script(true, true);
		player.setAttached(GravityPower.ROCK_FLIGHT, slab.getId());
		// up off the ground with it
		ServerPlayNetworking.send(player, new com.jaymospowers.power.web.WebNet.Nudge(new Vec3(0.0, 0.55, 0.0)));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.2F, 0.7F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_EMERGE, SoundSource.PLAYERS, 0.8F, 1.4F);
		com.jaymospowers.core.Shakes.heavy(player, player.position(), 10.0, 0.9F);
		BlockPos under = player.blockPosition().below();
		com.jaymospowers.core.Debris.puff(level, under, slab.pieces().isEmpty() ? level.getBlockState(under) : slab.pieces().get(0).blockState(), 2.0F);
		return true;
	}

	/** A dropped earthboard the player is standing on (or right over), or null. */
	private static @Nullable BlockCluster boardUnder(ServerPlayer player) {
		AABB feet = player.getBoundingBox().inflate(0.3, 0.0, 0.3).expandTowards(0.0, -0.8, 0.0);
		BlockCluster best = null;
		for (BlockCluster cluster : player.level().getEntitiesOfClass(BlockCluster.class, feet.inflate(4.0),
			c -> c.isAlive() && c.board() && !c.scripted())) {
			AABB box = cluster.getBoundingBox();
			if (box.intersects(feet) && player.getY() >= box.maxY - 0.5) {
				best = cluster;
				break;
			}
		}
		return best;
	}

	/** A ragged bowl of the ground under you, torn out as one slab (or null if none of it would come). */
	private static @Nullable BlockCluster tearUp(ServerPlayer player) {
		ServerLevel level = player.level();
		BlockPos under = player.blockPosition().below();
		int r = (int)Math.ceil(CRATER_RADIUS);
		Map<BlockPos, Double> rank = new HashMap<>();
		for (BlockPos pos : BlockPos.betweenClosed(under.offset(-r, -CRATER_DEPTH, -r), under.offset(r, 0, r))) {
			int dx = pos.getX() - under.getX(), dy = pos.getY() - under.getY(), dz = pos.getZ() - under.getZ();
			double d = Math.sqrt(dx * dx + dz * dz + dy * dy * CRATER_DEPTH_WEIGHT * CRATER_DEPTH_WEIGHT) + level.getRandom().nextDouble() * CRATER_RAGGED;
			if (d <= CRATER_RADIUS) {
				rank.put(pos.immutable(), d);
			}
		}
		List<BlockPos> around = new ArrayList<>(rank.keySet());
		around.sort(Comparator.comparingDouble(rank::get));
		List<BlockPos> taken = new ArrayList<>();
		for (BlockPos pos : around) {
			if (taken.size() >= CRATER_MAX) {
				break;
			}
			if (BlockCluster.takeable(level, pos, HARDEST, player) && level.getBlockState(pos).isSolid()) {
				taken.add(pos);
			}
		}
		// the block right underfoot has to come, or there's nothing to stand on
		if (taken.isEmpty() || !taken.contains(under) && !taken.contains(under.below())) {
			return null;
		}
		return BlockCluster.of(level, taken, Vec3.ZERO, player);
	}

	/** How far under the feet the slab's position rides, so its top is level with them (it's drawn 0.7 up, centred). */
	static double drop(BlockCluster slab) {
		return 0.7 + BlockCluster.bounds(slab.pieces()).getYsize() * 0.5;
	}

	/** Every tick while riding: the slab stays under your feet, and grit pours from it (more when it strains at the top). */
	static void tick(ServerPlayer player, int slabId) {
		ServerLevel level = player.level();
		Entity slab = level.getEntity(slabId);
		if (!(slab instanceof BlockCluster cluster) || !cluster.scripted()) {
			stop(player);
			return;
		}
		Vec3 to = player.position().subtract(0.0, drop(cluster), 0.0);
		Vec3 was = slab.position();
		slab.setPos(to);
		slab.setDeltaMovement(to.subtract(was));
		player.resetFallDistance();
		player.causeFoodExhaustion(EXHAUSTION);
		AABB bounds = BlockCluster.bounds(cluster.pieces());
		double spread = Math.max(0.8, bounds.getXsize() * 0.35);
		double height = altitude(player);
		boolean straining = height > MAX_ALTITUDE - STRUGGLE_BAND;
		if (player.tickCount % 2 == 0 && !cluster.pieces().isEmpty()) {
			// only from underneath: dust and grit falling away
			BlockParticleOption dust = new BlockParticleOption(ParticleTypes.FALLING_DUST, cluster.pieces().get(0).blockState());
			level.sendParticles(dust, to.x, to.y + 0.1, to.z, straining ? 12 : 4, spread, 0.0, spread, 0.0);
			if (straining) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, cluster.pieces().get(0).blockState()), to.x, to.y, to.z, 4,
					spread, 0.1, spread, 0.0);
			}
		}
		if (player.tickCount % (straining ? 10 : 30) == 0) {
			level.playSound(null, to.x, to.y, to.z, straining ? SoundEvents.STONE_BREAK : SoundEvents.GRAVEL_STEP, SoundSource.PLAYERS,
				straining ? 0.8F : 0.6F, 0.5F);
		}
	}

	/** How high the rider is over the ground (blocks), as far as the board's reach and a little more. */
	private static double altitude(ServerPlayer player) {
		Vec3 feet = player.position();
		HitResult ground = player.level().clip(new ClipContext(feet, feet.subtract(0.0, MAX_ALTITUDE + 3.0, 0.0), ClipContext.Block.COLLIDER,
			ClipContext.Fluid.ANY, player));
		return ground.getType() == HitResult.Type.MISS ? MAX_ALTITUDE + 3.0 : feet.y - ground.getLocation().y;
	}

	/** Off: the board drops to the ground whole and lies there a while (see {@link #BOARD_LINGER}). */
	static void stop(ServerPlayer player) {
		Integer id = player.getAttached(GravityPower.ROCK_FLIGHT);
		player.removeAttached(GravityPower.ROCK_FLIGHT);
		if (id == null) {
			return;
		}
		if (player.level().getEntity(id) instanceof BlockCluster cluster) {
			cluster.script(false, true);
			cluster.setAttached(BlockCluster.BOARD, true);
			cluster.refreshBox();
			cluster.linger(BOARD_LINGER, false);
			cluster.setDeltaMovement(0.0, -0.1, 0.0);
		}
		player.resetFallDistance();
	}
}
