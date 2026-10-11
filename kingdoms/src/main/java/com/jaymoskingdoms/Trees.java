package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * Living trees (0.5.4), our own take on the idea behind Dynamic Trees (none of its code or art): trees in the Overworld
 * <b>grow up</b>, <b>come down whole</b>, and <b>seed themselves</b>.
 * <ul>
 * <li><b>Growing</b>: a sapling ready to grow ({@code SaplingBlockMixin}) doesn't become a full tree at once. It sprouts
 * a sapling tree (a two-log stem and a tuft of leaves), which grows into a young tree (four logs, a small crown), and
 * only then into the full tree its kind grows (the game's own tree, so every kind keeps its shape). Each step takes
 * {@link #GROW_TICKS} or so in loaded land, and needs light and room. Two-by-two trees (dark oak, big spruce, big
 * jungle) and anything that isn't a plain {@code *_sapling} grow as before. Growing trees are kept on the Overworld.</li>
 * <li><b>Felling</b>: cut a log of a tree (a trunk under living leaves, not a building's logs) and everything above the
 * cut comes down, logs and the leaves they held, a few blocks a tick from the foot up, its wood dropped where it was
 * cut. The axe takes a point of wear per log. Sneak to cut one log only. Woodcutters fell the same way
 * ({@link Gatherers}).</li>
 * <li><b>Seeding</b>: now and then, round each player, a grown tree drops a sapling of its kind on open grass a few
 * blocks off, where the wood isn't already thick, so forests slowly spread (and grow up as above). Never in a
 * settlement's buildings or plots.</li>
 * </ul>
 * Sleeping through a night ({@link Nights}) grows them on as well.
 */
public final class Trees {
	/** A growing tree's step takes this long, and up to this much more at random (ticks). */
	static final int GROW_TICKS = 6000, GROW_JITTER = 4000;
	/** How often growing trees are looked at, and the light one needs. */
	static final int CHECK_TICKS = 100, LIGHT = 9;
	/** Steps: a sapling tree, a young tree; after that, the full tree. */
	static final int SPROUT = 1, YOUNG = 2;
	/** A felled tree: the most logs, how far from the cut they spread, the most leaves; blocks brought down a tick. */
	static final int FELL_MOST = 200, FELL_SPREAD = 8, LEAVES_MOST = 600, FELL_PER_TICK = 4;
	/** Seeding: tried this often round each player, this far, with this chance; a sapling lands this far from its tree. */
	static final int SEED_TICKS = 400, SEED_RANGE = 32, SEED_NEAR = 3, SEED_FAR = 7;
	static final float SEED_CHANCE = 0.35F;
	/** No seedling where there are this many logs within {@link #CROWD_RANGE} already. */
	static final int CROWDED = 3, CROWD_RANGE = 3;

	/** A growing tree: its foot, its sapling, its step, when it next grows. */
	record Growing(BlockPos pos, String sapling, int stage, long next) {
		static final Codec<Growing> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.fieldOf("pos").forGetter(Growing::pos),
				Codec.STRING.fieldOf("sapling").forGetter(Growing::sapling),
				Codec.INT.fieldOf("stage").forGetter(Growing::stage),
				Codec.LONG.fieldOf("next").forGetter(Growing::next))
			.apply(i, Growing::new));
	}

	static final AttachmentType<List<Growing>> GROWING = AttachmentRegistry.create(JaymosKingdoms.id("growing_trees"),
		builder -> builder.persistent(Growing.CODEC.listOf()));

	/** A tree coming down: what's left of it, foot first, and where what it gives goes. */
	private record Fall(ServerLevel level, Deque<BlockPos> blocks, @Nullable Entity cutter, ItemStack tool, Consumer<ItemStack> sink) {
	}

	private static final List<Fall> FALLING = new ArrayList<>();
	/** (set while a grown-up sapling grows the game's own tree, so the mixin lets it) */
	private static boolean growingUp;

	private Trees() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Trees::tick);
		PlayerBlockBreakEvents.AFTER.register(Trees::broken);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> FALLING.clear());
	}

	// ---- the kinds --------------------------------------------------------------------------------------------------

	/** A sapling's wood ("oak" for oak_sapling), or null for one that isn't a plain {@code *_sapling}. */
	private static @Nullable String wood(Block sapling) {
		String name = BuiltInRegistries.BLOCK.getKey(sapling).getPath();
		return name.endsWith("_sapling") ? name.substring(0, name.length() - "_sapling".length()) : null;
	}

	private static Block named(String name) {
		return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(name));
	}

	// ---- growing ----------------------------------------------------------------------------------------------------

	private static List<Growing> growing(MinecraftServer server) {
		return Factions.level(server).getAttachedOrElse(GROWING, List.<Growing>of());
	}

	private static void store(MinecraftServer server, List<Growing> list) {
		Factions.level(server).setAttached(GROWING, List.copyOf(list));
	}

	/** It's one of the trees still growing up (not to be felled for wood yet). */
	static boolean young(MinecraftServer server, BlockPos foot) {
		for (Growing g : growing(server)) {
			if (g.pos().equals(foot)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * A sapling is ready to grow ({@code SaplingBlockMixin}): in the Overworld, a plain single sapling sprouts a sapling
	 * tree instead (true: the game's growth is skipped). False lets the game grow it as ever.
	 */
	public static boolean sprout(ServerLevel level, BlockPos pos, BlockState state) {
		if (growingUp || level != Factions.level(level.getServer()) || !(state.getBlock() instanceof SaplingBlock)) {
			return false;
		}
		String wood = wood(state.getBlock());
		if (wood == null || wood.equals("dark_oak") || wood.equals("pale_oak") || named(wood + "_log") == Blocks.AIR || named(wood + "_leaves") == Blocks.AIR) {
			return false;
		}
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (level.getBlockState(pos.relative(d)).is(state.getBlock())) {
				// (two by two: a big tree, grown as ever)
				return false;
			}
		}
		if (!shape(level, pos, wood, SPROUT, true)) {
			return true;
		}
		List<Growing> list = new ArrayList<>(growing(level.getServer()));
		list.removeIf(g -> g.pos().equals(pos));
		list.add(new Growing(pos.immutable(), BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), SPROUT,
			level.getGameTime() + GROW_TICKS + level.getRandom().nextInt(GROW_JITTER)));
		store(level.getServer(), list);
		level.playSound(null, pos, SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 0.8F, 1.2F);
		return true;
	}

	/** Lays a growing tree's step at {@code foot} (true), or false if something's in the way (nothing laid then, when {@code check}). */
	private static boolean shape(ServerLevel level, BlockPos foot, String wood, int stage, boolean check) {
		BlockState log = named(wood + "_log").defaultBlockState();
		BlockState leaves = named(wood + "_leaves").defaultBlockState();
		int logs = stage == SPROUT ? 2 : 4;
		List<BlockPos> crown = crown(foot, stage);
		if (check) {
			for (int y = 1; y < logs; y++) {
				if (!room(level, foot.above(y))) {
					return false;
				}
			}
		}
		for (int y = 0; y < logs; y++) {
			level.setBlock(foot.above(y), log, Block.UPDATE_ALL);
		}
		// leaves nearest the trunk first, each knowing how far it is from wood (so none of them wither)
		crown.sort(Comparator.comparingDouble(p -> p.distSqr(foot.above(logs - 1))));
		for (BlockPos p : crown) {
			if (room(level, p)) {
				level.setBlock(p, leaves.setValue(LeavesBlock.DISTANCE, distance(level, p)), Block.UPDATE_ALL);
			}
		}
		return true;
	}

	/** A step's leaves: a sapling tree's tuft round and over its top; a young tree's small crown. */
	private static List<BlockPos> crown(BlockPos foot, int stage) {
		List<BlockPos> out = new ArrayList<>();
		if (stage == SPROUT) {
			BlockPos top = foot.above(1);
			for (Direction d : Direction.Plane.HORIZONTAL) {
				out.add(top.relative(d));
			}
			out.add(top.above());
			return out;
		}
		BlockPos top = foot.above(3);
		for (int dy = -1; dy <= 1; dy++) {
			int r = dy == 1 ? 1 : 2;
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if ((dx == 0 && dz == 0) || (Math.abs(dx) == 2 && Math.abs(dz) == 2) || (dy == -1 && Math.abs(dx) + Math.abs(dz) > 2)) {
						continue;
					}
					out.add(top.offset(dx, dy, dz));
				}
			}
		}
		out.add(top.above(2));
		return out;
	}

	private static boolean room(ServerLevel level, BlockPos p) {
		BlockState s = level.getBlockState(p);
		return s.isAir() || (s.canBeReplaced() && s.getFluidState().isEmpty());
	}

	private static int distance(ServerLevel level, BlockPos p) {
		int best = LeavesBlock.DECAY_DISTANCE;
		for (Direction d : Direction.values()) {
			BlockState n = level.getBlockState(p.relative(d));
			if (n.is(BlockTags.LOGS)) {
				return 1;
			}
			if (n.getBlock() instanceof LeavesBlock) {
				best = Math.min(best, n.getValue(LeavesBlock.DISTANCE) + 1);
			}
		}
		return best;
	}

	/** Clears a growing tree's step (its own logs and living leaves) from {@code foot}, the sapling left in its place. */
	private static void clear(ServerLevel level, BlockPos foot, int stage, BlockState sapling) {
		for (BlockPos p : crown(foot, stage)) {
			BlockState s = level.getBlockState(p);
			if (s.getBlock() instanceof LeavesBlock && !s.getValue(LeavesBlock.PERSISTENT)) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
		for (int y = (stage == SPROUT ? 2 : 4) - 1; y >= 1; y--) {
			if (level.getBlockState(foot.above(y)).is(BlockTags.LOGS)) {
				level.setBlock(foot.above(y), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
		level.setBlock(foot, sapling, Block.UPDATE_ALL);
	}

	/** Grows every growing tree whose time has come (loaded, lit, with room); {@code extra} ticks made up at once (sleep). */
	static int grow(ServerLevel level, long extra) {
		MinecraftServer server = level.getServer();
		List<Growing> list = new ArrayList<>(growing(server));
		if (list.isEmpty()) {
			return 0;
		}
		// (time made up ages the trees: their next step comes that much sooner, still on the game's own clock)
		long now = level.getGameTime();
		boolean changed = false;
		int grown = 0;
		for (int i = 0; i < list.size(); i++) {
			Growing g = list.get(i);
			if (!level.isLoaded(g.pos()) || !level.isPositionEntityTicking(g.pos())) {
				continue;
			}
			// (cut down, or dug up, meanwhile: forgotten)
			if (!level.getBlockState(g.pos()).is(BlockTags.LOGS)) {
				list.remove(i--);
				changed = true;
				continue;
			}
			if (extra > 0) {
				g = new Growing(g.pos(), g.sapling(), g.stage(), g.next() - extra);
				list.set(i, g);
				changed = true;
			}
			while (g != null && g.next() <= now) {
				Growing next = step(level, g);
				changed = true;
				if (next == null) {
					list.remove(i--);
					grown++;
				} else if (next.stage() == g.stage()) {
					list.set(i, next);
					break;
				} else {
					list.set(i, next);
					grown++;
				}
				g = next;
			}
		}
		if (changed) {
			store(server, list);
		}
		return grown;
	}

	/** One step up: a sapling tree to a young one, a young one to its full tree (null: grown, no longer kept). */
	private static @Nullable Growing step(ServerLevel level, Growing g) {
		RandomSource random = level.getRandom();
		long later = g.next() + GROW_TICKS + random.nextInt(GROW_JITTER);
		Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(g.sapling()));
		String wood = wood(block);
		if (!(block instanceof SaplingBlock sapling) || wood == null) {
			return null;
		}
		if (level.getMaxLocalRawBrightness(g.pos().above(g.stage() == SPROUT ? 2 : 4)) < LIGHT) {
			return new Growing(g.pos(), g.sapling(), g.stage(), later);
		}
		if (g.stage() == SPROUT) {
			for (int y = 2; y < 4; y++) {
				if (!room(level, g.pos().above(y)) && !level.getBlockState(g.pos().above(y)).is(BlockTags.LEAVES)) {
					return new Growing(g.pos(), g.sapling(), g.stage(), later);
				}
			}
			shape(level, g.pos(), wood, YOUNG, false);
			return new Growing(g.pos(), g.sapling(), YOUNG, later);
		}
		// grown: the young tree gives way to the full tree of its kind, the game's own
		BlockState ready = block.defaultBlockState().setValue(SaplingBlock.STAGE, 1);
		clear(level, g.pos(), YOUNG, ready);
		growingUp = true;
		try {
			sapling.advanceTree(level, g.pos(), ready, random);
		} finally {
			growingUp = false;
		}
		if (level.getBlockState(g.pos()).is(block)) {
			// (no room for it yet: young a while longer)
			shape(level, g.pos(), wood, YOUNG, false);
			return new Growing(g.pos(), g.sapling(), YOUNG, later);
		}
		return null;
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		fallSome();
		if (now % CHECK_TICKS == 0) {
			grow(level, 0);
		}
		if (now % SEED_TICKS == 0) {
			for (ServerPlayer player : level.players()) {
				if (!player.isSpectator()) {
					seed(level, player.blockPosition());
				}
			}
		}
	}

	// ---- seeding ----------------------------------------------------------------------------------------------------

	/** Maybe a seedling from a grown tree round {@code near}. */
	private static void seed(ServerLevel level, BlockPos near) {
		RandomSource random = level.getRandom();
		if (random.nextFloat() >= SEED_CHANCE) {
			return;
		}
		for (int tries = 0; tries < 8; tries++) {
			BlockPos column = near.offset(random.nextInt(SEED_RANGE * 2 + 1) - SEED_RANGE, 0, random.nextInt(SEED_RANGE * 2 + 1) - SEED_RANGE);
			if (!level.isLoaded(column)) {
				continue;
			}
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column).below();
			BlockState log = level.getBlockState(top);
			if (!log.is(BlockTags.LOGS) || !natural(level, top)) {
				continue;
			}
			BlockState sapling = Gatherers.sapling(log);
			if (sapling == null || wood(sapling.getBlock()) == null) {
				return;
			}
			int d = SEED_NEAR + random.nextInt(SEED_FAR - SEED_NEAR + 1);
			Direction way = Direction.Plane.HORIZONTAL.getRandomDirection(random);
			BlockPos spot = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				column.relative(way, d).relative(way.getClockWise(), random.nextInt(5) - 2));
			if (fit(level, spot, sapling)) {
				level.setBlock(spot, sapling, Block.UPDATE_ALL);
			}
			return;
		}
	}

	/** Open grass (or soil) with light and no wood crowding it, outside every settlement's buildings and plots. */
	private static boolean fit(ServerLevel level, BlockPos spot, BlockState sapling) {
		if (!level.getBlockState(spot).isAir() || !level.getBlockState(spot.below()).is(BlockTags.DIRT) || level.getBlockState(spot.below()).is(Blocks.FARMLAND)
			|| !sapling.canSurvive(level, spot) || level.getMaxLocalRawBrightness(spot) < LIGHT || Holds.guarded(level.getServer(), spot)) {
			return false;
		}
		int wood = 0;
		for (BlockPos p : BlockPos.betweenClosed(spot.offset(-CROWD_RANGE, -1, -CROWD_RANGE), spot.offset(CROWD_RANGE, 3, CROWD_RANGE))) {
			BlockState s = level.getBlockState(p);
			if (s.is(BlockTags.SAPLINGS) || (s.is(BlockTags.LOGS) && ++wood >= CROWDED)) {
				return false;
			}
		}
		for (Works.Site site : Works.all(level.getServer()).values()) {
			for (Works.Built b : site.built()) {
				if (spot.getX() >= b.min().getX() - 2 && spot.getX() <= b.max().getX() + 2 && spot.getZ() >= b.min().getZ() - 2 && spot.getZ() <= b.max().getZ() + 2) {
					return false;
				}
			}
			for (Works.Plot b : site.plots()) {
				if (spot.getX() >= b.min().getX() && spot.getX() <= b.max().getX() && spot.getZ() >= b.min().getZ() && spot.getZ() <= b.max().getZ()) {
					return false;
				}
			}
		}
		return true;
	}

	// ---- felling ----------------------------------------------------------------------------------------------------

	/** Living (not placed) leaves round a log: it's a tree, not a building. */
	static boolean natural(ServerLevel level, BlockPos log) {
		for (BlockPos q : BlockPos.betweenClosed(log.offset(-2, -2, -2), log.offset(2, 2, 2))) {
			BlockState state = level.getBlockState(q);
			if (state.getBlock() instanceof LeavesBlock && !state.getValue(LeavesBlock.PERSISTENT)) {
				return true;
			}
		}
		return false;
	}

	/** A player cut a log: unless they're sneaking, a tree's whole top comes down with it. */
	private static void broken(Level level, Player player, BlockPos pos, BlockState state, net.minecraft.world.level.block.entity.@Nullable BlockEntity be) {
		if (!(level instanceof ServerLevel server) || player.isShiftKeyDown() || player.isCreative() || !state.is(BlockTags.LOGS)) {
			return;
		}
		ItemStack tool = player.getMainHandItem();
		fell(server, pos, player, tool, stack -> Block.popResource(server, pos, stack), true);
	}

	/**
	 * Brings down the tree standing above {@code cut} (its logs joined to it at or above that height, not far off, and
	 * the leaves only they held): nothing if they don't make a tree (no living leaves). What it gives goes to
	 * {@code sink}. {@code wear}: the tool takes a point per log. Returns how many logs.
	 */
	static int fell(ServerLevel level, BlockPos cut, @Nullable Entity cutter, ItemStack tool, Consumer<ItemStack> sink, boolean wear) {
		List<BlockPos> logs = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		Deque<BlockPos> open = new ArrayDeque<>();
		for (BlockPos n : BlockPos.betweenClosed(cut.offset(-1, 0, -1), cut.offset(1, 1, 1))) {
			BlockPos q = n.immutable();
			if (!q.equals(cut) && level.getBlockState(q).is(BlockTags.LOGS)) {
				seen.add(q);
				open.add(q);
			}
		}
		if (level.getBlockState(cut).is(BlockTags.LOGS)) {
			seen.add(cut);
			open.addFirst(cut);
		}
		while (!open.isEmpty() && logs.size() < FELL_MOST) {
			BlockPos at = open.poll();
			logs.add(at);
			for (BlockPos n : BlockPos.betweenClosed(at.offset(-1, 0, -1), at.offset(1, 1, 1))) {
				if (n.getY() < cut.getY() || Math.abs(n.getX() - cut.getX()) > FELL_SPREAD || Math.abs(n.getZ() - cut.getZ()) > FELL_SPREAD) {
					continue;
				}
				BlockPos q = n.immutable();
				if (!seen.contains(q) && level.getBlockState(q).is(BlockTags.LOGS)) {
					seen.add(q);
					open.add(q);
				}
			}
		}
		if (logs.isEmpty() || logs.stream().noneMatch(p -> natural(level, p))) {
			return 0;
		}
		// the leaves no other wood holds
		Set<BlockPos> wood = new HashSet<>(logs);
		List<BlockPos> leaves = new ArrayList<>();
		Set<BlockPos> looked = new HashSet<>();
		Deque<BlockPos> grow = new ArrayDeque<>(logs);
		while (!grow.isEmpty() && leaves.size() < LEAVES_MOST) {
			BlockPos at = grow.poll();
			for (Direction d : Direction.values()) {
				BlockPos n = at.relative(d);
				if (!looked.add(n)) {
					continue;
				}
				BlockState s = level.getBlockState(n);
				if (s.getBlock() instanceof LeavesBlock && !s.getValue(LeavesBlock.PERSISTENT) && !heldElsewhere(level, n, wood)) {
					leaves.add(n);
					grow.add(n);
				}
			}
		}
		logs.sort(Comparator.comparingInt(BlockPos::getY));
		Deque<BlockPos> order = new ArrayDeque<>(logs);
		order.addAll(leaves);
		if (wear && cutter instanceof Player p && !tool.isEmpty() && tool.isDamageableItem()) {
			tool.hurtAndBreak(Math.max(0, logs.size() - (level.getBlockState(cut).isAir() ? 1 : 0)), p, EquipmentSlot.MAINHAND);
		}
		FALLING.add(new Fall(level, order, cutter, tool.copy(), sink));
		level.playSound(null, cut, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.0F, 0.6F);
		// (the growing-tree record, if it was one, is forgotten when its foot is gone)
		return logs.size();
	}

	/** Some other log (not the felled tree's) within reach of these leaves: they stay. */
	private static boolean heldElsewhere(ServerLevel level, BlockPos leaf, Set<BlockPos> felled) {
		for (BlockPos q : BlockPos.betweenClosed(leaf.offset(-3, -3, -3), leaf.offset(3, 3, 3))) {
			if (!felled.contains(q) && level.getBlockState(q).is(BlockTags.LOGS)) {
				return true;
			}
		}
		return false;
	}

	/** A few blocks of every falling tree come down this tick. */
	private static void fallSome() {
		for (int i = 0; i < FALLING.size(); i++) {
			Fall f = FALLING.get(i);
			for (int n = 0; n < FELL_PER_TICK && !f.blocks().isEmpty(); n++) {
				BlockPos p = f.blocks().poll();
				BlockState s = f.level().getBlockState(p);
				if (!(s.is(BlockTags.LOGS) || s.getBlock() instanceof LeavesBlock)) {
					continue;
				}
				for (ItemStack d : Block.getDrops(s, f.level(), p, f.level().getBlockEntity(p), f.cutter(), f.tool())) {
					f.sink().accept(d);
				}
				f.level().levelEvent(2001, p, Block.getId(s));
				f.level().setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
			if (f.blocks().isEmpty()) {
				FALLING.remove(i--);
			}
		}
	}
}
