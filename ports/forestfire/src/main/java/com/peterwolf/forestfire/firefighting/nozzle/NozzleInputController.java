package com.peterwolf.forestfire.firefighting.nozzle;

import com.peterwolf.forestfire.firefighting.hose.HoseEndpoint;
import com.peterwolf.forestfire.firefighting.hose.HoseEndpointManager;
import com.peterwolf.forestfire.item.ModItems;
import com.peterwolf.forestfire.network.NozzleLpmPayload;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class NozzleInputController {
   private static final int DOUBLE_CLICK_TICKS = 7;
   private static final Map<UUID, NozzleInputController.PlayerNozzleInput> INPUT = new HashMap<>();

   private NozzleInputController() {
   }

   public static void clear(UUID playerId) {
      INPUT.remove(playerId);
   }

   public static void clearAll() {
      INPUT.clear();
   }

   public static void handle(ServerPlayer player, NozzleLpmPayload payload) {
      ServerLevel stack = player.level();
      if (stack instanceof ServerLevel) {
         ItemStack stackx = player.getMainHandItem();
         if (!stackx.is(ModItems.FIRE_HOSE_NOZZLE)) {
            stackx = player.getOffhandItem();
         }

         if (stackx.is(ModItems.FIRE_HOSE_NOZZLE)) {
            UUID endpointId = FireHoseNozzleItem.getEndpointId(stackx);
            if (endpointId != null) {
               HoseEndpointManager manager = HoseEndpointManager.get(stack);
               HoseEndpoint ep = manager.get(endpointId).orElse(null);
               if (ep != null && ep.connected) {
                  NozzleInputController.PlayerNozzleInput state = INPUT.computeIfAbsent(player.getUUID(), id -> new NozzleInputController.PlayerNozzleInput());
                  long now = stack.getGameTime();
                  switch (payload.action()) {
                     case 0:
                        onClick(player, manager, ep, state, now);
                        break;
                     case 1:
                        onHold(player, manager, ep, state);
                        break;
                     case 2:
                        onRelease(player, manager, ep, state);
                  }
               }
            }
         }
      }
   }

   private static void onClick(ServerPlayer player, HoseEndpointManager manager, HoseEndpoint ep, NozzleInputController.PlayerNozzleInput state, long now) {
      ep.location = NozzleLocationState.HELD;
      ep.operatorId = player.getUUID();
      if (state.continuous) {
         state.continuous = false;
         state.holding = false;
         state.suppressHoldUntilRelease = true;
         state.lastClickTick = -999L;
         ep.closeValve();
         manager.setDirty();
         player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_flow_off"));
      } else if (state.lastClickTick < 0L || now - state.lastClickTick > 7L) {
         state.lastClickTick = now;
         state.holding = true;
         state.suppressHoldUntilRelease = false;
         if (ep.mode.allowsFlow()) {
            ep.valve = NozzleValveState.OPEN;
            manager.setDirty();
         } else {
            player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_shutoff"));
         }
      } else if (!ep.mode.allowsFlow()) {
         player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_shutoff"));
         state.lastClickTick = now;
      } else {
         state.continuous = true;
         state.holding = true;
         state.suppressHoldUntilRelease = false;
         state.lastClickTick = -999L;
         ep.valve = NozzleValveState.OPEN;
         manager.setDirty();
         player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_flow_on"));
      }
   }

   private static void onHold(ServerPlayer player, HoseEndpointManager manager, HoseEndpoint ep, NozzleInputController.PlayerNozzleInput state) {
      if (!state.suppressHoldUntilRelease) {
         ep.location = NozzleLocationState.HELD;
         ep.operatorId = player.getUUID();
         state.holding = true;
         if (state.continuous) {
            if (ep.mode.allowsFlow() && ep.valve != NozzleValveState.OPEN) {
               ep.valve = NozzleValveState.OPEN;
               manager.setDirty();
            }
         } else {
            if (ep.mode.allowsFlow() && ep.valve != NozzleValveState.OPEN) {
               ep.valve = NozzleValveState.OPEN;
               manager.setDirty();
            }
         }
      }
   }

   private static void onRelease(ServerPlayer player, HoseEndpointManager manager, HoseEndpoint ep, NozzleInputController.PlayerNozzleInput state) {
      state.holding = false;
      state.suppressHoldUntilRelease = false;
      if (!state.continuous) {
         if (ep.valve == NozzleValveState.OPEN) {
            ep.closeValve();
            manager.setDirty();
         }
      }
   }

   public static boolean wantsWater(UUID playerId) {
      NozzleInputController.PlayerNozzleInput state = INPUT.get(playerId);
      return state != null && (state.continuous || state.holding);
   }

   public static boolean isContinuous(UUID playerId) {
      NozzleInputController.PlayerNozzleInput state = INPUT.get(playerId);
      return state != null && state.continuous;
   }

   private static final class PlayerNozzleInput {
      long lastClickTick = -999L;
      boolean continuous;
      boolean holding;
      boolean suppressHoldUntilRelease;
   }
}
