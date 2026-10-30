package com.jaymospowers.power.zfighter.farworld;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * (0.18.53) The field anvil: a small anvil of stone and iron that a fighter sets down on a far world. Used, it opens a
 * crafting grid (a crafting table's, since planks are not crafted); it is where a world's rocks are knapped into its first metal
 * (three rocks in a row and an iron ingot under the middle one).
 */
public final class FieldAnvilBlock extends Block {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 11.0, 14.0);

	public FieldAnvilBlock(Block.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getClockWise());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide()) {
			player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new Menu(id, inventory, ContainerLevelAccess.create(level, pos)),
				Component.translatable("container.jaymospowers.field_anvil")));
		}
		return InteractionResult.SUCCESS;
	}

	/** A crafting table's menu that stays open beside a field anvil. */
	static final class Menu extends CraftingMenu {
		private final ContainerLevelAccess access;

		Menu(int id, Inventory inventory, ContainerLevelAccess access) {
			super(id, inventory, access);
			this.access = access;
		}

		@Override
		public boolean stillValid(Player player) {
			return AbstractContainerMenu.stillValid(this.access, player, FarWorlds.FIELD_ANVIL);
		}
	}
}
