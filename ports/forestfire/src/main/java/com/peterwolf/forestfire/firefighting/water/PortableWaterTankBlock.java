package com.peterwolf.forestfire.firefighting.water;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class PortableWaterTankBlock extends BaseEntityBlock {
   private static final VoxelShape SMALL_SHAPE = Block.box(3.0, 0.0, 3.0, 13.0, 12.0, 13.0);
   private static final VoxelShape MEDIUM_SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 15.0, 14.0);
   private static final VoxelShape LARGE_SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 16.0, 15.0);
   private final TankSize size;

   public PortableWaterTankBlock(Properties properties, TankSize size) {
      super(properties);
      this.size = size;
   }

   public TankSize size() {
      return this.size;
   }


   protected RenderShape getRenderShape(BlockState state) {
      return RenderShape.MODEL;
   }

   protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return switch (this.size) {
         case SMALL -> SMALL_SHAPE;
         case MEDIUM -> MEDIUM_SHAPE;
         case LARGE -> LARGE_SHAPE;
      };
   }

   @Nullable
   public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return new PortableWaterTankBlockEntity(pos, state, this.size);
   }

   protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
      if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof PortableWaterTankBlockEntity tank) {
         serverPlayer.sendSystemMessage(Component.translatable("message.peterwolfs_forestfire.tank_status", new Object[]{tank.getWater(), tank.getCapacity()}));
         return InteractionResult.CONSUME;
      } else {
         return InteractionResult.SUCCESS;
      }
   }
}
