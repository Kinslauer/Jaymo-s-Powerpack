package com.jaymoskingdoms;

import com.jaymoskingdoms.monsters.Monsters;
import com.jaymoskingdoms.monsters.Ogre;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.490) Ogre holds grow for as long as they hold ground and stores, with no list to run out of. Every
 * {@link #EVERY} ticks a standing hold's ogres forage something into its stash (the chests its raiders fill too:
 * {@link OgreRaids}), and when the stash can pay for the next stage, one is raised: in turn a <b>hut</b> of old stone
 * outside the walls, a <b>new ogre</b> (while the huts have room), and a <b>bone totem</b> with a stash chest of its own.
 * Stages ring the hold in widening turns of the golden angle ({@link #spot}), up to {@link #MAX_STAGE} stages and
 * {@link #OGRE_CAP} ogres, whichever the ground and the hoard allow; each stage costs more than the last.
 *
 * <p>Nothing is built with a player this near ({@link #HIDE_RANGE}), on ground that is not level and clear, or on land a
 * settlement holds.
 */
final class OgreGrowth {
	// ================================================================================================================
	// Tuning
	/** How often (ticks) a hold forages and may grow: a quarter of a day. */
	static final int EVERY = 6000;
	/** What its ogres forage into the stash each round, for each ogre at home (up to {@link #FORAGERS}). */
	static final int FORAGE_BONES = 3, FORAGE_FLESH = 2, FORAGE_NUGGETS = 3, FORAGE_COBBLE = 6, FORAGE_LEATHER = 1;
	static final int FORAGERS = 5;
	/** Stage {@code n} costs this many bones and cobblestone to begin with, and this many more for each stage so far, at most the caps. */
	static final int COST_BONES = 6, COST_BONES_PER = 1, COST_BONES_MOST = 40;
	static final int COST_COBBLE = 12, COST_COBBLE_PER = 2, COST_COBBLE_MOST = 64;
	/** Every this many stages a bent copper is asked too. */
	static final int COPPER_EVERY = 3;
	/** The most stages a hold grows (its ground runs out), the most ogres it keeps at home, and how many it starts with beyond that: base plus half the stages. */
	static final int MAX_STAGE = 60, OGRE_CAP = 12, BASE_OGRES = 3;
	/** Stages ring the hold: the first at this far (blocks) from its centre, a turn further out every few stages, no further than the most. */
	static final int RING_START = OgreHolds.HOLD_RADIUS + 6, RING_STEP = 5, STAGES_PER_RING = 6, RING_MOST = OgreHolds.HOLD_RADIUS + 40;
	/** The most the ground may rise and fall across a stage's footprint, and from the hold's floor. */
	static final int LEVEL_SPREAD = 1, FLOOR_SPREAD = 5;
	/** Nothing is built with a player this near. */
	static final double HIDE_RANGE = 24.0;
	/** A hut: its inner width (odd), its wall height (ogres are tall) and the doorway's width. */
	static final int HUT_HALF = 2, HUT_WALL = 4;
	/** (0.7.490) Rounds a stage may find no ground before the hold lets it go, unpaid, and tries the next stage's spot. */
	static final int STAGE_TRIES = 3;
	/** (0.7.490) How far (blocks, a square) from its centre a hold that lost every stash chest looks for ground for a new one. */
	static final int HOARD_SEARCH = 6;
	// ================================================================================================================

		private OgreGrowth() {
	}

	/** (0.7.490) Failed rounds at the current stage, by hold id. */
	private static final Map<Integer, Integer> TRIES = new HashMap<>();

	static void clear() {
		TRIES.clear();
	}

	/** Every {@link #EVERY} ticks: each standing, loaded hold forages, flushes its waiting loot, and grows a stage if it can pay. */
	static void grow(ServerLevel level) {
		MinecraftServer server = level.getServer();
		for (OgreHolds.Site site : List.copyOf(OgreHolds.sites(server))) {
			if (!site.hold() || site.cleared() || !level.isPositionEntityTicking(site.at())) {
				continue;
			}
			site = adopt(level, site);
			if (stash(level, site).isEmpty()) {
				continue;
			}
			site = flush(level, site);
			int ogres = ogresAt(level, site).size();
			forage(level, site, ogres);
			if (site.stage() >= MAX_STAGE) {
				continue;
			}
			stage(level, site, ogres);
		}
	}

	// ---- the stash ---------------------------------------------------------------------------------------------------

	/** Its stash chests that are loaded now. */
	static List<Container> stash(ServerLevel level, OgreHolds.Site site) {
		List<Container> out = new ArrayList<>();
		for (BlockPos pos : site.stash()) {
			if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof Container c) {
				out.add(c);
			}
		}
		return out;
	}

	/**
	 * A hold raised before it kept a stash list has its hoard chest found (the nearest chest inside its walls) and kept.
	 * (0.7.490) Stash chests that are gone (looted and broken) are struck off the list; if none is left, the nearest chest
	 * inside the walls is taken up instead, or failing that a new hoard chest is set down by its centre, so the hold
	 * carries on growing (and its waiting loot has somewhere to go).
	 */
	private static OgreHolds.Site adopt(ServerLevel level, OgreHolds.Site site) {
		List<BlockPos> kept = new ArrayList<>();
		for (BlockPos pos : site.stash()) {
			// (one whose chunk isn't loaded is kept: it can't be told gone)
			if (!level.isLoaded(pos) || level.getBlockEntity(pos) instanceof Container) {
				kept.add(pos);
			}
		}
		if (!kept.isEmpty()) {
			if (kept.size() == site.stash().size()) {
				return site;
			}
			OgreHolds.Site pruned = site.withStash(kept);
			OgreHolds.put(level.getServer(), pruned);
			return pruned;
		}
		int r = OgreHolds.HOLD_RADIUS;
		BlockPos found = null;
		double best = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(site.at().offset(-r, -1, -r), site.at().offset(r, 3, r))) {
			if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof ChestBlockEntity && pos.distSqr(site.at()) < best) {
				best = pos.distSqr(site.at());
				found = pos.immutable();
			}
		}
		if (found == null) {
			found = newHoard(level, site);
		}
		if (found == null) {
			if (!site.stash().isEmpty()) {
				OgreHolds.Site pruned = site.withStash(List.of());
				OgreHolds.put(level.getServer(), pruned);
				return pruned;
			}
			return site;
		}
		OgreHolds.Site adopted = site.withStash(List.of(found));
		OgreHolds.put(level.getServer(), adopted);
		return adopted;
	}

	/** (0.7.490) A new, empty hoard chest set down on clear ground near the hold's centre (not with a player near), or null. */
	private static @Nullable BlockPos newHoard(ServerLevel level, OgreHolds.Site site) {
		if (Raids.playerNear(level, site.at(), HIDE_RANGE)) {
			return null;
		}
		for (int ring = 0; ring <= HOARD_SEARCH; ring++) {
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
						continue;
					}
					BlockPos column = site.at().offset(dx, 0, dz);
					if (!level.isLoaded(column)) {
						continue;
					}
					BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
					if (Math.abs(at.getY() - site.at().getY()) > FLOOR_SPREAD || !level.getBlockState(at).isAir()
						|| !level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) {
						continue;
					}
					set(level, at, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(level.getRandom())));
					return level.getBlockEntity(at) instanceof Container ? at.immutable() : null;
				}
			}
		}
		return null;
	}

	/** Puts {@code stack} into the stash; what doesn't fit (or has nowhere loaded to go) waits in the site's record. Returns what is left. */
	static ItemStack put(ServerLevel level, OgreHolds.Site site, ItemStack stack) {
		ItemStack left = stack.copy();
		List<Container> containers = stash(level, site);
		for (Container c : containers) {
			for (int slot = 0; slot < c.getContainerSize() && !left.isEmpty(); slot++) {
				ItemStack there = c.getItem(slot);
				if (!there.isEmpty() && ItemStack.isSameItemSameComponents(there, left)) {
					int room = Math.min(c.getMaxStackSize(there), there.getMaxStackSize()) - there.getCount();
					if (room > 0) {
						int n = Math.min(room, left.getCount());
						there.grow(n);
						left.shrink(n);
						c.setChanged();
					}
				}
			}
		}
		for (Container c : containers) {
			for (int slot = 0; slot < c.getContainerSize() && !left.isEmpty(); slot++) {
				if (c.getItem(slot).isEmpty() && c.canPlaceItem(slot, left)) {
					c.setItem(slot, left.split(Math.min(left.getCount(), Math.min(c.getMaxStackSize(left), left.getMaxStackSize()))));
					c.setChanged();
				}
			}
		}
		return left;
	}

	/** A raider brought {@code load} home: into the stash, or kept for it (the whole stack) if it was not there to take it. */
	static void deposit(ServerLevel level, OgreHolds.Site site, ItemStack load) {
		ItemStack left = put(level, site, load);
		if (left.isEmpty()) {
			return;
		}
		MinecraftServer server = level.getServer();
		// (0.7.490) the record as it now stands: an ogre home before this one may have left loot waiting too
		OgreHolds.Site current = OgreHolds.site(server, site.id());
		OgreHolds.Site base = current != null ? current : site;
		// (0.7.490) the whole stack waits, so enchantments, names, potions and a box's contents come home with it
		List<ItemStack> pending = new ArrayList<>(base.pending());
		pending.add(left.copy());
		OgreHolds.put(server, base.withPending(pending));
	}

	/** Loot that waited for the stash goes in. */
	private static OgreHolds.Site flush(ServerLevel level, OgreHolds.Site site) {
		if (site.pending().isEmpty()) {
			return site;
		}
		List<ItemStack> left = new ArrayList<>();
		for (ItemStack stack : site.pending()) {
			if (stack.isEmpty()) {
				continue;
			}
			ItemStack lost = put(level, site, stack);
			if (!lost.isEmpty()) {
				left.add(lost);
			}
		}
		OgreHolds.Site flushed = site.withPending(left);
		OgreHolds.put(level.getServer(), flushed);
		return flushed;
	}

	private static int count(List<Container> containers, Item item) {
		int n = 0;
		for (Container c : containers) {
			for (int slot = 0; slot < c.getContainerSize(); slot++) {
				ItemStack stack = c.getItem(slot);
				if (stack.is(item)) {
					n += stack.getCount();
				}
			}
		}
		return n;
	}

	private static void take(List<Container> containers, Item item, int count) {
		int left = count;
		for (Container c : containers) {
			for (int slot = 0; slot < c.getContainerSize() && left > 0; slot++) {
				ItemStack stack = c.getItem(slot);
				if (stack.is(item)) {
					int n = Math.min(left, stack.getCount());
					c.removeItem(slot, n);
					left -= n;
					c.setChanged();
				}
			}
		}
	}

	// ---- foraging ----------------------------------------------------------------------------------------------------

	/** The hold's own ogres about it just now (alive, loaded, not on a raid). */
	static List<Ogre> ogresAt(ServerLevel level, OgreHolds.Site site) {
		String tag = OgreHolds.SITE_PREFIX + site.id();
		return level.getEntitiesOfClass(Ogre.class, new AABB(site.at()).inflate(RING_MOST + 8.0),
			o -> o.isAlive() && o.entityTags().contains(tag) && !OgreRaids.isRaider(o));
	}

	private static void forage(ServerLevel level, OgreHolds.Site site, int ogres) {
		int hands = Math.max(1, Math.min(FORAGERS, ogres));
		put(level, site, new ItemStack(Items.BONE, FORAGE_BONES * hands));
		put(level, site, new ItemStack(Items.ROTTEN_FLESH, FORAGE_FLESH * hands));
		put(level, site, new ItemStack(Items.IRON_NUGGET, FORAGE_NUGGETS * hands));
		put(level, site, new ItemStack(Items.COBBLESTONE, FORAGE_COBBLE * hands));
		put(level, site, new ItemStack(Items.LEATHER, FORAGE_LEATHER * hands));
	}

	// ---- the stages --------------------------------------------------------------------------------------------------

	/** What stage {@code n} costs: bones and cobblestone, rising with each stage; a bent copper every few. */
	static Map<Item, Integer> cost(int n) {
		Map<Item, Integer> cost = new java.util.LinkedHashMap<>();
		cost.put(Items.BONE, Math.min(COST_BONES_MOST, COST_BONES + COST_BONES_PER * n));
		cost.put(Items.COBBLESTONE, Math.min(COST_COBBLE_MOST, COST_COBBLE + COST_COBBLE_PER * n));
		if (n % COPPER_EVERY == COPPER_EVERY - 1) {
			cost.put(BentIngots.BENT_COPPER, 1);
		}
		return cost;
	}

	/** How many ogres a hold at stage {@code n} keeps at home at most. */
	static int allowed(int n) {
		return Math.min(OGRE_CAP, BASE_OGRES + n / 2);
	}

	private static void stage(ServerLevel level, OgreHolds.Site site, int ogres) {
		MinecraftServer server = level.getServer();
		int n = site.stage();
		List<Container> stash = stash(level, site);
		Map<Item, Integer> cost = cost(n);
		for (var e : cost.entrySet()) {
			if (count(stash, e.getKey()) < e.getValue()) {
				return;
			}
		}
		if (Raids.playerNear(level, site.at(), RING_MOST + HIDE_RANGE) && anyoneNear(level, site, n)) {
			return;
		}
		int kind = n % 3;
		if (kind == 1 && ogres >= allowed(n)) {
			kind = 2;
		}
		long now = level.getGameTime();
		boolean done = switch (kind) {
			case 0 -> hut(level, site, n);
			case 1 -> recruit(level, site);
			default -> totem(level, site, n);
		};
		if (!done) {
			// (nowhere good for it this time: the next round tries again; after a few, that stage is let go, unpaid)
			boolean skip = TRIES.merge(site.id(), 1, Integer::sum) >= STAGE_TRIES;
			if (skip) {
				TRIES.remove(site.id());
			}
			OgreHolds.put(server, site.withStage(skip ? n + 1 : n, now));
			return;
		}
		TRIES.remove(site.id());
		for (var e : cost.entrySet()) {
			take(stash, e.getKey(), e.getValue());
		}
		// (a totem added a chest to the stash: it is the record as it now stands that moves on a stage)
		OgreHolds.Site now_ = OgreHolds.site(server, site.id());
		OgreHolds.put(server, (now_ != null ? now_ : site).withStage(n + 1, now));
		JaymosKingdoms.LOGGER.debug("The ogre hold at {} grew to stage {}", site.at(), n + 1);
	}

	/** A player is near where stage {@code n} would go. */
	private static boolean anyoneNear(ServerLevel level, OgreHolds.Site site, int n) {
		BlockPos at = spot(level, site, n, 0);
		return at == null ? Raids.playerNear(level, site.at(), HIDE_RANGE) : Raids.playerNear(level, at, HIDE_RANGE);
	}

	/** Where stage {@code n} goes: a turn of the golden angle round the hold, further out every few stages; {@code skew} turns it a little. Its ground, or null. */
	static @Nullable BlockPos spot(ServerLevel level, OgreHolds.Site site, int n, int skew) {
		int ring = Outbuild.ring(n, RING_START, RING_STEP, STAGES_PER_RING, RING_MOST);
		return Outbuild.ground(level, site.at(), n * Outbuild.GOLDEN + skew * 0.35, ring, HUT_HALF, site.at().getY(), LEVEL_SPREAD, FLOOR_SPREAD, -1);
	}

	private static void set(ServerLevel level, BlockPos at, BlockState state) {
		Outbuild.set(level, at, state);
	}

	private static @Nullable BlockPos find(ServerLevel level, OgreHolds.Site site, int n, int half, int height) {
		int ring = Outbuild.ring(n, RING_START, RING_STEP, STAGES_PER_RING, RING_MOST);
		return Outbuild.find(level, site.at(), n * Outbuild.GOLDEN, 6, ring, half, height, site.at().getY(), LEVEL_SPREAD, FLOOR_SPREAD, -1);
	}

	/** A hut of old stone with a roof, a bone bed, a doorway toward the hold. */
	private static boolean hut(ServerLevel level, OgreHolds.Site site, int n) {
		BlockPos floor = find(level, site, n, HUT_HALF, HUT_WALL + 2);
		if (floor == null) {
			return false;
		}
		RandomSource random = level.getRandom();
		double dx = site.at().getX() - floor.getX(), dz = site.at().getZ() - floor.getZ();
		Direction door = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
		BlockState cobble = Blocks.COBBLESTONE.defaultBlockState(), mossy = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
		for (int x = -HUT_HALF; x <= HUT_HALF; x++) {
			for (int z = -HUT_HALF; z <= HUT_HALF; z++) {
				set(level, floor.offset(x, -1, z), cobble);
				boolean wall = Math.abs(x) == HUT_HALF || Math.abs(z) == HUT_HALF;
				int across = x * door.getStepX() + z * door.getStepZ();
				int along = x * door.getStepZ() - z * door.getStepX();
				boolean doorway = across == HUT_HALF && Math.abs(along) <= 0;
				for (int y = 0; y < HUT_WALL; y++) {
					BlockPos p = floor.offset(x, y, z);
					boolean open = !wall || doorway && y < HUT_WALL - 1;
					set(level, p, open ? Blocks.AIR.defaultBlockState() : random.nextInt(3) == 0 ? mossy : cobble);
				}
				set(level, floor.offset(x, HUT_WALL, z), Blocks.COBBLESTONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
			}
		}
		set(level, floor.relative(door.getOpposite(), 1), Blocks.BONE_BLOCK.defaultBlockState());
		set(level, floor.relative(door.getOpposite(), 1).above(), Blocks.SKELETON_SKULL.defaultBlockState());
		return true;
	}

	/** A bone totem with a stash chest of its own at its foot (kept in the site's stash). */
	private static boolean totem(ServerLevel level, OgreHolds.Site site, int n) {
		BlockPos floor = find(level, site, n, 1, 6);
		if (floor == null) {
			return false;
		}
		RandomSource random = level.getRandom();
		for (int y = 0; y < 4; y++) {
			set(level, floor.above(y), (y % 2 == 0 ? Blocks.MOSSY_COBBLESTONE : Blocks.BONE_BLOCK).defaultBlockState());
		}
		set(level, floor.above(4), Blocks.SKELETON_SKULL.defaultBlockState());
		Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		BlockPos chest = floor.relative(facing);
		set(level, chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing.getOpposite()));
		if (level.getBlockEntity(chest) instanceof ChestBlockEntity c) {
			c.setItem(0, new ItemStack(Items.BONE, 2 + random.nextInt(4)));
		}
		List<BlockPos> stash = new ArrayList<>(site.stash());
		stash.add(chest.immutable());
		OgreHolds.Site grown = site.withStash(stash);
		OgreHolds.put(level.getServer(), grown);
		return true;
	}

	/** Another ogre takes up with the hold: one of its kind, now and then a grey cave ogre, at the foot of its walls. */
	private static boolean recruit(ServerLevel level, OgreHolds.Site site) {
		RandomSource random = level.getRandom();
		BlockPos column = site.at().offset(random.nextInt(9) - 4, 0, random.nextInt(9) - 4);
		if (!level.isLoaded(column)) {
			return false;
		}
		BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
		EntityType<Ogre> type = random.nextInt(4) == 0 ? Monsters.CAVE_OGRE : Monsters.OGRE;
		Ogre ogre = OgreHolds.ogre(level, type, at, site.at(), OgreHolds.HOLD_HOME, site.id());
		if (ogre == null) {
			return false;
		}
		ogre.setTwoHeads(random.nextInt(3) == 0);
		return true;
	}
}
