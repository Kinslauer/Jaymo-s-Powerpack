package com.jaymospowers.tinkering.smeltery;

import com.jaymospowers.tinkering.Tinkering;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** The smeltery controller: set it in the wall, facing out. It lights up while the smeltery has heat. */
public class SmelteryBlock extends HorizontalDirectionalBlock implements EntityBlock {
	public static final BooleanProperty LIT = BlockStateProperties.LIT;

	public SmelteryBlock(Block.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH).setValue(LIT, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, LIT);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SmelteryEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() || type != Tinkering.SMELTERY_ENTITY ? null
			: (BlockEntityTicker<T>) (BlockEntityTicker<SmelteryEntity>) SmelteryEntity::serverTick;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SmelteryEntity smeltery) {
			if (!smeltery.formed()) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.smeltery.unformed"));
			}
			player.openMenu(new ExtendedMenuProvider<BlockPos>() {
				@Override
				public BlockPos getScreenOpeningData(ServerPlayer serverPlayer) {
					return pos;
				}

				@Override
				public Component getDisplayName() {
					return Component.translatable("container.jaymospowers.smeltery");
				}

				@Override
				public AbstractContainerMenu createMenu(int id, Inventory inventory, Player menuPlayer) {
					return new SmelteryMenu(id, inventory, smeltery, smeltery.data, pos);
				}
			});
		}
		return InteractionResult.SUCCESS;
	}
}
