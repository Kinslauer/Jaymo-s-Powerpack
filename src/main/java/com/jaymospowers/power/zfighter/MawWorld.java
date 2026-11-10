package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.zfighter.farworld.FarWorlds;
import com.jaymospowers.power.zfighter.pod.PodWrecks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;

/**
 * The Maw's body: a world broken open and hung in its own pull over the country it came from (the ruins below are
 * {@link MawCities}; the pull and the tide at its heart are {@link ZFighterSpace}). Built the first time anyone lands,
 * the same every time ({@link #SEED}):
 * <ul>
 * <li>the world itself, {@link #RADIUS} across from its middle: scorched soil over rock over deep rock, hollow inside
 * ({@link #HOLLOW}, where the tide is) round a smouldering heart; cracked through by {@link #CRACKS} planes into pieces
 * that have each drifted a little from the rest ({@link #DRIFT_MIN} to {@link #DRIFT_MAX}), so you see in to the heart,
 * the rock along the cracks fused to glass in patches under the crust (what sheds Maw dust, {@code BackHome}); one
 * piece, the one facing the pad, is gone, its rubble flung out on that side;</li>
 * <li>broken walls still standing on its top and hanging from its underside;</li>
 * <li>rubble all round it, and a trail of it the pull holds up between the world and the ground;</li>
 * <li>the {@link #WRECKS} wrecks the pod sets down on, each holding its deck's part set ({@link PodWrecks}): the even
 * ones what is left of ships, the odd ones pieces of the world's own ground with a house's broken corner on them.</li>
 * </ul>
 */
final class MawWorld {
	// ---- tuning ----
	/** The broken world: its outer radius, how deep its soil and crust go, the hollow inside it, and its heart. */
	static final int RADIUS = 22, CRUST = 3, HOLLOW = 10, HEART = 4;
	/** How far its face rises and falls (blocks). */
	static final double ROUGH = 1.5;
	/** The planes that crack it through the middle (each splits it in two; three make eight pieces). */
	static final double[][] CRACKS = {{0.92, 0.25, 0.30}, {-0.20, 0.95, 0.24}, {0.33, -0.28, 0.90}};
	/**
	 * How far each piece has drifted from the middle (min, max blocks); how deep the fused glass along a crack goes, and how much
	 * of a crack's face below the crust it covers (in 100: the rest shows the world's rock).
	 */
	static final double DRIFT_MIN = 2.5, DRIFT_MAX = 5.0, GLASS_DEPTH = 0.7;
	static final int GLASS_SHARE = 55;
	/** Broken walls on its top and under it, how far from the middle they may stand, and how long and high one runs at most. */
	static final int RUINS_TOP = 7, RUINS_UNDER = 5, RUIN_SPREAD = 13, RUIN_LENGTH = 6, RUIN_HEIGHT = 5;
	/** Rubble round it: how many pieces, how far out (from the middle), and the most blocks across one is. */
	static final int RUBBLE = 140, RUBBLE_MIN = 27, RUBBLE_MAX = 72, RUBBLE_SIZE = 3;
	/** The torn-away piece's rubble: how many, and how far past the world's face it was flung (min, max). */
	static final int TORN_RUBBLE = 50, TORN_MIN = 4, TORN_MAX = 30;
	/** The trail the pull holds up under it: how many pieces, how far below its underside it starts and reaches, and how wide. */
	static final int TRAIL = 60, TRAIL_TOP = 4, TRAIL_DROP = 38, TRAIL_SPREAD = 30;
	/** The wrecks: how many, how far out (min, max), and how high above or below the pad's height. */
	static final int WRECKS = 9, WRECK_MIN = 34, WRECK_MAX = 64, WRECK_RISE = 14;
	/** Rubble keeps this far from a wreck's middle and from the pad, and out of the open sky over them. */
	static final int CLEAR_OF_WRECK = 8, CLEAR_OF_PAD = 10;
	static final long SEED = 0x4D4157L;

