package com.jaymoskingdoms;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.484) Cannon craters. A round shot that lands on shore tears the ground up: everything within the blast that the
 * shell can break (dirt, sand, gravel, wood, leaves, wool, glass, plank walls; stone, bricks and iron hold) is blown out
 * in a bowl, so a pirate broadside leaves a shelled coast down to its stone.
 *
 * <p>Like the meteor's carving ({@code GravityMeteor}), a crater isn't torn out in one tick: it is queued, worked from the
 * middle outward at {@link #PER_TICK} blocks a tick across all craters, and only while a player is within {@link #NEAR}
 * of it (a shell that lands where nobody is looking digs nothing). Only so many craters wait at once.
 */
public final class Blasts {
	// ================================================================================================================
	// Tuning
	/** What a shell tears through: blocks with an explosion resistance up to this. Stone (6) and bricks hold; wood (2 to 3), dirt, sand, glass, wool give. */
	public static final float SOFT = 4.5F;
	/** Blocks torn out each tick, all craters together. */
	static final int PER_TICK = 180;
	/** Craters waiting at once (the oldest is dropped past this), how near a player must be to dig one, and to take one on. */
	static final int MAX_PENDING = 24;
	static final double NEAR = 112.0, TAKE_ON = 96.0;
	/** The bowl is this much shallower than it is wide, and its rim is ragged from this share of its radius out. */
	static final double DEPTH_SHARE = 0.85, RAGGED_FROM = 0.7;
	/** How many blocks of a crater are left as rubble to pick up, at most, and the chance of each. */
	static final int RUBBLE_MOST = 4;
	static final float RUBBLE = 0.05F;
	// ================================================================================================================

	/** One crater being dug: where, how big, how hard a shell it was, who fired it, and the blocks still to go (middle first). */
	private static final class Crater {
		final ServerLevel level;
		final Vec3 at;
		final double radius;
		final float strength;
		final @Nullable Entity by;
		final ArrayDeque<BlockPos> todo = new ArrayDeque<>();
		int rubble;
		int torn;

		Crater(ServerLevel level, Vec3 at, double radius, float strength, @Nullable Entity by) {
			this.level = level;
			this.at = at;
			this.radius = radius;
			this.strength = strength;
			this.by = by;
		}
	}

	private static final List<Crater> PENDING = new ArrayList<>();

	private Blasts() {
	}

	/** A shell burst at {@code at} (on land): a crater of {@code radius} is queued, if anyone is near to see it and griefing allows. */
	public static void crater(ServerLevel level, Vec3 at, double radius, float strength, @Nullable Entity by) {
		if (!(by instanceof Player) && !level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			return;
		}
		if (!playerNear(level, at, TAKE_ON)) {
			return;
		}
		Crater crater = new Crater(level, at, radius, strength, by);
		int r = (int)Math.ceil(radius);
		BlockPos centre = BlockPos.containing(at);
		RandomSource random = level.getRandom();
		List<BlockPos> inside = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-r, -r, -r), centre.offset(r, r, r))) {
			double dx = pos.getX() + 0.5 - at.x, dy = (pos.getY() + 0.5 - at.y) / DEPTH_SHARE, dz = pos.getZ() + 0.5 - at.z;
			double d = Math.sqrt(dx * dx + dy * dy + dz * dz) / radius;
			if (d > 1.0 || d > RAGGED_FROM && random.nextDouble() < (d - RAGGED_FROM) / (1.0 - RAGGED_FROM) * 0.8) {
				continue;
			}
			inside.add(pos.immutable());
		}
		inside.sort(Comparator.comparingDouble(p -> p.distToCenterSqr(at)));
		crater.todo.addAll(inside);
		PENDING.add(crater);
		while (PENDING.size() > MAX_PENDING) {
			PENDING.removeFirst();
		}
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
	}

	/** Every tick: the waiting craters are dug a little, up to {@link #PER_TICK} blocks. */
	public static void tick(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}
		int budget = PER_TICK;
		Iterator<Crater> it = PENDING.iterator();
		while (it.hasNext() && budget > 0) {
			Crater crater = it.next();
			if (crater.level.getServer() != server) {
				it.remove();
				continue;
			}
			if (!playerNear(crater.level, crater.at, NEAR)) {
				// (nobody left to see it: the rest is let be)
				it.remove();
				continue;
			}
			int before = crater.torn;
			while (budget > 0 && !crater.todo.isEmpty()) {
				BlockPos pos = crater.todo.poll();
				budget--;
				tear(crater, pos);
			}
			if (crater.torn > before) {
				Vec3 at = crater.at;
				crater.level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.5, at.z, 3, crater.radius * 0.3, 0.4, crater.radius * 0.3, 0.02);
				crater.level.sendParticles(ParticleTypes.POOF, at.x, at.y + 0.5, at.z, 4, crater.radius * 0.4, 0.5, crater.radius * 0.4, 0.04);
				if (crater.level.getGameTime() % 3 == 0) {
					crater.level.playSound(null, at.x, at.y, at.z, SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, 1.6F, 0.6F);
				}
			}
			if (crater.todo.isEmpty()) {
				it.remove();
			}
		}
	}

	/** Whether a shell of {@code strength} can tear out {@code state} (not bedrock, liquids, air; nothing tougher than the shell). */
	public static boolean breaks(ServerLevel level, BlockPos pos, BlockState state, float strength) {
		return !state.isAir() && !(state.getBlock() instanceof LiquidBlock) && state.getDestroySpeed(level, pos) >= 0.0F
			&& state.getBlock().getExplosionResistance() <= strength;
	}

	private static void tear(Crater crater, BlockPos pos) {
		ServerLevel level = crater.level;
		if (!level.isLoaded(pos)) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		if (!breaks(level, pos, state, crater.strength)) {
			return;
		}
		boolean rubble = crater.rubble < RUBBLE_MOST && level.getRandom().nextFloat() < RUBBLE;
		if (level.getBlockEntity(pos) != null || rubble) {
			// (containers spill; some of the rest is left to pick up)
			level.destroyBlock(pos, true, crater.by);
			crater.rubble += rubble ? 1 : 0;
		} else {
			// (what it was waterlogged with stays)
			level.setBlock(pos, state.getFluidState().createLegacyBlock(), Block.UPDATE_ALL);
		}
		crater.torn++;
	}

	private static boolean playerNear(ServerLevel level, Vec3 at, double range) {
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.distanceToSqr(at) <= range * range) {
				return true;
			}
		}
		return false;
	}

	public static void clear() {
		PENDING.clear();
	}
}
