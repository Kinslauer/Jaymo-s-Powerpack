package com.jaymoskitchen.mill.power;

import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The hub of a windmill or waterwheel, set on the end of a level shaft by its item ({@link RotorItem}); the sails or
 * paddles are drawn turning round it, and only the hub is solid. {@link #FACING} points along the shaft.
 * Broken, it gives back the windmill or waterwheel.
 */
public class RotorBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	private static final VoxelShape SHAPE = Block.box(4, 4, 4, 12, 12, 12);

	public final Rotor rotor;

	public RotorBlock(Rotor rotor, BlockBehaviour.Properties properties) {
		super(properties);
		this.rotor = rotor;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		// the renderer draws it all, turning
		return RenderShape.INVISIBLE;
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		return new ItemStack(rotor == Rotor.WINDMILL ? Power.WINDMILL_ITEM : Power.WATERWHEEL_ITEM);
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	/**
	 * On a windmill: a dye colours its next sail (Better With Mods' way), using up the dye; a sail goes on the first bare
	 * or torn spar; a sail's own stuff (hemp cloth, wool, leather, paper) patches its most worn sail.
	 */
	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hit) {
		if (rotor != Rotor.WINDMILL || !(level.getBlockEntity(pos) instanceof RotorEntity entity)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (stack.getItem() instanceof SailItem) {
			if (!level.isClientSide()) {
				if (!entity.fit(stack)) {
					return InteractionResult.FAIL;
				}
				stack.consume(1, player);
				level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
			}
			return InteractionResult.SUCCESS;
		}
		// nothing to patch: the wool block (or paper, or leather) does what it would anywhere else
		if (entity.needsPatch(stack)) {
			if (!level.isClientSide() && entity.patch(stack)) {
				stack.consume(1, player);
				level.playSound(null, pos, SoundEvents.WOOL_HIT, SoundSource.BLOCKS, 1.0F, 1.2F);
			}
			return InteractionResult.SUCCESS;
		}
		DyeColor colour = stack.getItem() instanceof DyeItem ? stack.get(DataComponents.DYE) : null;
		if (colour == null) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (!level.isClientSide() && entity.dye(colour)) {
			stack.consume(1, player);
			level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
		}
		return InteractionResult.SUCCESS;
	}

	/** Sneaking with an empty hand on a windmill takes down its last sail (or cuts away a torn one's rags). */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (rotor != Rotor.WINDMILL || !player.isSecondaryUseActive() || !(level.getBlockEntity(pos) instanceof RotorEntity entity)
			|| !entity.hasSail()) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			ItemStack sail = entity.takeDown();
			if (!sail.isEmpty()) {
				player.getInventory().placeItemBackInInventory(sail, net.minecraft.util.Prediction.SERVER_ONLY);
			}
			level.playSound(null, pos, SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 0.8F, 1.0F);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new RotorEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? createTickerHelper(type, Power.ROTOR_ENTITY, RotorEntity::clientTick)
			: createTickerHelper(type, Power.ROTOR_ENTITY, RotorEntity::serverTick);
	}
}
