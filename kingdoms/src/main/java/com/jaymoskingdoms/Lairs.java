package com.jaymoskingdoms;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.storage.LevelData;

/**
 * (0.7.61) Where the newly cursed come to: a vampire wakes from its turning in a crypt, a werewolf from its first moon
 * in a den, each dug out beneath where they stood, with a bed that's now where they wake after dying, and a ladder up.
 * Only natural ground is dug (stone, dirt, sand, gravel, ores); if there's anything else in the way (a build, a cave,
 * the sea), there's no lair and they wake where they are.
 */
final class Lairs {
	/** How far down the floor of a crypt / a den lies. */
	static final int CRYPT_DEPTH = 9, DEN_DEPTH = 5;
	/** Half widths (the walls are one further out) and inside heights. */
	static final int CRYPT_HALF = 3, CRYPT_HEIGHT = 3, DEN_HALF = 2, DEN_HEIGHT = 2;
	/** The share of a lair's blocks that may be air already (small caves are fine). */
	static final float AIR_ALLOWED = 0.2F;

	private Lairs() {
	}

	/** A crypt beneath {@code player}; true if it was built (they're in it). */
	static boolean crypt(ServerLevel level, ServerPlayer player) {
		BlockPos floor = player.blockPosition().below(CRYPT_DEPTH);
		if (!fits(level, floor, CRYPT_HALF, CRYPT_HEIGHT, player.blockPosition())) {
			return false;
		}
		RandomSource random = level.getRandom();
		shell(level, floor, CRYPT_HALF, CRYPT_HEIGHT, (pos, wall) -> {
			if (wall) {
				float r = random.nextFloat();
				return (r < 0.2F ? Blocks.MOSSY_STONE_BRICKS : r < 0.4F ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS).defaultBlockState();
			}
			return pos.getY() == floor.getY() ? Blocks.DEEPSLATE_TILES.defaultBlockState() : Blocks.AIR.defaultBlockState();
		});
		int y = floor.getY() + 1;
		// the coffin: a black bed boxed in dark oak, at the far end
		BlockPos head = new BlockPos(floor.getX(), y, floor.getZ() - 2), foot = head.south();
		bed(level, head, foot, Direction.NORTH, Blocks.BED.pick(net.minecraft.world.item.DyeColor.BLACK));
		for (BlockPos side : new BlockPos[] {head.east(), head.west(), foot.east(), foot.west()}) {
			level.setBlock(side, Blocks.DARK_OAK_SLAB.defaultBlockState(), Block.UPDATE_ALL);
		}
		level.setBlock(head.north().above(), Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, 0), Block.UPDATE_ALL);
		// candles in the corners, cobwebs and chains overhead
		int h = CRYPT_HALF;
		for (int[] c : new int[][] {{-h, -h}, {h, -h}, {-h, h}, {h, h}}) {
			level.setBlock(new BlockPos(floor.getX() + c[0], y, floor.getZ() + c[1]),
				Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 1 + random.nextInt(4)).setValue(CandleBlock.LIT, true), Block.UPDATE_ALL);
			if (random.nextBoolean()) {
				level.setBlock(new BlockPos(floor.getX() + c[0], floor.getY() + CRYPT_HEIGHT, floor.getZ() + c[1] + (c[1] < 0 ? 1 : -1)),
					Blocks.COBWEB.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
		level.setBlock(new BlockPos(floor.getX(), floor.getY() + CRYPT_HEIGHT, floor.getZ()), Blocks.IRON_CHAIN.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(new BlockPos(floor.getX(), floor.getY() + CRYPT_HEIGHT - 1, floor.getZ()), Blocks.SOUL_LANTERN.defaultBlockState()
			.setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true), Block.UPDATE_ALL);
		ladder(level, floor, CRYPT_HALF, CRYPT_HEIGHT, player.blockPosition());
		wake(level, player, foot, head);
		return true;
	}

	/** A den beneath {@code player}; true if it was built (they're in it). */
	static boolean den(ServerLevel level, ServerPlayer player) {
		BlockPos floor = player.blockPosition().below(DEN_DEPTH);
		if (!fits(level, floor, DEN_HALF, DEN_HEIGHT, player.blockPosition())) {
			return false;
		}
		RandomSource random = level.getRandom();
		shell(level, floor, DEN_HALF, DEN_HEIGHT, (pos, wall) -> {
			if (wall) {
				float r = random.nextFloat();
				return (r < 0.35F ? Blocks.ROOTED_DIRT : r < 0.6F ? Blocks.COARSE_DIRT : Blocks.PACKED_MUD).defaultBlockState();
			}
			return pos.getY() == floor.getY() ? (random.nextBoolean() ? Blocks.PODZOL : Blocks.COARSE_DIRT).defaultBlockState() : Blocks.AIR.defaultBlockState();
		});
		int y = floor.getY() + 1;
		BlockPos head = new BlockPos(floor.getX() - 1, y, floor.getZ() - 1), foot = head.south();
		bed(level, head, foot, Direction.NORTH, Blocks.STRAW_BED);
		// gnawed bones and a skull, roots hanging from the roof
		level.setBlock(new BlockPos(floor.getX() + 1, y, floor.getZ() - 1), Blocks.BONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(new BlockPos(floor.getX() + 1, y, floor.getZ() + 1), Blocks.SKELETON_SKULL.defaultBlockState()
			.setValue(SkullBlock.ROTATION, random.nextInt(16)), Block.UPDATE_ALL);
		for (int i = 0; i < 4; i++) {
			BlockPos root = new BlockPos(floor.getX() + random.nextInt(3) - 1, floor.getY() + DEN_HEIGHT, floor.getZ() + random.nextInt(3) - 1);
			if (level.getBlockState(root).isAir()) {
				level.setBlock(root, Blocks.HANGING_ROOTS.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
		ladder(level, floor, DEN_HALF, DEN_HEIGHT, player.blockPosition());
		wake(level, player, foot, head);
		return true;
	}

	/** In the Overworld, deep enough, and dug only into natural ground. */
	private static boolean fits(ServerLevel level, BlockPos floor, int half, int height, BlockPos top) {
		if (level.dimension() != Level.OVERWORLD || floor.getY() <= level.getMinY() + 4) {
			return false;
		}
		int air = 0, all = 0;
		for (BlockPos p : BlockPos.betweenClosed(floor.offset(-half - 1, 0, -half - 1), floor.offset(half + 1, height + 1, half + 1))) {
			BlockState s = level.getBlockState(p);
			all++;
			if (s.isAir()) {
				air++;
			} else if (!natural(s)) {
				return false;
			}
		}
		// the ladder's shaft, at the room's east wall, up to where they stood
		for (int y = floor.getY() + height + 1; y < top.getY(); y++) {
			BlockState s = level.getBlockState(new BlockPos(floor.getX() + half, y, floor.getZ()));
			if (!s.isAir() && !natural(s)) {
				return false;
			}
		}
		return air <= all * AIR_ALLOWED;
	}

	private static boolean natural(BlockState s) {
		return !s.hasBlockEntity() && s.getFluidState().isEmpty() && (s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.DIRT)
			|| s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) || s.is(BlockTags.ORES) || s.is(Blocks.SNOW) || s.is(Blocks.SNOW_BLOCK)
			|| s.is(BlockTags.REPLACEABLE_BY_TREES) || s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE) || s.is(Blocks.TERRACOTTA)
			|| s.is(BlockTags.TERRACOTTA));
	}

	private interface Fill {
		BlockState at(BlockPos pos, boolean wall);
	}

	/** The room: a shell one block thick round {@code half}/{@code height} of inside, the floor at {@code floor}'s height. */
	private static void shell(ServerLevel level, BlockPos floor, int half, int height, Fill fill) {
		for (BlockPos p : BlockPos.betweenClosed(floor.offset(-half - 1, 0, -half - 1), floor.offset(half + 1, height + 1, half + 1))) {
			int dx = Math.abs(p.getX() - floor.getX()), dz = Math.abs(p.getZ() - floor.getZ()), dy = p.getY() - floor.getY();
			boolean wall = dx > half || dz > half || dy == height + 1;
			level.setBlock(p, fill.at(p, wall), Block.UPDATE_ALL);
		}
	}

	private static void bed(ServerLevel level, BlockPos head, BlockPos foot, Direction facing, Block bed) {
		level.setBlock(foot, bed.defaultBlockState().setValue(AbstractBedBlock.FACING, facing).setValue(AbstractBedBlock.PART, BedPart.FOOT), Block.UPDATE_ALL);
		level.setBlock(head, bed.defaultBlockState().setValue(AbstractBedBlock.FACING, facing).setValue(AbstractBedBlock.PART, BedPart.HEAD), Block.UPDATE_ALL);
	}

	/** A ladder up the east side to where they stood (the shaft dug through what's natural). */
	private static void ladder(ServerLevel level, BlockPos floor, int half, int height, BlockPos top) {
		int x = floor.getX() + half, z = floor.getZ();
		for (int y = floor.getY() + 1; y < top.getY(); y++) {
			BlockPos p = new BlockPos(x, y, z);
			if (y > floor.getY() + height && !level.getBlockState(p).isAir()) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
			BlockPos wall = p.east();
			if (!level.getBlockState(wall).isSolid()) {
				level.setBlock(wall, Blocks.STONE_BRICKS.defaultBlockState(), Block.UPDATE_ALL);
			}
			level.setBlock(p, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST), Block.UPDATE_ALL);
		}
	}

	/** They come to beside the bed, which is theirs now: where they'll wake after dying. */
	private static void wake(ServerLevel level, ServerPlayer player, BlockPos foot, BlockPos head) {
		BlockPos stand = foot.south();
		player.teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
		player.setYRot(0.0F);
		player.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(level.dimension(), head, 180.0F, 0.0F), false), false);
		player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, false, false));
		player.fallDistance = 0.0;
		level.playSound(null, stand, SoundEvents.CANDLE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 0.6F);
	}
}
