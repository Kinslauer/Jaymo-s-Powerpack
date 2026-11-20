package com.peterwolf.forestfire.firefighting.nozzle;

import com.peterwolf.forestfire.block.ModBlocks;
import com.peterwolf.forestfire.firefighting.hose.HoseConnection;
import com.peterwolf.forestfire.firefighting.hose.HoseConnectionManager;
import com.peterwolf.forestfire.firefighting.hose.HoseEndpoint;
import com.peterwolf.forestfire.firefighting.hose.HoseEndpointManager;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public class FireHoseNozzleItem extends Item {
   private static final int PUMP_SEARCH = 48;

   public FireHoseNozzleItem(Properties properties) {
      super(properties.stacksTo(1));
   }

   public static NozzleMode getMode(ItemStack stack) {
      return NozzleMode.byOrdinal((Integer)stack.getOrDefault(ModDataComponents.NOZZLE_MODE, NozzleMode.STRAIGHT_STREAM.ordinal()));
   }

   public static void setMode(ItemStack stack, NozzleMode mode) {
      stack.set(ModDataComponents.NOZZLE_MODE, mode.ordinal());
   }

   @Nullable
   public static UUID getEndpointId(ItemStack stack) {
      String raw = (String)stack.get(ModDataComponents.NOZZLE_ENDPOINT_ID);
      if (raw != null && !raw.isEmpty()) {
         try {
            return UUID.fromString(raw);
         } catch (IllegalArgumentException var3) {
            return null;
         }
      } else {
         return null;
      }
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
         UUID endpointId = getEndpointId(stack);
         HoseEndpointManager manager = HoseEndpointManager.get(serverLevel);
         HoseConnectionManager connections = HoseConnectionManager.get(serverLevel);
         if (endpointId == null && HoseEndpointManager.isPump(serverLevel, clicked)) {
            boolean ok = connections.createAttackHeld(serverLevel, serverPlayer, clicked, getMode(stack));
            return (InteractionResult)(ok ? InteractionResult.CONSUME : InteractionResult.FAIL);
         } else if (endpointId == null && serverLevel.getBlockState(clicked).is(ModBlocks.HOSE_SPLITTER)) {
            BlockPos pump = HoseEndpointManager.findNearbyPump(serverLevel, clicked, 48);
            if (pump == null) {
               serverPlayer.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_need_hose"));
               return InteractionResult.FAIL;
            } else {
               boolean ok = connections.createAttackHeld(serverLevel, serverPlayer, pump, getMode(stack));
               return (InteractionResult)(ok ? InteractionResult.CONSUME : InteractionResult.FAIL);
            }
         } else {
            if (endpointId != null && HoseEndpointManager.isPump(serverLevel, clicked) && !player.isShiftKeyDown()) {
               HoseEndpoint ep = manager.get(endpointId).orElse(null);
               if (ep != null) {
                  HoseConnection conn = connections.findAttackByEndpoint(endpointId);
                  if (conn != null && !conn.sourcePos.equals(clicked)) {
                     serverPlayer.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_wrong_pump"));
                     return InteractionResult.FAIL;
                  }

                  if (ep.pumpPos == null || ep.pumpPos.equals(clicked) || conn != null && conn.sourcePos.equals(clicked)) {
                     if (manager.disconnect(serverLevel, serverPlayer, ep)) {
                        return InteractionResult.CONSUME;
                     }

                     return InteractionResult.FAIL;
                  }

                  serverPlayer.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_wrong_pump"));
                  return InteractionResult.FAIL;
               }
            }

            BlockPos placePos = resolvePlacePos(serverLevel, clicked, context.getClickedFace(), player);
            if (placePos == null) {
               serverPlayer.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_invalid_place"));
               return InteractionResult.FAIL;
            } else if (endpointId != null) {
               HoseEndpoint ep = manager.get(endpointId).orElse(null);
               if (ep == null) {
                  stack.remove(ModDataComponents.NOZZLE_ENDPOINT_ID);
                  return placeFreeOnPump(serverLevel, serverPlayer, stack, placePos);
               } else {
                  if (player.isShiftKeyDown() || context.getClickedFace() == Direction.UP) {
                     if (ep.valve == NozzleValveState.OPEN) {
                        ep.closeValve();
                     }

                     if (manager.placeFromHand(serverLevel, serverPlayer, ep, placePos, player.getYRot(), player.getXRot())) {
                        return InteractionResult.CONSUME;
                     }
                  }

                  return InteractionResult.PASS;
               }
            } else {
               return placeFreeOnPump(serverLevel, serverPlayer, stack, placePos);
            }
         }
      } else {
         return InteractionResult.PASS;
      }
   }

   private static InteractionResult placeFreeOnPump(ServerLevel level, ServerPlayer player, ItemStack stack, BlockPos placePos) {
      BlockPos pump = HoseEndpointManager.findNearbyPump(level, placePos, 48);
      if (pump == null) {
         pump = HoseEndpointManager.findNearbyPump(level, player.blockPosition(), 48);
      }

      if (pump == null) {
         player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_need_hose"));
         return InteractionResult.FAIL;
      } else {
         boolean ok = HoseConnectionManager.get(level).createAttackGround(level, player, pump, placePos, player.getYRot(), player.getXRot(), getMode(stack));
         if (ok) {
            player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_connected"));
            return InteractionResult.CONSUME;
         } else {
            return InteractionResult.FAIL;
         }
      }
   }

   @Nullable
   private static BlockPos resolvePlacePos(ServerLevel level, BlockPos clicked, Direction face, Player player) {
      BlockPos preferred = clicked.relative(face);
      if (canPlaceAt(level, preferred)) {
         return preferred;
      } else if (face != Direction.UP && canPlaceAt(level, clicked.above())) {
         return clicked.above();
      } else if (isReplaceableSurface(level, preferred)) {
         return preferred;
      } else if (isReplaceableSurface(level, clicked.above())) {
         return clicked.above();
      } else {
         BlockPos feet = player.blockPosition();
         if (canPlaceAt(level, feet) && feet.closerThan(clicked, 3.0)) {
            return feet;
         } else {
            for (Direction d : Plane.HORIZONTAL) {
               BlockPos p = clicked.relative(d);
               if (canPlaceAt(level, p)) {
                  return p;
               }
            }

            return null;
         }
      }
   }

   private static boolean canPlaceAt(ServerLevel level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      return state.isAir() || state.canBeReplaced() || isReplaceableSurface(level, pos);
   }

   private static boolean isReplaceableSurface(ServerLevel level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      return state.is(BlockTags.REPLACEABLE) || state.is(BlockTags.FLOWERS) || state.canBeReplaced();
   }

   public InteractionResult use(Level level, Player player, InteractionHand hand) {
      ItemStack stack = player.getItemInHand(hand);
      UUID endpointId = getEndpointId(stack);
      if (endpointId == null) {
         if (!level.isClientSide()) {
            NozzleMode next = getMode(stack).cycleCombat();
            setMode(stack, next);
            player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_mode", new Object[]{next.label}));
         }

         return InteractionResult.SUCCESS;
      } else if (level.isClientSide()) {
         return InteractionResult.SUCCESS;
      } else if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
         HoseEndpointManager manager = HoseEndpointManager.get(serverLevel);
         HoseEndpoint ep = manager.get(endpointId).orElse(null);
         if (ep != null && ep.connected) {
            NozzleMode next = ep.mode.cycleCombat();
            ep.mode = next;
            setMode(stack, next);
            if (!next.allowsFlow()) {
               ep.closeValve();
               NozzleInputController.clear(serverPlayer.getUUID());
            }

            manager.setDirty();
            serverPlayer.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_mode", new Object[]{next.label}));
            return InteractionResult.SUCCESS;
         } else {
            stack.remove(ModDataComponents.NOZZLE_ENDPOINT_ID);
            serverPlayer.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_orphaned"));
            return InteractionResult.FAIL;
         }
      } else {
         return InteractionResult.PASS;
      }
   }

   public int getUseDuration(ItemStack stack, LivingEntity entity) {
      return 0;
   }

   public ItemUseAnimation getUseAnimation(ItemStack stack) {
      return ItemUseAnimation.NONE;
   }

   public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
      return false;
   }

   public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
   }
}
