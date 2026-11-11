package com.jaymoskingdoms;

import com.jaymoskingdoms.films.LoreFilms;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * The field of The Long Defence (Lore.md, "The field of the long defence"): a wooded forest, a Keep the defenders held, and
 * below it the ford where the forest road crosses water. One per world, and it has always been there: Andy (2026-11-11), "the
 * keep should only ever form in a forest biome ... it should never generate near just because, if it ends up being far out
 * then so be it". So the first time a world starts, its site is chosen from the world's own biome map ({@link #plan}): a
 * forest beside a river, somewhere between {@link #PLAN_NEAREST} and {@link #PLAN_FURTHEST} blocks from spawn, wherever the
 * map puts one. Nothing is built until someone comes near ({@link #BUILD_NEAR}); then the keep goes up as it always stood: a
 * ruined keep of mossy and cracked stone with a broken corner tower and its gate toward the water, a worn road down to a
 * shallow gravel crossing, rubble, vines and cobwebs, and in the keep a chest holding a page in the gate-warden's hand
 * ({@code lore/the_wardens_page}).
 * <p>
 * Standing at the ford is the Act II milestone {@code pack/ford} (the Retaking, {@link Retaking}); the first time it counts,
 * the book's wordless film plays again ({@link LoreFilms#LONG_DEFENCE}), now where it happened. Village talk can point the way
 * ({@code TalkBook}'s {@code retaking_ford}), as to a place long forgotten. The Second Defence (Act V) comes back here.
 */
public final class Ford {
	/** How often (ticks) the world is looked at. */
	static final int CHECK_TICKS = 200;
	/** How far from spawn (blocks) the site may be, how many places are sampled per ring, and how many rings out. */
	static final int PLAN_NEAREST = 1200, PLAN_FURTHEST = 6000, PLAN_SAMPLES = 400, PLAN_RINGS = 4;
	/** How far (blocks) from a forest column a river is looked for on the map, and the step. */
	static final int RIVER_REACH = 48, RIVER_STEP = 4;
	/** A player this near (blocks) the planned site has the keep built, once the ground round it is loaded. */
	static final double BUILD_NEAR = 112.0;
	/** How far from the river's edge the keep stands, and how far from a settlement it must be (only for ops raising one). */
	static final int KEEP_BACK = 14;
	/** (Ops) How many columns near the player are tried for a river. */
	static final int SPOT_TRIES = 40;
	static final double SETTLEMENT_CLEARANCE = 128.0;
	/** The keep's half-width, its walls' height (before breaking), its corner tower's height. */
	static final int HALF = 5, WALL = 8, TOWER = 13;
	/** The ford's width, and the most blocks of water it crosses. */
	static final int FORD_WIDTH = 3, FORD_LONGEST = 40;
	/** How near the ford (blocks) counts as standing at it. */
	static final double AT_FORD = 16.0;

	/** Where the keep's gate and the ford are, once raised (on the Overworld). */
	public record Site(BlockPos keep, BlockPos ford) {
		static final com.mojang.serialization.Codec<Site> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(i -> i.group(
			BlockPos.CODEC.fieldOf("keep").forGetter(Site::keep), BlockPos.CODEC.fieldOf("ford").forGetter(Site::ford)).apply(i, Site::new));
	}

	static final AttachmentType<Site> SITE = AttachmentRegistry.create(JaymosKingdoms.id("long_defence_ford"), builder -> builder.persistent(Site.CODEC));

	/** Where the keep is to stand before it is built: a forest column, and which way the river lies from it. */
	public record Plan(BlockPos column, Direction toWater) {
		static final com.mojang.serialization.Codec<Plan> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(i -> i.group(
			BlockPos.CODEC.fieldOf("column").forGetter(Plan::column), Direction.CODEC.fieldOf("to_water").forGetter(Plan::toWater)).apply(i, Plan::new));
	}

	static final AttachmentType<Plan> PLAN = AttachmentRegistry.create(JaymosKingdoms.id("long_defence_plan"), builder -> builder.persistent(Plan.CODEC));
	/** Set once no site could be found on the map, so the search isn't run on every start. */
	static final AttachmentType<Boolean> NO_SITE = AttachmentRegistry.create(JaymosKingdoms.id("long_defence_no_site"),
		builder -> builder.persistent(com.mojang.serialization.Codec.BOOL));

	private Ford() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % CHECK_TICKS == 0) {
				tick(server);
			}
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(Commands.literal("kingdom").then(command())));
	}

	/** The site, or null if it hasn't been built in this world. */
	public static @Nullable Site site(MinecraftServer server) {
		return Factions.level(server).getAttached(SITE);
	}

	/** Where the keep stands, or will once someone goes near: built or only planned; null if the world has no site. */
	public static @Nullable BlockPos where(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		Site site = level.getAttached(SITE);
		if (site != null) {
			return site.keep();
		}
		Plan plan = level.getAttached(PLAN);
		return plan == null ? null : plan.column();
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		Site site = level.getAttached(SITE);
		if (site == null) {
			Plan plan = level.getAttached(PLAN);
			if (plan == null) {
				if (!level.getAttachedOrElse(NO_SITE, false)) {
					plan(level);
				}
				return;
			}
			for (ServerPlayer player : level.players()) {
				if (horizontal(player.blockPosition(), plan.column()) < BUILD_NEAR * BUILD_NEAR && loadedAround(level, plan.column())) {
					build(level, plan);
					return;
				}
			}
			return;
		}
		for (ServerPlayer player : level.players()) {
			if (player.blockPosition().closerThan(site.ford(), AT_FORD)) {
				atFord(player);
			}
		}
	}

	private static double horizontal(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}

	/** The keep's ground, the road and the far bank are all loaded. */
	private static boolean loadedAround(ServerLevel level, BlockPos column) {
		int reach = HALF * 2 + KEEP_BACK + FORD_LONGEST;
		for (int dx = -reach; dx <= reach; dx += reach) {
			for (int dz = -reach; dz <= reach; dz += reach) {
				if (!level.isLoaded(column.offset(dx, 0, dz))) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Chooses the site from the world's biome map without loading anything: rings out from spawn, a forest column with a
	 * river in reach of it on the map, the river's side remembered. Run once per world.
	 */
	static void plan(ServerLevel level) {
		RandomSource random = RandomSource.create(level.getSeed() ^ 0x1F0DDEFEC1L);
		BlockPos spawn = level.getRespawnData().pos();
		int sea = level.getSeaLevel();
		int span = (PLAN_FURTHEST - PLAN_NEAREST) / PLAN_RINGS;
		for (int ring = 0; ring < PLAN_RINGS; ring++) {
			for (int i = 0; i < PLAN_SAMPLES; i++) {
				double angle = random.nextDouble() * Math.PI * 2.0;
				int r = PLAN_NEAREST + ring * span + random.nextInt(span);
				BlockPos column = new BlockPos(spawn.getX() + Mth.floor(Math.cos(angle) * r), sea, spawn.getZ() + Mth.floor(Math.sin(angle) * r));
				if (!biome(level, column).is(BiomeTags.IS_FOREST)) {
					continue;
				}
				for (Direction d : Direction.Plane.HORIZONTAL.shuffledCopy(random)) {
					for (int step = RIVER_STEP * 2; step <= RIVER_REACH; step += RIVER_STEP) {
						BlockPos at = column.relative(d, step);
						Holder<Biome> b = biome(level, at);
						if (b.is(BiomeTags.IS_RIVER)) {
							// the keep stands well back in the trees, the river ahead of its gate
							level.setAttached(PLAN, new Plan(column, d));
							JaymosKingdoms.LOGGER.info("The keep above the ford will stand at {} (river to the {})", column, d.getSerializedName());
							return;
						}
						if (!b.is(BiomeTags.IS_FOREST)) {
							break;
						}
					}
				}
			}
		}
		level.setAttached(NO_SITE, true);
		JaymosKingdoms.LOGGER.warn("No forest beside a river found for the keep above the ford");
	}

	private static Holder<Biome> biome(ServerLevel level, BlockPos at) {
		return level.getUncachedNoiseBiome(QuartPos.fromBlock(at.getX()), QuartPos.fromBlock(at.getY()), QuartPos.fromBlock(at.getZ()));
	}

	/** Builds the planned keep now that someone is near, facing the river; the ford wherever the water is found. */
	static Site build(ServerLevel level, Plan plan) {
		BlockPos keep = surface(level, plan.column());
		// walk toward the river to the last dry ground before it, and stand the gate a little way back from that
		BlockPos at = keep;
		for (int i = 0; i < RIVER_REACH + FORD_LONGEST; i++) {
			BlockPos next = surface(level, at.relative(plan.toWater()));
			if (!level.getFluidState(next).isEmpty()) {
				break;
			}
			at = next;
		}
		BlockPos gate = surface(level, at.relative(plan.toWater().getOpposite(), Math.min(KEEP_BACK, Math.max(4, horizontalBlocks(keep, at)))));
		level.removeAttached(PLAN);
		return raise(level, gate, plan.toWater(), level.getRandom());
	}

	private static int horizontalBlocks(BlockPos a, BlockPos b) {
		return (int)Math.sqrt(horizontal(a, b));
	}

	/** {@code player} stands at the ford: the milestone, and the first time it counts, the film. */
	private static void atFord(ServerPlayer player) {
		AdvancementHolder holder = PackAdvancements.get(player.level().getServer(), "ford");
		if (holder == null || player.getAdvancements().getOrStartProgress(holder).isDone() || !Retaking.open(player, "ford")) {
			return;
		}
		PackAdvancements.award(player, "ford", "done");
		LoreFilms.play(player, LoreFilms.LONG_DEFENCE);
	}

	// ---- raising it ---------------------------------------------------------------------------------------------------

	/** (Ops) Looks for a river with forest on a bank between {@code nearest} and {@code furthest} blocks of {@code from}; raises it there. */
	static @Nullable Site raiseNear(ServerLevel level, BlockPos from, int nearest, int furthest, RandomSource random) {
		for (int i = 0; i < SPOT_TRIES; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int r = nearest + random.nextInt(Math.max(1, furthest - nearest + 1));
			BlockPos column = from.offset(Mth.floor(Math.cos(angle) * r), 0, Mth.floor(Math.sin(angle) * r));
			if (!level.isLoaded(column)) {
				continue;
			}
			BlockPos water = surface(level, column);
			if (!level.getFluidState(water).isSource() || !level.getBiome(water).is(BiomeTags.IS_RIVER)) {
				continue;
			}
			for (Direction d : Direction.Plane.HORIZONTAL.shuffledCopy(random)) {
				BlockPos edge = bank(level, water, d);
				if (edge == null) {
					continue;
				}
				BlockPos keep = surface(level, edge.relative(d, KEEP_BACK));
				if (!level.isLoaded(keep) || !wooded(level.getBiome(keep)) || !level.getFluidState(keep).isEmpty()
					|| Factions.around(level.getServer(), level, keep, SETTLEMENT_CLEARANCE) != null) {
					continue;
				}
				return raise(level, keep, d.getOpposite(), random);
			}
		}
		return null;
	}

	private static boolean wooded(Holder<Biome> biome) {
		return biome.is(BiomeTags.IS_FOREST);
	}

	/** The top block of a column: water's surface, or the first solid ground (leaves and trees ignored). */
	private static BlockPos surface(ServerLevel level, BlockPos column) {
		return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column).below();
	}

	/** Walking from {@code water} toward {@code d}, the first dry column within the ford's reach; null if none. */
	private static @Nullable BlockPos bank(ServerLevel level, BlockPos water, Direction d) {
		BlockPos at = water;
		for (int i = 0; i < FORD_LONGEST; i++) {
			at = at.relative(d);
			BlockPos top = surface(level, at);
			if (level.getFluidState(top).isEmpty()) {
				return top;
			}
		}
		return null;
	}

	/**
	 * Raises the keep with its gate at {@code keep} looking {@code toWater}, the road down from it and the ford across the
	 * water, and remembers it.
	 */
	public static Site raise(ServerLevel level, BlockPos keep, Direction toWater, RandomSource random) {
		Direction side = toWater.getClockWise();
		BlockPos centre = keep.relative(toWater.getOpposite(), HALF);
		int base = centre.getY();
		// the keep: cleared inside, a stone floor, broken walls round it, a tower on one far corner
		for (int dx = -HALF; dx <= HALF; dx++) {
			for (int dz = -HALF; dz <= HALF; dz++) {
				BlockPos col = centre.offset(dx, 0, dz);
				for (int y = 1; y <= TOWER + 2; y++) {
					set(level, col.above(y), Blocks.AIR.defaultBlockState());
				}
				for (int y = 0; y >= -4; y--) {
					BlockPos below = col.above(y);
					if (y == 0 || level.getBlockState(below).isAir() || !level.getFluidState(below).isEmpty()) {
						set(level, below, y == 0 && random.nextInt(4) != 0 ? floor(random) : wall(random));
					}
				}
				boolean edge = Math.abs(dx) == HALF || Math.abs(dz) == HALF;
				if (!edge) {
					continue;
				}
				// the gate: a gap three wide in the wall facing the water
				int along = toWater.getAxis() == Direction.Axis.X ? dz : dx;
				int out = toWater.getAxis() == Direction.Axis.X ? dx * toWater.getStepX() : dz * toWater.getStepZ();
				if (out == HALF && Math.abs(along) <= 1) {
					continue;
				}
				int height = WALL - random.nextInt(WALL - 1);
				if (random.nextInt(5) == 0) {
					height = 1 + random.nextInt(2);
				}
				for (int y = 1; y <= height; y++) {
					set(level, col.above(y), wall(random));
				}
				if (height > 3 && random.nextInt(4) == 0) {
					set(level, col.above(height + 1), Blocks.VINE.defaultBlockState());
				}
			}
		}
		// the corner tower, a 3x3 core on the corner away from the water, its top half fallen in
		BlockPos tower = centre.relative(toWater.getOpposite(), HALF - 1).relative(side, HALF - 1);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				int top = TOWER - (dx == 0 && dz == 0 ? 0 : random.nextInt(6));
				for (int y = 1; y <= top; y++) {
					set(level, tower.offset(dx, y, dz), wall(random));
				}
			}
		}
		// cobwebs in the corners inside, and the chest with the page
		for (int i = 0; i < 6; i++) {
			BlockPos web = centre.offset(random.nextInt(2 * HALF - 1) - HALF + 1, 1 + random.nextInt(3), random.nextInt(2 * HALF - 1) - HALF + 1);
			if (level.getBlockState(web).isAir()) {
				set(level, web, Blocks.COBWEB.defaultBlockState());
			}
		}
		BlockPos chest = centre.relative(toWater.getOpposite(), HALF - 2).relative(side.getOpposite(), HALF - 2).above();
		set(level, chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, toWater));
		if (level.getBlockEntity(chest) instanceof ChestBlockEntity box) {
			box.setItem(0, LoreBooks.book("the_wardens_page"));
			box.setItem(4, new ItemStack(Items.ARROW, 3 + random.nextInt(6)));
			box.setItem(8, new ItemStack(Items.IRON_NUGGET, 2 + random.nextInt(5)));
			box.setItem(13, new ItemStack(Items.LANTERN));
		}
		// rubble fallen outside the walls
		for (int i = 0; i < 10; i++) {
			BlockPos at = surface(level, centre.offset(random.nextInt(2 * HALF + 9) - HALF - 4, 0, random.nextInt(2 * HALF + 9) - HALF - 4)).above();
			if (level.getBlockState(at).isAir() && level.getFluidState(at.below()).isEmpty()) {
				set(level, at, random.nextBoolean() ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.COBBLESTONE_SLAB.defaultBlockState());
			}
		}
		// the road down from the gate to the water, then the ford across it
		BlockPos at = keep.relative(toWater);
		BlockPos ford = null;
		for (int i = 0; i < KEEP_BACK + FORD_LONGEST + 8; i++) {
			BlockPos top = surface(level, at);
			boolean wet = !level.getFluidState(top).isEmpty();
			if (wet && ford == null) {
				ford = top;
			}
			if (!wet && ford != null) {
				break;
			}
			for (int w = -(FORD_WIDTH / 2); w <= FORD_WIDTH / 2; w++) {
				BlockPos lane = surface(level, at.relative(side, w));
				if (wet) {
					// a shallow crossing: the bed raised to one block under the surface
					BlockPos bed = lane.below();
					while (bed.getY() > lane.getY() - 6 && !level.getFluidState(bed).isEmpty()) {
						set(level, bed, random.nextInt(3) == 0 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.GRAVEL.defaultBlockState());
						bed = bed.below();
					}
				} else if (level.getBlockState(lane).is(Blocks.GRASS_BLOCK) || level.getBlockState(lane).is(Blocks.DIRT)
					|| level.getBlockState(lane).is(Blocks.PODZOL) || level.getBlockState(lane).is(Blocks.COARSE_DIRT)) {
					set(level, lane, random.nextInt(4) == 0 ? Blocks.GRAVEL.defaultBlockState() : Blocks.DIRT_PATH.defaultBlockState());
					set(level, lane.above(), Blocks.AIR.defaultBlockState());
				}
			}
			at = at.relative(toWater);
		}
		Site site = new Site(keep.immutable(), (ford == null ? keep : ford).immutable());
		level.setAttached(SITE, site);
		JaymosKingdoms.LOGGER.info("The keep above the ford rose at {} (ford {}), base {}", keep, site.ford(), base);
		return site;
	}

	private static BlockState wall(RandomSource random) {
		int n = random.nextInt(10);
		return n < 4 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : n < 7 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
			: n < 9 ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState();
	}

	private static BlockState floor(RandomSource random) {
		return random.nextBoolean() ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState();
	}

	private static void set(ServerLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state, Block.UPDATE_ALL);
	}

	// ---- for ops -------------------------------------------------------------------------------------------------------

	/** {@code /kingdom ford where|raise}, for ops. */
	static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("ford").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("where").executes(c -> {
				Site site = site(c.getSource().getServer());
				BlockPos planned = where(c.getSource().getServer());
				c.getSource().sendSuccess(() -> site != null ? Component.literal("Keep at " + site.keep().toShortString() + ", ford at " + site.ford().toShortString())
					: planned != null ? Component.literal("The keep will stand at " + planned.getX() + " " + planned.getZ() + " (built when someone comes near).")
					: Component.literal("This world has no forest beside a river for the keep."), false);
				return 1;
			}))
			.then(Commands.literal("raise").executes(c -> {
				ServerPlayer player = c.getSource().getPlayerOrException();
				ServerLevel level = Factions.level(c.getSource().getServer());
				if (player.level() != level) {
					c.getSource().sendFailure(Component.literal("Only in the Overworld."));
					return 0;
				}
				Site site = raiseNear(level, player.blockPosition(), 8, 128, level.getRandom());
				if (site == null) {
					c.getSource().sendFailure(Component.literal("No river with woods on its bank near enough. Try nearer a forest river."));
					return 0;
				}
				c.getSource().sendSuccess(() -> Component.literal("Keep raised at " + site.keep().toShortString() + ", ford at " + site.ford().toShortString()),
					true);
				return 1;
			}));
	}
}
