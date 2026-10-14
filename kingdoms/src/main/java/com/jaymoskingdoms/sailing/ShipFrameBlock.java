package com.jaymoskingdoms.sailing;

import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.12) A ship's frame, laid on the water by a {@link ShipKits build kit}: one block of the ship still to build, its
 * bare ribs showing what it wants (planks for the hull and deck, a fence for the rail, a fence or log for a mast, a
 * sail, the helm, an oar, a gun, a chest). Right-click it with that to put it in. When the last one's in, the water in
 * her hold is bailed out and she's ready to cast off. (AlekiShips, MIT, builds its ships in the world from frames; ours
 * are our own.)
 */
public final class ShipFrameBlock extends Block implements SimpleWaterloggedBlock {
	public enum Want implements StringRepresentable {
		HULL, RAIL, MAST, SAIL, HELM, OAR, CANNON, CHEST;

		@Override
		public String getSerializedName() {
			return this.name().toLowerCase(Locale.ROOT);
		}

		public Component label() {
			return Component.translatable("ship.jaymoskingdoms.want." + this.getSerializedName());
		}
	}

	public static final EnumProperty<Want> WANT = EnumProperty.create("want", Want.class);
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

	public ShipFrameBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(WANT, Want.HULL).setValue(FACING, Direction.NORTH).setValue(WATERLOGGED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(WANT, FACING, WATERLOGGED);
	}

	/** The block {@code stack} would put in a frame wanting {@code want} facing {@code facing} (null: not the right thing). */
	static @Nullable BlockState fills(Want want, Direction facing, ItemStack stack) {
		if (!(stack.getItem() instanceof BlockItem item)) {
			return null;
		}
		Block block = item.getBlock();
		return switch (want) {
			case HULL -> stack.is(ItemTags.PLANKS) ? block.defaultBlockState() : null;
			case RAIL -> stack.is(ItemTags.WOODEN_FENCES) ? block.defaultBlockState() : null;
			case MAST -> stack.is(ItemTags.WOODEN_FENCES) || stack.is(ItemTags.LOGS) && block.defaultBlockState().is(BlockTags.LOGS) ? block.defaultBlockState() : null;
			case SAIL -> block == Sailing.SAIL ? block.defaultBlockState().setValue(SailBlock.FACING, facing) : null;
			case HELM -> block == Sailing.HELM ? block.defaultBlockState().setValue(HelmBlock.FACING, facing) : null;
			case OAR -> block == Sailing.OAR ? block.defaultBlockState().setValue(OarBlock.FACING, facing) : null;
			case CANNON -> block == Sailing.CANNON ? block.defaultBlockState().setValue(ShipCannonBlock.FACING, facing) : null;
			case CHEST -> stack.is(Items.CHEST) ? Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing) : null;
		};
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		BlockState next = fills(state.getValue(WANT), state.getValue(FACING), stack);
		if (next == null) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (!(level instanceof ServerLevel server)) {
			return InteractionResult.SUCCESS;
		}
		if (next.hasProperty(BlockStateProperties.WATERLOGGED)) {
			next = next.setValue(BlockStateProperties.WATERLOGGED, state.getValue(WATERLOGGED));
		}
		next = Block.updateFromNeighbourShapes(next, level, pos);
		if (next.getBlock() instanceof SailBlock) {
			next = SailBlock.connect(next, level, pos);
		}
		level.setBlock(pos, next, Block.UPDATE_ALL);
		SoundType sound = next.getSoundType();
		level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
		stack.consume(1, player);
		ShipKits.placed(server, pos, player instanceof ServerPlayer p ? p : null);
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player instanceof ServerPlayer p) {
			p.sendOverlayMessage(Component.translatable("ship.jaymoskingdoms.frame_wants", state.getValue(WANT).label()));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		// you can walk the hull's frames as you build her; the rest you reach through
		return state.getValue(WANT) == Want.HULL ? Shapes.block() : Shapes.empty();
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction toNeighbour,
		BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		if (state.getValue(WATERLOGGED)) {
			ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		}
		return state;
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}
}
