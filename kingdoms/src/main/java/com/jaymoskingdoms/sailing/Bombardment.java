package com.jaymoskingdoms.sailing;

import com.jaymoskingdoms.Placed;
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
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.484) What a cannon shot does to the ground it lands on: a crater that strips soil, sand, wood, leaves and (for the
 * heavier shot) weak masonry down to the natural stone, the way a shelled shore would be left. Nothing is broken at the
 * moment of impact: the crater is queued and worked off a little each tick, outward from the middle, only near players,
 * at most {@link #BLOCKS_PER_TICK} blocks a tick for everything shelled at once, the way the flood is spread
 * ({@code Calamities}). Most of what's destroyed drops nothing. Never touched: bedrock and what's as hard as obsidian,
 * natural stone and ore, anything with a block entity (chests, spawners), anything a player placed ({@link Placed}), and,
 * from a mob's gun, anything at all while mob griefing is off.
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
	/** The share of broken blocks that fling up dust, and the number of blocks between thuds. */
	static final float DUST = 0.12F;
	static final int THUD_EVERY = 25;

	/** How much a crater takes. */
	public enum Strip {
		/** Loose soil, sand, gravel, snow, plants and leaves. */
		SOFT,
		/** And what's built light: timber, thatch, wool, glass, doors. */
		STRUCTURES,
		/** And weak masonry (cobble, brick, sandstone, plaster), up to the crater's reach. */
		MASONRY
	}

	/**
	 * A crater's shape: its radius, how far it goes down and up (blocks), the toughest (explosion resistance) masonry it
	 * breaks, what it takes, how many blocks it destroys per one that drops its items, and whether it strips the soil
	 * below its floor on down to the stone.
	 */
	public record Crater(double radius, int down, int up, float reach, Strip strip, int dropEvery, boolean stripDown) {
	}

	/** A field cannon's ball: it only scuffs the ground. */
	public static final Crater FIELD = new Crater(2.2, 1, 2, 1.0F, Strip.SOFT, 20, false);

	private static final class Job {
		final ServerLevel level;
		final BlockPos centre;
		final Crater crater;
		final List<int[]> columns;
		int column, y = Integer.MIN_VALUE, wait, removed;

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
					if (breaks(level, pos, state, c)) {
						remove(level, pos, state, c, job.removed++);
						removed++;
					}
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

	/** Whether the crater {@code c} takes the block at {@code pos}. */
	static boolean breaks(Level level, BlockPos pos, BlockState state, Crater c) {
		if (state.isAir() || state.getBlock() instanceof LiquidBlock || !allowed(level, pos, state)) {
			return false;
		}
		if (soft(level, pos, state)) {
			return true;
		}
		if (state.getBlock().getExplosionResistance() > c.reach()) {
			return false;
		}
		return switch (c.strip()) {
			case SOFT -> false;
			case STRUCTURES -> light(level, pos, state);
			case MASONRY -> light(level, pos, state) || masonry(state);
		};
	}

	/** Whatever no shot may break: the unbreakable, the tough, the natural stone and ore, a block entity, a player's own work. */
	private static boolean allowed(Level level, BlockPos pos, BlockState state) {
		if (state.getDestroySpeed(level, pos) < 0.0F || state.getBlock().getExplosionResistance() >= TOUGHEST) {
			return false;
		}
		if (state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER) || state.getBlock() instanceof DropExperienceBlock
			|| state.getBlock() instanceof EntityBlock) {
			return false;
		}
		return !(level instanceof ServerLevel server && Placed.any(server, pos));
	}

	/** Loose ground and growth: soil, sand, gravel, snow, plants, leaves. */
	private static boolean soft(Level level, BlockPos pos, BlockState state) {
		return state.is(BlockTags.MINEABLE_WITH_SHOVEL) || state.is(BlockTags.LEAVES) || state.is(BlockTags.REPLACEABLE)
			|| state.is(BlockTags.CROPS) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)
			|| state.is(BlockTags.MINEABLE_WITH_HOE) && state.getDestroySpeed(level, pos) <= 1.0F;
	}

	/** Timber, thatch, cloth, glass and the like: whatever's built light. */
	private static boolean light(Level level, BlockPos pos, BlockState state) {
		return state.is(BlockTags.MINEABLE_WITH_AXE) || state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS) || state.is(BlockTags.BEDS)
			|| state.is(BlockTags.BANNERS) || state.is(BlockTags.IMPERMEABLE) || state.getDestroySpeed(level, pos) <= 0.5F;
	}

	/** Worked stone that a heavy shot shatters: anything a pick mines that isn't natural stone (already ruled out). */
	private static boolean masonry(BlockState state) {
		return state.is(BlockTags.MINEABLE_WITH_PICKAXE);
	}
}
