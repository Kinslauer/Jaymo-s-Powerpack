package com.jaymospowers.tinkering.smithing;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * (Smithing) The Drafting Table: use it to open its screen ({@link DraftingTableMenu}), where a blueprint and paper make a
 * copy one level lower ({@link Blueprints#copyOf}), or a forged piece and paper are taken apart for its design
 * ({@link Patterns}).
 */
public class DraftingTableBlock extends Block {
	private static final Component TITLE = Component.translatable("container.jaymospowers.drafting_table");

	public DraftingTableBlock(Block.Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide()) {
			player.openMenu(state.getMenuProvider(level, pos));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
		return new SimpleMenuProvider((id, inventory, player) -> new DraftingTableMenu(id, inventory, ContainerLevelAccess.create(level, pos)), TITLE);
	}
}
