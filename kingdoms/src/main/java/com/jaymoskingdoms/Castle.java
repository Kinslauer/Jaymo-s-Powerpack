package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

/**
 * A kingdom's seat, built beside its village when it's founded, its gate facing the bell, in its village's own
 * materials ({@link Palette}). One of three:
 * <ul>
 * <li><b>The keep</b>: a walled keep with corner towers and battlements; a throne room (a carpet aisle in the
 * kingdom's colour, a dais, the throne, banners), an upper floor of arrow slits.</li>
 * <li><b>The castle</b>: a curtain wall with corner towers and a gatehouse (its portcullis raised) round a courtyard
 * (barracks, a training yard, a well), the keep at the back of it.</li>
 * <li><b>The tower</b>: a tall donjon, four storeys: the guard room, the throne room above it, the king's chamber,
 * and the battlements on the roof.</li>
 * <li><b>The citadel</b> (0.5.1, what a seat built from nothing is now): 61 blocks square, see {@link Citadel}.</li>
 * </ul>
 * Under every throne, behind a hidden trapdoor, a fortified panic room keeps the treasury (and maybe the king). Round
 * the town, watchtowers stand at the kingdom's edge. Each piece is built in its own frame (x across, z inward from
 * its gate, y from its floor) and turned to face the bell.
 */
final class Castle {
	enum Kind { KEEP, CASTLE, TOWER, CITADEL }

	/** Looks for a site this far from the bell; watchtowers this far out. */
	static final int SITE_MIN = 20, SITE_MAX = 48, TOWER_MIN = 56, TOWER_MAX = 88;
	static final int WATCHTOWERS = 3;
	/** The panic room: below the throne, this big (half-width) and deep. */
	static final int CELLAR_HALF = 3, CELLAR_DEPTH = 5;

