package com.jaymoskitchen.delight;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * (Farmer's Delight's tomatoes on rope, MIT; their art, our own code.) A tomato vine growing up a rope: it's the rope
 * with a vine on it, so it can be climbed, and when a player picks the whole block out the rope is left behind.
 */
public class HangingTomatoBlock extends TomatoBlock {
	public HangingTomatoBlock(Properties properties) {
		super(properties);
	}

	/** The rope a broken vine leaves in its place, joined to what's around it. */
	public static void placeRope(LevelAccessor level, BlockPos pos) {
		level.setBlock(pos, RopeBlock.getStateWithConnections(DelightFarm.ROPE.defaultBlockState(), level, pos, Direction.UP),
			level.isClientSide() ? Block.UPDATE_ALL_IMMEDIATE : Block.UPDATE_ALL);
	}

	@Override
	public void playerDestroy(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity,
		ItemStack destroyedWith) {
		super.playerDestroy(level, player, pos, state, blockEntity, destroyedWith);
		if (level.getBlockState(pos).isAir()) {
			placeRope(level, pos);
		}
	}
}
