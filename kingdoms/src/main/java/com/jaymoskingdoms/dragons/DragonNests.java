package com.jaymoskingdoms.dragons;

import com.jaymoskingdoms.Factions;
import com.jaymoskingdoms.JaymosKingdoms;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * A wild dragon's nest (after Dragonforge's, RochStudio; lore "Dragon nests"): where a wild one first settles (a flier on
 * its perch, a walker at its home) it lays a nest round itself in its breath's colours, a floor, a broken rim with bones in
 * it and, at the rim, a chest of what it has taken ({@code chests/dragon_nest}: bones, hides and gold, never eggs).
 * Fire: blackstone and basalt with a little magma. Frost: packed snow and ice with a little blue ice. Storm: sand fused
 * half to glass. Stone (and acid, in the swamps): a moss-grown pit. Only where blocks may be broken, never on a realm's
 * land, and once a dragon.
 */
final class DragonNests {
	static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE, JaymosKingdoms.id("chests/dragon_nest"));
	/** The nest's radius: a share of its width, at least this and at most that. */
	static final int MIN_RADIUS = 3, MAX_RADIUS = 7;

	private DragonNests() {
	}

	/** Lays {@code dragon}'s nest round where it stands. False if it may not (no griefing, a realm's land, no ground). */
	static boolean lay(ServerLevel level, WindDragon dragon) {
		BlockPos feet = dragon.blockPosition();
		if (!level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.MOB_GRIEFING)
			|| Factions.around(level.getServer(), level, feet, 24.0) != null || !solidGround(level, feet.below())) {
			return false;
		}
		RandomSource random = dragon.getRandom();
		WindDragon.Element element = dragon.element();
		int r = Mth.clamp(Mth.ceil(dragon.getBbWidth() * 0.8F), MIN_RADIUS, MAX_RADIUS);
		boolean pit = element == WindDragon.Element.EARTH || element == WindDragon.Element.ACID;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				int d2 = dx * dx + dz * dz;
				if (d2 > r * r) {
					continue;
				}
				BlockPos floor = feet.offset(dx, -1, dz);
				if (!level.isLoaded(floor) || !solidGround(level, floor) || !open(level, floor.above())) {
					continue;
				}
				clearPlant(level, floor.above());
				set(level, floor, floor(element, random));
				if (pit && d2 * 4 < r * r && solidGround(level, floor.below())) {
					// the pit: its middle sunk a block, moss below
					set(level, floor, Blocks.AIR.defaultBlockState());
					set(level, floor.below(), Blocks.MOSS_BLOCK.defaultBlockState());
				}
				if (d2 > (r - 1) * (r - 1) && random.nextInt(3) != 0) {
					set(level, floor.above(), rim(element, random));
				}
			}
		}
		double a = random.nextDouble() * Mth.TWO_PI;
		BlockPos chest = feet.offset(Mth.floor(Math.cos(a) * (r - 1)), 0, Mth.floor(Math.sin(a) * (r - 1)));
		if (open(level, chest) && solidGround(level, chest.below())) {
			level.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random)),
				Block.UPDATE_ALL);
			RandomizableContainer.setBlockEntityLootTable(level, random, chest, LOOT);
		}
		return true;
	}

	private static BlockState floor(WindDragon.Element element, RandomSource random) {
		return (switch (element) {
			case FIRE -> random.nextInt(10) == 0 ? Blocks.MAGMA_BLOCK : random.nextBoolean() ? Blocks.BLACKSTONE : Blocks.SMOOTH_BASALT;
			case FROST -> random.nextInt(10) == 0 ? Blocks.BLUE_ICE : random.nextBoolean() ? Blocks.SNOW_BLOCK : Blocks.PACKED_ICE;
			case STORM -> random.nextBoolean() ? Blocks.GLASS : Blocks.SAND;
			default -> random.nextInt(3) == 0 ? Blocks.ROOTED_DIRT : random.nextBoolean() ? Blocks.MOSS_BLOCK : Blocks.COARSE_DIRT;
		}).defaultBlockState();
	}

	private static BlockState rim(WindDragon.Element element, RandomSource random) {
		if (random.nextInt(4) == 0) {
			return Blocks.BONE_BLOCK.defaultBlockState();
		}
		return (switch (element) {
			case FIRE -> Blocks.BASALT;
			case FROST -> Blocks.PACKED_ICE;
			case STORM -> Blocks.SANDSTONE;
			default -> Blocks.MOSSY_COBBLESTONE;
		}).defaultBlockState();
	}

	private static boolean solidGround(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.isFaceSturdy(level, pos, Direction.UP) && !state.hasBlockEntity() && state.getDestroySpeed(level, pos) >= 0.0F;
	}

	private static boolean open(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.isAir() || state.canBeReplaced() && state.getFluidState().isEmpty();
	}

	private static void clearPlant(ServerLevel level, BlockPos pos) {
		if (!level.getBlockState(pos).isAir()) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
	}

	private static void set(ServerLevel level, BlockPos pos, BlockState state) {
		if (!level.getBlockState(pos).hasBlockEntity()) {
			level.setBlock(pos, state, Block.UPDATE_ALL);
		}
	}
}
