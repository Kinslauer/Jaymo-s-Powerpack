package com.jaymoskingdoms.wild;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.PowersCompat;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (medieval picks, de-spiders; Andy: "you can awaken from a death roll, webbed up inside a lair instead of poisoned and
 * out in the world") The spiders' wake. When a spider downed you, or you fell underground with spiders near, and you're
 * left for dead (Jaymo's Powers' death by cause asks {@link #take} through {@code Mortality.LAIRS}), you come to in their
 * lair instead of out in the wild with the bite's poison: wrapped head to foot (Powers' Webbed, every layer), with
 * everything you carried in a cache by the wall, wrapped in silk with eggs laid on it.
 * <ul>
 * <li><b>The lair:</b> a nest cave near by if there is one (a spider spawner among cobwebs, the old mines' nests; you wake
 * a little way off from it, {@link #NEST_KEEP_OFF}), else a small round hollow the spiders dug: beneath where you
 * fell if that was open ground (with a burrow sloping up to the air), or about where you fell underground. Only natural
 * ground is dug; with nowhere to dig, nobody takes you and you wake as anyone left for dead.</li>
 * <li><b>The cache:</b> a chest (two if it needs) at the hollow's wall, cobwebs round it and eggs laid on top.</li>
 * </ul>
 * Lore: the silk deeps (Medieval-Plan): spiders that take a living thing alive carry it home wrapped, which is how the
 * dead sometimes wake there.
 */
public final class SpiderLairs {
	// ---- tuning ----
	/** Underground (no sky over you, below the sea), a spider this near (blocks) is enough for them to take you. */
	static final double SPIDERS_NEAR = 16.0;
	/** A nest cave's spawner is looked for this many chunks round where you fell; one counts with this many cobwebs round it. */
	static final int NEST_CHUNKS = 3, NEST_WEBS = 3, NEST_WEB_REACH = 4;
	/** In a nest cave you wake between these distances (blocks) from its spawner. */
	static final int NEST_KEEP_OFF = 10, NEST_KEEP_MOST = 15;
	/** A hollow dug under open ground has its floor this far down (blocks). */
	static final int DEPTH = 6;
	/** The hollow's half width and inside height (blocks), and how many cobwebs hang in it. */
	static final int HALF = 3, HEIGHT = 3, WEBS = 9;
	/** Eggs laid on the cache. */
	static final int EGGS = 3;

	private SpiderLairs() {
	}

	public static void register() {
		PowersCompat.tellLairs(SpiderLairs::take);
	}

	/** Where {@code player}, downed by {@code by} (or nothing known), wakes in a spiders' lair, or null: they weren't taken. */
	public static @Nullable GlobalPos take(ServerPlayer player, @Nullable Entity by) {
		ServerLevel level = player.level();
		BlockPos fell = player.blockPosition();
		boolean underground = !level.canSeeSky(fell) && fell.getY() < level.getSeaLevel();
		if (!(by instanceof Spider) && !(underground && !level.getEntitiesOfClass(Spider.class, new AABB(fell).inflate(SPIDERS_NEAR),
			Entity::isAlive).isEmpty())) {
			return null;
		}
		RandomSource random = player.getRandom();
		BlockPos at = nest(level, fell, random);
		BlockPos cache = at == null ? null : cacheSpot(level, at, 3);
		if (at == null || cache == null) {
			at = hollow(level, fell, !underground && level.canSeeSky(fell), random);
			cache = at == null ? null : cacheSpot(level, at, HALF);
		}
		if (at == null) {
			return null;
		}
		stow(level, player, cache, at, random);
		JaymosKingdoms.LOGGER.info("{} was carried off to a spiders' lair at {}", player.getScoreboardName(), at);
		return GlobalPos.of(level.dimension(), at);
	}

	// ---- a nest cave ----

	/** A spot to lie in a nest cave near {@code from}: a spawner with cobwebs round it, and open floor a little way off it. */
	private static @Nullable BlockPos nest(ServerLevel level, BlockPos from, RandomSource random) {
		int cx = from.getX() >> 4, cz = from.getZ() >> 4;
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (int x = cx - NEST_CHUNKS; x <= cx + NEST_CHUNKS; x++) {
			for (int z = cz - NEST_CHUNKS; z <= cz + NEST_CHUNKS; z++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
				if (chunk == null) {
					continue;
				}
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					if (be instanceof SpawnerBlockEntity && webbed(level, be.getBlockPos())) {
						double d = be.getBlockPos().distSqr(from);
						if (d < bestD) {
							bestD = d;
							best = be.getBlockPos();
						}
					}
				}
			}
		}
		if (best == null) {
			return null;
		}
		for (int tries = 0; tries < 40; tries++) {
			double a = random.nextDouble() * Math.PI * 2.0;
			double r = NEST_KEEP_OFF + random.nextDouble() * (NEST_KEEP_MOST - NEST_KEEP_OFF);
			BlockPos column = BlockPos.containing(best.getX() + Math.cos(a) * r, best.getY(), best.getZ() + Math.sin(a) * r);
			for (int dy = 3; dy >= -3; dy--) {
				BlockPos feet = column.above(dy);
				if (standable(level, feet) && !level.canSeeSky(feet)) {
					return feet;
				}
			}
		}
		return null;
	}

	private static boolean webbed(ServerLevel level, BlockPos spawner) {
		int webs = 0;
		for (BlockPos p : BlockPos.betweenClosed(spawner.offset(-NEST_WEB_REACH, -2, -NEST_WEB_REACH), spawner.offset(NEST_WEB_REACH, 2, NEST_WEB_REACH))) {
			if (level.getBlockState(p).is(Blocks.COBWEB) && ++webs >= NEST_WEBS) {
				return true;
			}
		}
		return false;
	}

	// ---- a hollow they dug ----

	/**
	 * A round hollow dug in natural ground: under {@code fell} with a burrow up to the air ({@code open}), or about it
	 * (underground). Its floor's middle (where they lie), or null if the ground there can't be dug.
	 */
	private static @Nullable BlockPos hollow(ServerLevel level, BlockPos fell, boolean open, RandomSource random) {
		BlockPos floor = open ? fell.below(DEPTH) : fell;
		if (floor.getY() - 1 <= level.getMinY() + 2) {
			return null;
		}
		List<BlockPos> room = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(floor.offset(-HALF, 0, -HALF), floor.offset(HALF, HEIGHT - 1, HALF))) {
			double dx = (p.getX() - floor.getX()) / (HALF + 0.5), dz = (p.getZ() - floor.getZ()) / (HALF + 0.5);
			double dy = (p.getY() - floor.getY() + 0.5 - HEIGHT * 0.5) / (HEIGHT * 0.5 + 0.3);
			if (dx * dx + dz * dz + dy * dy * 0.6 <= 1.0) {
				room.add(p.immutable());
			}
		}
		List<BlockPos> burrow = new ArrayList<>();
		if (open) {
			// a burrow sloping up a block a step, from the hollow's edge to the air, whichever way the ground lets
			Direction[] ways = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
			int start = random.nextInt(4);
			for (int i = 0; i < 4 && burrow.isEmpty(); i++) {
				List<BlockPos> path = burrow(level, floor, fell, ways[(start + i) % 4]);
				if (path != null) {
					burrow.addAll(path);
				}
			}
			if (burrow.isEmpty()) {
				return null;
			}
		}
		for (BlockPos p : room) {
			if (!diggable(level.getBlockState(p))) {
				return null;
			}
		}
		if (!level.getBlockState(floor.below()).isFaceSturdy(level, floor.below(), Direction.UP)) {
			return null;
		}
		for (BlockPos p : room) {
			level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
		for (BlockPos p : burrow) {
			level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
		// silk strung across the roof and the corners, never where they lie
		for (int i = 0, placed = 0; i < WEBS * 4 && placed < WEBS; i++) {
			BlockPos p = room.get(random.nextInt(room.size()));
			if (Math.abs(p.getX() - floor.getX()) + Math.abs(p.getZ() - floor.getZ()) <= 1 || !level.getBlockState(p).isAir()) {
				continue;
			}
			boolean touching = false;
			for (Direction d : Direction.values()) {
				touching |= level.getBlockState(p.relative(d)).isSolid();
			}
			if (touching) {
				level.setBlock(p, Blocks.COBWEB.defaultBlockState(), Block.UPDATE_ALL);
				placed++;
			}
		}
		return floor;
	}

	/** The burrow from the hollow's edge toward {@code way}, up a block a step to the air over {@code fell}'s height, or null. */
	private static @Nullable List<BlockPos> burrow(ServerLevel level, BlockPos floor, BlockPos fell, Direction way) {
		List<BlockPos> path = new ArrayList<>();
		BlockPos at = floor.relative(way, HALF);
		for (int step = 0; step <= DEPTH + 2; step++) {
			at = at.relative(way).above();
			for (BlockPos p : new BlockPos[] {at, at.above()}) {
				BlockState s = level.getBlockState(p);
				if (!s.isAir() && !diggable(s)) {
					return null;
				}
				path.add(p);
			}
			if (at.getY() >= fell.getY() && level.canSeeSky(at.above())) {
				return path;
			}
		}
		return null;
	}

	/** A floor spot by the wall about {@code centre} ({@code reach} out) for the cache, or null. */
	private static @Nullable BlockPos cacheSpot(ServerLevel level, BlockPos centre, int reach) {
		for (int r = reach; r >= 1; r--) {
			for (Direction d : new Direction[] {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
				BlockPos p = centre.relative(d, r);
				if (standable(level, p) && level.getBlockState(p.relative(d.getClockWise())).isAir()) {
					return p;
				}
			}
		}
		return null;
	}

	/** Open air two high over a sturdy floor, out of anything wet. */
	private static boolean standable(ServerLevel level, BlockPos feet) {
		return level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP) && level.getBlockState(feet).isAir()
			&& level.getBlockState(feet.above()).isAir() && level.getFluidState(feet.below()).isEmpty();
	}

	/** The land's own ground: stone, dirt, sand, gravel, ores; or a cobweb, or air (a cave's open space). */
	private static boolean diggable(BlockState s) {
		return s.isAir() || s.is(Blocks.COBWEB) || !s.hasBlockEntity() && s.getFluidState().isEmpty() && (s.is(BlockTags.BASE_STONE_OVERWORLD)
			|| s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) || s.is(BlockTags.ORES)
			|| s.is(Blocks.TUFF) || s.is(Blocks.CALCITE) || s.is(Blocks.DRIPSTONE_BLOCK) || s.is(Blocks.SNOW) || s.is(Blocks.SNOW_BLOCK)
			|| s.is(BlockTags.REPLACEABLE_BY_TREES) || s.is(BlockTags.TERRACOTTA));
	}

	// ---- the cache ----

	/**
	 * Everything {@code player} carries, into chests at {@code at} (and beside it if it needs two), wrapped in silk, eggs laid
	 * on top; never where they lie ({@code wake}). With no spot for a chest it's left lying by them.
	 */
	private static void stow(ServerLevel level, ServerPlayer player, @Nullable BlockPos at, BlockPos wake, RandomSource random) {
		Inventory inventory = player.getInventory();
		List<ItemStack> taken = new ArrayList<>();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty()) {
				taken.add(stack.copy());
				inventory.setItem(i, ItemStack.EMPTY);
			}
		}
		if (taken.isEmpty()) {
			return;
		}
		int next = 0;
		BlockPos[] spots = at == null ? new BlockPos[0] : new BlockPos[] {at, at.relative(Direction.Plane.HORIZONTAL.getRandomDirection(random))};
		for (BlockPos spot : spots) {
			if (next >= taken.size()) {
				break;
			}
			if (spot.equals(wake) || spot.equals(wake.above())) {
				continue;
			}
			BlockState here = level.getBlockState(spot);
			if (!here.isAir() && !here.is(Blocks.COBWEB) || !level.setBlock(spot, Blocks.CHEST.defaultBlockState()
				.setValue(ChestBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random)), Block.UPDATE_ALL)
				|| !(level.getBlockEntity(spot) instanceof ChestBlockEntity chest)) {
				continue;
			}
			for (int slot = 0; slot < chest.getContainerSize() && next < taken.size(); slot++) {
				chest.setItem(slot, taken.get(next++));
			}
			chest.setChanged();
			// laid on it, and wrapped round it
			if (level.getBlockState(spot.above()).isAir()) {
				level.setBlock(spot.above(), Blocks.TURTLE_EGG.defaultBlockState().setValue(TurtleEggBlock.EGGS, 1 + random.nextInt(EGGS + 1) % 4),
					Block.UPDATE_ALL);
			}
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos side = spot.relative(d);
				if (level.getBlockState(side).isAir() && random.nextFloat() < 0.5F && !side.equals(wake) && !side.equals(wake.above())) {
					level.setBlock(side, Blocks.COBWEB.defaultBlockState(), Block.UPDATE_ALL);
				}
			}
		}
		// (whatever didn't fit lies at the cache)
		BlockPos lie = at != null ? at : wake;
		for (int i = next; i < taken.size(); i++) {
			level.addFreshEntity(new ItemEntity(level, lie.getX() + 0.5, lie.getY() + 1.0, lie.getZ() + 0.5, taken.get(i)));
		}
	}
}
