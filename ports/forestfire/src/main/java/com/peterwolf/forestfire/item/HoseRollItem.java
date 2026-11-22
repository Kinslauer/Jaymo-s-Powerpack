package com.peterwolf.forestfire.item;

import com.peterwolf.forestfire.block.ModBlocks;
import com.peterwolf.forestfire.firefighting.hose.HoseConnectionManager;
import com.peterwolf.forestfire.firefighting.hose.HoseEndpointManager;
import com.peterwolf.forestfire.firefighting.intake.IntakeSourceResolver;
import com.peterwolf.forestfire.firefighting.nozzle.GroundNozzleBlockEntity;
import com.peterwolf.forestfire.firefighting.nozzle.ModDataComponents;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class HoseRollItem extends Item {
   private final int capacity;

   public HoseRollItem(Properties properties, int capacity) {
      super(properties.stacksTo(1));
      this.capacity = capacity;
   }

   public int capacity() {
      return this.capacity;
   }

   public static int getRemaining(ItemStack stack) {
      if (stack.getItem() instanceof HoseRollItem roll) {
         Integer stored = (Integer)stack.get(ModDataComponents.HOSE_REMAINING);
         return stored == null ? roll.capacity : Math.max(0, Math.min(roll.capacity, stored));
      } else {
         return 0;
      }
   }

   public static void setRemaining(ItemStack stack, int remaining) {
      if (stack.getItem() instanceof HoseRollItem roll) {
         int var4 = Math.max(0, Math.min(roll.capacity, remaining));
         stack.set(ModDataComponents.HOSE_REMAINING, var4);
      }
   }

   public static void ensureInitialized(ItemStack stack) {
      if (stack.getItem() instanceof HoseRollItem roll) {
         if (!stack.has(ModDataComponents.HOSE_REMAINING)) {
            setRemaining(stack, roll.capacity);
         }
      }
   }

   public ItemStack getDefaultInstance() {
      ItemStack stack = super.getDefaultInstance();
      setRemaining(stack, this.capacity);
      return stack;
   }

   public static void clearPending(ItemStack stack) {
      stack.remove(ModDataComponents.CONNECTOR_PUMP_POS);
      stack.remove(ModDataComponents.CONNECTOR_MODE);
   }

   public InteractionResult useOn(UseOnContext context) {
      Level level = context.getLevel();
      Player player = context.getPlayer();
      if (player == null) {
         return InteractionResult.PASS;
      } else if (level.isClientSide()) {
         return InteractionResult.SUCCESS;
      } else if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
         ItemStack stack = context.getItemInHand();
         ensureInitialized(stack);
         if (getRemaining(stack) <= 0) {
            serverPlayer.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.hose_roll_empty"));
            return InteractionResult.FAIL;
         } else {
            BlockPos clicked = context.getClickedPos();
            BlockState state = level.getBlockState(clicked);
            HoseConnectionManager connections = HoseConnectionManager.get(serverLevel);
            if (player.isShiftKeyDown() && state.is(ModBlocks.PORTABLE_PUMP)) {
               int removed = connections.disconnectAllAtPump(serverLevel, serverPlayer, clicked);
               if (removed > 0) {
                  serverPlayer.sendSystemMessage(Component.translatable("message.peterwolfs_forestfire.hose_disconnected_count", new Object[]{removed}));
                  clearPending(stack);
                  return InteractionResult.CONSUME;
               } else {
                  serverPlayer.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.hose_no_connection"));
                  return InteractionResult.FAIL;
               }
            } else if (state.is(ModBlocks.PORTABLE_PUMP)) {
               stack.set(ModDataComponents.CONNECTOR_PUMP_POS, clicked.asLong());
               String mode = "OUTPUT";
               if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                  Direction facing = (Direction)state.getValue(BlockStateProperties.HORIZONTAL_FACING);
                  if (context.getClickedFace() == facing.getOpposite()) {
                     mode = "INTAKE";
                  } else if (context.getClickedFace() == facing) {
                     mode = "OUTPUT";
                  } else if (context.getClickLocation().y - clicked.getY() < 0.45) {
                     mode = "INTAKE";
                  }
               } else if (context.getClickLocation().y - clicked.getY() < 0.45) {
                  mode = "INTAKE";
               }

               stack.set(ModDataComponents.CONNECTOR_MODE, mode);
               int rem = getRemaining(stack);
               if ("INTAKE".equals(mode)) {
                  serverPlayer.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.hose_roll_intake_ready", new Object[]{rem}));
               } else {
                  serverPlayer.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.hose_roll_output_ready", new Object[]{rem}));
               }

               return InteractionResult.CONSUME;
            } else {
               Long pumpKey = (Long)stack.get(ModDataComponents.CONNECTOR_PUMP_POS);
               if (pumpKey == null) {
                  if (IntakeSourceResolver.isValidSource(serverLevel, clicked)) {
                     BlockPos pump = HoseEndpointManager.findNearbyPump(serverLevel, clicked, 24);
                     if (pump == null) {
                        pump = HoseEndpointManager.findNearbyPump(serverLevel, player.blockPosition(), 24);
                     }

                     if (pump != null) {
                        boolean ok = connections.createIntake(serverLevel, serverPlayer, pump, clicked);
                        return (InteractionResult)(ok ? InteractionResult.CONSUME : InteractionResult.FAIL);
                     }
                  }

                  serverPlayer.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.hose_roll_select_pump"));
                  return InteractionResult.FAIL;
               } else {
                  BlockPos pumpPos = BlockPos.of(pumpKey);
                  if (!level.getBlockState(pumpPos).is(ModBlocks.PORTABLE_PUMP)) {
                     clearPending(stack);
                     serverPlayer.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.connector_pump_gone"));
                     return InteractionResult.FAIL;
                  } else {
                     String modex = (String)stack.getOrDefault(ModDataComponents.CONNECTOR_MODE, "OUTPUT");
                     if ("INTAKE".equals(modex)) {
                        if (!IntakeSourceResolver.isValidSource(serverLevel, clicked)) {
                           serverPlayer.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.intake_invalid"));
                           return InteractionResult.FAIL;
                        } else {
                           boolean ok = connections.createIntake(serverLevel, serverPlayer, pumpPos, clicked);
                           clearPending(stack);
                           return (InteractionResult)(ok ? InteractionResult.CONSUME : InteractionResult.FAIL);
                        }
                     } else if (state.is(ModBlocks.GROUND_NOZZLE)) {
                        UUID epId = null;
                        if (level.getBlockEntity(clicked) instanceof GroundNozzleBlockEntity ground) {
                           epId = ground.getEndpointId();
                        }

                        if (epId == null) {
                           epId = HoseEndpointManager.get(serverLevel).byGroundPos(clicked).map(ep -> ep.id).orElse(null);
                        }

                        if (epId == null) {
                           serverPlayer.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_orphaned"));
                           return InteractionResult.FAIL;
                        } else {
                           boolean ok = connections.createAttackToEndpoint(serverLevel, serverPlayer, pumpPos, epId, clicked);
                           clearPending(stack);
                           return (InteractionResult)(ok ? InteractionResult.CONSUME : InteractionResult.FAIL);
                        }
                     } else if (state.is(ModBlocks.HOSE_SPLITTER)) {
                        boolean ok = connections.createSupplyToSplitter(serverLevel, serverPlayer, pumpPos, clicked);
                        clearPending(stack);
                        return (InteractionResult)(ok ? InteractionResult.CONSUME : InteractionResult.FAIL);
                     } else {
                        serverPlayer.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.hose_roll_output_next"));
                        return InteractionResult.FAIL;
                     }
                  }
               }
            }
         }
      } else {
         return InteractionResult.PASS;
      }
   }
}
