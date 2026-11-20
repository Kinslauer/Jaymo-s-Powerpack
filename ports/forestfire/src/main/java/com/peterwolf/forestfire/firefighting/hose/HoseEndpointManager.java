package com.peterwolf.forestfire.firefighting.hose;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.peterwolf.forestfire.ForestFireMod;
import com.peterwolf.forestfire.block.ModBlocks;
import com.peterwolf.forestfire.config.ForestFireConfig;
import com.peterwolf.forestfire.firefighting.nozzle.GroundNozzleBlockEntity;
import com.peterwolf.forestfire.firefighting.nozzle.ModDataComponents;
import com.peterwolf.forestfire.firefighting.nozzle.NozzleLocationState;
import com.peterwolf.forestfire.firefighting.nozzle.NozzleMode;
import com.peterwolf.forestfire.firefighting.nozzle.NozzleValveState;
import com.peterwolf.forestfire.firefighting.pump.PortablePumpBlockEntity;
import com.peterwolf.forestfire.firefighting.water.NozzleWaterSimulation;
import com.peterwolf.forestfire.item.ModItems;
import com.peterwolf.forestfire.network.ModNetworking;
import com.peterwolf.forestfire.network.NozzleHudPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.util.Prediction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class HoseEndpointManager extends SavedData {
   private static final Codec<HoseEndpointManager> CODEC = RecordCodecBuilder.create(
      instance -> instance.group(HoseEndpoint.CODEC.listOf().optionalFieldOf("endpoints", List.of()).forGetter(m -> new ArrayList<>(m.endpoints.values())))
         .apply(instance, HoseEndpointManager::new)
   );
   public static final SavedDataType<HoseEndpointManager> TYPE = new SavedDataType(
      ForestFireMod.id("hose_endpoints"), HoseEndpointManager::new, CODEC, DataFixTypes.LEVEL
   );
   private static final Map<ServerLevel, HoseEndpointManager> CACHE = new HashMap<>();
   private final Map<UUID, HoseEndpoint> endpoints = new HashMap<>();

   public HoseEndpointManager() {
   }

   public HoseEndpointManager(List<HoseEndpoint> list) {
      for (HoseEndpoint ep : list) {
         this.endpoints.put(ep.id, ep);
      }
   }

   public static HoseEndpointManager get(ServerLevel level) {
      return CACHE.computeIfAbsent(level, l -> (HoseEndpointManager)l.getDataStorage().computeIfAbsent(TYPE));
   }

   public static void invalidate(ServerLevel level) {
      CACHE.remove(level);
   }

   public static void clearAll() {
      CACHE.clear();
   }

   public Optional<HoseEndpoint> get(UUID id) {
      return Optional.ofNullable(this.endpoints.get(id));
   }

   public void putEndpoint(HoseEndpoint ep) {
      this.endpoints.put(ep.id, ep);
      this.setDirty();
   }

   public void removeEndpoint(UUID id) {
      if (this.endpoints.remove(id) != null) {
         this.setDirty();
      }
   }

   public void purgePump(ServerLevel level, BlockPos pumpPos, @Nullable ServerPlayer preferredPlayer) {
      List<UUID> doomed = new ArrayList<>();

      for (HoseEndpoint ep : this.endpoints.values()) {
         if (ep.pumpPos != null && ep.pumpPos.equals(pumpPos)) {
            doomed.add(ep.id);
         } else if (ep.anchorPos.equals(pumpPos)) {
            doomed.add(ep.id);
         }
      }

      for (UUID id : doomed) {
         HoseEndpoint epx = this.endpoints.get(id);
         if (epx != null) {
            epx.closeValve();
            epx.connected = false;
            if (epx.location != NozzleLocationState.GROUND && !level.getBlockState(epx.endpointPos).is(ModBlocks.GROUND_NOZZLE)) {
               if (epx.operatorId != null) {
                  ServerPlayer op = preferredPlayer != null && preferredPlayer.getUUID().equals(epx.operatorId)
                     ? preferredPlayer
                     : level.getServer().getPlayerList().getPlayer(epx.operatorId);
                  if (op != null) {
                     for (int i = 0; i < op.getInventory().getContainerSize(); i++) {
                        ItemStack stack = op.getInventory().getItem(i);
                        if (stack.is(ModItems.FIRE_HOSE_NOZZLE)) {
                           String sid = (String)stack.get(ModDataComponents.NOZZLE_ENDPOINT_ID);
                           if (id.toString().equals(sid)) {
                              stack.remove(ModDataComponents.NOZZLE_ENDPOINT_ID);
                           }
                        }
                     }
                  }
               }
            } else {
               if (level.getBlockState(epx.endpointPos).is(ModBlocks.GROUND_NOZZLE)) {
                  level.removeBlock(epx.endpointPos, false);
               }

               ItemStack free = new ItemStack(ModItems.FIRE_HOSE_NOZZLE);
               level.addFreshEntity(new ItemEntity(level, epx.endpointPos.getX() + 0.5, epx.endpointPos.getY() + 0.5, epx.endpointPos.getZ() + 0.5, free));
            }

            this.endpoints.remove(id);
         }
      }

      if (!doomed.isEmpty()) {
         this.setDirty();
      }
   }

   public Optional<HoseEndpoint> byOperator(UUID playerId) {
      for (HoseEndpoint ep : this.endpoints.values()) {
         if (ep.isHeldBy(playerId)) {
            return Optional.of(ep);
         }
      }

      return Optional.empty();
   }

   public Optional<HoseEndpoint> byGroundPos(BlockPos pos) {
      long key = pos.asLong();

      for (HoseEndpoint ep : this.endpoints.values()) {
         if (ep.location == NozzleLocationState.GROUND && ep.endpointPos.asLong() == key) {
            return Optional.of(ep);
         }
      }

      return Optional.empty();
   }

   public HoseEndpoint createGround(ServerLevel level, BlockPos groundPos, BlockPos pumpPos, float yaw, float pitch, NozzleMode mode) {
      HoseEndpoint ep = new HoseEndpoint(UUID.randomUUID(), pumpPos, groundPos);
      ep.bindPump(pumpPos);
      ep.location = NozzleLocationState.GROUND;
      ep.mode = mode == null ? NozzleMode.STRAIGHT_STREAM : mode;
      ep.valve = NozzleValveState.CLOSED;
      ep.yaw = yaw;
      ep.pitch = pitch;
      this.refreshMetrics(level, ep);
      this.endpoints.put(ep.id, ep);
      this.setDirty();
      return ep;
   }

   public boolean pickup(ServerLevel level, ServerPlayer player, HoseEndpoint ep) {
      if (ep.location == NozzleLocationState.HELD && ep.operatorId != null && !ep.operatorId.equals(player.getUUID())) {
         player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_in_use"));
         return false;
      } else {
         if (ep.location == NozzleLocationState.GROUND) {
            BlockState state = level.getBlockState(ep.endpointPos);
            if (state.is(ModBlocks.GROUND_NOZZLE)) {
               level.removeBlock(ep.endpointPos, false);
            }
         }

         ep.closeValve();
         ep.location = NozzleLocationState.HELD;
         ep.operatorId = player.getUUID();
         ep.endpointPos = player.blockPosition();
         HoseConnectionManager.get(level)
            .onNozzleMoved(level, ep.id, HosePathGenerator.playerHandEndpoint(player), HosePointType.PLAYER_ENDPOINT, player.blockPosition());
         this.refreshMetrics(level, ep);
         ItemStack stack = connectedNozzleStack(ep);
         ItemStack main = player.getMainHandItem();
         if (main.isEmpty()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
         } else if (main.is(ModItems.FIRE_HOSE_NOZZLE)) {
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
         } else {
            if (!player.getInventory().add(main.copy())) {
               player.drop(main.copy(), false, Prediction.SERVER_ONLY);
            }

            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
         }

         stripDuplicateEndpoints(player, ep.id, true);
         this.setDirty();
         player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_picked_up"));
         return true;
      }
   }

   public boolean placeFromHand(ServerLevel level, ServerPlayer player, HoseEndpoint ep, BlockPos placePos, float yaw, float pitch) {
      if (!ep.isHeldBy(player.getUUID())) {
         return false;
      } else {
         ep.closeValve();
         ep.location = NozzleLocationState.GROUND;
         ep.operatorId = null;
         ep.endpointPos = placePos.immutable();
         ep.yaw = yaw;
         ep.pitch = pitch;
         Vec3 tip = new Vec3(placePos.getX() + 0.5, placePos.getY() + 0.25, placePos.getZ() + 0.5);
         HoseConnectionManager.get(level).onNozzleMoved(level, ep.id, tip, HosePointType.NOZZLE_ENDPOINT, placePos);
         this.refreshMetrics(level, ep);
         level.setBlock(placePos, ModBlocks.GROUND_NOZZLE.defaultBlockState(), 3);
         if (level.getBlockEntity(placePos) instanceof GroundNozzleBlockEntity ground) {
            ground.setEndpointId(ep.id);
            ground.setYaw(yaw);
         }

         stripDuplicateEndpoints(player, ep.id, false);
         this.setDirty();
         player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_placed"));
         return true;
      }
   }

   @Nullable
   public HoseEndpoint connectAndPlace(ServerLevel level, ServerPlayer player, BlockPos placePos, BlockPos hoseAnchor, float yaw, float pitch, NozzleMode mode) {
      HoseEndpoint ep = this.createGround(level, placePos, hoseAnchor, yaw, pitch, mode);
      level.setBlock(placePos, ModBlocks.GROUND_NOZZLE.defaultBlockState(), 3);
      if (level.getBlockEntity(placePos) instanceof GroundNozzleBlockEntity ground) {
         ground.setEndpointId(ep.id);
         ground.setYaw(yaw);
      }

      ItemStack hand = player.getMainHandItem();
      if (hand.is(ModItems.FIRE_HOSE_NOZZLE)) {
         hand.shrink(1);
      }

      return ep;
   }

   public boolean disconnect(ServerLevel level, ServerPlayer player, HoseEndpoint ep) {
      if (ep.valve == NozzleValveState.OPEN) {
         player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_close_first"));
         return false;
      } else {
         HoseConnectionManager.get(level).disconnectByNozzleEndpoint(level, player, ep.id);
         ep.connected = false;
         ep.location = NozzleLocationState.DISCONNECTED;
         ep.closeValve();
         UUID id = ep.id;
         if (ep.operatorId != null && ep.operatorId.equals(player.getUUID())) {
            stripDuplicateEndpoints(player, id, false);
            ItemStack free = new ItemStack(ModItems.FIRE_HOSE_NOZZLE);
            if (!player.getInventory().add(free)) {
               player.drop(free, false, Prediction.SERVER_ONLY);
            }
         } else if (ep.location == NozzleLocationState.GROUND || level.getBlockState(ep.endpointPos).is(ModBlocks.GROUND_NOZZLE)) {
            level.removeBlock(ep.endpointPos, false);
            ItemStack free = new ItemStack(ModItems.FIRE_HOSE_NOZZLE);
            ItemEntity entity = new ItemEntity(level, ep.endpointPos.getX() + 0.5, ep.endpointPos.getY() + 0.5, ep.endpointPos.getZ() + 0.5, free);
            level.addFreshEntity(entity);
         }

         this.endpoints.remove(id);
         this.setDirty();
         player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_disconnected"));
         return true;
      }
   }

   public void releaseOperator(ServerLevel level, UUID playerId, boolean placeOnGround) {
      this.byOperator(playerId).ifPresent(ep -> {
         ep.closeValve();
         if (placeOnGround) {
            BlockPos pos = ep.endpointPos;
            if (!level.getBlockState(pos).canBeReplaced() && !level.getBlockState(pos).isAir()) {
               pos = pos.above();
            }

            if (!level.getBlockState(pos).isAir() && !level.getBlockState(pos).canBeReplaced()) {
               ep.location = NozzleLocationState.DISCONNECTED;
               ep.operatorId = null;
               ep.connected = false;
               ItemStack free = new ItemStack(ModItems.FIRE_HOSE_NOZZLE);
               level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, free));
               this.endpoints.remove(ep.id);
            } else {
               ep.location = NozzleLocationState.GROUND;
               ep.operatorId = null;
               ep.endpointPos = pos.immutable();
               level.setBlock(pos, ModBlocks.GROUND_NOZZLE.defaultBlockState(), 3);
               if (level.getBlockEntity(pos) instanceof GroundNozzleBlockEntity ground) {
                  ground.setEndpointId(ep.id);
                  ground.setYaw(ep.yaw);
               }
            }
         } else {
            ep.operatorId = null;
            ep.location = NozzleLocationState.GROUND;
         }

         this.setDirty();
      });
   }

   public void tick(ServerLevel level) {
      if (!this.endpoints.isEmpty()) {
         Iterator<Entry<UUID, HoseEndpoint>> it = this.endpoints.entrySet().iterator();

         while (it.hasNext()) {
            HoseEndpoint ep = it.next().getValue();
            if (ep.connected) {
               HoseConnection autoLink = HoseConnectionManager.get(level).findAttackByEndpoint(ep.id);
               if (autoLink != null && autoLink.connected) {
                  ep.bindPump(autoLink.sourcePos);
               } else if (ep.pumpPos != null && level.isLoaded(ep.pumpPos) && isPump(level, ep.pumpPos)) {
                  ep.anchorPos = ep.pumpPos;
               } else if (ep.pumpPos != null && level.isLoaded(ep.pumpPos) && !isPump(level, ep.pumpPos)) {
                  ep.connected = false;
                  ep.closeValve();
                  ForestFireMod.LOGGER.debug("Nozzle {} lost assigned pump {}", ep.id, ep.pumpPos);
               } else if (!level.isLoaded(ep.anchorPos) || !isHoseAnchor(level, ep.anchorPos)) {
                  BlockPos found = findNearbyPump(level, ep.endpointPos, 8);
                  if (found != null) {
                     ep.bindPump(found);
                  } else {
                     ep.connected = false;
                     ep.closeValve();
                     ForestFireMod.LOGGER.debug("Nozzle {} lost hose/pump assignment", ep.id);
                  }
               }

               if (ep.location == NozzleLocationState.HELD && ep.operatorId != null) {
                  ServerPlayer player = level.getServer().getPlayerList().getPlayer(ep.operatorId);
                  if (player == null || player.level() != level) {
                     this.releaseOperator(level, ep.operatorId, true);
                  } else if (!playerHoldsEndpoint(player, ep.id)) {
                     ep.missingHoldTicks++;
                     if (ep.missingHoldTicks >= 40) {
                        this.releaseOperator(level, player.getUUID(), true);
                     }
                  } else {
                     ep.missingHoldTicks = 0;
                     ep.endpointPos = player.blockPosition();
                     ep.yaw = player.getYRot();
                     ep.pitch = player.getXRot();
                     this.refreshMetrics(level, ep);
                     this.applyHoseConstraint(level, player, ep);
                     if (ep.valve == NozzleValveState.OPEN && ep.mode.allowsFlow()) {
                        this.tickSpray(level, player, ep);
                     }

                     if (level.getGameTime() % 5L == 0L) {
                        this.syncHud(player, ep);
                     }
                  }
               } else if (ep.location == NozzleLocationState.GROUND) {
                  ep.closeValve();
                  ep.missingHoldTicks = 0;
                  this.refreshMetrics(level, ep);
               }
            }
         }

         if (level.getGameTime() % 40L == 0L) {
            this.setDirty();
         }
      }
   }

   private void tickSpray(ServerLevel level, ServerPlayer player, HoseEndpoint ep) {
      PortablePumpBlockEntity pump = HoseNetwork.findPumpForEndpoint(level, ep.id);
      if (pump == null && ep.pumpPos != null && level.getBlockEntity(ep.pumpPos) instanceof PortablePumpBlockEntity p) {
         pump = p;
      }

      if (pump == null && ep.anchorPos != null && isPump(level, ep.anchorPos) && level.getBlockEntity(ep.anchorPos) instanceof PortablePumpBlockEntity p) {
         pump = p;
      }

      if (pump == null) {
         ep.pressure01 = 0.0F;
         if (level.getGameTime() % 20L == 0L) {
            player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_no_pressure"));
         }
      } else if (!pump.canSupplyNozzle()) {
         ep.pressure01 = Math.max(0.0F, pump.getPressure());
         if (level.getGameTime() % 20L == 0L) {
            player.sendOverlayMessage(Component.literal("Pump: " + pump.getPumpState().name() + " — check fuel / water intake"));
         }
      } else {
         float demand = 0.12F + ep.mode.pressureDemand * 0.18F + ep.mode.flowDemand * 0.08F;
         float lengthPenalty = 1.0F - Math.min(0.4F, ep.hoseLengthFromPump * ForestFireConfig.get().pressureLossPerSegment * 0.8F);
         float supplied = pump.consumeForNozzle(demand) * lengthPenalty;
         ep.pressure01 = Mth.clamp(Math.max(pump.getPressure(), supplied / Math.max(0.05F, demand)), 0.0F, 1.0F);
         if (ep.pressure01 < 0.06F) {
            if (level.getGameTime() % 20L == 0L) {
               player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_low_pressure"));
            }
         } else {
            if (ep.mode == NozzleMode.STRAIGHT_STREAM && ep.pressure01 > 0.5F) {
               Vec3 look = player.getLookAngle();
               player.setDeltaMovement(player.getDeltaMovement().add(look.scale(-0.004 * ep.pressure01)));
               player.needsSync = true;
            }

            NozzleWaterSimulation.spray(level, player, ep.mode, ep.pressure01);
         }
      }
   }

   private void applyHoseConstraint(ServerLevel level, ServerPlayer player, HoseEndpoint ep) {
      HoseConnection auto = HoseConnectionManager.get(level).findAttackByEndpoint(ep.id);
      double dx;
      double dz;
      double dist;
      int max;
      if (auto != null) {
         Vec3 hand = HosePathGenerator.playerHandEndpoint(player);
         auto.updateDynamicEndpoint(hand, HosePointType.PLAYER_ENDPOINT);
         auto.refreshTension();
         ep.tension = auto.tension;
         ep.remainingDistance = (int)Math.max(0.0, Math.floor(auto.remainingSlack));
         ep.maxReach = auto.deployedLength;
         ep.hoseLengthFromPump = (int)Math.ceil(auto.currentPathLength);
         dx = player.getX() - (auto.sourcePos.getX() + 0.5);
         dz = player.getZ() - (auto.sourcePos.getZ() + 0.5);
         dist = Math.sqrt(dx * dx + dz * dz);
         max = Math.max(1, auto.deployedLength);
         if (auto.currentPathLength > auto.deployedLength) {
            ep.tension = HoseTension.MAXIMUM;
            ep.remainingDistance = 0;
         }
      } else {
         dx = player.getX() - (ep.anchorPos.getX() + 0.5);
         dz = player.getZ() - (ep.anchorPos.getZ() + 0.5);
         dist = Math.sqrt(dx * dx + dz * dz);
         max = Math.max(1, ep.maxReach);
         ep.remainingDistance = (int)Math.max(0.0, Math.floor(max - dist));
         ep.tension = HoseTension.fromRemaining(ep.remainingDistance, max);
      }

      if (ep.tension == HoseTension.TIGHT) {
         Vec3 motion = player.getDeltaMovement();
         Vec3 away = new Vec3(dx, 0.0, dz).normalize();
         double outward = motion.x * away.x + motion.z * away.z;
         if (outward > 0.0) {
            player.setDeltaMovement(motion.x - away.x * outward * 0.55, motion.y, motion.z - away.z * outward * 0.55);
            player.needsSync = true;
         }

         if (level.getGameTime() % 25L == 0L) {
            player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.hose_tight"));
         }
      } else if (ep.tension == HoseTension.MAXIMUM) {
         Vec3 motionx = player.getDeltaMovement();
         Vec3 awayx = dist < 1.0E-4 ? Vec3.ZERO : new Vec3(dx, 0.0, dz).normalize();
         double outwardx = motionx.x * awayx.x + motionx.z * awayx.z;
         if (outwardx > 0.0) {
            player.setDeltaMovement(motionx.x - awayx.x * outwardx, motionx.y, motionx.z - awayx.z * outwardx);
         }

         if (dist > max) {
            double excess = dist - max;
            double pull = Math.min(0.28, excess * 0.12);
            player.setDeltaMovement(player.getDeltaMovement().add(awayx.scale(-pull)));
         }

         player.needsSync = true;
         if (level.getGameTime() % 15L == 0L) {
            player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.hose_maximum"));
         }
      }
   }

   public int countOpenNozzlesNear(BlockPos pumpPos) {
      return this.countOpenNozzlesOnPump(null, pumpPos);
   }

   public int countOpenNozzlesOnPump(@Nullable ServerLevel level, BlockPos pumpPos) {
      int n = 0;

      for (HoseEndpoint ep : this.endpoints.values()) {
         if (ep.connected && ep.valve == NozzleValveState.OPEN && ep.mode.allowsFlow()) {
            boolean assigned = ep.pumpPos != null && ep.pumpPos.equals(pumpPos);
            if (!assigned && level != null) {
               HoseConnection c = HoseConnectionManager.get(level).findAttackByEndpoint(ep.id);
               assigned = c != null && c.connected && c.sourcePos.equals(pumpPos);
            }

            if (!assigned && ep.anchorPos.equals(pumpPos)) {
               assigned = true;
            }

            if (assigned) {
               n++;
            }
         }
      }

      return Math.max(0, n);
   }

   public void refreshMetrics(ServerLevel level, HoseEndpoint ep) {
      HoseConnection auto = HoseConnectionManager.get(level).findAttackByEndpoint(ep.id);
      if (auto != null && auto.connected) {
         ep.bindPump(auto.sourcePos);
         ep.hoseLengthFromPump = (int)Math.ceil(auto.currentPathLength);
         ep.maxReach = Math.max(4, auto.deployedLength);
         ep.tension = auto.tension;
         ep.remainingDistance = (int)Math.max(0.0, Math.floor(auto.remainingSlack));
         if (level.getBlockEntity(auto.sourcePos) instanceof PortablePumpBlockEntity pump) {
            ep.pressure01 = Mth.clamp(pump.getPressure(), 0.0F, 1.0F);
         } else {
            ep.pressure01 = 0.0F;
         }
      } else {
         ep.hoseLengthFromPump = 0;
         ep.maxReach = Math.max(4, ForestFireConfig.get().nozzleFreeHoseBlocks);
         ep.pressure01 = 0.0F;
         if (ep.pumpPos != null && level.getBlockEntity(ep.pumpPos) instanceof PortablePumpBlockEntity pump) {
            ep.pressure01 = Mth.clamp(pump.getPressure() * 0.1F, 0.0F, 0.15F);
         }
      }
   }

   private void syncHud(ServerPlayer player, HoseEndpoint ep) {
      ModNetworking.sendNozzleHud(
         player, new NozzleHudPayload(ep.mode.label, ep.pressure01, ep.valve == NozzleValveState.OPEN, ep.tension.name(), ep.remainingDistance, ep.connected)
      );
   }

   public static ItemStack connectedNozzleStack(HoseEndpoint ep) {
      ItemStack stack = new ItemStack(ModItems.FIRE_HOSE_NOZZLE);
      stack.set(ModDataComponents.NOZZLE_ENDPOINT_ID, ep.id.toString());
      stack.set(ModDataComponents.NOZZLE_MODE, ep.mode.ordinal());
      return stack;
   }

   public static boolean playerHoldsEndpoint(ServerPlayer player, UUID endpointId) {
      String id = endpointId.toString();
      ItemStack main = player.getMainHandItem();
      if (main.is(ModItems.FIRE_HOSE_NOZZLE) && id.equals(main.getOrDefault(ModDataComponents.NOZZLE_ENDPOINT_ID, ""))) {
         return true;
      } else {
         ItemStack off = player.getOffhandItem();
         if (off.is(ModItems.FIRE_HOSE_NOZZLE) && id.equals(off.getOrDefault(ModDataComponents.NOZZLE_ENDPOINT_ID, ""))) {
            return true;
         } else {
            for (int i = 0; i < 9; i++) {
               ItemStack stack = player.getInventory().getItem(i);
               if (stack.is(ModItems.FIRE_HOSE_NOZZLE) && id.equals(stack.getOrDefault(ModDataComponents.NOZZLE_ENDPOINT_ID, ""))) {
                  return true;
               }
            }

            return false;
         }
      }
   }

   private static void stripDuplicateEndpoints(ServerPlayer player, UUID endpointId, boolean keepOneInMain) {
      String id = endpointId.toString();
      boolean kept = false;

      for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
         ItemStack stack = player.getInventory().getItem(i);
         if (stack.is(ModItems.FIRE_HOSE_NOZZLE)) {
            String stackId = (String)stack.get(ModDataComponents.NOZZLE_ENDPOINT_ID);
            if (id.equals(stackId)) {
               if (keepOneInMain && !kept && i == player.getInventory().getSelectedSlot()) {
                  kept = true;
               } else {
                  player.getInventory().setItem(i, ItemStack.EMPTY);
               }
            }
         }
      }

      ItemStack off = player.getOffhandItem();
      if (off.is(ModItems.FIRE_HOSE_NOZZLE)
         && id.equals(off.get(ModDataComponents.NOZZLE_ENDPOINT_ID))
         && (!keepOneInMain || !player.getMainHandItem().isEmpty())) {
         player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
      }
   }

   public static boolean isHoseAnchor(ServerLevel level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      return state.is(ModBlocks.HOSE_SPLITTER) || state.is(ModBlocks.PORTABLE_PUMP) || state.is(ModBlocks.PORTABLE_SPRINKLER);
   }

   public static boolean isPump(ServerLevel level, BlockPos pos) {
      return level.getBlockState(pos).is(ModBlocks.PORTABLE_PUMP);
   }

   @Nullable
   public static BlockPos findNearbyPump(ServerLevel level, BlockPos origin, int radius) {
      MutableBlockPos cursor = new MutableBlockPos();
      BlockPos best = null;
      int bestDist = Integer.MAX_VALUE;
      int r = Math.min(radius, 48);

      for (int dx = -r; dx <= r; dx++) {
         for (int dy = -4; dy <= 4; dy++) {
            for (int dz = -r; dz <= r; dz++) {
               cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
               if (level.isLoaded(cursor) && isPump(level, cursor)) {
                  int d = dx * dx + dy * dy + dz * dz;
                  if (d < bestDist) {
                     bestDist = d;
                     best = cursor.immutable();
                  }
               }
            }
         }
      }

      return best;
   }

   @Nullable
   public static BlockPos findNearbyHose(ServerLevel level, BlockPos origin, int radius) {
      BlockPos pump = findNearbyPump(level, origin, radius);
      if (pump != null) {
         return pump;
      } else {
         MutableBlockPos cursor = new MutableBlockPos();
         BlockPos best = null;
         int bestDist = Integer.MAX_VALUE;

         for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
               for (int dz = -radius; dz <= radius; dz++) {
                  cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                  if (level.isLoaded(cursor) && isHoseAnchor(level, cursor)) {
                     int d = dx * dx + dy * dy + dz * dz;
                     if (d < bestDist) {
                        bestDist = d;
                        best = cursor.immutable();
                     }
                  }
               }
            }
         }

         return best;
      }
   }
}
