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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class HoseConnectorItem extends Item {
   public HoseConnectorItem(Properties properties) {
      super(properties.stacksTo(1));
   }

   public static boolean hasPendingPump(ItemStack stack) {
      return stack.has(ModDataComponents.CONNECTOR_PUMP_POS);
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
         BlockPos clicked = context.getClickedPos();
         BlockState state = level.getBlockState(clicked);
         HoseConnectionManager connections = HoseConnectionManager.get(serverLevel);
         if (player.isShiftKeyDown()) {
            if (state.is(ModBlocks.PORTABLE_PUMP)) {
               int removed = connections.disconnectAllAtPump(serverLevel, serverPlayer, clicked);
               if (removed > 0) {
                  serverPlayer.sendSystemMessage(Component.translatable("message.peterwolfs_forestfire.hose_disconnected_count", new Object[]{removed}));
                  clearPending(stack);
                  return InteractionResult.CONSUME;
               }
            }

            if (state.is(ModBlocks.GROUND_NOZZLE)
               && level.getBlockEntity(clicked) instanceof GroundNozzleBlockEntity ground
               && ground.getEndpointId() != null
               && connections.disconnectByNozzleEndpoint(serverLevel, serverPlayer, ground.getEndpointId())) {
               clearPending(stack);
               return InteractionResult.CONSUME;
            } else if (connections.disconnectIntakeAt(serverLevel, serverPlayer, clicked)) {
               clearPending(stack);
               return InteractionResult.CONSUME;
            } else {
               serverPlayer.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.hose_no_connection"));
               return InteractionResult.FAIL;
            }
         } else if (state.is(ModBlocks.PORTABLE_PUMP)) {
            stack.set(ModDataComponents.CONNECTOR_PUMP_POS, clicked.asLong());
            String mode = context.getClickLocation().y - clicked.getY() < 0.45 ? "INTAKE" : "OUTPUT";
            if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
               Direction facing = (Direction)state.getValue(BlockStateProperties.HORIZONTAL_FACING);
               if (context.getClickedFace() == facing.getOpposite()) {
                  mode = "INTAKE";
               } else if (context.getClickedFace() == facing) {
                  mode = "OUTPUT";
               }
            }

            stack.set(ModDataComponents.CONNECTOR_MODE, mode);
            if ("INTAKE".equals(mode)) {
               serverPlayer.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.connector_select_water"));
            } else {
               serverPlayer.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.connector_select_nozzle"));
            }

            return InteractionResult.CONSUME;
         } else {
            Long pumpKey = (Long)stack.get(ModDataComponents.CONNECTOR_PUMP_POS);
            if (pumpKey == null) {
               serverPlayer.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.connector_select_pump"));
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
                        boolean result = connections.createIntake(serverLevel, serverPlayer, pumpPos, clicked);
                        clearPending(stack);
                        return (InteractionResult)(result ? InteractionResult.CONSUME : InteractionResult.FAIL);
                     }
                  } else if (state.is(ModBlocks.GROUND_NOZZLE)) {
                     BlockEntity be = level.getBlockEntity(clicked);
                     UUID epId = null;
                     if (be instanceof GroundNozzleBlockEntity ground) {
                        epId = ground.getEndpointId();
                     }

                     if (epId == null) {
                        HoseEndpointManager.get(serverLevel).byGroundPos(clicked).ifPresent(ep -> {});
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
                     serverPlayer.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.connector_select_nozzle"));
                     return InteractionResult.FAIL;
                  }
               }
            }
         }
      } else {
         return InteractionResult.PASS;
      }
   }
}
