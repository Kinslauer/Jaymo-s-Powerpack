package com.peterwolf.forestfire.firefighting.nozzle;

import com.peterwolf.forestfire.firefighting.hose.HoseConnectionManager;
import com.peterwolf.forestfire.firefighting.hose.HoseEndpoint;
import com.peterwolf.forestfire.firefighting.hose.HoseEndpointManager;
import com.peterwolf.forestfire.item.ModItems;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AfterDeath;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AfterRespawn;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndLevelTick;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Disconnect;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Join;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

public final class NozzleEvents {
   private NozzleEvents() {
   }

   public static void register() {
      ServerTickEvents.END_LEVEL_TICK.register((EndLevelTick)level -> {
         if (level instanceof ServerLevel) {
            HoseConnectionManager.get(level).tick(level);
            HoseEndpointManager.get(level).tick(level);
         }
      });
      ServerPlayConnectionEvents.JOIN.register((Join)(handler, sender, server) -> {
         ServerPlayer player = handler.getPlayer();
         ServerLevel patt0$temp = player.level();
         if (patt0$temp instanceof ServerLevel) {
            HoseConnectionManager.get(patt0$temp).syncToPlayer(player);
         }
      });
      ServerPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, server) -> {
         ServerPlayer player = handler.getPlayer();
         NozzleInputController.clear(player.getUUID());
         ServerLevel patt0$temp = player.level();
         if (patt0$temp instanceof ServerLevel) {
            HoseEndpointManager.get(patt0$temp).releaseOperator(patt0$temp, player.getUUID(), true);
         }
      });
      AttackBlockCallback.EVENT
         .register(
            (AttackBlockCallback)(player, world, hand, pos, direction) -> (InteractionResult)(!holdsConnectedNozzle(player.getItemInHand(hand))
                  && !holdsConnectedNozzle(player.getMainHandItem())
               ? InteractionResult.PASS
               : InteractionResult.SUCCESS)
         );
      AttackEntityCallback.EVENT
         .register(
            (AttackEntityCallback)(player, world, hand, entity, hitResult) -> (InteractionResult)(!holdsConnectedNozzle(player.getItemInHand(hand))
                  && !holdsConnectedNozzle(player.getMainHandItem())
               ? InteractionResult.PASS
               : InteractionResult.SUCCESS)
         );
      ServerLivingEntityEvents.AFTER_DEATH.register((AfterDeath)(entity, damageSource) -> {
         if (entity instanceof ServerPlayer player) {
            ServerLevel patt0$temp = player.level();
            if (patt0$temp instanceof ServerLevel) {
               clearHeldEndpointItems(player);
               HoseEndpointManager.get(patt0$temp).releaseOperator(patt0$temp, player.getUUID(), true);
            }
         }
      });
      ServerPlayerEvents.AFTER_RESPAWN.register((AfterRespawn)(oldPlayer, newPlayer, alive) -> clearHeldEndpointItems(newPlayer));
   }

   public static boolean handleDropAttempt(ServerPlayer player, ItemStack stack) {
      if (!stack.is(ModItems.FIRE_HOSE_NOZZLE)) {
         return false;
      } else {
         String raw = (String)stack.get(ModDataComponents.NOZZLE_ENDPOINT_ID);
         if (raw != null && !raw.isEmpty()) {
            ServerLevel e = player.level();
            if (e instanceof ServerLevel) {
               ServerLevel level = e;

               try {
                  UUID id = UUID.fromString(raw);
                  HoseEndpointManager manager = HoseEndpointManager.get(level);
                  HoseEndpoint ep = manager.get(id).orElse(null);
                  if (ep == null) {
                     return false;
                  } else {
                     player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_no_drop"));
                     if (ep.valve == NozzleValveState.CLOSED) {
                        BlockPos feet = player.blockPosition();
                        if (level.getBlockState(feet).isAir() || level.getBlockState(feet).canBeReplaced()) {
                           manager.placeFromHand(level, player, ep, feet, player.getYRot(), player.getXRot());
                        }
                     }

                     return true;
                  }
               } catch (IllegalArgumentException var8) {
                  return false;
               }
            } else {
               return true;
            }
         } else {
            return false;
         }
      }
   }

   private static void clearHeldEndpointItems(ServerPlayer player) {
      NozzleInputController.clear(player.getUUID());

      for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
         ItemStack stack = player.getInventory().getItem(i);
         if (stack.is(ModItems.FIRE_HOSE_NOZZLE)) {
            String id = (String)stack.get(ModDataComponents.NOZZLE_ENDPOINT_ID);
            if (id != null && !id.isEmpty()) {
               player.getInventory().setItem(i, ItemStack.EMPTY);
            }
         }
      }
   }

   private static boolean holdsConnectedNozzle(ItemStack stack) {
      if (!stack.is(ModItems.FIRE_HOSE_NOZZLE)) {
         return false;
      } else {
         String id = (String)stack.get(ModDataComponents.NOZZLE_ENDPOINT_ID);
         return id != null && !id.isEmpty();
      }
   }
}