	/** Where a built seat's king and guards belong; {@code posts}: where archers stand (walls, towers). */
	record Keep(BlockPos throne, BlockPos cellar, BlockPos gate, BlockPos hall, List<BlockPos> posts) {
		static final Codec<Keep> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.fieldOf("throne").forGetter(Keep::throne),
				BlockPos.CODEC.fieldOf("cellar").forGetter(Keep::cellar),
				BlockPos.CODEC.fieldOf("gate").forGetter(Keep::gate),
				BlockPos.CODEC.fieldOf("hall").forGetter(Keep::hall),
				BlockPos.CODEC.listOf().optionalFieldOf("posts", List.of()).forGetter(Keep::posts))
			.apply(i, Keep::new));
	}

	private Castle() {
	}

	/** A candidate: its middle, which way the gate faces, the floor height, and how bad a spot it is. */
	record Site(BlockPos center, Direction facing, int floor, double score) {
	}

	/** The flattest spot {@code min}-{@code max} from the bell, big enough, with the fewest of the village's buildings in the way. */
	static Site site(ServerLevel level, BlockPos bell, int halfWidth, int min, int max, double turnFrom) {
		// (0.5.1) only ground already loaded, if there's any: a big seat's far candidates would otherwise be generated to look at
		for (boolean onlyLoaded : new boolean[] {true, false}) {
			Site best = null;
			for (int r = min; r <= max; r += 6) {
				for (int i = 0; i < 16; i++) {
					double a = turnFrom + i / 16.0 * Math.PI * 2.0;
					BlockPos center = bell.offset((int)Math.round(Math.cos(a) * r), 0, (int)Math.round(Math.sin(a) * r));
					if (onlyLoaded && !level.hasChunksAt(center.getX() - halfWidth - 1, center.getZ() - halfWidth - 1, center.getX() + halfWidth + 1,
						center.getZ() + halfWidth + 1)) {
						continue;
					}
					Direction facing = Direction.getApproximateNearest(bell.getX() - center.getX(), 0, bell.getZ() - center.getZ());
					Site site = score(level, center, facing, halfWidth);
					if (best == null || site.score < best.score) {
						best = site;
					}
				}
			}
			if (best != null) {
				return best;
			}
		}
		throw new IllegalStateException("no candidates");
	}

	private static Site score(ServerLevel level, BlockPos center, Direction facing, int half) {
		List<Integer> heights = new ArrayList<>();
		int built = 0;
		int step = half > 10 ? 2 : 1;
		for (int dx = -half - 1; dx <= half + 1; dx += step) {
			for (int dz = -half - 1; dz <= half + 1; dz += step) {
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.offset(dx, 0, dz));
				heights.add(top.getY());
				if (!natural(level.getBlockState(top.below()))) {
					built++;
				}
			}
		}
		heights.sort(Integer::compare);
		int floor = heights.get(heights.size() / 2);
		double spread = heights.getLast() - heights.getFirst();
		boolean water = !level.getFluidState(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, center).below()).isEmpty();
		return new Site(center, facing, floor, spread + built * 4.0 * step * step + (water ? 500.0 : 0.0));
	}

	/** Ground the land made, not the village. */
	static boolean natural(BlockState state) {
		return state.is(BlockTags.SUBSTRATE_OVERWORLD) || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.DIRT_PATH)
			|| state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.GRAVEL) || state.is(BlockTags.TERRACOTTA)
			|| state.is(Blocks.SANDSTONE) || state.is(BlockTags.ICE) || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS);
	}

	/** Builds the kingdom's seat (a random kind) and its watchtowers, in the village's materials. */
	static Keep build(ServerLevel level, Faction kingdom, BlockPos bell, @org.jspecify.annotations.Nullable Kind forced) {
		RandomSource random = level.getRandom();
		// (0.5.1: a seat built from nothing is the citadel, unless an operator asks for one of the old kinds)
		Kind kind = forced != null ? forced : Kind.CITADEL;
		int half = switch (kind) {
			case KEEP -> 8;
			case CASTLE -> 17;
			case TOWER -> 6;
			case CITADEL -> Citadel.HALF + Citadel.TOWER_HALF + 1;
		};
		Site site = switch (kind) {
			case CITADEL -> site(level, bell, half, Citadel.SITE_MIN, Citadel.SITE_MAX, 0.0);
			case CASTLE -> site(level, bell, half, SITE_MIN + 10, SITE_MAX + 12, 0.0);
			default -> site(level, bell, half, SITE_MIN, SITE_MAX, 0.0);
		};
		Palette p = Palette.at(level, bell);
		Frame frame = new Frame(site.center(), site.facing(), site.floor(), switch (kind) {
			case KEEP -> KEEP_DEPTH / 2;
			case CASTLE -> CASTLE_DEPTH / 2;
			case TOWER -> TOWER_SIZE / 2;
			case CITADEL -> Citadel.DEPTH / 2;
		});
		Keep keep = switch (kind) {
			case KEEP -> {
				prepare(level, frame, p, -KEEP_HALF - 1, KEEP_HALF + 1, -1, KEEP_DEPTH + 1);
				yield keep(level, frame, p, kingdom, random, 0);
			}
			case CASTLE -> castle(level, frame, p, kingdom, random);
			case TOWER -> tower(level, frame, p, kingdom, random);
			case CITADEL -> Citadel.build(level, frame, p, kingdom, random);
		};
		// a way to the gate from the land in front of it (just outside the foundation)
		approach(level, frame, p, kind == Kind.KEEP ? -2 : -3);
		// watchtowers round the town's edge (the citadel has towers enough of its own)
		List<BlockPos> posts = new ArrayList<>(keep.posts());
		double turn = random.nextDouble() * Math.PI * 2.0;
		for (int i = 0; i < (kind == Kind.CITADEL ? 0 : WATCHTOWERS); i++) {
			Site spot = site(level, bell, 3, TOWER_MIN, TOWER_MAX, turn + i * Math.PI * 2.0 / WATCHTOWERS);
			posts.add(watchtower(level, new Frame(spot.center(), spot.facing(), spot.floor(), 2), p, random));
		}
		JaymosKingdoms.LOGGER.info("{} builds a {} at {}", kingdom.name(), kind.name().toLowerCase(java.util.Locale.ROOT), site.center());
		return new Keep(keep.throne(), keep.cellar(), keep.gate(), keep.hall(), List.copyOf(posts));
	}

	/** The longest approach: steps down to (or a cutting through) the land in front of the gate. */
	static final int APPROACH_MAX = 24;

	/**
	 * A seat on a slope stands above the land in front of its gate, or is dug into it: three-wide steps from {@code z0}
	 * outward (frame z falling) down to the ground, or a path cut through the rise, until the land meets the gate's level.
	 */
	private static void approach(ServerLevel level, Frame f, Palette p, int z0) {
		BlockState stairs = f.turn(p.stairs().setValue(StairBlock.FACING, Direction.SOUTH));
		for (int i = 0; i < APPROACH_MAX; i++) {
			int z = z0 - i;
			// the land's height here (the first air above it), relative to the floor
			int ground = Integer.MIN_VALUE;
			for (int x = -1; x <= 1; x++) {
				ground = Math.max(ground, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, f.at(x, 0, z)).getY() - f.floor());
			}
			int step = -1 - i;
			if (ground > 0) {
				// dug in: cut the path through at the floor's level
				for (int x = -1; x <= 1; x++) {
					f.set(level, x, -1, z, p.path());
					for (int y = 0; y < ground && y <= 4; y++) {
						f.set(level, x, y, z, Blocks.AIR.defaultBlockState());
					}
				}
				continue;
			}
			if (ground >= step + 1) {
				// the land has come up to meet the steps
				return;
			}
			for (int x = -1; x <= 1; x++) {
				f.set(level, x, step, z, stairs);
				for (int y = step - 1; y >= ground - 1; y--) {
					f.set(level, x, y, z, p.wall());
				}
				for (int y = step + 1; y <= step + 3; y++) {
					f.set(level, x, y, z, Blocks.AIR.defaultBlockState());
				}
			}
		}
	}

	/** The ground made ready in {@code x0..x1, z0..z1}: a foundation under it, the air cleared above. */
	private static void prepare(ServerLevel level, Frame f, Palette p, int x0, int x1, int z0, int z1) {
		prepare(level, f, p, x0, x1, z0, z1, 18);
	}

	/** (clearing {@code high} above the floor) */
	static void prepare(ServerLevel level, Frame f, Palette p, int x0, int x1, int z0, int z1, int high) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				for (int y = -CELLAR_DEPTH - 2; y < 0; y++) {
					if (!level.getBlockState(f.at(x, y, z)).isSolid() || y == -1) {
						f.set(level, x, y, z, y == -1 ? p.wall() : Blocks.STONE.defaultBlockState());
					}
				}
				for (int y = 0; y <= high; y++) {
					f.set(level, x, y, z, Blocks.AIR.defaultBlockState());
				}
			}
		}
	}

	/** A weathered wall block. */
	static BlockState weathered(Palette p, RandomSource random) {
		float roll = random.nextFloat();
		return roll < 0.15F ? p.mossy() : roll < 0.28F ? p.cracked() : p.wall();
	}

	// ---- the keep (also the castle's inner keep) ----------------------------------------------------------------

	static final int KEEP_HALF = 7, KEEP_DEPTH = 14, KEEP_WALL = 10, KEEP_UPPER = 5;

	/** The keep, its gate at frame z = {@code z0}. */
	private static Keep keep(ServerLevel level, Frame f, Palette p, Faction kingdom, RandomSource random, int z0) {
		int h = KEEP_HALF, d = KEEP_DEPTH;
		for (int x = -h; x <= h; x++) {
			for (int z = z0; z <= z0 + d; z++) {
				f.set(level, x, -1, z, p.floor());
				if (Math.abs(x) < h && z > z0 && z < z0 + d) {
					f.set(level, x, KEEP_UPPER, z, p.planks());
				}
				boolean edge = Math.abs(x) == h || z == z0 || z == z0 + d;
				if (edge) {
					for (int y = 0; y <= KEEP_WALL; y++) {
						f.set(level, x, y, z, weathered(p, random));
					}
					if ((x + z) % 2 == 0) {
						f.set(level, x, KEEP_WALL + 1, z, p.wall());
					}
				}
			}
		}
		for (int cx : new int[] {-h, h}) {
			for (int cz : new int[] {z0, z0 + d}) {
				column(level, f, p.trim(), cx, cz, KEEP_WALL + 3, true);
			}
		}
		gate(level, f, p, z0, 3);
		for (int z = z0 + 3; z < z0 + d; z += 3) {
			f.set(level, -h, KEEP_UPPER + 2, z, Blocks.IRON_BARS.defaultBlockState());
			f.set(level, h, KEEP_UPPER + 2, z, Blocks.IRON_BARS.defaultBlockState());
		}
		for (int y = 0; y <= KEEP_UPPER; y++) {
			f.set(level, h - 1, y, z0 + 1, f.turn(Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST)));
		}
		torches(level, f, -h + 1, h - 1, z0 + 2, z0 + d - 1, 2);
		f.set(level, 0, KEEP_UPPER - 1, z0 + d / 2, Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
		BlockPos throne = throneRoom(level, f, p, kingdom, -3, 3, z0 + 1, z0 + d - 1, 0);
		BlockPos cellar = panicRoom(level, f, p, random, 2, z0 + d - 1, 0, z0 + d - 4);
		List<BlockPos> posts = List.of(f.at(-h, KEEP_WALL + 1, z0 + d / 2), f.at(h, KEEP_WALL + 1, z0 + d / 2));
		return new Keep(throne, cellar, f.at(0, 0, z0 - 2), f.at(0, 0, z0 + d / 2), posts);
	}

	/** A solid 3 x 3 column (a corner tower) from the ground to {@code top}, crenellated. */
	static void column(ServerLevel level, Frame f, BlockState block, int cx, int cz, int top, boolean battlements) {
		for (int y = 0; y <= top; y++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					boolean notch = battlements && y == top && (dx + dz) % 2 != 0;
					f.set(level, cx + dx, y, cz + dz, notch ? Blocks.AIR.defaultBlockState() : block);
				}
			}
		}
	}

	/** An arched gate in the wall at frame z = {@code z}, three wide and {@code high} high. */
	private static void gate(ServerLevel level, Frame f, Palette p, int z, int high) {
		for (int x = -1; x <= 1; x++) {
			for (int y = 0; y <= high; y++) {
				f.set(level, x, y, z, Blocks.AIR.defaultBlockState());
			}
		}
		f.set(level, -2, high + 1, z, p.trim());
		f.set(level, 2, high + 1, z, p.trim());
	}

	static void torches(ServerLevel level, Frame f, int x0, int x1, int z0, int z1, int y) {
		for (int z = z0; z <= z1; z += 4) {
			f.set(level, x0, y, z, f.turn(Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.EAST)));
			f.set(level, x1, y, z, f.turn(Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.WEST)));
		}
	}

	/**
	 * A throne room on the floor at frame y = {@code y}: the carpet aisle from {@code zFront} to the dais, the dais
	 * (x {@code x0..x1}) at the back ({@code zBack}), the throne, the banners. Returns where the king stands.
	 */
	static BlockPos throneRoom(ServerLevel level, Frame f, Palette p, Faction kingdom, int x0, int x1, int zFront, int zBack, int y) {
		BlockState carpet = byName(kingdom.color().getName() + "_carpet", Blocks.AIR);
		for (int z = zFront; z <= zBack - 3; z++) {
			f.set(level, 0, y, z, carpet);
		}
		for (int x = x0; x <= x1; x++) {
			for (int z = zBack - 2; z <= zBack; z++) {
				f.set(level, x, y, z, p.trim());
			}
			// steps up onto the dais, rising away from the gate
			f.set(level, x, y, zBack - 3, f.turn(p.stairs().setValue(StairBlock.FACING, Direction.SOUTH)));
		}
		f.set(level, 0, y + 1, zBack - 1, f.turn(Blocks.QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH)));
		f.set(level, -1, y + 1, zBack - 1, Blocks.GOLD_BLOCK.defaultBlockState());
		f.set(level, 1, y + 1, zBack - 1, Blocks.GOLD_BLOCK.defaultBlockState());
		f.set(level, 0, y + 1, zBack, Blocks.GOLD_BLOCK.defaultBlockState());
		f.set(level, 0, y + 2, zBack, Blocks.GOLD_BLOCK.defaultBlockState());
		BlockState banner = byName(kingdom.color().getName() + "_wall_banner", Blocks.AIR);
		if (!banner.isAir()) {
			for (int x : new int[] {x0, x0 + 1, x1 - 1, x1}) {
				f.set(level, x, y + 3, zBack, f.turn(banner.setValue(net.minecraft.world.level.block.WallBannerBlock.FACING, Direction.NORTH)));
			}
		}
		return f.at(0, y + 1, zBack - 2);
	}

	/**
	 * The panic room under a throne: a fortified cellar centred at frame z {@code cz} below floor y {@code y}, reached
	 * down a ladder at ({@code lx}, {@code lz}) under a trapdoor flush with the floor (y). Returns where one stands in it.
	 */
	static BlockPos panicRoom(ServerLevel level, Frame f, Palette p, RandomSource random, int lx, int lz, int y, int cz) {
		for (int x = -CELLAR_HALF - 1; x <= CELLAR_HALF + 1; x++) {
			for (int z = cz - CELLAR_HALF - 1; z <= cz + CELLAR_HALF + 1; z++) {
				for (int dy = -CELLAR_DEPTH - 1; dy <= -2; dy++) {
					boolean shell = Math.abs(x) == CELLAR_HALF + 1 || Math.abs(z - cz) == CELLAR_HALF + 1 || dy == -CELLAR_DEPTH - 1 || dy == -2;
					f.set(level, x, y + dy, z, shell ? p.fortified() : Blocks.AIR.defaultBlockState());
				}
			}
		}
		// the ladder runs down the back wall of the cellar; the trapdoor sits in the floor above it
		for (int dy = -CELLAR_DEPTH; dy <= -1; dy++) {
			f.set(level, lx, y + dy, lz, f.turn(Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH)));
		}
		f.set(level, lx, y, lz, f.turn(p.trapdoor().setValue(TrapDoorBlock.HALF, Half.TOP)));
		f.set(level, -2, y - CELLAR_DEPTH, cz - 2, Blocks.LANTERN.defaultBlockState());
		BlockPos chest = f.at(-CELLAR_HALF, y - CELLAR_DEPTH, cz + CELLAR_HALF);
		level.setBlock(chest, f.turn(Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH)), Block.UPDATE_ALL);
		if (level.getBlockEntity(chest) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity box) {
			box.setLootTable(BuiltInLootTables.VILLAGE_WEAPONSMITH, random.nextLong());
		}
		return f.at(0, y - CELLAR_DEPTH, cz);
	}

	// ---- the castle -----------------------------------------------------------------------------------------------

	static final int CASTLE_HALF = 15, CASTLE_DEPTH = 32, CURTAIN = 8;

	private static Keep castle(ServerLevel level, Frame f, Palette p, Faction kingdom, RandomSource random) {
		int h = CASTLE_HALF, d = CASTLE_DEPTH;
		prepare(level, f, p, -h - 2, h + 2, -2, d + 2);
		// the curtain wall, with a walk along its top
		for (int x = -h; x <= h; x++) {
			for (int z = 0; z <= d; z++) {
				boolean edge = Math.abs(x) == h || z == 0 || z == d;
				if (!edge) {
					continue;
				}
				for (int y = 0; y <= CURTAIN; y++) {
					f.set(level, x, y, z, weathered(p, random));
				}
				if ((x + z) % 2 == 0) {
					f.set(level, x, CURTAIN + 1, z, p.wall());
				}
			}
		}
		for (int x = -h + 1; x <= h - 1; x++) {
			f.set(level, x, CURTAIN, 1, p.slab().setValue(SlabBlock.TYPE, SlabType.TOP));
			f.set(level, x, CURTAIN, d - 1, p.slab().setValue(SlabBlock.TYPE, SlabType.TOP));
		}
		for (int z = 1; z <= d - 1; z++) {
			f.set(level, -h + 1, CURTAIN, z, p.slab().setValue(SlabBlock.TYPE, SlabType.TOP));
			f.set(level, h - 1, CURTAIN, z, p.slab().setValue(SlabBlock.TYPE, SlabType.TOP));
		}
		// the corner towers, and the gatehouse
		for (int cx : new int[] {-h, h}) {
			for (int cz : new int[] {0, d}) {
				column(level, f, p.trim(), cx, cz, CURTAIN + 4, true);
			}
		}
		column(level, f, p.trim(), -3, 0, CURTAIN + 3, true);
		column(level, f, p.trim(), 3, 0, CURTAIN + 3, true);
		gate(level, f, p, 0, 4);
		// the portcullis, raised (its teeth showing under the arch)
		for (int x = -1; x <= 1; x++) {
			f.set(level, x, 4, 0, Blocks.IRON_BARS.defaultBlockState());
		}
		// ladders up to the wall walk, inside each front corner
		for (int y = 0; y < CURTAIN; y++) {
			f.set(level, -h + 2, y, 1, f.turn(Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH)));
			f.set(level, h - 2, y, 1, f.turn(Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH)));
		}
		// the courtyard: a road from the gate to the keep, the barracks, the training yard, the well
		for (int z = 1; z < d - KEEP_DEPTH - 1; z++) {
			for (int x = -1; x <= 1; x++) {
				f.set(level, x, -1, z, p.path());
			}
		}
		barracks(level, f, p, -h + 1, -h + 5, 4, 14);
		// training dummies: hay on posts, targets
		for (int z = 5; z <= 11; z += 3) {
			f.set(level, h - 4, 0, z, p.fence());
			f.set(level, h - 4, 1, z, Blocks.HAY_BLOCK.defaultBlockState());
			f.set(level, h - 4, 2, z, Blocks.CARVED_PUMPKIN.defaultBlockState());
		}
		f.set(level, h - 1, 1, 8, Blocks.TARGET.defaultBlockState());
		f.set(level, h - 1, 1, 12, Blocks.TARGET.defaultBlockState());
		// the well
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				f.set(level, -6 + dx, -1, 9 + dz, p.wall());
				f.set(level, -6 + dx, 0, 9 + dz, dx == 0 && dz == 0 ? Blocks.WATER.defaultBlockState() : p.wall());
			}
		}
		f.set(level, -6, -1, 9, Blocks.WATER.defaultBlockState());
		// the keep, at the back of the courtyard
		Keep inner = keep(level, f, p, kingdom, random, d - KEEP_DEPTH - 1);
		torches(level, f, -h + 1, h - 1, 2, d - KEEP_DEPTH - 3, 3);
		List<BlockPos> posts = new ArrayList<>(inner.posts());
		posts.add(f.at(-h, CURTAIN + 5, 0));
		posts.add(f.at(h, CURTAIN + 5, 0));
		posts.add(f.at(-3, CURTAIN + 4, 0));
		posts.add(f.at(3, CURTAIN + 4, 0));
		posts.add(f.at(-h + 1, CURTAIN + 1, d / 2));
		posts.add(f.at(h - 1, CURTAIN + 1, d / 2));
		return new Keep(inner.throne(), inner.cellar(), f.at(0, 0, -2), f.at(0, 0, 9), List.copyOf(posts));
	}

	/** A lean-to of bunks along the wall: x {@code x0..x1}, z {@code z0..z1}. */
	static void barracks(ServerLevel level, Frame f, Palette p, int x0, int x1, int z0, int z1) {
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				f.set(level, x, 4, z, p.woodSlab());
			}
			f.set(level, x1, 0, z, z % 3 == 0 ? p.log() : Blocks.AIR.defaultBlockState());
			f.set(level, x1, 3, z, z % 3 == 0 ? p.log() : Blocks.AIR.defaultBlockState());
		}
		for (int z = z0 + 1; z <= z1 - 1; z += 3) {
			// a bed, its head to the wall
			f.set(level, x0, 0, z, f.turn(byName("red_bed", Blocks.AIR).setValue(BedBlock.FACING, Direction.WEST).setValue(BedBlock.PART, BedPart.HEAD)));
			f.set(level, x0 + 1, 0, z, f.turn(byName("red_bed", Blocks.AIR).setValue(BedBlock.FACING, Direction.WEST).setValue(BedBlock.PART, BedPart.FOOT)));
		}
		f.set(level, x0, 0, z1, Blocks.BARREL.defaultBlockState());
	}

	// ---- the tower ------------------------------------------------------------------------------------------------

	static final int TOWER_SIZE = 12, STOREY = 6, STOREYS = 4;

	private static Keep tower(ServerLevel level, Frame f, Palette p, Faction kingdom, RandomSource random) {
		int h = TOWER_SIZE / 2, d = TOWER_SIZE, top = STOREY * STOREYS;
		prepare(level, f, p, -h - 2, h + 2, -2, d + 2);
		for (int y = 0; y <= top + 2; y++) {
			for (int x = -h; x <= h; x++) {
				for (int z = 0; z <= d; z++) {
					boolean edge = Math.abs(x) == h || z == 0 || z == d;
					// chamfered at the corners
					boolean corner = Math.abs(x) == h && (z == 0 || z == d);
					if (edge && !corner && y <= top) {
						f.set(level, x, y, z, weathered(p, random));
					} else if (edge && !corner && y == top + 1 && (x + z) % 2 == 0) {
						f.set(level, x, y, z, p.wall());
					}
				}
			}
		}
		for (int s = 1; s <= STOREYS; s++) {
			int y = s * STOREY - 1;
			for (int x = -h + 1; x <= h - 1; x++) {
				for (int z = 1; z <= d - 1; z++) {
					f.set(level, x, y, z, s == STOREYS ? p.wall() : p.planks());
				}
			}
			if (s < STOREYS) {
				for (int x : new int[] {-2, 2}) {
					f.set(level, x, y + 2, 0, Blocks.GLASS_PANE.defaultBlockState());
					f.set(level, x, y + 3, 0, Blocks.GLASS_PANE.defaultBlockState());
				}
				for (int z : new int[] {4, 8}) {
					f.set(level, -h, y + 2, z, Blocks.GLASS_PANE.defaultBlockState());
					f.set(level, h, y + 2, z, Blocks.GLASS_PANE.defaultBlockState());
				}
			}
			f.set(level, 0, y - 1, d / 2, Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
		}
		for (int x = -h + 1; x <= h - 1; x++) {
			for (int z = 1; z <= d - 1; z++) {
				f.set(level, x, -1, z, p.floor());
			}
		}
		gate(level, f, p, 0, 3);
		// one ladder all the way up, in a back corner
		for (int y = 0; y <= top; y++) {
			f.set(level, h - 1, y, d - 1, f.turn(Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH)));
		}
		torches(level, f, -h + 1, h - 1, 2, d - 2, 2);
		// the throne room is the second storey
		BlockPos throne = throneRoom(level, f, p, kingdom, -3, 2, 2, d - 1, STOREY);
		// the king's chamber on the third: a bed and shelves
		int chamber = STOREY * 2;
		f.set(level, -2, chamber, d - 1, f.turn(byName("purple_bed", Blocks.AIR).setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD)));
		f.set(level, -2, chamber, d - 2, f.turn(byName("purple_bed", Blocks.AIR).setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT)));
		f.set(level, -4, chamber, d - 1, Blocks.BOOKSHELF.defaultBlockState());
		f.set(level, -4, chamber + 1, d - 1, Blocks.BOOKSHELF.defaultBlockState());
		BlockPos cellar = panicRoom(level, f, p, random, -2, d / 2 + CELLAR_HALF, 0, d / 2);
		List<BlockPos> posts = List.of(f.at(-3, top + 1, 2), f.at(3, top + 1, d - 2), f.at(-3, top + 1, d - 2));
		return new Keep(throne, cellar, f.at(0, 0, -2), f.at(0, 0, d / 2), posts);
	}

	// ---- watchtowers -----------------------------------------------------------------------------------------------

	/** A 5 x 5 watchtower with a platform up top: returns where its archer stands. */
	private static BlockPos watchtower(ServerLevel level, Frame f, Palette p, RandomSource random) {
		int high = 12;
		prepare(level, f, p, -3, 3, -1, 5);
		for (int y = 0; y <= high; y++) {
			for (int x = -2; x <= 2; x++) {
				for (int z = 0; z <= 4; z++) {
					boolean edge = Math.abs(x) == 2 || z == 0 || z == 4;
					boolean post = Math.abs(x) == 2 && (z == 0 || z == 4);
					f.set(level, x, y, z, post ? p.log() : edge ? weathered(p, random) : Blocks.AIR.defaultBlockState());
				}
			}
		}
		f.set(level, 0, 0, 0, Blocks.AIR.defaultBlockState());
		f.set(level, 0, 1, 0, Blocks.AIR.defaultBlockState());
		for (int y = 0; y <= high; y++) {
			f.set(level, 0, y, 3, f.turn(Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH)));
		}
		// the platform, wider than the tower, with a parapet
		for (int x = -3; x <= 3; x++) {
			for (int z = -1; z <= 5; z++) {
				boolean rim = Math.abs(x) == 3 || z == -1 || z == 5;
				if (!(x == 0 && z == 3)) {
					f.set(level, x, high + 1, z, p.planks());
				}
				if (rim && (x + z) % 2 == 0) {
					f.set(level, x, high + 2, z, p.wall());
				}
			}
		}
		f.set(level, 0, high + 1, 3, f.turn(p.trapdoor().setValue(TrapDoorBlock.HALF, Half.TOP)));
		f.set(level, 1, high + 2, 1, Blocks.LANTERN.defaultBlockState());
		return f.at(0, high + 2, 1);
	}

	static BlockState byName(String name, Block fallback) {
		Block block = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(name));
		return (block == Blocks.AIR ? fallback : block).defaultBlockState();
	}

	/** A piece's own frame: x across, z inward from its gate, y from its floor; turned to face the bell. */
	record Frame(BlockPos center, Direction facing, int floor, int middle) {
		/** The gate is at z = 0, facing {@code facing}; the piece runs back from it (its middle at z = {@code middle}). */
		BlockPos at(int x, int y, int z) {
			Direction back = this.facing.getOpposite();
			Direction right = this.facing.getClockWise();
			int lz = z - this.middle;
			return new BlockPos(this.center.getX() + back.getStepX() * lz + right.getStepX() * x, this.floor + y,
				this.center.getZ() + back.getStepZ() * lz + right.getStepZ() * x);
		}

		/** A block's facing, given in the frame where the gate faces north, turned to this piece. */
		BlockState turn(BlockState state) {
			Rotation rotation = switch (this.facing) {
				case EAST -> Rotation.CLOCKWISE_90;
				case SOUTH -> Rotation.CLOCKWISE_180;
				case WEST -> Rotation.COUNTERCLOCKWISE_90;
				default -> Rotation.NONE;
			};
			return state.rotate(rotation);
		}

		void set(ServerLevel level, int x, int y, int z, BlockState state) {
			level.setBlock(this.at(x, y, z), state, Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
		}
	}
}
