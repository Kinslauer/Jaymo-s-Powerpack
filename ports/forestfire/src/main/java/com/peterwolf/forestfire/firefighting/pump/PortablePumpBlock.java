package com.peterwolf.forestfire.firefighting.pump;

import com.peterwolf.forestfire.block.ModBlockEntities;
import com.peterwolf.forestfire.firefighting.hose.HoseConnectionManager;
import com.peterwolf.forestfire.item.HoseConnectorItem;
import com.peterwolf.forestfire.item.HoseRollItem;
import com.peterwolf.forestfire.item.ModItems;
import com.peterwolf.forestfire.item.PumpFuelCanItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class PortablePumpBlock extends BaseEntityBlock {
   public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
   private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 2.0, 16.0, 15.0, 14.0);

   public PortablePumpBlock(Properties properties) {
      super(properties);
      this.registerDefaultState((BlockState)((BlockState)this.stateDefinition.any()).setValue(FACING, Direction.NORTH));
   }


   public BlockState getStateForPlacement(BlockPlaceContext context) {
      return (BlockState)this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      builder.add(new Property[]{FACING});
   }

   protected RenderShape getRenderShape(BlockState state) {
      return RenderShape.MODEL;
   }

   protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return SHAPE;
   }

   @Nullable
   public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return new PortablePumpBlockEntity(pos, state);
   }

   @Nullable
   public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
      return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.PORTABLE_PUMP, PortablePumpBlockEntity::serverTick);
   }

   protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
      if (!isHoseTool(stack) && !isFuelCan(stack) && !isNozzle(stack)) {
         return this.useWithoutItem(state, level, pos, player, hit);
      } else {
         UseOnContext ctx = new UseOnContext(level, player, hand, stack, hit);
         InteractionResult itemResult = stack.useOn(ctx);
         return (InteractionResult)(!itemResult.consumesAction() && itemResult == InteractionResult.PASS ? InteractionResult.PASS : itemResult);
      }
   }

   protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
      if (level.isClientSide()) {
         return InteractionResult.SUCCESS;
      } else {
         ItemStack main = player.getMainHandItem();
         ItemStack off = player.getOffhandItem();
         if (!isHoseTool(main) && !isHoseTool(off) && !isFuelCan(main) && !isFuelCan(off) && !isNozzle(main) && !isNozzle(off)) {
            if (level.getBlockEntity(pos) instanceof PortablePumpBlockEntity pump && player instanceof ServerPlayer serverPlayer) {
               if (player.isShiftKeyDown()) {
                  for (Component line : pump.statusLines()) {
                     serverPlayer.sendSystemMessage(line);
                  }

                  if (level instanceof ServerLevel serverLevel) {
                     HoseConnectionManager mgr = HoseConnectionManager.get(serverLevel);
                     HoseConnectionManager.IntakeScanResult intake = mgr.scanIntake(serverLevel, pos);
                     if (intake.hasIntake()) {
                        serverPlayer.sendSystemMessage(
                           Component.literal("Auto intake: OK len=" + intake.intakeLength() + " eff=" + String.format("%.0f%%", intake.efficiency() * 100.0F))
                        );
                     } else {
                        serverPlayer.sendSystemMessage(Component.literal("Auto intake: none — hose roll: back of pump → water"));
                     }

                     int lines = mgr.countAttackLines(pos);
                     serverPlayer.sendSystemMessage(Component.literal("Auto attack lines: " + lines));
                  }
               } else {
                  pump.togglePower(serverPlayer);

                  for (Component line : pump.statusLines()) {
                     serverPlayer.sendSystemMessage(line);
                  }
               }

               return InteractionResult.CONSUME;
            } else {
               return InteractionResult.PASS;
            }
         } else {
            return InteractionResult.PASS;
         }
      }
   }

   public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
      if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
         ServerPlayer sp = player instanceof ServerPlayer serverPlayer ? serverPlayer : null;
         int n = HoseConnectionManager.get(serverLevel).clearAllOnPumpDestroyed(serverLevel, pos, sp);
         if (sp != null && n > 0) {
            sp.sendSystemMessage(Component.translatable("message.peterwolfs_forestfire.pump_hoses_cleared", new Object[]{n}));
         }
      }

      return super.playerWillDestroy(level, pos, state, player);
   }

   public void destroy(LevelAccessor level, BlockPos pos, BlockState state) {
      if (level instanceof ServerLevel serverLevel) {
         HoseConnectionManager.get(serverLevel).clearAllOnPumpDestroyed(serverLevel, pos, null);
      }

      super.destroy(level, pos, state);
   }

   public void wasExploded(ServerLevel level, BlockPos pos, Explosion explosion) {
      HoseConnectionManager.get(level).clearAllOnPumpDestroyed(level, pos, null);
      super.wasExploded(level, pos, explosion);
   }

   private static boolean isHoseTool(ItemStack stack) {
      return stack.isEmpty()
         ? false
         : stack.getItem() instanceof HoseRollItem
            || stack.getItem() instanceof HoseConnectorItem
            || stack.is(ModItems.HOSE_ROLL_SMALL)
            || stack.is(ModItems.HOSE_ROLL_STANDARD)
            || stack.is(ModItems.HOSE_ROLL_LARGE)
            || stack.is(ModItems.HOSE_CONNECTOR)
            || stack.is(ModItems.HOSE_ANCHOR);
   }

   private static boolean isFuelCan(ItemStack stack) {
      return !stack.isEmpty() && (stack.getItem() instanceof PumpFuelCanItem || stack.is(ModItems.PUMP_FUEL_CAN));
   }

   private static boolean isNozzle(ItemStack stack) {
      return !stack.isEmpty() && stack.is(ModItems.FIRE_HOSE_NOZZLE);
   }
}
