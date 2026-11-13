package com.jaymoskingdoms.creatures;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * A basilisk's den: its old victims stand round it in stone, mid-stride, hands up, faces turned to where it lies, and in the
 * middle of them a chest of what they carried. The statues are plain blocks (legs, a chiselled head, a raised arm), each a
 * little different.
 */
final class Statues {
	/** How many statues, and how far from the middle (blocks). */
	static final int MOST = 9, LEAST = 5;
	static final int NEAR = 3, FAR = 7;

	private Statues() {
	}

	/** Lays the den round {@code middle} (at the surface) and puts the basilisk in it; true if it was built. */
	static boolean den(ServerLevel level, BlockPos middle) {
		return den(level, middle, false);
	}

	/**
	 * Lays the den round {@code middle}. {@code gorgon}: it is a gorgon's instead, a ring of broken columns among the statues
	 * and the snake-haired archer standing in it.
	 */
	static boolean den(ServerLevel level, BlockPos middle, boolean gorgon) {
		RandomSource random = level.getRandom();
		BlockPos ground = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, middle);
		if (level.getBlockState(ground.below()).isAir() || !level.getFluidState(ground).isEmpty()) {
			return false;
		}
		int count = LEAST + random.nextInt(MOST - LEAST + 1);
		int made = 0;
		for (int i = 0; i < count * 3 && made < count; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double r = NEAR + random.nextDouble() * (FAR - NEAR);
			BlockPos column = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, ground.offset((int) Math.round(Math.cos(angle) * r), 0, (int) Math.round(Math.sin(angle) * r)));
			if (Math.abs(column.getY() - ground.getY()) > 2 || !level.getBlockState(column).isAir() || !level.getFluidState(column).isEmpty()
				|| !level.getBlockState(column.below()).isSolid()) {
				continue;
			}
			statue(level, column, random, angle);
			made++;
		}
		if (made < LEAST) {
			return false;
		}
		BlockPos chest = ground;
		level.setBlock(chest, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
		if (level.getBlockEntity(chest) instanceof ChestBlockEntity box) {
			ItemStack[] loot = {new ItemStack(Items.IRON_SWORD), new ItemStack(Items.GOLD_INGOT, 3 + random.nextInt(5)), new ItemStack(Items.BONE, 4),
				new ItemStack(Items.IRON_HELMET), new ItemStack(Items.EMERALD, 2 + random.nextInt(4)), new ItemStack(Creatures.MIRROR),
				new ItemStack(Items.TORCH, 6), new ItemStack(Items.BREAD, 3)};
			for (int i = 0; i < loot.length; i++) {
				if (random.nextFloat() < 0.6F || loot[i].is(Creatures.MIRROR) && random.nextFloat() < 0.3F) {
					box.setItem(random.nextInt(box.getContainerSize()), loot[i]);
				}
			}
		}
		if (gorgon) {
			// her ruin: a ring of broken columns, no two the same height
			int columns = 5 + random.nextInt(3);
			for (int i = 0; i < columns; i++) {
				double angle = Math.PI * 2.0 * i / columns + random.nextDouble() * 0.3;
				BlockPos foot = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, ground.offset((int) Math.round(Math.cos(angle) * (FAR + 2)), 0, (int) Math.round(Math.sin(angle) * (FAR + 2))));
				if (Math.abs(foot.getY() - ground.getY()) > 3 || !level.getFluidState(foot).isEmpty() || !level.getBlockState(foot.below()).isSolid()) {
					continue;
				}
				int tall = 1 + random.nextInt(4);
				for (int y = 0; y < tall; y++) {
					float roll = random.nextFloat();
					level.setBlock(foot.above(y), (roll < 0.3F ? Blocks.CRACKED_STONE_BRICKS : roll < 0.6F ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS).defaultBlockState(), Block.UPDATE_ALL);
				}
				if (random.nextBoolean()) {
					level.setBlock(foot.above(tall), Blocks.STONE_BRICK_SLAB.defaultBlockState(), Block.UPDATE_ALL);
				}
			}
			net.minecraft.world.entity.Mob her = com.jaymoskingdoms.beasts.Beasts.GORGON.create(level, EntitySpawnReason.EVENT);
			if (her != null) {
				her.snapTo(ground.getX() + 0.5, ground.getY() + 1.0, ground.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
				her.finalizeSpawn(level, level.getCurrentDifficultyAt(ground), EntitySpawnReason.EVENT, null);
				her.setPersistenceRequired();
				level.addFreshEntity(her);
			}
			return true;
		}
		Basilisk beast = Creatures.BASILISK.create(level, EntitySpawnReason.EVENT);
		if (beast == null) {
			return true;
		}
		beast.snapTo(ground.getX() + 0.5, ground.getY() + 1.0, ground.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
		beast.setPersistenceRequired();
		level.addFreshEntity(beast);
		return true;
	}

	/** One stone person at {@code at}, hands up, face turned to the middle (the angle is from there). */
	private static void statue(ServerLevel level, BlockPos at, RandomSource random, double angle) {
		Block body = random.nextFloat() < 0.3F ? Blocks.MOSSY_COBBLESTONE : random.nextBoolean() ? Blocks.STONE : Blocks.ANDESITE;
		level.setBlock(at, body.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(at.above(), (random.nextFloat() < 0.4F ? Blocks.MOSSY_STONE_BRICKS : Blocks.CHISELED_STONE_BRICKS).defaultBlockState(), Block.UPDATE_ALL);
		if (random.nextBoolean()) {
			// a hand thrown up, to the side of the head, toward the middle
			BlockPos hand = at.offset((int) -Math.signum(Math.cos(angle)), 1, 0);
			if (level.getBlockState(hand).isAir()) {
				level.setBlock(hand, Blocks.STONE_BRICK_SLAB.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
	}
}
