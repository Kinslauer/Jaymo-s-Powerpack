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
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
 * cut comes down, logs and the leaves they held, and (0.5.9) it topples over whole, away from the
 * cutter, breaking apart where it lands and giving its wood there ({@link FallingTree}). (0.5.25) As in HT's TreeChop,
 * it takes chops, not one break: each stroke at a log narrows it ({@link ChoppedLog}), and the tree comes down on the
 * chop that makes up what its size needs ({@link #needed}); the axe takes a point of wear per chop. Sneak to break one
 * log only. Woodcutters chop and fell the same way ({@link Gatherers}). (0.7.11) As in Physical Falling Trees, it falls
 * to its heavier side (or away from the cutter if it's even), strikes what's in its way as hard as it's moving, angers
 * the bees of any nest in it, and comes to rest as real blocks lying on the ground: its logs on their side, its leaves
 * withering away ({@link FallingTree}).</li>
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
	/** A felled tree: the most logs, how far from the cut they spread, the most leaves. */
	static final int FELL_MOST = 200, FELL_SPREAD = 8, LEAVES_MOST = 600;
	/** Seeding: tried this often round each player, this far, with this chance; a sapling lands this far from its tree. */
	static final int SEED_TICKS = 400, SEED_RANGE = 32, SEED_NEAR = 3, SEED_FAR = 7;
	static final float SEED_CHANCE = 0.35F;
	/** No seedling where there are this many logs within {@link #CROWD_RANGE} already. */
	static final int CROWDED = 3, CROWD_RANGE = 3;
	/**
	 * (0.5.25) How steeply the chops a tree needs rise with its size (TreeChop's logarithmic count): bigger trees take
	 * more chops, but fewer per log. 0 would be one chop for any tree; very large, a chop per log.
	 */
	static final double CHOP_SCALE = 10.0;
	/** (0.5.59) Leaves broken by hand: the chance of a stick, for the first hand axe (3 flint and a stick). */
	static final float LEAF_STICK_CHANCE = 0.12F;

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

	/** (set while a grown-up sapling grows the game's own tree, so the mixin lets it) */
	private static boolean growingUp;

	private Trees() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Trees::tick);
		PlayerBlockBreakEvents.BEFORE.register(Trees::breaking);
		// (0.5.59) said as soon as a bare hand starts on wood, not after the long, empty punch
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			if (!level.isClientSide() && needsAxe(player, level.getBlockState(pos))) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.needs_axe").withStyle(ChatFormatting.GRAY));
			}
			return InteractionResult.PASS;
		});
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
			if (level instanceof ServerLevel server && !player.isCreative() && state.getBlock() instanceof LeavesBlock
				&& !state.getValue(LeavesBlock.PERSISTENT) && player.getMainHandItem().isEmpty()
				&& server.getRandom().nextFloat() < LEAF_STICK_CHANCE) {
				Block.popResource(server, pos, new ItemStack(Items.STICK));
			}
		});
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

	/**
	 * (0.5.25) A player's axe stroke at a tree's log is a chop, not a break (unless they're sneaking or in creative, or it
	 * isn't a tree): the log narrows ({@link ChoppedLog}), and the tree comes down whole on the chop that makes up
	 * {@link #needed} for its size. False keeps the block (the chop was done here instead).
	 */
	private static boolean breaking(Level level, Player player, BlockPos pos, BlockState state, net.minecraft.world.level.block.entity.@Nullable BlockEntity be) {
		if (needsAxe(player, state)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.needs_axe").withStyle(ChatFormatting.GRAY));
			return false;
		}
		if (!(level instanceof ServerLevel server) || player.isShiftKeyDown() || player.isCreative() || !state.is(BlockTags.LOGS)) {
			return true;
		}
		ItemStack tool = player.getMainHandItem();
		int felled = chop(server, pos, player, tool, null);
		if (felled < 0) {
			return true;
		}
		// (a chop costs what breaking the log would: a point of the axe's wear, and a little hunger)
		if (!tool.isEmpty() && tool.isDamageableItem()) {
			tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
		}
		player.causeFoodExhaustion(0.005F);
		return false;
	}

	/**
	 * (0.5.59) Andy: no punching trees, so the first tool is a flint hand axe. Wood (logs, and what's left of a chopped
	 * one) takes an axe: without one it's slow, drops nothing ({@code PlayerLogToolMixin}) and won't chop.
	 */
	public static boolean needsAxe(Player player, BlockState state) {
		return !player.isCreative() && (state.is(BlockTags.LOGS) || state.is(ChoppedLog.BLOCK)) && !isAxe(player.getMainHandItem());
	}

	/** Anything that cuts wood faster than a hand: axes, the hand axe, mattocks. */
	static boolean isAxe(ItemStack tool) {
		return tool.getDestroySpeed(Blocks.OAK_LOG.defaultBlockState()) > 1.0F;
	}

	/** Chops a tree of {@code logs} logs needs to come down: 1 for one log, 4 for five, 12 for twenty, 25 for a hundred. */
	static int needed(int logs) {
		return Math.max(1, Math.round((float)(1.0 + CHOP_SCALE * Math.log(1.0 + (logs - 1) / CHOP_SCALE))));
	}

	/**
	 * (0.5.25) One chop at the tree whose log is {@code cut}, the way HT's TreeChop fells them: the log narrows (once it's
	 * as thin as it goes, the chop goes into the next whole-enough log up the trunk), and once the tree has taken
	 * {@link #needed} chops for its size it comes down from {@code cut} ({@link #fell}). Returns -1 if it isn't a tree
	 * (break it as ever), 0 for a chop that left it standing, or how many logs came down.
	 */
	static int chop(ServerLevel level, BlockPos cut, @Nullable Entity cutter, ItemStack tool, @Nullable Consumer<ItemStack> sink) {
		BlockState state = level.getBlockState(cut);
		if (!state.is(BlockTags.LOGS) || fallen(level, cut, state)) {
			return -1;
		}
		List<BlockPos> logs = trunk(level, cut);
		if (logs.isEmpty() || logs.stream().noneMatch(p -> natural(level, p))) {
			return -1;
		}
		int taken = 0;
		for (BlockPos p : logs) {
			taken += ChoppedLog.chops(level.getBlockState(p));
		}
		// (and the chops in the stump below the cut, which this tree stood on)
		for (BlockPos p = cut.below(); level.getBlockState(p).is(ChoppedLog.BLOCK) && cut.getY() - p.getY() <= ChoppedLog.MOST; p = p.below()) {
			taken += ChoppedLog.chops(level.getBlockState(p));
		}
		BlockState real = ChoppedLog.real(level, cut, state);
		level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, cut, Block.getId(real));
		if (taken + 1 >= needed(logs.size())) {
			return fell(level, cut, cutter, tool, sink, false);
		}
		// the chop: into the cut, or once that's as thin as it goes, the nearest log up the trunk with wood to spare
		BlockPos at = cut;
		if (ChoppedLog.chops(state) >= ChoppedLog.MOST) {
			at = null;
			for (BlockPos p : logs) {
				if (ChoppedLog.chops(level.getBlockState(p)) < ChoppedLog.MOST && (at == null || p.distSqr(cut) < at.distSqr(cut))) {
					at = p;
				}
			}
			if (at == null) {
				return fell(level, cut, cutter, tool, sink, false);
			}
		}
		BlockState there = level.getBlockState(at);
		ChoppedLog.set(level, at, there, ChoppedLog.chops(there) + 1);
		return 0;
	}

	/**
	 * Brings down the tree standing above {@code cut} (its logs joined to it at or above that height, not far off, and
	 * the leaves only they held): nothing if they don't make a tree (no living leaves). What it gives goes to
	 * {@code sink} (null: where it lands). {@code wear}: the tool takes a point per log. Returns how many logs.
	 * (0.5.9) It topples over whole, away from the cutter ({@link FallingTree}), and breaks apart where it lands.
	 */
	static int fell(ServerLevel level, BlockPos cut, @Nullable Entity cutter, ItemStack tool, @Nullable Consumer<ItemStack> sink, boolean wear) {
		List<BlockPos> logs = trunk(level, cut);
		if (logs.isEmpty() || logs.stream().noneMatch(p -> natural(level, p))) {
			return 0;
		}
		List<BlockPos> leaves = leavesOf(level, logs);
		logs.sort(Comparator.comparingInt(BlockPos::getY));
		if (wear && cutter instanceof Player p && !tool.isEmpty() && tool.isDamageableItem()) {
			tool.hurtAndBreak(Math.max(0, logs.size() - (level.getBlockState(cut).isAir() ? 1 : 0)), p, EquipmentSlot.MAINHAND);
		}
		// (0.7.11) a nest in it comes down too, its bees out and angry at whoever cut it
		leaves.addAll(FallingTree.hives(level, logs, cutter));
		FallingTree.topple(level, cut, logs, leaves, FallingTree.heavier(level, cut, logs, leaves, cutter), cutter, tool, sink);
		// (the growing-tree record, if it was one, is forgotten when its foot is gone)
		return logs.size();
	}

	/** (0.7.152) A Giant walks into the tree standing on {@code log}: it topples away from it, if it's a natural tree. */
	public static void trample(ServerLevel level, BlockPos log, Entity giant) {
		fell(level, log, giant, ItemStack.EMPTY, null, false);
	}

	/**
	 * (0.6.12) A tornado tears the tree standing on {@code foot} out of the ground whole and flings it off at
	 * {@code velocity} ({@link FallingTree#fling}). Null (and nothing happens) if it isn't a living tree rooted in the
	 * ground, or it has more than {@code mostLogs} logs.
	 */
	public static @Nullable FallingTree uproot(ServerLevel level, BlockPos foot, int mostLogs, net.minecraft.world.phys.Vec3 velocity, float heading, float tumble) {
		if (!level.getBlockState(foot.below()).is(BlockTags.DIRT)) {
			return null;
		}
		List<BlockPos> logs = trunk(level, foot);
		if (logs.isEmpty() || logs.size() > mostLogs || logs.size() >= FELL_MOST || logs.stream().noneMatch(p -> natural(level, p))) {
			return null;
		}
		return FallingTree.fling(level, foot, logs, leavesOf(level, logs), velocity, heading, tumble);
	}

	/**
	 * (0.7.11) A log lying on its side on the ground (a felled tree come to rest, {@link FallingTree}) is a fallen trunk,
	 * not a tree: it's broken log by log, not chopped.
	 */
	static boolean fallen(ServerLevel level, BlockPos pos, BlockState state) {
		if (!state.hasProperty(BlockStateProperties.AXIS) || state.getValue(BlockStateProperties.AXIS) == Direction.Axis.Y) {
			return false;
		}
		BlockState below = level.getBlockState(pos.below());
		if (below.is(BlockTags.LOGS)) {
			// (one fallen trunk across another)
			return below.hasProperty(BlockStateProperties.AXIS) && below.getValue(BlockStateProperties.AXIS) != Direction.Axis.Y;
		}
		return below.isFaceSturdy(level, pos.below(), Direction.UP);
	}

	/** The leaves these logs hold that no other wood does. */
	private static List<BlockPos> leavesOf(ServerLevel level, List<BlockPos> logs) {
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
		return leaves;
	}

	/** The logs that come down when {@code cut} is cut: it and those joined to it above (not far off), lowest-found first. */
	private static List<BlockPos> trunk(ServerLevel level, BlockPos cut) {
		List<BlockPos> logs = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		Deque<BlockPos> open = new ArrayDeque<>();
		// (0.5.10) from the logs over the cut: roots and the rest of a wide trunk at the cut's own height stay as a stump
		for (BlockPos n : BlockPos.betweenClosed(cut.offset(-1, 1, -1), cut.offset(1, 1, 1))) {
			BlockPos q = n.immutable();
			if (level.getBlockState(q).is(BlockTags.LOGS)) {
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
				if (n.getY() <= cut.getY() || Math.abs(n.getX() - cut.getX()) > FELL_SPREAD || Math.abs(n.getZ() - cut.getZ()) > FELL_SPREAD) {
					continue;
				}
				BlockPos q = n.immutable();
				if (!seen.contains(q) && level.getBlockState(q).is(BlockTags.LOGS)) {
					seen.add(q);
					open.add(q);
				}
			}
		}
		return logs;
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
}
