package com.jaymospowers.tinkering;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A Tinkers table: the Part Builder or the Tool Station. Like the pack's crafting tables, what you leave on it stays there
 * ({@link StationEntity}), and one player works it at a time.
 */
public class StationBlock extends Block implements EntityBlock {
	private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 12, 0, 16, 16, 16), Block.box(0, 0, 0, 4, 12, 4),
		Block.box(12, 0, 0, 16, 12, 4), Block.box(0, 0, 12, 4, 12, 16), Block.box(12, 0, 12, 16, 12, 16));

	/** Which table this is. */
	public enum Kind { PART_BUILDER, TOOL_STATION }

	private final Kind kind;

	public StationBlock(Block.Properties properties, Kind kind) {
		super(properties);
		this.kind = kind;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide()) {
			if (level.getBlockEntity(pos) instanceof StationEntity table && table.busyFor(player)) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.tinkering.busy"));
				return InteractionResult.SUCCESS;
			}
			player.openMenu(state.getMenuProvider(level, pos));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new StationEntity(pos, state);
	}

	@Override
	protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
		ContainerLevelAccess access = ContainerLevelAccess.create(level, pos);
		return kind == Kind.PART_BUILDER
			? new SimpleMenuProvider((id, inventory, player) -> new PartBuilderMenu(id, inventory, access),
				Component.translatable("container.jaymospowers.part_builder"))
			: new SimpleMenuProvider((id, inventory, player) -> new ToolStationMenu(id, inventory, access),
				Component.translatable("container.jaymospowers.tool_station"));
	}
}
