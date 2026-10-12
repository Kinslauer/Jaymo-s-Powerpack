package com.jaymoskingdoms;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.Cushion;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (0.5.27) Seats you can sit on: every stool in a finished building (a bottom wooden slab beside a table, a fence with a
 * pressure plate on it) gets one of 26.3's cushions in the realm's colour, facing the table. Right-click it to sit.
 */
final class Cushions {
	private Cushions() {
	}

	/** Cushions on the stools between {@code min} and {@code top} that have none. */
	static void seat(ServerLevel level, Faction f, BlockPos min, BlockPos top) {
		for (BlockPos pos : BlockPos.betweenClosed(min, top)) {
			Direction table = stool(level, pos);
			if (table == null) {
				continue;
			}
			Vec3 at = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
			AABB box = EntityTypes.CUSHION.getSpawnAABB(at);
			if (!level.getEntitiesOfClass(Cushion.class, box).isEmpty() || !Cushion.canBePlacedAt(level, box)) {
				continue;
			}
			Cushion cushion = EntityTypes.CUSHION.create(level, EntitySpawnReason.TRIGGERED);
			if (cushion == null) {
				continue;
			}
			cushion.setColor(f.color());
			cushion.snapTo(at, table.toYRot(), 0.0F);
			level.addFreshEntity(cushion);
		}
	}

	/** The way to the table a stool at {@code pos} stands beside, or null if it isn't a stool. */
	private static Direction stool(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof SlabBlock) || !state.is(BlockTags.WOODEN_SLABS)
			|| state.getValue(BlockStateProperties.SLAB_TYPE) != SlabType.BOTTOM) {
			return null;
		}
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos side = pos.relative(d);
			if (level.getBlockState(side).getBlock() instanceof FenceBlock
				&& level.getBlockState(side.above()).getBlock() instanceof PressurePlateBlock) {
				return d;
			}
		}
		return null;
	}
}
