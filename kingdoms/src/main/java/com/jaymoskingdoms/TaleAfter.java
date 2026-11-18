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
import net.minecraft.world.entity.Entity;
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
	/** How far round a grave nothing may be built or placed (blocks). */
	static final int AROUND = 2;
	/** The most lines a grave's board holds. */
	static final int BOARD_LINES = 4;

	private TaleAfter() {
	}

	/**
	 * A grave for whoever {@code lines} name, dug among the town's other graves if it has any (so they gather into a
	 * burying ground), else on open ground at the town's edge: a mound, and a board at its head (on the seat's side), its
	 * words facing the grave. Only on natural ground under the sky, never on or beside anything built or placed by a
	 * player. False if nowhere would do just now.
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
				// (a mound is too low to stop the heightmap: it's the block the map gives, or the one under it)
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
				BlockPos mound = level.getBlockState(top).is(Graves.GRAVE) ? top : level.getBlockState(top.below()).is(Graves.GRAVE) ? top.below() : null;
				if (mound != null) {
					for (Direction d : Direction.Plane.HORIZONTAL) {
						spots.add(mound.relative(d, BESIDE));
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

	/**
	 * Open ground at {@code at}: earth, sand, gravel or snow under it, room above (grass, flowers or snow are cleared),
	 * the sky over it, and nothing round it but the land itself and other graves: no block a player placed, nothing
	 * built, nothing standing beside it above the ground, no block entity but a grave's board.
	 */
	private static boolean open(ServerLevel level, BlockPos at) {
		BlockState under = level.getBlockState(at.below());
		if (!room(level, at) || !room(level, at.above()) || !level.canSeeSky(at)
			|| !(under.is(BlockTags.DIRT) || under.is(BlockTags.SAND) || under.is(Blocks.GRAVEL) || under.is(Blocks.SNOW_BLOCK))) {
			return false;
		}
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-AROUND, -1, -AROUND), at.offset(AROUND, 2, AROUND))) {
			BlockState state = level.getBlockState(p);
			if (state.isAir()) {
				continue;
			}
			if (Placed.any(level, p)) {
				return false;
			}
			if (state.is(Graves.GRAVE) || state.is(Blocks.SPRUCE_SIGN) && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)
				&& Direction.Plane.HORIZONTAL.stream().anyMatch(d -> level.getBlockState(p.relative(d)).is(Graves.GRAVE))) {
				// (the town's other graves, and their boards)
				continue;
			}
			if (state.hasBlockEntity() || !state.getFluidState().isEmpty() || !(Lairs.natural(state) || state.is(BlockTags.FLOWERS) || state.canBeReplaced())) {
				return false;
			}
			if (p.getY() >= at.getY() && !state.canBeReplaced() && !state.is(BlockTags.FLOWERS)) {
				// (nothing standing beside it at all, of stone or sandstone or clay: that's a wall, a house's or a ruin's)
				return false;
			}
		}
		return true;
	}

	/** Air, or a plant or snow a grave can be dug through. */
	private static boolean room(ServerLevel level, BlockPos p) {
		BlockState state = level.getBlockState(p);
		return state.isAir() || state.canBeReplaced() && state.getFluidState().isEmpty() && !state.hasBlockEntity();
	}

	/**
	 * Whoever {@code id} is packs up and leaves the town with their household, the way any household that has had enough
	 * does ({@link Emigrants#hardship}): their house is left as it was. Never one who leads a town, a player's family, a
	 * child, someone's sworn companion, a captive, one asleep under a keeping, or one still in a tale of anyone's.
	 * True once they've set off, or if they never may; false to try again another day (not loaded here just now, or in
	 * someone's tale for now).
	 */
	static boolean leave(ServerLevel level, UUID id) {
		Entity e = level.getEntity(id);
		if (e == null) {
			return false;
		}
		if (!(e instanceof Villager v) || !v.isAlive() || v.isBaby() || Bonds.lordOf(v) != null || Captives.bound(v) || KeepingCells.kept(v)
			|| McaCompat.playerFamily(v) || Emigrants.leads(level, id)) {
			return true;
		}
		if (TaleCast.cast(v) != null || Tales.inTale(level.getServer(), id)) {
			return false;
		}
		return Emigrants.hardship(level, v);
	}

}
