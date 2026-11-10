package com.jaymoskitchen.rats;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A rat trap, set facing you: right-click it with food to bait it (bait draws rats from {@link Rat#RAID_RANGE}
 * blocks), sneak-click with an empty hand to take the bait back. What it does to a rat is its own
 * ({@link SnapTrapBlock}, {@link CageTrapBlock}).
 */
public abstract class TrapBlock extends BaseEntityBlock {
	/** Rats this close to a trap that goes off run for it (blocks). */
	static final double SCATTER_RADIUS = 8.0;

	protected TrapBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(HorizontalDirectionalBlock.FACING, rotation.rotate(state.getValue(HorizontalDirectionalBlock.FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(HorizontalDirectionalBlock.FACING)));
	}

	/** It goes off on what's in the trap itself, not on anything brushing the edge of its block. */
	@Override
	protected VoxelShape getEntityInsideCollisionShape(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
		return getShape(state, level, pos, CollisionContext.empty());
	}

	/** Whether it can be baited as it stands (set, and empty). */
	protected abstract boolean open(BlockState state);

	@Override
	protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (!open(state) || !FoodStores.ratFood(held) || !(level.getBlockEntity(pos) instanceof BaitTrapEntity trap)
			|| !trap.bait().isEmpty()) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (!level.isClientSide()) {
			trap.setBait(held.copyWithCount(1));
			held.consume(1, player);
			level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.8F, 1.0F);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player.isShiftKeyDown() && open(state) && level.getBlockEntity(pos) instanceof BaitTrapEntity trap && !trap.bait().isEmpty()) {
			if (!level.isClientSide()) {
				ItemStack taken = trap.takeBait();
				if (!player.getInventory().add(taken)) {
					Block.popResource(level, pos.above(), taken);
				}
				level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.8F, 1.0F);
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	/** Small game a baited trap catches (Andy's TFC Hardrock picks): rabbits, chickens, foxes, Naturalist's critters. */
	public static final net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>> SMALL_GAME = net.minecraft.tags.TagKey.create(
		net.minecraft.core.registries.Registries.ENTITY_TYPE, com.jaymoskitchen.JaymosKitchen.id("small_game"));
	/** How far a baited trap draws small game from, and one random tick in how many it does. */
	static final double LURE_RANGE = 12.0;
	static final int LURE_CHANCE = 6;

	static boolean smallGame(Entity entity) {
		return entity.getType().builtInRegistryHolder().is(SMALL_GAME) && entity instanceof net.minecraft.world.entity.Mob mob && mob.isAlive() && !mob.isBaby()
			&& !(entity instanceof net.minecraft.world.entity.TamableAnimal tame && tame.isTame()) && !mob.isLeashed();
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return open(state);
	}

	/** Now and then a baited trap draws small game near it in to the bait. */
	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
		if (!open(state) || random.nextInt(LURE_CHANCE) != 0 || !(level.getBlockEntity(pos) instanceof BaitTrapEntity trap) || trap.bait().isEmpty()) {
			return;
		}
		level.getEntitiesOfClass(net.minecraft.world.entity.PathfinderMob.class, new net.minecraft.world.phys.AABB(pos).inflate(LURE_RANGE),
			TrapBlock::smallGame).stream().findFirst()
			.ifPresent(game -> game.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.0));
	}

	/** Every other rat near a trap that just went off runs for it. */
	static void scatter(ServerLevel level, BlockPos pos, Rat caught) {
		Vec3 at = Vec3.atCenterOf(pos);
		for (Rat rat : level.getEntitiesOfClass(Rat.class, new net.minecraft.world.phys.AABB(pos).inflate(SCATTER_RADIUS), r -> r != caught)) {
			rat.scare(at);
		}
	}
}
