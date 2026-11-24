package com.jaymospowers.umbrella;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * An umbrella stand, by a door or at a market stall: an umbrella put in stands there, furled or open. An open one keeps
 * the rain off the ground round it ({@link Umbrellas#keepsRain}). Use it with an umbrella to set one in; empty-handed
 * to open or furl it; sneaking, to take it back.
 */
public class UmbrellaStandBlock extends Block implements EntityBlock {
	public enum Contents implements StringRepresentable {
		EMPTY, CLOSED, OPEN;

		@Override
		public String getSerializedName() {
			return this.name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	public static final EnumProperty<Contents> CONTENTS = EnumProperty.create("contents", Contents.class);
	/** Which kind of umbrella stands in it ({@link Umbrellas.Kind#ordinal}), so the model shows it. */
	public static final IntegerProperty KIND = IntegerProperty.create("kind", 0, 2);
	private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 16, 12);

	public UmbrellaStandBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(CONTENTS, Contents.EMPTY).setValue(KIND, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(CONTENTS, KIND);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new UmbrellaStandEntity(pos, state);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (state.getValue(CONTENTS) != Contents.EMPTY || !(stack.getItem() instanceof UmbrellaItem item)
			|| !(level.getBlockEntity(pos) instanceof UmbrellaStandEntity stand)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (!level.isClientSide()) {
			stand.set(stack.copyWithCount(1));
			level.setBlock(pos, state.setValue(CONTENTS, Umbrellas.isOpen(stack) ? Contents.OPEN : Contents.CLOSED).setValue(KIND, item.kind.ordinal()),
				Block.UPDATE_ALL);
			level.playSound(null, pos, SoundEvents.BUNDLE_INSERT, SoundSource.BLOCKS, 0.7F, 1.0F);
			if (!player.isCreative()) {
				stack.shrink(1);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (state.getValue(CONTENTS) == Contents.EMPTY || !(level.getBlockEntity(pos) instanceof UmbrellaStandEntity stand)) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			ItemStack held = stand.umbrella();
			if (player.isShiftKeyDown()) {
				player.getInventory().placeItemBackInInventory(held, Prediction.SERVER_ONLY);
				stand.set(ItemStack.EMPTY);
				level.setBlock(pos, state.setValue(CONTENTS, Contents.EMPTY), Block.UPDATE_ALL);
				level.playSound(null, pos, SoundEvents.BUNDLE_REMOVE_ONE, SoundSource.BLOCKS, 0.7F, 1.0F);
			} else {
				boolean open = state.getValue(CONTENTS) != Contents.OPEN;
				Umbrellas.setOpen(held, open);
				stand.set(held);
				level.setBlock(pos, state.setValue(CONTENTS, open ? Contents.OPEN : Contents.CLOSED), Block.UPDATE_ALL);
				level.playSound(null, pos, open ? SoundEvents.BUNDLE_INSERT : SoundEvents.BUNDLE_REMOVE_ONE, SoundSource.BLOCKS, 0.7F, open ? 1.3F : 0.9F);
			}
		}
		return InteractionResult.SUCCESS;
	}
}
