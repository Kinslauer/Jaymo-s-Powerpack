package com.jaymospowers.costume;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Costume Table: right-click it to open the designer ({@link CostumeNet.Open}). Nothing is kept on it; the design
 * lives in the screen until it is sewn, and the server checks the table is still in reach when it is.
 */
public class CostumeTableBlock extends Block {
	public CostumeTableBlock(Block.Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player instanceof ServerPlayer server) {
			ServerPlayNetworking.send(server, new CostumeNet.Open(pos));
		}
		return InteractionResult.SUCCESS;
	}
}
