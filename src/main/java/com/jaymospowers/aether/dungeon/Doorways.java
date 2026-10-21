package com.jaymospowers.aether.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** The boss room's doors, found by looking over the room's walls (the interior {@code room} box and the shell round it). */
final class Doorways {
	private Doorways() {
	}

	private static Iterable<BlockPos> shell(BoundingBox room) {
		return BlockPos.betweenClosed(room.minX() - 1, room.minY() - 1, room.minZ() - 1, room.maxX() + 1, room.maxY() + 1, room.maxZ() + 1);
	}

	/** Closes (or opens) every boss doorway of the room. Returns how many there were. */
	static int setClosed(ServerLevel level, BoundingBox room, boolean closed) {
		int count = 0;
		for (BlockPos pos : shell(room)) {
			BlockState state = level.getBlockState(pos);
			if (state.is(DungeonBlocks.BOSS_DOORWAY)) {
				count++;
				if (state.getValue(BossDoorwayBlock.CLOSED) != closed) {
					level.setBlock(pos, state.setValue(BossDoorwayBlock.CLOSED, closed), 3);
				}
			}
		}
		return count;
	}

	/** Breaks the treasure doorways (with the usual dust and noise); the way to the crate is open. */
	static int breakTreasure(ServerLevel level, BoundingBox room) {
		int count = 0;
		for (BlockPos pos : shell(room)) {
			if (level.getBlockState(pos).is(DungeonBlocks.TREASURE_DOORWAY)) {
				level.destroyBlock(pos.immutable(), false);
				count++;
			}
		}
		return count;
	}

	/** The boss is dead: the locked blocks of the room's shell become the stone they were made to look like (so the walls can be mined). */
	static int unlock(ServerLevel level, BoundingBox room) {
		int count = 0;
		for (BlockPos pos : shell(room)) {
			BlockState state = level.getBlockState(pos);
			if (!(state.getBlock() instanceof LockedBlock)) {
				continue;
			}
			String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
			net.minecraft.world.level.block.Block plain = net.minecraft.core.registries.BuiltInRegistries.BLOCK
				.getValue(com.jaymospowers.JaymosPowers.id(id.substring("locked_".length())));
			if (plain != net.minecraft.world.level.block.Blocks.AIR) {
				level.setBlock(pos, plain.defaultBlockState(), 2);
				count++;
			}
		}
		return count;
	}

	/** Every loaded (unsprung) sentry trap inside the room. */
	static java.util.List<BlockPos> loadedTraps(ServerLevel level, BoundingBox room) {
		java.util.List<BlockPos> traps = new java.util.ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(room.minX(), room.minY() - 1, room.minZ(), room.maxX(), room.maxY(), room.maxZ())) {
			BlockState state = level.getBlockState(pos);
			if (state.is(DungeonBlocks.SENTRY_TRAP) && state.getValue(SentryTrapBlock.LOADED)) {
				traps.add(pos.immutable());
			}
		}
		return traps;
	}
}
