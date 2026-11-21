package com.jaymospowers.bonebond;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Bonding Vat ({@link Boneiron}): a tub of brine with boneiron worked into it. Use a boneiron ingot on it to put one
 * in (up to {@link Boneiron#VAT_MOST}); a fast healer climbs in and settles (sneaking) to take the next tier into their bones.
 */
public class BondingVatBlock extends Block {
	/** Boneiron ingots in the brine. */
	public static final IntegerProperty METAL = IntegerProperty.create("metal", 0, Boneiron.VAT_MOST);
	/** A tub's walls and floor, as a cauldron's (the floor 4 high). */
	private static final VoxelShape SHAPE = Shapes.join(Shapes.block(), Block.column(12.0, 4.0, 16.0), net.minecraft.world.phys.shapes.BooleanOp.ONLY_FIRST);

	public BondingVatBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(METAL, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(METAL);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (!stack.is(Boneiron.INGOT)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		int metal = state.getValue(METAL);
		if (metal >= Boneiron.VAT_MOST) {
			return InteractionResult.FAIL;
		}
		if (!level.isClientSide()) {
			level.setBlock(pos, state.setValue(METAL, metal + 1), Block.UPDATE_ALL);
			stack.consume(1, player);
			level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.7F, 0.6F);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.bonebond.vat", state.getValue(METAL), Boneiron.cost(Boneiron.tier(player) + 1)));
		}
		return InteractionResult.SUCCESS;
	}
}
