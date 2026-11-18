package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * What a tale leaves weeks after it ends that changes the town itself ({@link TaleWays#later}): a new grave at the edge
 * of the town, with the name on a board at its head; and someone of the town who has packed up and gone.
 */
final class TaleAfter {
	/** How far from the seat a town's graves are looked for (blocks), and where a first one is dug if it has none: between these. */
	static final int GRAVES_REACH = 40, FIRST_LEAST = 16, FIRST_MOST = 32;
	/** How many spots are tried for a first grave, and how far beside an old one a new one goes (blocks). */
	static final int GRAVE_TRIES = 40, BESIDE = 2;
	/** Someone moving away goes only with no player this near (blocks): nobody sees them vanish. */
	static final double LEAVE_UNSEEN = 32.0;
	/** The most lines a grave's board holds. */
	static final int BOARD_LINES = 4;

	private TaleAfter() {
	}

	/**
	 * A grave for whoever {@code lines} name, dug among the town's other graves if it has any (so they gather into a
	 * burying ground), else on open ground at the town's edge: a mound, and a board at its head facing the seat. Only on
	 * natural ground under the sky, never on anything built. False if nowhere would do just now.
	 */
	static boolean grave(ServerLevel level, Faction f, List<String> lines) {
		BlockPos seat = f.seat();
		if (!level.isLoaded(seat)) {
			return false;
		}
		RandomSource random = level.getRandom();
		List<BlockPos> spots = new ArrayList<>();
		// (beside the graves already there)
		for (int dx = -GRAVES_REACH; dx <= GRAVES_REACH; dx++) {
			for (int dz = -GRAVES_REACH; dz <= GRAVES_REACH; dz++) {
				BlockPos column = seat.offset(dx, 0, dz);
				if (!level.isLoaded(column)) {
					continue;
				}
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
				if (level.getBlockState(top.below()).is(Graves.GRAVE)) {
					for (Direction d : Direction.Plane.HORIZONTAL) {
						spots.add(top.below().relative(d, BESIDE));
					}
				}
			}
		}
		for (int i = 0; i < GRAVE_TRIES; i++) {
			double a = random.nextDouble() * Math.PI * 2.0;
			int r = FIRST_LEAST + random.nextInt(FIRST_MOST - FIRST_LEAST + 1);
			spots.add(seat.offset((int) Math.round(Math.cos(a) * r), 0, (int) Math.round(Math.sin(a) * r)));
		}
		for (BlockPos spot : spots) {
			if (!level.isLoaded(spot)) {
				continue;
			}
			BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spot);
			Direction toSeat = Direction.getApproximateNearest(seat.getX() - at.getX(), 0, seat.getZ() - at.getZ());
			BlockPos board = at.relative(toSeat);
			if (!open(level, at) || !open(level, board) || Math.abs(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, board).getY() - at.getY()) > 0) {
				continue;
			}
			level.setBlockAndUpdate(at, Graves.GRAVE.defaultBlockState());
			BlockState sign = Blocks.SPRUCE_SIGN.defaultBlockState().setValue(BlockStateProperties.ROTATION_16, RotationSegment.convertToSegment(toSeat.getOpposite()));
			level.setBlockAndUpdate(board, sign);
			if (level.getBlockEntity(board) instanceof SignBlockEntity entity) {
				Component[] words = new Component[BOARD_LINES];
				for (int i = 0; i < BOARD_LINES; i++) {
					words[i] = i < lines.size() ? Component.literal(lines.get(i)) : Component.empty();
				}
				entity.setText(new SignText(List.of(words), List.of(words), DyeColor.BLACK, false), SignTextSlot.FRONT);
				entity.setWaxed(true);
				level.sendBlockUpdated(board, sign, sign, Block.UPDATE_ALL);
			}
			return true;
		}
		return false;
	}

	/** Open ground at {@code at}: earth under it, nothing built on it or beside it, two blocks of air, the sky above. */
	private static boolean open(ServerLevel level, BlockPos at) {
		if (!level.getBlockState(at).isAir() || !level.getBlockState(at.above()).isAir() || !level.canSeeSky(at)
			|| !level.getBlockState(at.below()).is(BlockTags.DIRT)) {
			return false;
		}
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-1, -1, -1), at.offset(1, 1, 1))) {
			if (level.getBlockState(p).hasBlockEntity()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Whoever {@code id} is has packed up and left the town: gone from the world while nobody is near enough to see
	 * (their house is left as it was, empty). Only a grown villager who is nobody's sworn companion and not on a throne.
	 * True once they're gone, or if they never can be (dead, or one who stays); false to try again another time.
	 */
	static boolean leave(ServerLevel level, UUID id) {
		if (!(level.getEntity(id) instanceof Villager v) || !v.isAlive()) {
			// (not loaded here just now: asked again another day, until the entry is too old to keep)
			return level.getEntity(id) != null;
		}
		if (v.isBaby() || Rulers.is(v) || Bonds.lordOf(v) != null) {
			return true;
		}
		if (level.getNearestPlayer(v, LEAVE_UNSEEN) != null) {
			return false;
		}
		McaCompat.vanish(v);
		return true;
	}
}
