package com.jaymoskitchen.vintner;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The treading tub (grapes; jump in it) and the apple press (apples; turn the screw). Both are the same machine
 * ({@link JuiceEntity}); their juice is drawn off in glass bottles and goes into a cask.
 */
public class JuiceBlock extends BaseEntityBlock {
	private static final VoxelShape TUB = Block.box(1, 0, 1, 15, 8, 15);
	private static final VoxelShape PRESS = Block.box(2, 0, 2, 14, 16, 14);
	/** A landing this far counts as a tread. */
	static final double TREAD_FALL = 0.5D;
	/** How much of a fall the tub softens (the share that still hurts). */
	private static final float SOFT_FALL = 0.2F;

	public final boolean tub;

	public JuiceBlock(boolean tub, BlockBehaviour.Properties properties) {
		super(properties);
		this.tub = tub;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return tub ? TUB : PRESS;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new JuiceEntity(pos, state);
	}

	@Override
	public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, double fallDistance) {
		if (tub && fallDistance >= TREAD_FALL && entity instanceof Player player && level.getBlockEntity(pos) instanceof JuiceEntity juice) {
			juice.tread(player);
		}
		entity.causeFallDamage(fallDistance, SOFT_FALL, level.damageSources().fall());
	}

	@Override
	protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (level.getBlockEntity(pos) instanceof JuiceEntity juice) {
			InteractionResult result = juice.use(player, hand, held);
			return result == InteractionResult.PASS ? (held.isEmpty() ? InteractionResult.TRY_WITH_EMPTY_HAND : InteractionResult.PASS) : result;
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.getBlockEntity(pos) instanceof JuiceEntity juice) {
			return juice.use(player, InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		}
		return InteractionResult.PASS;
	}
}
