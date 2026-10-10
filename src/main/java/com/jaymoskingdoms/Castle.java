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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

/**
 * A kingdom's keep, built beside its village when it's founded: a walled stone keep with corner towers and
 * battlements, its gate facing the bell. Inside, the throne room: a carpet aisle in the kingdom's colour up to a dais,
 * the throne, banners behind it, guards along the walls. Above, a floor of arrow slits. Below the throne, behind a
 * hidden trapdoor, a fortified panic room where the treasury is kept (and where the king may be hiding).
 *
 * <p>Built in its own frame: x across (-7..7), z from the gate (z = 0, toward the bell) to the back wall (z = 14),
 * turned to face the bell. In a desert it's built of sandstone.
 */
final class Castle {
	/** Half the keep's width; its depth; wall height; where the upper floor is. */
	static final int HALF = 7, DEPTH = 14, WALL = 10, UPPER = 5;
	/** Looks for a site this far from the bell. */
	static final int SITE_MIN = 18, SITE_MAX = 40;
	/** The panic room: below the floor, this big (half-width) and deep. */
	static final int CELLAR_HALF = 3, CELLAR_DEPTH = 5;

	/** Where a built keep's king and guards belong. */
	record Keep(BlockPos throne, BlockPos cellar, BlockPos gate, BlockPos hall) {
		static final Codec<Keep> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.fieldOf("throne").forGetter(Keep::throne),
				BlockPos.CODEC.fieldOf("cellar").forGetter(Keep::cellar),
				BlockPos.CODEC.fieldOf("gate").forGetter(Keep::gate),
				BlockPos.CODEC.fieldOf("hall").forGetter(Keep::hall))
			.apply(i, Keep::new));
	}

	/** The keep's materials. */
	private record Palette(BlockState wall, BlockState mossy, BlockState cracked, BlockState floor, BlockState trim, BlockState stairs,
		BlockState slab, BlockState fortified) {
	}

	private static final Palette STONE = new Palette(Blocks.STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),
		Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), Blocks.POLISHED_ANDESITE.defaultBlockState(), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(),
		Blocks.STONE_BRICK_STAIRS.defaultBlockState(), Blocks.STONE_BRICK_SLAB.defaultBlockState(), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
	private static final Palette SAND = new Palette(Blocks.CUT_SANDSTONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(),
		Blocks.SMOOTH_SANDSTONE.defaultBlockState(), Blocks.SMOOTH_SANDSTONE.defaultBlockState(), Blocks.CHISELED_SANDSTONE.defaultBlockState(),
		Blocks.SANDSTONE_STAIRS.defaultBlockState(), Blocks.SANDSTONE_SLAB.defaultBlockState(), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());

	private Castle() {
	}

	/** Where to build: the flattest spot round the bell with the fewest of the village's buildings in the way. */
	static Site site(ServerLevel level, BlockPos bell) {
		Site best = null;
		for (int r = SITE_MIN; r <= SITE_MAX; r += 6) {
			for (int i = 0; i < 16; i++) {
				double a = i / 16.0 * Math.PI * 2.0;
				BlockPos center = bell.offset((int)Math.round(Math.cos(a) * r), 0, (int)Math.round(Math.sin(a) * r));
				// the gate faces the bell
				Direction facing = Direction.getApproximateNearest(bell.getX() - center.getX(), 0, bell.getZ() - center.getZ());
				Site site = score(level, center, facing);
				if (best == null || site.score < best.score) {
					best = site;
				}
			}
		}
		return best;
	}

	/** A candidate: its middle, which way the gate faces, the floor height, and how bad a spot it is. */
	record Site(BlockPos center, Direction facing, int floor, double score) {
	}

	private static Site score(ServerLevel level, BlockPos center, Direction facing) {
		List<Integer> heights = new ArrayList<>();
		int built = 0;
		for (int dx = -HALF - 1; dx <= HALF + 1; dx++) {
			for (int dz = -HALF - 1; dz <= HALF + 1; dz++) {
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.offset(dx, 0, dz));
				heights.add(top.getY());
				BlockState under = level.getBlockState(top.below());
				if (!natural(under)) {
					built++;
				}
			}
		}
		heights.sort(Integer::compare);
		int floor = heights.get(heights.size() / 2);
		double spread = heights.getLast() - heights.getFirst();
		boolean water = !level.getFluidState(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, center).below()).isEmpty();
		return new Site(center, facing, floor, spread + built * 4.0 + (water ? 500.0 : 0.0));
	}

	/** Ground the land made, not the village. */
	private static boolean natural(BlockState state) {
		return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.DIRT_PATH)
			|| state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.GRAVEL) || state.is(BlockTags.TERRACOTTA)
			|| state.is(Blocks.SANDSTONE) || state.is(BlockTags.ICE) || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS);
	}

	/** Builds the keep for {@code kingdom} at {@code site}. */
	static Keep build(ServerLevel level, Kingdom kingdom, Site site) {
		RandomSource random = level.getRandom();
		var biome = level.getBiome(site.center());
		boolean desert = biome.is(net.minecraft.tags.BiomeTags.HAS_DESERT_PYRAMID) || biome.is(net.minecraft.tags.BiomeTags.IS_BADLANDS);
		Palette p = desert ? SAND : STONE;
		Frame f = new Frame(site.center(), site.facing(), site.floor());
		// the ground made ready: a foundation under it all, the air cleared above
		for (int x = -HALF - 1; x <= HALF + 1; x++) {
			for (int z = -1; z <= DEPTH + 1; z++) {
				for (int y = -CELLAR_DEPTH - 2; y < 0; y++) {
					BlockPos at = f.at(x, y, z);
					if (!level.getBlockState(at).isSolid() || y >= -1) {
						f.set(level, x, y, z, y == -1 ? p.wall() : Blocks.STONE.defaultBlockState());
					}
				}
				for (int y = 0; y <= WALL + 4; y++) {
					f.set(level, x, y, z, Blocks.AIR.defaultBlockState());
				}
			}
		}
		// the floors
		for (int x = -HALF; x <= HALF; x++) {
			for (int z = 0; z <= DEPTH; z++) {
				f.set(level, x, -1, z, p.floor());
				if (Math.abs(x) < HALF && z > 0 && z < DEPTH) {
					f.set(level, x, UPPER, z, p.wall());
				}
			}
		}
		// the walls, weathered, with battlements
		for (int x = -HALF; x <= HALF; x++) {
			for (int z = 0; z <= DEPTH; z++) {
				boolean edge = Math.abs(x) == HALF || z == 0 || z == DEPTH;
				if (!edge) {
					continue;
				}
				for (int y = 0; y <= WALL; y++) {
					float roll = random.nextFloat();
					f.set(level, x, y, z, roll < 0.15F ? p.mossy() : roll < 0.28F ? p.cracked() : p.wall());
				}
				if ((x + z) % 2 == 0) {
					f.set(level, x, WALL + 1, z, p.wall());
				}
			}
		}
		// corner towers, a little taller
		for (int cx : new int[] {-HALF, HALF}) {
			for (int cz : new int[] {0, DEPTH}) {
				for (int y = 0; y <= WALL + 3; y++) {
					for (int dx = -1; dx <= 1; dx++) {
						for (int dz = -1; dz <= 1; dz++) {
							f.set(level, cx + dx, y, cz + dz, y == WALL + 3 && (dx + dz) % 2 != 0 ? Blocks.AIR.defaultBlockState() : p.trim());
						}
					}
				}
			}
		}
		// the gate: an arch three wide and four high, toward the bell
		for (int x = -1; x <= 1; x++) {
			for (int y = 0; y <= 3; y++) {
				f.set(level, x, y, 0, Blocks.AIR.defaultBlockState());
			}
		}
		f.set(level, -2, 4, 0, p.trim());
		f.set(level, 2, 4, 0, p.trim());
		// arrow slits on the upper floor, and on the lower beside the gate
		for (int z = 3; z < DEPTH; z += 3) {
			f.set(level, -HALF, UPPER + 2, z, Blocks.IRON_BARS.defaultBlockState());
			f.set(level, HALF, UPPER + 2, z, Blocks.IRON_BARS.defaultBlockState());
		}
		for (int x = -4; x <= 4; x += 8) {
			f.set(level, x, 2, 0, Blocks.IRON_BARS.defaultBlockState());
			f.set(level, x, UPPER + 2, 0, Blocks.IRON_BARS.defaultBlockState());
			f.set(level, x, UPPER + 2, DEPTH, Blocks.IRON_BARS.defaultBlockState());
		}
		// a ladder up to the upper floor, in the front corner
		for (int y = 0; y <= UPPER; y++) {
			f.set(level, HALF - 1, y, 1, f.turn(Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST)));
		}
		f.set(level, HALF - 1, UPPER, 1, f.turn(Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST)));
		// the throne room: the carpet aisle, the dais, the throne, the banners
		BlockState carpet = byName(kingdom.color().getName() + "_carpet", Blocks.AIR);
		for (int z = 1; z <= DEPTH - 4; z++) {
			f.set(level, 0, 0, z, carpet);
		}
		for (int x = -3; x <= 3; x++) {
			for (int z = DEPTH - 3; z <= DEPTH - 1; z++) {
				f.set(level, x, 0, z, p.trim());
			}
		}
		for (int x = -3; x <= 3; x++) {
			// steps up onto the dais, rising away from the gate
			f.set(level, x, 0, DEPTH - 4, f.turn(p.stairs().setValue(StairBlock.FACING, Direction.SOUTH)));
		}
		// the throne itself: a seat facing the gate, gold arms
		f.set(level, 0, 1, DEPTH - 2, f.turn(Blocks.QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH)));
		f.set(level, -1, 1, DEPTH - 2, Blocks.GOLD_BLOCK.defaultBlockState());
		f.set(level, 1, 1, DEPTH - 2, Blocks.GOLD_BLOCK.defaultBlockState());
		f.set(level, 0, 1, DEPTH - 1, Blocks.GOLD_BLOCK.defaultBlockState());
		f.set(level, 0, 2, DEPTH - 1, Blocks.GOLD_BLOCK.defaultBlockState());
		BlockState banner = byName(kingdom.color().getName() + "_wall_banner", Blocks.AIR);
		if (!banner.isAir()) {
			for (int x : new int[] {-3, -2, 2, 3}) {
				f.set(level, x, 3, DEPTH - 1, f.turn(banner.setValue(net.minecraft.world.level.block.WallBannerBlock.FACING, Direction.NORTH)));
			}
		}
		// light along the walls
		for (int z = 2; z < DEPTH; z += 4) {
			f.set(level, -HALF + 1, 2, z, f.turn(Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.EAST)));
			f.set(level, HALF - 1, 2, z, f.turn(Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.WEST)));
		}
		f.set(level, 0, UPPER - 1, DEPTH / 2, Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
		// the panic room, below the dais, behind a trapdoor beside the throne
		int cz = DEPTH - 4;
		for (int x = -CELLAR_HALF - 1; x <= CELLAR_HALF + 1; x++) {
			for (int z = cz - CELLAR_HALF - 1; z <= cz + CELLAR_HALF + 1; z++) {
				for (int y = -CELLAR_DEPTH - 1; y <= -2; y++) {
					boolean shell = Math.abs(x) == CELLAR_HALF + 1 || Math.abs(z - cz) == CELLAR_HALF + 1 || y == -CELLAR_DEPTH - 1 || y == -2;
					f.set(level, x, y, z, shell ? p.fortified() : Blocks.AIR.defaultBlockState());
				}
			}
		}
		// down through the dais beside the throne: a trapdoor flush with it, a ladder on the back wall
		for (int y = -CELLAR_DEPTH; y <= -1; y++) {
			f.set(level, 2, y, DEPTH - 1, f.turn(Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH)));
		}
		f.set(level, 2, 0, DEPTH - 1, f.turn(Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.TOP)));
		f.set(level, -2, -CELLAR_DEPTH, cz - 2, Blocks.LANTERN.defaultBlockState());
		// the treasury chest
		BlockPos chest = f.at(-CELLAR_HALF, -CELLAR_DEPTH, cz + CELLAR_HALF);
		level.setBlock(chest, f.turn(Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH)), Block.UPDATE_ALL);
		if (level.getBlockEntity(chest) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity box) {
			box.setLootTable(BuiltInLootTables.VILLAGE_WEAPONSMITH, random.nextLong());
		}
		return new Keep(f.at(0, 1, DEPTH - 3), f.at(0, -CELLAR_DEPTH, cz), f.at(0, 0, -2), f.at(0, 0, DEPTH / 2));
	}

	private static BlockState byName(String name, Block fallback) {
		Block block = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(name));
		return (block == Blocks.AIR ? fallback : block).defaultBlockState();
	}

	/** The keep's own frame: x across, z inward from the gate, y from the floor; turned to face the bell. */
	private record Frame(BlockPos center, Direction facing, int floor) {
		/** The gate is at z = 0, facing {@code facing}; the keep runs back from it. */
		BlockPos at(int x, int y, int z) {
			Direction back = this.facing.getOpposite();
			Direction right = this.facing.getClockWise();
			int lz = z - DEPTH / 2;
			return new BlockPos(this.center.getX() + back.getStepX() * lz + right.getStepX() * x, this.floor + y,
				this.center.getZ() + back.getStepZ() * lz + right.getStepZ() * x);
		}

		/** A block's facing, given in the frame where the gate faces north, turned to this keep. */
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
			level.setBlock(this.at(x, y, z), state, Block.UPDATE_CLIENTS);
		}
	}
}
