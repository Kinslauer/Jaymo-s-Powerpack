package com.jaymoskingdoms.blossomland;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.seas.Mohknee;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;

/**
 * The sunken palace: a two-storeyed hall of green sea stone under a double roof, standing whole on the seabed in the
 * deep water off Mohknee. There are {@link #PALACES} of them in a world, each on its own heading from the land's
 * middle, where the sea first runs {@link #DEPTH} deep past the shore ({@link #sites}: read from the world's shape, so
 * the same world always has them in the same places). One is built the first time a player comes within
 * {@link #LAY_RANGE} blocks of where it stands, with guardians keeping it and its chests stocked
 * ({@code chests/sunken_palace}). Nothing marks it from above: a ship over it and a dive are how it is found, and what
 * the blossom fishers say of roofs in the water is the only hint.
 */
public final class SunkenPalace {
	/** How many there are, and the sea's depth one needs (its roof ridge stands {@link #HEIGHT} above its floor). */
	static final int PALACES = 3, DEPTH = 24, HEIGHT = 15;
	/** Where the search for deep water starts past the land's widest shore, its step, and how far out it gives up (blocks). */
	static final int SEARCH_FROM = 250, SEARCH_STEP = 32, SEARCH_REACH = 2600;
	/** A palace is built once a player is this near it (and the place is loaded). */
	static final double LAY_RANGE = 96.0;
	/** Half its platform's width and depth, and how far its lower roof overhangs. */
	static final int HALF_W = 12, HALF_D = 8, EAVES = 2;
	/** The upper hall's half width and depth. */
	static final int UP_W = 7, UP_D = 4;
	/** How far down the platform's footing is carried to meet an uneven seabed. */
	static final int FOOTING = 8;
	/** Its keepers, and the chance a roof block has coral grown on it. */
	static final int GUARDIANS = 8;
	static final float CORAL = 0.07F;

