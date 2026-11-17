package com.jaymoskitchen.kitchen;

import com.jaymoskitchen.cooking.Dish;
import com.jaymoskitchen.cooking.RealTimeCookingBlockEntity;
import com.jaymoskitchen.cooking.Station;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** A block that cooks items in real-time using heat and fuel. */
public class RealTimeCookerBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 14, 15);

    public RealTimeCookerBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RealTimeCookingBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, KitchenBlocks.REAL_TIME_COOKER_ENTITY, RealTimeCookingBlockEntity::serverTick);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof RealTimeCookingBlockEntity entity) {
            // Sneak + right-click with empty hand: take fuel
            if (player.isShiftKeyDown()) {
                if (!entity.getFuelItem().isEmpty()) {
                    ItemStack fuel = entity.getFuelItem();
                    entity.setFuelItem(ItemStack.EMPTY);
                    if (player.addItem(fuel)) {
                        return InteractionResult.SUCCESS;
                    } else {
                        entity.setFuelItem(fuel); // Put back if inventory full
                    }
                }
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof RealTimeCookingBlockEntity entity) {
            // Try to add fuel first
            if (entity.canAcceptFuel(held)) {
                ItemStack toAdd = held.copyWithCount(1);
                held.shrink(1);
                if (entity.getFuelItem().isEmpty()) {
                    entity.setFuelItem(toAdd);
                } else if (entity.getFuelItem().is(toAdd.getItem()) && entity.getFuelItem().getCount() < entity.getFuelItem().getMaxStackSize()) {
                    entity.getFuelItem().grow(1);
                } else {
                    // Put back if can't stack
                    if (!player.addItem(toAdd)) {
                        player.drop(toAdd, false);
                    }
                }
                return InteractionResult.SUCCESS;
            }

            // Try to start cooking with the held item
            if (!held.isEmpty()) {
                // Find a matching dish for this ingredient at furnace station
                Dish dish = entity.findRecipeFor(held, Station.FURNACE);
                if (dish != null) {
                    // Try to start cooking
                    int slot = entity.tryStartCooking((net.minecraft.server.level.ServerLevel) level, dish, held);
                    if (slot >= 0) {
                        held.shrink(1);
                        return InteractionResult.SUCCESS;
                    }
                }
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState();
    }
}