package com.jaymoskingdoms.sailing;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.490) What a cannon shot does where it lands: it explodes, and the blast breaks blocks the way TNT's does, by their
 * blast resistance. Whatever's loose (soil, sand, plants, leaves) always goes; anything harder breaks if the blast is
 * still strong enough where it stands, strongest at the middle and weaker toward the edge, so a heavy shell takes
 * stone, brick, timber and players' builds alike, and the heavier shot strip loose soil below on down to the rock.
 * Nothing is broken at the moment of impact: the crater is queued and worked off a little each tick, outward from the
 * middle, only near players, at most {@link #BLOCKS_PER_TICK} blocks a tick for everything shelled at once, the way the
 * flood is spread ({@code Calamities}). Most of what's destroyed drops nothing. Never touched: bedrock and what's as
 * hard as obsidian, anything with a block entity (so chests and their loot survive), and, from a mob's gun, anything at
 * all while mob griefing is off.
 */
public final class Bombardment {
	/** Blocks removed per tick, over all craters; blocks looked at per tick at most; columns waiting at most. */
	static final int BLOCKS_PER_TICK = 80, LOOKS_PER_TICK = 400, MOST_PENDING = 5000;
	/** A crater with no player within this far (blocks) waits; it's dropped after waiting this long (ticks). */
	static final double NEAR = 128.0;
	static final int MOST_WAIT = 200;
	/** A crater's deep stripping goes this far below its floor through loose soil, to the stone, and over this share of its radius. */
	static final int STRIP_DEEP = 10;
	static final double STRIP_SHARE = 0.7;
	/** Nothing this tough (explosion resistance, like obsidian's) breaks. */
	static final float TOUGHEST = 1200.0F;
	/** At a crater's edge its blast keeps this share of its strength; each block's blast is jittered by this share either way. */
	static final float EDGE_STRENGTH = 0.35F, JITTER = 0.2F;
	/** The share of broken blocks that fling up dust, and the number of blocks between thuds. */
	static final float DUST = 0.12F;
	static final int THUD_EVERY = 25;

	/** How much a crater takes. */
	public enum Strip {
		/** Only loose soil, sand, gravel, snow, plants and leaves (chain and grape: they tear, they don't explode). */
		SOFT,
		/** An explosion: anything whose blast resistance its blast still beats where it stands. */
		STRUCTURES,
		/** The same, for the heavy shot and shells (kept apart for their names in {@code ShotKind}). */
		MASONRY
	}

	/**
	 * A crater's shape: its radius, how far it goes down and up (blocks), its blast strength at the middle (the blast
	 * resistance it beats there: stone and cobble are 6, TNT's own blast is about 4 near the middle), what it takes, how
	 * many blocks it destroys per one that drops its items, and whether it strips the soil below its floor on down to the stone.
	 */
	public record Crater(double radius, int down, int up, float reach, Strip strip, int dropEvery, boolean stripDown) {
	}

	/** A field cannon's ball: a small blast, about a TNT's. */
	public static final Crater FIELD = new Crater(2.6, 2, 2, 6.5F, Strip.STRUCTURES, 16, false);

	private static final class Job {
		final ServerLevel level;
		final BlockPos centre;
		final Crater crater;
		final List<int[]> columns;
		int column, y = Integer.MIN_VALUE, wait, removed;
		/** (0.7.490) this column kept a solid block inside the crater below the centre, so nothing under it is stripped. */
		boolean kept;

		Job(ServerLevel level, BlockPos centre, Crater crater, List<int[]> columns) {
			this.level = level;
			this.centre = centre;
			this.crater = crater;
			this.columns = columns;
		}
	}

	private static final ArrayDeque<Job> JOBS = new ArrayDeque<>();
	private static int pending;

	private Bombardment() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> tick());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			JOBS.clear();
			pending = 0;
		});
	}

	/**
	 * {@code crater} is to be blasted at {@code at}, by {@code by} (whose gun it was). Queued, then worked off over the next
	 * few seconds. Nothing if the shot is a mob's and mob griefing is off, no player is near, or too much is waiting already.
	 */
	public static void crater(ServerLevel level, Vec3 at, Crater crater, Entity by) {
		if (!(by instanceof Player) && !level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			return;
		}
		boolean near = false;
		for (ServerPlayer p : level.players()) {
			near |= p.distanceToSqr(at) <= NEAR * NEAR;
		}
		if (!near || pending > MOST_PENDING) {
			return;
		}
		BlockPos centre = BlockPos.containing(at);
		int reach = (int)Math.ceil(crater.radius());
		double stripR = crater.radius() * STRIP_SHARE;
		List<int[]> columns = new ArrayList<>();
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				if (Math.sqrt(dx * dx + dz * dz) <= crater.radius()) {
					columns.add(new int[] {centre.getX() + dx, centre.getZ() + dz});
				}
			}
		}
		if (columns.isEmpty()) {
			return;
		}
		// outward from the middle, the way a blast spreads
		columns.sort(Comparator.comparingDouble(c -> Math.hypot(c[0] - centre.getX(), c[1] - centre.getZ())));
		JOBS.add(new Job(level, centre, crater, columns));
		pending += columns.size();
	}

	private static void tick() {
		if (JOBS.isEmpty()) {
			return;
		}
		int budget = BLOCKS_PER_TICK, looks = LOOKS_PER_TICK;
		for (Iterator<Job> it = JOBS.iterator(); it.hasNext() && budget > 0 && looks > 0;) {
			Job job = it.next();
			boolean near = false;
			for (ServerPlayer p : job.level.players()) {
				near |= p.distanceToSqr(job.centre.getX(), job.centre.getY(), job.centre.getZ()) <= NEAR * NEAR;
			}
			if (!near || !job.level.hasChunkAt(job.centre)) {
				if (++job.wait > MOST_WAIT) {
					pending -= job.columns.size() - job.column;
					it.remove();
				}
				continue;
			}
			job.wait = 0;
			int before = job.column;
			int[] spent = work(job, budget, looks);
			budget -= spent[0];
			looks -= spent[1];
			pending -= job.column - before;
			if (job.column >= job.columns.size()) {
				it.remove();
			}
		}
		if (JOBS.isEmpty()) {
			pending = 0;
		}
	}

	/** Works {@code job} until it has removed {@code budget} blocks or looked at {@code looks}; the blocks removed and looked at. */
	private static int[] work(Job job, int budget, int looks) {
		ServerLevel level = job.level;
		Crater c = job.crater;
		int removed = 0, looked = 0;
		int top = job.centre.getY() + c.up();
		int bottom = Math.max(level.getMinY(), job.centre.getY() - c.down() - (c.stripDown() ? STRIP_DEEP : 0));
		while (job.column < job.columns.size() && removed < budget && looked < looks) {
			int[] col = job.columns.get(job.column);
			if (job.y == Integer.MIN_VALUE) {
				job.y = top;
			}
			double flat = Math.hypot(col[0] - job.centre.getX(), col[1] - job.centre.getZ());
			boolean done = false;
			while (job.y >= bottom && removed < budget && looked < looks) {
				BlockPos pos = new BlockPos(col[0], job.y, col[1]);
				job.y--;
				looked++;
				if (!level.hasChunkAt(pos)) {
					continue;
				}
				BlockState state = level.getBlockState(pos);
				int dy = pos.getY() - job.centre.getY();
				double span = dy < 0 ? Math.max(1, c.down()) : Math.max(1, c.up());
				double r = flat / c.radius(), v = dy / span;
				if (r * r + v * v <= 1.0) {
					if (breaks(level, pos, state, c, Math.sqrt(r * r + v * v))) {
						remove(level, pos, state, c, job.removed++);
						removed++;
					} else if (dy < 0 && !state.isAir() && !(state.getBlock() instanceof LiquidBlock)) {
						// (0.7.490) a kept floor or stone below the centre shields what lies under it: no stripping beneath
						job.kept = true;
					}
				} else if (job.kept) {
					done = true;
					break;
				} else if (c.stripDown() && dy < 0 && flat <= c.radius() * STRIP_SHARE) {
					// below the floor: loose soil goes on down to the stone, and there it stops
					if (state.isAir() || state.getBlock() instanceof LiquidBlock) {
						continue;
					}
					if (soft(level, pos, state) && allowed(level, pos, state)) {
						remove(level, pos, state, c, job.removed++);
						removed++;
					} else {
						done = true;
						break;
					}
				}
			}
			if (done || job.y < bottom) {
				job.column++;
				job.y = Integer.MIN_VALUE;
				job.kept = false;
			}
		}
		return new int[] {removed, looked};
	}

	private static void remove(ServerLevel level, BlockPos pos, BlockState state, Crater c, int nth) {
		if (nth % Math.max(1, c.dropEvery()) == 0) {
			Block.dropResources(state, level, pos);
		}
		BlockState left = state.getFluidState().isEmpty() ? Blocks.AIR.defaultBlockState() : state.getFluidState().createLegacyBlock();
		level.setBlock(pos, left, Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
		if (level.getRandom().nextFloat() < DUST) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.4, 0.4, 0.4, 0.1);
		}
		if (nth % THUD_EVERY == 0) {
			level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.6F, 0.9F + level.getRandom().nextFloat() * 0.4F);
		}
	}

	// ---- what breaks --------------------------------------------------------------------------------------------------

	/**
	 * Whether the crater {@code c} takes the block at {@code pos}, {@code out} of the way from its middle to its edge (0 to
	 * 1): loose ground always, anything else if the blast there (weaker toward the edge, a little random) beats its blast
	 * resistance, the way TNT's does.
	 */
	static boolean breaks(Level level, BlockPos pos, BlockState state, Crater c, double out) {
		if (state.isAir() || state.getBlock() instanceof LiquidBlock || !allowed(level, pos, state)) {
			return false;
		}
		if (soft(level, pos, state)) {
			return true;
		}
		if (c.strip() == Strip.SOFT) {
			return false;
		}
		double blast = c.reach() * (1.0 - (1.0 - EDGE_STRENGTH) * Math.min(1.0, out)) * (1.0 + JITTER * (level.getRandom().nextFloat() * 2.0F - 1.0F));
		return state.getBlock().getExplosionResistance() <= blast;
	}

	/** Whatever no shot may break: the unbreakable, the as-tough-as-obsidian, a block entity (a chest keeps its loot). */
	private static boolean allowed(Level level, BlockPos pos, BlockState state) {
		return state.getDestroySpeed(level, pos) >= 0.0F && state.getBlock().getExplosionResistance() < TOUGHEST
			&& !(state.getBlock() instanceof EntityBlock);
	}

	/** Loose ground and growth: soil, sand, gravel, snow, plants, leaves. */
	private static boolean soft(Level level, BlockPos pos, BlockState state) {
		return state.is(BlockTags.MINEABLE_WITH_SHOVEL) || state.is(BlockTags.LEAVES) || state.is(BlockTags.REPLACEABLE)
			|| state.is(BlockTags.CROPS) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)
			|| state.is(BlockTags.MINEABLE_WITH_HOE) && state.getDestroySpeed(level, pos) <= 1.0F;
	}
}