	static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE, JaymosKingdoms.id("chests/sunken_palace"));
	/** The palaces already built (which of {@link #sites}). */
	static final AttachmentType<List<Integer>> BUILT = AttachmentRegistry.create(JaymosKingdoms.id("sunken_palaces"),
		builder -> builder.persistent(Codec.INT.listOf()));

	private static final Block[] CORALS = {Blocks.TUBE_CORAL_BLOCK, Blocks.BRAIN_CORAL_BLOCK, Blocks.BUBBLE_CORAL_BLOCK, Blocks.FIRE_CORAL_BLOCK,
		Blocks.HORN_CORAL_BLOCK};
	private static @Nullable List<BlockPos> found;
	private static @Nullable MinecraftServer foundFor;

	private SunkenPalace() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(SunkenPalace::tick);
	}

	/**
	 * Where the palaces stand (x, sea level, z): on {@link #PALACES} headings a third of a turn apart, turned by the
	 * world's seed, each at the first place past the shore where the sea is {@link #DEPTH} deep. A heading with no such
	 * water has none. Read from the generator, so no chunk is loaded.
	 */
	public static List<BlockPos> sites(ServerLevel level) {
		if (found != null && foundFor == level.getServer()) {
			return found;
		}
		List<BlockPos> sites = new ArrayList<>();
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		if (Mohknee.overworld(generator)) {
			RandomState state = level.getChunkSource().randomState();
			double turn = new java.util.Random(level.getSeed() ^ 0x5eaf100dL).nextDouble() * Math.PI * 2.0;
			int sea = generator.getSeaLevel();
			for (int i = 0; i < PALACES; i++) {
				double angle = turn + i * Math.PI * 2.0 / PALACES;
				int from = Mohknee.LAND + Mohknee.WOBBLE + SEARCH_FROM;
				for (int r = from; r < from + SEARCH_REACH; r += SEARCH_STEP) {
					int x = Mohknee.CENTRE_X + Mth.floor(Math.cos(angle) * r), z = Mohknee.CENTRE_Z + Mth.floor(Math.sin(angle) * r);
					if (!Mohknee.island(x, z) && sea - generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, state) >= DEPTH) {
						sites.add(new BlockPos(x, sea, z));
						break;
					}
				}
			}
		}
		found = List.copyOf(sites);
		foundFor = level.getServer();
		return found;
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (level.getGameTime() % 100 != 0 || level.players().isEmpty()) {
			return;
		}
		List<BlockPos> sites = null;
		for (ServerPlayer player : level.players()) {
			// nobody further than this from the land's middle is near one
			if (Mohknee.distance(player.getX(), player.getZ()) > Mohknee.LAND + Mohknee.WOBBLE + SEARCH_FROM + SEARCH_REACH + LAY_RANGE) {
				continue;
			}
			sites = sites == null ? sites(level) : sites;
			for (int i = 0; i < sites.size(); i++) {
				BlockPos at = sites.get(i);
				double dx = at.getX() - player.getX(), dz = at.getZ() - player.getZ();
				if (dx * dx + dz * dz < LAY_RANGE * LAY_RANGE && level.isPositionEntityTicking(at) && !level.getAttachedOrElse(BUILT, List.of()).contains(i)) {
					List<Integer> built = new ArrayList<>(level.getAttachedOrElse(BUILT, List.of()));
					built.add(i);
					level.setAttached(BUILT, List.copyOf(built));
					BlockPos floor = level.getHeightmapPos(Heightmap.Types.OCEAN_FLOOR, at);
					if (at.getY() - floor.getY() >= HEIGHT + 2 && level.getFluidState(floor).is(FluidTags.WATER)) {
						build(level, floor, level.getRandom());
						JaymosKingdoms.LOGGER.info("A sunken palace stands at {}", floor);
					} else {
						JaymosKingdoms.LOGGER.info("No sunken palace at {}: the sea there is too shallow", floor);
					}
					return;
				}
			}
		}
	}

	/** The site nearest {@code x, z}, or null if the world has none. */
	public static @Nullable BlockPos nearest(ServerLevel level, double x, double z) {
		BlockPos best = null;
		double least = Double.MAX_VALUE;
		for (BlockPos at : sites(level)) {
			double d = (at.getX() - x) * (at.getX() - x) + (at.getZ() - z) * (at.getZ() - z);
			if (d < least) {
				least = d;
				best = at;
			}
		}
		return best;
	}

	// ---- the building -----------------------------------------------------------------------------------------------------

	/**
	 * Builds the palace with the middle of its platform on the seabed at {@code floor}, its door to the north. From the
	 * bottom: a footing down to the seabed, a platform two deep, a pillared veranda round a walled hall five high with
	 * a dais and two chests at its back, a wide roof with turned-up corners, a small upper hall with two more chests,
	 * and a second roof stepped up to a ridge. Sea lanterns light it and the sea fills it.
	 */
	public static void build(ServerLevel level, BlockPos floor, RandomSource random) {
		BlockState brick = Blocks.PRISMARINE_BRICKS.defaultBlockState(), dark = Blocks.DARK_PRISMARINE.defaultBlockState();
		BlockState lamp = Blocks.SEA_LANTERN.defaultBlockState(), water = Blocks.WATER.defaultBlockState(), rough = Blocks.PRISMARINE.defaultBlockState();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int ox = floor.getX(), oy = floor.getY(), oz = floor.getZ();
		// the footing and the platform
		for (int x = -HALF_W; x <= HALF_W; x++) {
			for (int z = -HALF_D; z <= HALF_D; z++) {
				for (int y = -1; y >= -FOOTING; y--) {
					p.set(ox + x, oy + y, oz + z);
					if (!level.getFluidState(p).is(FluidTags.WATER) && !level.getBlockState(p).canBeReplaced()) {
						break;
					}
					level.setBlock(p, rough, Block.UPDATE_CLIENTS);
				}
				boolean lit = Math.abs(x) == 6 && Math.abs(z) == 3;
				set(level, p, ox + x, oy, oz + z, brick);
				set(level, p, ox + x, oy + 1, oz + z, lit ? lamp : brick);
			}
		}
		// the steps before the door
		for (int x = -3; x <= 3; x++) {
			set(level, p, ox + x, oy, oz - HALF_D - 1, brick);
			set(level, p, ox + x, oy, oz - HALF_D - 2, Blocks.PRISMARINE_BRICK_SLAB.defaultBlockState()
				.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true));
		}
		// the sea through both halls, whatever of the seabed stood in them
		for (int y = 2; y <= 10; y++) {
			int w = y <= 6 ? HALF_W : UP_W, d = y <= 6 ? HALF_D : UP_D;
			for (int x = -w; x <= w; x++) {
				for (int z = -d; z <= d; z++) {
					set(level, p, ox + x, oy + y, oz + z, water);
				}
			}
		}
		// the lower hall: pillars round the veranda, and the wall inside them
		for (int y = 2; y <= 6; y++) {
			for (int x = -HALF_W; x <= HALF_W; x++) {
				for (int z = -HALF_D; z <= HALF_D; z++) {
					boolean rim = Math.abs(x) == HALF_W || Math.abs(z) == HALF_D;
					if (rim && (Math.abs(z) == HALF_D && x % 4 == 0 || Math.abs(x) == HALF_W && z % 4 == 0)) {
						set(level, p, ox + x, oy + y, oz + z, dark);
					}
					boolean wall = (Math.abs(x) == HALF_W - 2 && Math.abs(z) <= HALF_D - 2) || (Math.abs(z) == HALF_D - 2 && Math.abs(x) <= HALF_W - 2);
					boolean door = z == -(HALF_D - 2) && Math.abs(x) <= 1 && y <= 4;
					boolean corner = Math.abs(x) == HALF_W - 2 && Math.abs(z) == HALF_D - 2;
					boolean window = y == 4 && !corner && (x + z) % 2 != 0;
					if (wall && !door && !window) {
						set(level, p, ox + x, oy + y, oz + z, brick);
					}
				}
			}
		}
		// the dais at the back, what stood on it, and the hall's two chests
		for (int x = -3; x <= 3; x++) {
			for (int z = HALF_D - 4; z <= HALF_D - 3; z++) {
				set(level, p, ox + x, oy + 2, oz + z, dark);
			}
		}
		set(level, p, ox, oy + 3, oz + HALF_D - 3, lamp);
		set(level, p, ox, oy + 4, oz + HALF_D - 3, Blocks.GOLD_BLOCK.defaultBlockState());
		chest(level, new BlockPos(ox - 2, oy + 3, oz + HALF_D - 3), random);
		chest(level, new BlockPos(ox + 2, oy + 3, oz + HALF_D - 3), random);
		// the lower roof: a wide deck with its corners turned up, a step in from it, and a way up through its middle
		for (int x = -HALF_W - EAVES; x <= HALF_W + EAVES; x++) {
			for (int z = -HALF_D - EAVES; z <= HALF_D + EAVES; z++) {
				boolean hatch = Math.abs(x) <= 1 && Math.abs(z) <= 1;
				set(level, p, ox + x, oy + 7, oz + z, hatch ? water : dark);
				boolean corner = Math.abs(x) == HALF_W + EAVES && Math.abs(z) == HALF_D + EAVES;
				boolean step = Math.abs(x) <= HALF_W - 1 && Math.abs(z) <= HALF_D - 1 && (Math.abs(x) > UP_W || Math.abs(z) > UP_D);
				if (corner) {
					set(level, p, ox + x, oy + 8, oz + z, lamp);
				} else if (step) {
					set(level, p, ox + x, oy + 8, oz + z, dark);
				} else if (Math.abs(x) > HALF_W - 1 || Math.abs(z) > HALF_D - 1) {
					coral(level, p, ox + x, oy + 8, oz + z, random);
				}
			}
		}
		// the upper hall
		for (int y = 8; y <= 10; y++) {
			for (int x = -UP_W; x <= UP_W; x++) {
				for (int z = -UP_D; z <= UP_D; z++) {
					boolean edge = Math.abs(x) == UP_W || Math.abs(z) == UP_D;
					boolean post = Math.abs(x) == UP_W && Math.abs(z) == UP_D;
					boolean open = y == 9 && !post && (x + z) % 2 == 0;
					if (edge && !open) {
						set(level, p, ox + x, oy + y, oz + z, post ? dark : brick);
					}
				}
			}
		}
		chest(level, new BlockPos(ox - UP_W + 2, oy + 8, oz + UP_D - 1), random);
		chest(level, new BlockPos(ox + UP_W - 2, oy + 8, oz + UP_D - 1), random);
		set(level, p, ox, oy + 10, oz, lamp);
		// the upper roof, stepped up to its ridge
		for (int tier = 0; tier < 3; tier++) {
			int w = UP_W + 2 - tier * 3 + (tier == 2 ? 1 : 0), d = UP_D + 2 - tier * 2 - (tier == 2 ? 1 : 0), y = 11 + tier;
			for (int x = -w; x <= w; x++) {
				for (int z = -d; z <= d; z++) {
					set(level, p, ox + x, oy + y, oz + z, dark);
					if (tier == 0 && Math.abs(x) == w && Math.abs(z) == d) {
						set(level, p, ox + x, oy + y + 1, oz + z, lamp);
					} else if (tier < 2 && (Math.abs(x) == w || Math.abs(z) == d)) {
						coral(level, p, ox + x, oy + y + 1, oz + z, random);
					}
				}
			}
			if (tier == 2) {
				set(level, p, ox - w, oy + y + 1, oz, lamp);
				set(level, p, ox + w, oy + y + 1, oz, lamp);
			}
		}
		// its keepers
		for (int i = 0; i < GUARDIANS; i++) {
			Guardian keeper = EntityTypes.GUARDIAN.create(level, EntitySpawnReason.STRUCTURE);
			if (keeper != null) {
				double angle = Math.PI * 2.0 * i / GUARDIANS;
				boolean inside = i % 4 == 0;
				double r = inside ? 3.0 : HALF_W + 4.0;
				keeper.setPos(ox + 0.5 + Math.cos(angle) * r, oy + (inside ? 4.0 : 5.0 + random.nextInt(6)), oz + 0.5 + Math.sin(angle) * r * 0.75);
				keeper.setPersistenceRequired();
				keeper.setHomeTo(floor.above(5), HALF_W + 8);
				level.addFreshEntity(keeper);
			}
		}
	}

	private static void set(ServerLevel level, BlockPos.MutableBlockPos p, int x, int y, int z, BlockState state) {
		level.setBlock(p.set(x, y, z), state, Block.UPDATE_CLIENTS);
	}

	private static void coral(ServerLevel level, BlockPos.MutableBlockPos p, int x, int y, int z, RandomSource random) {
		if (random.nextFloat() < CORAL && level.getFluidState(p.set(x, y, z)).is(FluidTags.WATER)) {
			level.setBlock(p, CORALS[random.nextInt(CORALS.length)].defaultBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	private static void chest(ServerLevel level, BlockPos at, RandomSource random) {
		level.setBlock(at, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.WATERLOGGED, true),
			Block.UPDATE_CLIENTS);
		if (level.getBlockEntity(at) instanceof RandomizableContainerBlockEntity box) {
			box.setLootTable(LOOT, random.nextLong());
		}
	}
}