	private static final ResourceKey<LootTable> WRECK_LOOT = ResourceKey.create(Registries.LOOT_TABLE, JaymosPowers.id("chests/maw_wreck"));

	/** A wreck: its middle and its deck's number (the part set it holds). */
	record Wreck(BlockPos centre, int deck) {
	}

	private MawWorld() {
	}

	private static BlockState vanilla(String id) {
		return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(id)).defaultBlockState();
	}

	private static BlockPos middle() {
		return BlockPos.containing(ZFighterSpace.mawHeart());
	}

	private static BlockPos pad() {
		return new BlockPos(ZFighterSpace.MAW_PAD_X, ZFighterSpace.MAW_Y, 0);
	}

	// ---------------------------------------------------------------- where things are

	/** The wrecks, the same every time ({@link #SEED}); one that would crowd the pad is left out. */
	static List<Wreck> wrecks() {
		RandomSource random = RandomSource.create(SEED);
		BlockPos pad = pad();
		List<Wreck> wrecks = new ArrayList<>();
		for (int i = 0; i < WRECKS; i++) {
			double angle = Math.PI * 2.0 * i / WRECKS + random.nextDouble() * 0.5;
			double distance = WRECK_MIN + random.nextDouble() * (WRECK_MAX - WRECK_MIN);
			BlockPos centre = new BlockPos((int)Math.round(Math.cos(angle) * distance), ZFighterSpace.MAW_Y + random.nextInt(WRECK_RISE * 2 + 1) - WRECK_RISE,
				(int)Math.round(Math.sin(angle) * distance));
			if (Math.abs(centre.getX() - pad.getX()) < 12 && Math.abs(centre.getZ() - pad.getZ()) < 12) {
				continue;
			}
			wrecks.add(new Wreck(centre, i));
		}
		return wrecks;
	}

	/** Where a pod may come down in the Maw: on a wreck's deck, clear of its chest, lantern, mast and wrecked pod (the pad if none). */
	static List<BlockPos> decks() {
		List<BlockPos> decks = new ArrayList<>();
		for (Wreck wreck : wrecks()) {
			decks.add(wreck.centre().offset(1, 1, -2));
		}
		if (decks.isEmpty()) {
			decks.add(pad());
		}
		return decks;
	}

	// ---------------------------------------------------------------- building it

	/** The broken world, its rubble and the wrecks: built the first time anyone lands, if not there (its heart is the mark). */
	static void build(ServerLevel level) {
		BlockPos middle = middle();
		BlockState heart = vanilla("black_concrete");
		if (level.getBlockState(middle).is(heart.getBlock())) {
			return;
		}
		RandomSource random = RandomSource.create(SEED);
		Vec3[] cracks = new Vec3[CRACKS.length];
		for (int i = 0; i < cracks.length; i++) {
			cracks[i] = new Vec3(CRACKS[i][0], CRACKS[i][1], CRACKS[i][2]).normalize();
		}
		// each piece's way out (between its cracks), how far it has drifted along it, and the one torn away (facing the pad)
		int pieces = 1 << cracks.length;
		Vec3[] outward = new Vec3[pieces];
		BlockPos[] drift = new BlockPos[pieces];
		int torn = 0;
		for (int piece = 0; piece < pieces; piece++) {
			Vec3 way = Vec3.ZERO;
			for (int i = 0; i < cracks.length; i++) {
				way = way.add(cracks[i].scale((piece & 1 << i) != 0 ? 1.0 : -1.0));
			}
			outward[piece] = way.normalize();
			Vec3 moved = outward[piece].scale(DRIFT_MIN + random.nextDouble() * (DRIFT_MAX - DRIFT_MIN));
			drift[piece] = new BlockPos((int)Math.round(moved.x), (int)Math.round(moved.y), (int)Math.round(moved.z));
			if (outward[piece].x < outward[torn].x) {
				torn = piece;
			}
		}
		world(level, middle, heart, cracks, drift, torn);
		for (int k = 0; k < RUINS_TOP + RUINS_UNDER; k++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double out = random.nextDouble() * RUIN_SPREAD;
			ruin(level, middle, (int)Math.round(Math.cos(angle) * out), (int)Math.round(Math.sin(angle) * out), k < RUINS_TOP, random);
		}
		List<Wreck> wrecks = wrecks();
		for (Wreck wreck : wrecks) {
			wreck(level, wreck, RandomSource.create(SEED ^ wreck.centre().asLong()));
		}
		// (after the wrecks) the decks hold veiled ore under them and the Maw's rocks on top
		for (Wreck wreck : wrecks) {
			FarWorlds.mawDeck(level, wreck.centre(), wreck.deck());
		}
		rubble(level, middle, wrecks, outward[torn], random);
	}

	/** The world's body and heart; piece {@code torn} is left out, the others moved by their {@code drift}. */
	private static void world(ServerLevel level, BlockPos middle, BlockState heart, Vec3[] cracks, BlockPos[] drift, int torn) {
		int reach = RADIUS + (int)Math.ceil(ROUGH);
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dy = -reach; dy <= reach; dy++) {
				for (int dz = -reach; dz <= reach; dz++) {
					double r = Math.sqrt(dx * dx + dy * dy + dz * dz);
					int h = hash(dx, dy, dz);
					if (r <= HEART) {
						// the heart, still smouldering round a burnt-out middle
						BlockState state = r <= 1.5 ? heart : (h % 100 < 65 ? Blocks.MAGMA_BLOCK : Blocks.CRYING_OBSIDIAN).defaultBlockState();
						level.setBlock(middle.offset(dx, dy, dz), state, Block.UPDATE_CLIENTS);
						continue;
					}
					double face = RADIUS + ROUGH * Math.sin(dx / r * 5.1 + 1.3) * Math.sin(dy / r * 4.3 + 0.4) * Math.sin(dz / r * 5.7 + 2.2);
					if (r < HOLLOW || r > face) {
						continue;
					}
					int piece = 0;
					double crack = Double.MAX_VALUE;
					for (int i = 0; i < cracks.length; i++) {
						double side = dx * cracks[i].x + dy * cracks[i].y + dz * cracks[i].z;
						if (side > 0.0) {
							piece |= 1 << i;
						}
						crack = Math.min(crack, Math.abs(side));
					}
					if (piece == torn) {
						continue;
					}
					boolean fused = crack <= GLASS_DEPTH && face - r >= CRUST && h % 100 < GLASS_SHARE;
					BlockState state = fused ? vanilla(h % 2 == 0 ? "orange_stained_glass" : "yellow_stained_glass") : body(r, face, h);
					level.setBlock(middle.offset(dx, dy, dz).offset(drift[piece]), state, Block.UPDATE_CLIENTS);
				}
			}
		}
	}

	/** The world's rock at radius {@code r} under a face at {@code face}: its soil, crust, deep rock, and the burning inside. */
	private static BlockState body(double r, double face, int h) {
		double depth = face - r;
		int roll = h % 100;
		Block block;
		if (depth < 1.0) {
			block = roll < 38 ? Blocks.COARSE_DIRT : roll < 56 ? Blocks.GRAVEL : roll < 70 ? Blocks.COBBLESTONE : roll < 85 ? Blocks.SOUL_SOIL : Blocks.ROOTED_DIRT;
		} else if (depth < CRUST) {
			block = roll < 55 ? Blocks.DIRT : roll < 80 ? Blocks.STONE : Blocks.GRAVEL;
		} else if (r < HOLLOW + 2) {
			block = roll < 30 ? Blocks.MAGMA_BLOCK : roll < 70 ? Blocks.BLACKSTONE : Blocks.BASALT;
		} else if (depth < CRUST + 4) {
			block = roll < 60 ? Blocks.STONE : roll < 80 ? Blocks.ANDESITE : Blocks.TUFF;
		} else {
			block = roll < 55 ? Blocks.DEEPSLATE : roll < 80 ? Blocks.COBBLED_DEEPSLATE : Blocks.TUFF;
		}
		return block.defaultBlockState();
	}

	/** A broken wall on the world's face at column ({@code x}, {@code z}) from its middle: standing on the top, or hanging under it. */
	private static void ruin(ServerLevel level, BlockPos middle, int x, int z, boolean top, RandomSource random) {
		int up = top ? 1 : -1;
		boolean alongX = random.nextBoolean();
		int length = 3 + random.nextInt(RUIN_LENGTH - 2);
		for (int s = 0; s < length; s++) {
			int cx = middle.getX() + x + (alongX ? s : 0), cz = middle.getZ() + z + (alongX ? 0 : s);
			if (random.nextInt(5) == 0) {
				// a breach
				continue;
			}
			// the face under (or over) this column: the first block met coming in from outside, not past the hollow
			BlockPos ground = null;
			for (int d = RADIUS + (int)DRIFT_MAX + 3; d > HOLLOW + 2 && ground == null; d--) {
				BlockPos at = new BlockPos(cx, middle.getY() + up * d, cz);
				if (!level.getBlockState(at).isAir()) {
					ground = at;
				}
			}
			if (ground == null) {
				continue;
			}
			int height = 1 + random.nextInt(RUIN_HEIGHT);
			for (int k = 1; k <= height; k++) {
				BlockPos at = ground.above(up * k);
				if (!level.getBlockState(at).isAir()) {
					break;
				}
				int roll = random.nextInt(100);
				Block block = k == 2 && roll < 15 ? Blocks.IRON_BARS
					: roll < 45 ? Blocks.STONE_BRICKS : roll < 80 ? Blocks.CRACKED_STONE_BRICKS : roll < 90 ? Blocks.COBBLESTONE : Blocks.ANDESITE;
				level.setBlock(at, block.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
	}

	/** The rubble round the world, the torn piece's flung out on its side, and the trail the pull holds up under it. */
	private static void rubble(ServerLevel level, BlockPos middle, List<Wreck> wrecks, Vec3 tornWay, RandomSource random) {
		BlockPos pad = pad();
		for (int k = 0; k < RUBBLE + TORN_RUBBLE + TRAIL; k++) {
			Vec3 at;
			if (k < RUBBLE) {
				Vec3 way = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
				at = Vec3.atLowerCornerOf(middle).add(way.scale(RUBBLE_MIN + random.nextDouble() * (RUBBLE_MAX - RUBBLE_MIN)));
			} else if (k < RUBBLE + TORN_RUBBLE) {
				Vec3 jitter = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).scale(5.0);
				at = Vec3.atLowerCornerOf(middle).add(tornWay.scale(RADIUS + TORN_MIN + random.nextDouble() * (TORN_MAX - TORN_MIN))).add(jitter);
			} else {
				// thicker near the world, thinning toward the ground
				double fall = random.nextDouble();
				at = new Vec3(middle.getX() + (random.nextDouble() * 2.0 - 1.0) * TRAIL_SPREAD,
					middle.getY() - RADIUS - TRAIL_TOP - fall * fall * TRAIL_DROP, middle.getZ() + (random.nextDouble() * 2.0 - 1.0) * TRAIL_SPREAD);
			}
			BlockPos spot = BlockPos.containing(at);
			if (inColumn(spot, pad, CLEAR_OF_PAD) || wrecks.stream().anyMatch(wreck -> inColumn(spot, wreck.centre(), CLEAR_OF_WRECK))) {
				continue;
			}
			int size = 1 + random.nextInt(RUBBLE_SIZE);
			for (int dx = 0; dx < size; dx++) {
				for (int dy = 0; dy < size; dy++) {
					for (int dz = 0; dz < size; dz++) {
						BlockPos block = spot.offset(dx, dy, dz);
						if ((dx + dy + dz == 0 || random.nextInt(10) < 7) && level.getBlockState(block).isAir()) {
							level.setBlock(block, debris(random), Block.UPDATE_CLIENTS);
						}
					}
				}
			}
		}
	}

	/** Whether {@code spot} is within {@code clear} of {@code place} or anywhere above it (pods lift off and come down straight). */
	private static boolean inColumn(BlockPos spot, BlockPos place, int clear) {
		int dx = spot.getX() - place.getX(), dz = spot.getZ() - place.getZ();
		return dx * dx + dz * dz < clear * clear && spot.getY() >= place.getY() - clear;
	}

	/** A piece of what broke: the world's rock and soil, its buildings' stone, a ship's plating, a still-warm cinder. */
	private static BlockState debris(RandomSource random) {
		int roll = random.nextInt(100);
		Block block = roll < 18 ? Blocks.STONE : roll < 30 ? Blocks.COBBLESTONE : roll < 42 ? Blocks.DEEPSLATE : roll < 52 ? Blocks.COARSE_DIRT
			: roll < 62 ? Blocks.TUFF : roll < 72 ? Blocks.CRACKED_STONE_BRICKS : roll < 80 ? Blocks.STONE_BRICKS : roll < 88 ? Blocks.BLACKSTONE
			: roll < 95 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS : Blocks.MAGMA_BLOCK;
		return block.defaultBlockState();
	}

	// ---------------------------------------------------------------- the wrecks

	/**
	 * One wreck round its middle: a ship's broken hull (even decks) or a piece of the world's ground with a house's corner on it
	 * (odd), each with its stores, a lantern, a bent iron mast or frame, and a pod that came down with it holding its deck's set.
	 * Whatever the kind, the deck within about three blocks of the middle is solid and left clear above, for the landing, the
	 * rocks, the stores and the pod.
	 */
	private static void wreck(ServerLevel level, Wreck wreck, RandomSource random) {
		BlockPos centre = wreck.centre();
		BlockState stores;
		if (wreck.deck() % 2 == 0) {
			hulk(level, centre, random, random.nextBoolean());
			stores = Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP);
		} else {
			ground(level, centre, random, random.nextInt(4));
			stores = Blocks.CHEST.defaultBlockState();
		}
		BlockPos chest = centre.above();
		level.setBlock(chest, stores, Block.UPDATE_ALL);
		RandomizableContainer.setBlockEntityLootTable(level, random, chest, WRECK_LOOT);
		level.setBlock(centre.offset(2, 1, 1), Blocks.LANTERN.defaultBlockState(), Block.UPDATE_CLIENTS);
		for (int up = 1; up <= 3; up++) {
			level.setBlock(centre.offset(-2, up, -1), Blocks.IRON_BARS.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		PodWrecks.build(level, Vec3.atBottomCenterOf(centre.offset(-1, 1, 2)), wreck.deck(), random.nextFloat() * 360.0F);
	}

	/** Whether a deck cell is kept clear above (the landing, the rocks, the stores, the lantern, the mast and the pod stand there). */
	private static boolean kept(int dx, int dz) {
		return dx * dx + dz * dz <= 13;
	}

	/** A ship's broken hull: a plated deck long along one way, its bow and stern ragged, a keel under it, a broken bulwark and hanging chains. */
	private static void hulk(ServerLevel level, BlockPos centre, RandomSource random, boolean alongX) {
		for (int u = -6; u <= 6; u++) {
			for (int v = -4; v <= 4; v++) {
				double round = (u / 6.0) * (u / 6.0) + (v / 3.6) * (v / 3.6);
				if (round > 1.0 || (Math.abs(u) >= 5 && random.nextInt(3) == 0)) {
					continue;
				}
				int dx = alongX ? u : v, dz = alongX ? v : u;
				level.setBlock(centre.offset(dx, 0, dz), (random.nextInt(4) == 0 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS : Blocks.POLISHED_BLACKSTONE_BRICKS)
					.defaultBlockState(), Block.UPDATE_CLIENTS);
				if (Math.abs(v) <= 1) {
					level.setBlock(centre.offset(dx, -1, dz), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
				if (v == 0 && Math.abs(u) <= 4) {
					level.setBlock(centre.offset(dx, -2, dz), Blocks.BLACKSTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
				if (round > 0.62 && !kept(dx, dz)) {
					if (random.nextInt(5) > 0) {
						level.setBlock(centre.offset(dx, 1, dz), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
					if (Math.abs(v) > 1 && random.nextInt(4) == 0) {
						int hang = 1 + random.nextInt(3);
						for (int down = 1; down <= hang; down++) {
							level.setBlock(centre.offset(dx, -down, dz), Blocks.IRON_CHAIN.defaultBlockState(), Block.UPDATE_CLIENTS);
						}
					}
				}
			}
		}
	}

	/**
	 * A piece of the world's ground: a ragged slab of scorched soil and paving over an earth-and-rock root, with two walls of a
	 * house still standing at one corner ({@code turn} picks which), broken to uneven heights.
	 */
	private static void ground(ServerLevel level, BlockPos centre, RandomSource random, int turn) {
		for (int dx = -5; dx <= 5; dx++) {
			for (int dz = -5; dz <= 5; dz++) {
				int out = dx * dx + dz * dz;
				if (out > 34 || (Math.max(Math.abs(dx), Math.abs(dz)) == 5 && random.nextInt(2) == 0)) {
					continue;
				}
				int roll = random.nextInt(100);
				Block top = roll < 35 ? Blocks.COARSE_DIRT : roll < 50 ? Blocks.GRAVEL : roll < 75 ? Blocks.COBBLESTONE : roll < 85 ? Blocks.STONE_BRICKS
					: Blocks.ROOTED_DIRT;
				level.setBlock(centre.offset(dx, 0, dz), top.defaultBlockState(), Block.UPDATE_CLIENTS);
				int root = 1 + (int)((5.8 - Math.sqrt(out)) * 1.2) + random.nextInt(2);
				for (int down = 1; down <= root; down++) {
					Block under = down == 1 ? Blocks.DIRT : down < 4 ? (random.nextInt(3) == 0 ? Blocks.DIRT : Blocks.STONE) : Blocks.DEEPSLATE;
					level.setBlock(centre.offset(dx, -down, dz), under.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
		// the house's two walls: along the deck's edge four blocks out, on two sides that meet at a corner
		int[][] sides = {{0, -4}, {-4, 0}, {0, 4}, {4, 0}};
		for (int w = 0; w < 2; w++) {
			int[] side = sides[(turn + w) % sides.length];
			for (int s = -4; s <= 4; s++) {
				int dx = side[0] != 0 ? side[0] : s, dz = side[1] != 0 ? side[1] : s;
				int height = random.nextInt(7) == 0 ? 0 : 1 + random.nextInt(4);
				for (int up = 1; up <= height; up++) {
					int roll = random.nextInt(100);
					Block block = roll < 45 ? Blocks.STONE_BRICKS : roll < 80 ? Blocks.CRACKED_STONE_BRICKS : roll < 92 ? Blocks.COBBLESTONE : Blocks.ANDESITE;
					level.setBlock(centre.offset(dx, up, dz), block.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
	}

	private static int hash(int x, int y, int z) {
		int h = x * 374761393 + y * 668265263 + z * 1274126177 + (int)SEED;
		h = (h ^ (h >>> 13)) * 1103515245;
		return (h ^ (h >>> 16)) & 0x7FFFFFFF;
	}
}
