package com.peterwolf.forestfire.firefighting.hose;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.peterwolf.forestfire.ForestFireMod;
import com.peterwolf.forestfire.block.ModBlocks;
import com.peterwolf.forestfire.config.ForestFireConfig;
import com.peterwolf.forestfire.firefighting.intake.IntakeEfficiencyCalculator;
import com.peterwolf.forestfire.firefighting.intake.IntakeSourceResolver;
import com.peterwolf.forestfire.firefighting.nozzle.FireHoseNozzleItem;
import com.peterwolf.forestfire.firefighting.nozzle.GroundNozzleBlockEntity;
import com.peterwolf.forestfire.firefighting.nozzle.ModDataComponents;
import com.peterwolf.forestfire.firefighting.nozzle.NozzleLocationState;
import com.peterwolf.forestfire.firefighting.nozzle.NozzleMode;
import com.peterwolf.forestfire.firefighting.nozzle.NozzleValveState;
import com.peterwolf.forestfire.item.ModItems;
import com.peterwolf.forestfire.network.HoseSyncPayload;
import com.peterwolf.forestfire.network.ModNetworking;
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
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class HoseConnectionManager extends SavedData {
   private static final Codec<HoseConnectionManager> CODEC = RecordCodecBuilder.create(
      instance -> instance.group(
            HoseConnection.CODEC.listOf().optionalFieldOf("connections", List.of()).forGetter(m -> new ArrayList<>(m.connections.values()))
         )
         .apply(instance, HoseConnectionManager::new)
   );
   public static final SavedDataType<HoseConnectionManager> TYPE = new SavedDataType(
      ForestFireMod.id("hose_connections"), HoseConnectionManager::new, CODEC, DataFixTypes.LEVEL
   );
   private static final Map<ServerLevel, HoseConnectionManager> CACHE = new HashMap<>();
   private final Map<UUID, HoseConnection> connections = new HashMap<>();
   private boolean broadcastDirty;

   public HoseConnectionManager() {
   }

   public HoseConnectionManager(List<HoseConnection> list) {
      for (HoseConnection c : list) {
         c.pressurized = false;
         this.connections.put(c.connectionId, c);
      }
   }

   public static HoseConnectionManager get(ServerLevel level) {
      return CACHE.computeIfAbsent(level, l -> (HoseConnectionManager)l.getDataStorage().computeIfAbsent(TYPE));
   }

   public static void invalidate(ServerLevel level) {
      CACHE.remove(level);
   }

   public static void clearAll() {
      CACHE.clear();
   }

   public Optional<HoseConnection> get(UUID id) {
      return Optional.ofNullable(this.connections.get(id));
   }

   public List<HoseConnection> all() {
      return new ArrayList<>(this.connections.values());
   }

   public List<HoseConnection> byPump(BlockPos pumpPos) {
      List<HoseConnection> list = new ArrayList<>();
      long key = pumpPos.asLong();

      for (HoseConnection c : this.connections.values()) {
         if (c.connected && c.sourcePos.asLong() == key) {
            list.add(c);
         }
      }

      return list;
   }

   @Nullable
   public HoseConnection findIntake(BlockPos pumpPos) {
      for (HoseConnection c : this.connections.values()) {
         if (c.connected && c.type == HoseConnectionType.INTAKE && c.sourcePos.equals(pumpPos)) {
            return c;
         }
      }

      return null;
   }

   @Nullable
   public HoseConnection findAttackByEndpoint(UUID endpointId) {
      for (HoseConnection c : this.connections.values()) {
         if (c.connected && c.type == HoseConnectionType.ATTACK && endpointId.equals(c.nozzleEndpointId)) {
            return c;
         }
      }

      return null;
   }

   @Nullable
   public HoseConnection findAttackOnPump(BlockPos pumpPos) {
      for (HoseConnection c : this.connections.values()) {
         if (c.connected && c.type == HoseConnectionType.ATTACK && c.sourcePos.equals(pumpPos)) {
            return c;
         }
      }

      return null;
   }

   public boolean createIntake(ServerLevel level, ServerPlayer player, BlockPos pumpPos, BlockPos waterPos) {
      if (this.findIntake(pumpPos) != null) {
         player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.intake_port_occupied"));
         return false;
      } else {
         IntakeSourceResolver.SourceInfo source = IntakeSourceResolver.resolve(level, waterPos);
         if (!source.valid()) {
            player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.intake_invalid"));
            return false;
         } else {
            Vec3 start = HosePathGenerator.pumpPort(level, pumpPos, true);
            Vec3 end = HosePathGenerator.surfacePoint(level, waterPos);
            end = new Vec3(waterPos.getX() + 0.5, waterPos.getY() + 0.35, waterPos.getZ() + 0.5);
            HosePath path = HosePathGenerator.generate(level, start, end, HosePointType.PUMP_PORT, HosePointType.WATER_ENDPOINT);
            int available = HoseInventoryService.countAvailable(player, true);
            int maxLen = Math.min(ForestFireConfig.get().maxHoseLength, ForestFireConfig.get().maximumIntakeHoseLength);
            HosePathValidator.Result validation = HosePathValidator.validate(level, path, available, maxLen, true, waterPos);
            if (!validation.ok()) {
               player.sendOverlayMessage(Component.literal(validation.message()));
               return false;
            } else {
               int required = validation.requiredLength();
               if (!HoseInventoryService.consume(player, required, true)) {
                  player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.hose_insufficient"));
                  return false;
               } else {
                  HoseConnection conn = new HoseConnection(UUID.randomUUID(), HoseConnectionType.INTAKE, pumpPos, waterPos);
                  conn.setPath(path);
                  conn.deployedLength = required;
                  conn.networkId = uuidFromPos(pumpPos);
                  double lift = IntakeEfficiencyCalculator.verticalLift(start.y, end.y);
                  boolean strainer = level.getBlockState(waterPos).is(ModBlocks.INTAKE_STRAINER) || hasNearbyStrainer(level, waterPos);
                  conn.intakeEfficiency = IntakeEfficiencyCalculator.calculate(path, lift, strainer);
                  this.connections.put(conn.connectionId, conn);
                  this.setDirty();
                  this.broadcastDirty = true;
                  player.sendSystemMessage(Component.translatable("message.peterwolfs_forestfire.intake_connected"));
                  player.sendSystemMessage(Component.literal("Source: " + source.label()));
                  player.sendSystemMessage(Component.literal("Length: " + required + " blocks"));
                  player.sendSystemMessage(Component.literal("Vertical lift: " + (int)Math.ceil(lift) + " blocks"));
                  player.sendSystemMessage(Component.literal(String.format("Estimated intake efficiency: %.0f%%", conn.intakeEfficiency * 100.0F)));
                  int remaining = HoseInventoryService.countAvailable(player, true);
                  if (remaining < 268435455) {
                     player.sendSystemMessage(Component.literal("Remaining hose: " + remaining + " blocks"));
                  }

                  this.syncNearby(level, conn);
                  return true;
               }
            }
         }
      }
   }

   public boolean createAttackHeld(ServerLevel level, ServerPlayer player, BlockPos pumpPos, NozzleMode mode) {
      if (this.findAttackOnPump(pumpPos) != null && ForestFireConfig.get().singleAttackLinePerPump) {
      }

      Vec3 start = HosePathGenerator.pumpPort(level, pumpPos, false);
      Vec3 end = HosePathGenerator.playerHandEndpoint(player);
      HosePath path = HosePathGenerator.generate(level, start, end, HosePointType.PUMP_PORT, HosePointType.PLAYER_ENDPOINT);
      int available = HoseInventoryService.countAvailable(player, false);
      int maxLen = ForestFireConfig.get().maxHoseLength;
      HosePathValidator.Result validation = HosePathValidator.validate(level, path, available, maxLen, false, null);
      if (!validation.ok()) {
         player.sendOverlayMessage(Component.literal(validation.message()));
         return false;
      } else {
         int required = validation.requiredLength();
         int deploy = Math.min(maxLen, Math.max(required, required + ForestFireConfig.get().nozzleFreeHoseBlocks));
         if (!HoseInventoryService.consume(player, required, false)) {
            player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.hose_insufficient"));
            return false;
         } else {
            HoseEndpointManager endpoints = HoseEndpointManager.get(level);
            HoseEndpoint ep = new HoseEndpoint(UUID.randomUUID(), pumpPos, player.blockPosition());
            ep.bindPump(pumpPos);
            ep.location = NozzleLocationState.HELD;
            ep.operatorId = player.getUUID();
            ep.mode = mode == null ? NozzleMode.STRAIGHT_STREAM : mode;
            ep.valve = NozzleValveState.CLOSED;
            ep.hoseLengthFromPump = required;
            ep.maxReach = Math.max(4, deploy);
            ep.yaw = player.getYRot();
            ep.pitch = player.getXRot();
            ep.connected = true;
            endpoints.putEndpoint(ep);
            HoseConnection conn = new HoseConnection(UUID.randomUUID(), HoseConnectionType.ATTACK, pumpPos, player.blockPosition());
            conn.setPath(path);
            conn.deployedLength = deploy;
            conn.nozzleEndpointId = ep.id;
            conn.targetEntityId = player.getUUID();
            conn.networkId = uuidFromPos(pumpPos);
            this.connections.put(conn.connectionId, conn);
            this.setDirty();
            this.broadcastDirty = true;
            ItemStack stack = HoseEndpointManager.connectedNozzleStack(ep);
            ItemStack main = player.getMainHandItem();
            if (main.is(ModItems.FIRE_HOSE_NOZZLE)) {
               player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            } else {
               if (!main.isEmpty() && !player.getInventory().add(main.copy())) {
                  player.drop(main.copy(), false, Prediction.SERVER_ONLY);
               }

               player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            }

            player.sendSystemMessage(Component.translatable("message.peterwolfs_forestfire.attack_hose_connected"));
            player.sendSystemMessage(Component.literal("Pump: " + pumpPos.toShortString()));
            player.sendSystemMessage(Component.literal("Nozzle: " + ep.mode.label));
            player.sendSystemMessage(Component.literal("Deployed length: " + deploy + " blocks"));
            int rem = HoseInventoryService.countAvailable(player, false);
            if (rem < 268435455) {
               player.sendSystemMessage(Component.literal("Remaining hose: " + rem + " blocks"));
            }

            player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_connected"));
            this.syncNearby(level, conn);
            return true;
         }
      }
   }

   public boolean createAttackGround(ServerLevel level, ServerPlayer player, BlockPos pumpPos, BlockPos placePos, float yaw, float pitch, NozzleMode mode) {
      Vec3 start = HosePathGenerator.pumpPort(level, pumpPos, false);
      Vec3 end = new Vec3(placePos.getX() + 0.5, placePos.getY() + 0.25, placePos.getZ() + 0.5);
      HosePath path = HosePathGenerator.generate(level, start, end, HosePointType.PUMP_PORT, HosePointType.NOZZLE_ENDPOINT);
      int available = HoseInventoryService.countAvailable(player, false);
      int maxLen = ForestFireConfig.get().maxHoseLength;
      HosePathValidator.Result validation = HosePathValidator.validate(level, path, available, maxLen, false, null);
      if (!validation.ok()) {
         player.sendOverlayMessage(Component.literal(validation.message()));
         return false;
      } else {
         int required = validation.requiredLength();
         int deploy = Math.min(maxLen, Math.max(required, required + 2));
         if (!HoseInventoryService.consume(player, required, false)) {
            player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.hose_insufficient"));
            return false;
         } else {
            HoseEndpointManager endpoints = HoseEndpointManager.get(level);
            HoseEndpoint ep = endpoints.createGround(level, placePos, pumpPos, yaw, pitch, mode);
            ep.bindPump(pumpPos);
            ep.hoseLengthFromPump = required;
            ep.maxReach = Math.max(4, deploy);
            ep.valve = NozzleValveState.CLOSED;
            level.setBlock(placePos, ModBlocks.GROUND_NOZZLE.defaultBlockState(), 3);
            if (level.getBlockEntity(placePos) instanceof GroundNozzleBlockEntity ground) {
               ground.setEndpointId(ep.id);
               ground.setYaw(yaw);
            }

            ItemStack hand = player.getMainHandItem();
            if (hand.is(ModItems.FIRE_HOSE_NOZZLE) && FireHoseNozzleItem.getEndpointId(hand) == null) {
               hand.shrink(1);
            }

            HoseConnection conn = new HoseConnection(UUID.randomUUID(), HoseConnectionType.ATTACK, pumpPos, placePos);
            conn.setPath(path);
            conn.deployedLength = deploy;
            conn.nozzleEndpointId = ep.id;
            conn.networkId = uuidFromPos(pumpPos);
            this.connections.put(conn.connectionId, conn);
            this.setDirty();
            this.broadcastDirty = true;
            player.sendSystemMessage(Component.translatable("message.peterwolfs_forestfire.attack_hose_connected"));
            player.sendSystemMessage(Component.literal("Deployed length: " + deploy + " blocks"));
            this.syncNearby(level, conn);
            return true;
         }
      }
   }

   public boolean createAttackToEndpoint(ServerLevel level, ServerPlayer player, BlockPos pumpPos, UUID endpointId, BlockPos nozzlePos) {
      HoseEndpointManager endpoints = HoseEndpointManager.get(level);
      HoseEndpoint ep = endpoints.get(endpointId).orElse(null);
      if (ep == null) {
         player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_orphaned"));
         return false;
      } else if (this.findAttackByEndpoint(endpointId) != null) {
         player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_already_connected"));
         return false;
      } else {
         Vec3 start = HosePathGenerator.pumpPort(level, pumpPos, false);
         Vec3 end = new Vec3(nozzlePos.getX() + 0.5, nozzlePos.getY() + 0.25, nozzlePos.getZ() + 0.5);
         HosePath path = HosePathGenerator.generate(level, start, end, HosePointType.PUMP_PORT, HosePointType.NOZZLE_ENDPOINT);
         int available = HoseInventoryService.countAvailable(player, false);
         HosePathValidator.Result validation = HosePathValidator.validate(level, path, available, ForestFireConfig.get().maxHoseLength, false, null);
         if (!validation.ok()) {
            player.sendOverlayMessage(Component.literal(validation.message()));
            return false;
         } else {
            int required = validation.requiredLength();
            if (!HoseInventoryService.consume(player, required, false)) {
               player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.hose_insufficient"));
               return false;
            } else {
               ep.bindPump(pumpPos);
               ep.connected = true;
               ep.hoseLengthFromPump = required;
               ep.maxReach = Math.max(4, required + ForestFireConfig.get().nozzleFreeHoseBlocks);
               endpoints.setDirty();
               HoseConnection conn = new HoseConnection(UUID.randomUUID(), HoseConnectionType.ATTACK, pumpPos, nozzlePos);
               conn.setPath(path);
               conn.deployedLength = required + ForestFireConfig.get().nozzleFreeHoseBlocks;
               conn.nozzleEndpointId = ep.id;
               conn.networkId = uuidFromPos(pumpPos);
               this.connections.put(conn.connectionId, conn);
               this.setDirty();
               this.broadcastDirty = true;
               player.sendSystemMessage(Component.translatable("message.peterwolfs_forestfire.attack_hose_connected"));
               this.syncNearby(level, conn);
               return true;
            }
         }
      }
   }

   public boolean createSupplyToSplitter(ServerLevel level, ServerPlayer player, BlockPos pumpPos, BlockPos splitterPos) {
      for (HoseConnection c : this.connections.values()) {
         if (c.connected && c.type == HoseConnectionType.SUPPLY && c.sourcePos.equals(pumpPos) && c.targetPos.equals(splitterPos)) {
            player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.hose_already_exists"));
            return false;
         }
      }

      Vec3 start = HosePathGenerator.pumpPort(level, pumpPos, false);
      Vec3 end = HosePathGenerator.surfacePoint(level, splitterPos);
      HosePath path = HosePathGenerator.generate(level, start, end, HosePointType.PUMP_PORT, HosePointType.SPLITTER_PORT);
      int available = HoseInventoryService.countAvailable(player, false);
      HosePathValidator.Result validation = HosePathValidator.validate(level, path, available, ForestFireConfig.get().maxHoseLength, false, null);
      if (!validation.ok()) {
         player.sendOverlayMessage(Component.literal(validation.message()));
         return false;
      } else {
         int required = validation.requiredLength();
         if (!HoseInventoryService.consume(player, required, false)) {
            player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.hose_insufficient"));
            return false;
         } else {
            HoseConnection conn = new HoseConnection(UUID.randomUUID(), HoseConnectionType.SUPPLY, pumpPos, splitterPos);
            conn.setPath(path);
            conn.deployedLength = required;
            conn.networkId = uuidFromPos(pumpPos);
            this.connections.put(conn.connectionId, conn);
            this.setDirty();
            this.broadcastDirty = true;
            player.sendSystemMessage(Component.literal("Supply hose connected to splitter (" + required + " blocks)"));
            this.syncNearby(level, conn);
            return true;
         }
      }
   }

   public int disconnectAllAtPump(ServerLevel level, ServerPlayer player, BlockPos pumpPos) {
      int n = 0;
      Iterator<Entry<UUID, HoseConnection>> it = this.connections.entrySet().iterator();

      while (it.hasNext()) {
         HoseConnection c = it.next().getValue();
         if (c.sourcePos.equals(pumpPos) || c.targetPos.equals(pumpPos)) {
            this.retract(level, player, c);
            this.cleanupEndpointForConnection(level, player, c, false);
            this.syncRemove(level, c);
            it.remove();
            n++;
         }
      }

      if (n > 0) {
         this.setDirty();
         this.broadcastDirty = true;
         this.syncAllNearby(level, pumpPos);
      }

      return n;
   }

   public int clearAllOnPumpDestroyed(ServerLevel level, BlockPos pumpPos, @Nullable ServerPlayer player) {
      int n = 0;
      int totalHose = 0;
      List<HoseConnection> doomed = new ArrayList<>();

      for (HoseConnection c : this.connections.values()) {
         if (c.sourcePos.equals(pumpPos) || c.targetPos.equals(pumpPos)) {
            doomed.add(c);
         }
      }

      for (HoseConnection cx : this.connections.values()) {
         if (!doomed.contains(cx) && cx.type == HoseConnectionType.ATTACK && cx.nozzleEndpointId != null) {
            HoseEndpoint ep = HoseEndpointManager.get(level).get(cx.nozzleEndpointId).orElse(null);
            if (ep != null && ep.pumpPos != null && ep.pumpPos.equals(pumpPos)) {
               doomed.add(cx);
            }
         }
      }

      for (HoseConnection cxx : doomed) {
         int returnAmt = ForestFireConfig.get().returnFullHoseLength ? cxx.deployedLength : (int)Math.ceil(cxx.currentPathLength);
         totalHose += Math.max(0, returnAmt);
         this.cleanupEndpointForConnection(level, player, cxx, true);
         cxx.connected = false;
         cxx.pressurized = false;
         this.syncRemove(level, cxx);
         this.connections.remove(cxx.connectionId);
         n++;
      }

      HoseEndpointManager.get(level).purgePump(level, pumpPos, player);
      if (totalHose > 0) {
         if (player != null) {
            HoseInventoryService.returnHose(player, totalHose, false);
         } else {
            HoseInventoryService.dropHoseAt(level, pumpPos, totalHose);
         }
      }

      if (n > 0) {
         this.setDirty();
         this.broadcastDirty = true;
         this.syncAllInDimension(level);
         ForestFireMod.LOGGER.debug("Cleared {} hose connection(s) after pump removed at {}", n, pumpPos);
      }

      return n;
   }

   private void cleanupEndpointForConnection(ServerLevel level, @Nullable ServerPlayer player, HoseConnection c, boolean orphanNozzle) {
      if (c.nozzleEndpointId != null) {
         HoseEndpointManager mgr = HoseEndpointManager.get(level);
         mgr.get(c.nozzleEndpointId).ifPresent(ep -> {
            ep.closeValve();
            ep.connected = false;
            if (orphanNozzle) {
               if (ep.location != NozzleLocationState.GROUND && !level.getBlockState(ep.endpointPos).is(ModBlocks.GROUND_NOZZLE)) {
                  if (ep.location == NozzleLocationState.HELD && ep.operatorId != null) {
                     ServerPlayer op = level.getServer().getPlayerList().getPlayer(ep.operatorId);
                     if (op != null) {
                        for (int i = 0; i < op.getInventory().getContainerSize(); i++) {
                           ItemStack stack = op.getInventory().getItem(i);
                           if (stack.is(ModItems.FIRE_HOSE_NOZZLE)) {
                              String id = (String)stack.get(ModDataComponents.NOZZLE_ENDPOINT_ID);
                              if (ep.id.toString().equals(id)) {
                                 stack.remove(ModDataComponents.NOZZLE_ENDPOINT_ID);
                              }
                           }
                        }
                     }
                  }
               } else {
                  if (level.getBlockState(ep.endpointPos).is(ModBlocks.GROUND_NOZZLE)) {
                     level.removeBlock(ep.endpointPos, false);
                  }

                  ItemStack free = new ItemStack(ModItems.FIRE_HOSE_NOZZLE);
                  level.addFreshEntity(new ItemEntity(level, ep.endpointPos.getX() + 0.5, ep.endpointPos.getY() + 0.5, ep.endpointPos.getZ() + 0.5, free));
               }

               mgr.removeEndpoint(ep.id);
            }
         });
      }
   }

   public boolean disconnectByNozzleEndpoint(ServerLevel level, ServerPlayer player, UUID endpointId) {
      HoseConnection c = this.findAttackByEndpoint(endpointId);
      if (c == null) {
         return false;
      } else {
         HoseEndpointManager.get(level).get(endpointId).ifPresent(ep -> {
            ep.closeValve();
            ep.connected = false;
         });
         this.retract(level, player, c);
         this.connections.remove(c.connectionId);
         this.setDirty();
         this.broadcastDirty = true;
         this.syncRemove(level, c);
         player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.hose_retracted"));
         return true;
      }
   }

   public boolean disconnectIntakeAt(ServerLevel level, ServerPlayer player, BlockPos pos) {
      Iterator<Entry<UUID, HoseConnection>> it = this.connections.entrySet().iterator();

      while (it.hasNext()) {
         HoseConnection c = it.next().getValue();
         if (c.type == HoseConnectionType.INTAKE && (c.targetPos.equals(pos) || c.sourcePos.equals(pos))) {
            this.retract(level, player, c);
            it.remove();
            this.setDirty();
            this.broadcastDirty = true;
            this.syncRemove(level, c);
            player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.hose_retracted"));
            return true;
         }
      }

      return false;
   }

   public boolean disconnectConnection(ServerLevel level, ServerPlayer player, UUID connectionId) {
      HoseConnection c = this.connections.remove(connectionId);
      if (c == null) {
         return false;
      } else {
         if (c.nozzleEndpointId != null) {
            HoseEndpointManager.get(level).get(c.nozzleEndpointId).ifPresent(ep -> ep.closeValve());
         }

         this.retract(level, player, c);
         this.setDirty();
         this.broadcastDirty = true;
         this.syncRemove(level, c);
         return true;
      }
   }

   private void retract(ServerLevel level, ServerPlayer player, HoseConnection c) {
      if (c.nozzleEndpointId != null) {
         HoseEndpointManager.get(level).get(c.nozzleEndpointId).ifPresent(ep -> ep.closeValve());
      }

      int returnAmt = ForestFireConfig.get().returnFullHoseLength ? c.deployedLength : (int)Math.ceil(c.currentPathLength);
      HoseInventoryService.returnHose(player, returnAmt, c.type == HoseConnectionType.INTAKE);
   }

   public boolean insertAnchorNear(ServerLevel level, ServerPlayer player, Vec3 anchor, double radius) {
      HoseConnection best = null;
      double bestDist = radius;

      for (HoseConnection c : this.connections.values()) {
         if (c.connected && !c.path.isEmpty()) {
            for (HoseControlPoint p : c.path.points()) {
               double d = p.position().distanceTo(anchor);
               if (d < bestDist) {
                  bestDist = d;
                  best = c;
               }
            }
         }
      }

      if (best == null) {
         return false;
      } else {
         List<HoseControlPoint> pts = new ArrayList<>(best.path.points());
         int insertAt = 1;
         double minSeg = Double.MAX_VALUE;

         for (int i = 0; i < pts.size() - 1; i++) {
            double d = distToSegment(anchor, pts.get(i).position(), pts.get(i + 1).position());
            if (d < minSeg) {
               minSeg = d;
               insertAt = i + 1;
            }
         }

         pts.add(insertAt, new HoseControlPoint(anchor, HosePointType.GROUND_ANCHOR));
         best.setPath(HosePath.of(pts));
         int extra = Math.max(0, best.path.blockLength() - best.deployedLength);
         if (extra > 0) {
            if (!HoseInventoryService.consume(player, extra, best.type == HoseConnectionType.INTAKE)) {
               pts.remove(insertAt);
               best.setPath(HosePath.of(pts));
               player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.hose_insufficient"));
               return false;
            }

            best.deployedLength += extra;
         }

         best.routeDirty = false;
         this.setDirty();
         this.broadcastDirty = true;
         this.syncNearby(level, best);
         return true;
      }
   }

   private static double distToSegment(Vec3 p, Vec3 a, Vec3 b) {
      Vec3 ab = b.subtract(a);
      double len2 = ab.lengthSqr();
      if (len2 < 1.0E-8) {
         return p.distanceTo(a);
      } else {
         double t = Math.max(0.0, Math.min(1.0, p.subtract(a).dot(ab) / len2));
         return p.distanceTo(a.add(ab.scale(t)));
      }
   }

   public void onNozzleMoved(ServerLevel level, UUID endpointId, Vec3 newEndpoint, HosePointType endType, BlockPos blockTarget) {
      HoseConnection c = this.findAttackByEndpoint(endpointId);
      if (c != null) {
         c.updateDynamicEndpoint(newEndpoint, endType);
         c.targetPos = blockTarget.immutable();
         if (c.currentPathLength > c.deployedLength + 0.5) {
            Vec3 start = HosePathGenerator.pumpPort(level, c.sourcePos, false);
            HosePath regen = HosePathGenerator.generate(level, start, newEndpoint, HosePointType.PUMP_PORT, endType);
            if (regen.pathLength() <= c.deployedLength + 1.0) {
               c.setPath(regen);
            }
         }

         c.refreshTension();
         this.setDirty();
         this.broadcastDirty = true;
         if (level.getGameTime() % 5L == 0L) {
            this.syncNearby(level, c);
         }
      }
   }

   public void tick(ServerLevel level) {
      if (!this.connections.isEmpty()) {
         boolean anyChange = false;

         for (HoseConnection c : this.connections.values()) {
            if (c.connected) {
               if (c.type == HoseConnectionType.INTAKE) {
                  if (!level.isLoaded(c.targetPos) || !IntakeSourceResolver.stillValid(level, c.targetPos)) {
                     c.connected = false;
                     c.pressurized = false;
                     c.intakeEfficiency = 0.0F;
                     anyChange = true;
                     if (level.getGameTime() % 40L == 0L) {
                        notifyNear(level, c.sourcePos, Component.translatable("message.peterwolfs_forestfire.intake_lost"));
                     }
                  } else if (c.connected && level.getGameTime() % 80L == 0L) {
                     double lift = IntakeEfficiencyCalculator.verticalLift(HosePathGenerator.pumpPort(level, c.sourcePos, true).y, c.targetPos.getY() + 0.35);
                     boolean strainer = level.getBlockState(c.targetPos).is(ModBlocks.INTAKE_STRAINER) || hasNearbyStrainer(level, c.targetPos);
                     c.intakeEfficiency = IntakeEfficiencyCalculator.calculate(c.path, lift, strainer);
                  }
               } else if (c.type == HoseConnectionType.ATTACK && c.nozzleEndpointId != null) {
                  HoseEndpoint ep = HoseEndpointManager.get(level).get(c.nozzleEndpointId).orElse(null);
                  if (ep == null || !ep.connected) {
                     continue;
                  }

                  if (ep.location == NozzleLocationState.HELD && ep.operatorId != null) {
                     ServerPlayer operator = level.getServer().getPlayerList().getPlayer(ep.operatorId);
                     if (operator != null && operator.level() == level) {
                        Vec3 hand = HosePathGenerator.playerHandEndpoint(operator);
                        Vec3 lastEnd = c.path.end();
                        if (lastEnd.distanceToSqr(hand) > 0.25) {
                           c.updateDynamicEndpoint(hand, HosePointType.PLAYER_ENDPOINT);
                           c.targetPos = operator.blockPosition();
                           ep.tension = c.tension;
                           ep.remainingDistance = (int)Math.max(0.0, Math.floor(c.remainingSlack));
                           ep.maxReach = c.deployedLength;
                           ep.hoseLengthFromPump = (int)Math.ceil(c.currentPathLength);
                           anyChange = true;
                        }
                     }
                  } else if (ep.location == NozzleLocationState.GROUND) {
                     Vec3 tip = new Vec3(ep.endpointPos.getX() + 0.5, ep.endpointPos.getY() + 0.25, ep.endpointPos.getZ() + 0.5);
                     if (c.path.end().distanceToSqr(tip) > 0.1) {
                        c.updateDynamicEndpoint(tip, HosePointType.NOZZLE_ENDPOINT);
                        c.targetPos = ep.endpointPos;
                        anyChange = true;
                     }
                  }

                  c.pressurized = ep.valve == NozzleValveState.OPEN;
               }

               if ((!level.isLoaded(c.sourcePos) || !level.getBlockState(c.sourcePos).is(ModBlocks.PORTABLE_PUMP))
                  && c.type != HoseConnectionType.SPLITTER_BRANCH
                  && level.isLoaded(c.sourcePos)) {
                  BlockState st = level.getBlockState(c.sourcePos);
                  if (!st.is(ModBlocks.PORTABLE_PUMP) && !st.is(ModBlocks.HOSE_SPLITTER)) {
                     c.connected = false;
                     anyChange = true;
                  }
               }
            }
         }

         if (anyChange) {
            this.setDirty();
            this.broadcastDirty = true;
         }

         if (this.broadcastDirty && level.getGameTime() % 10L == 0L) {
            this.syncAllInDimension(level);
            this.broadcastDirty = false;
         } else if (level.getGameTime() % 100L == 0L) {
            this.syncAllInDimension(level);
         }
      }
   }

   public HoseConnectionManager.IntakeScanResult scanIntake(ServerLevel level, BlockPos pumpPos) {
      HoseConnection intake = this.findIntake(pumpPos);
      if (intake == null || !intake.connected) {
         return HoseConnectionManager.IntakeScanResult.none();
      } else if (!IntakeSourceResolver.stillValid(level, intake.targetPos)) {
         return HoseConnectionManager.IntakeScanResult.none();
      } else {
         IntakeSourceResolver.SourceInfo info = IntakeSourceResolver.resolve(level, intake.targetPos);
         boolean strainer = level.getBlockState(intake.targetPos).is(ModBlocks.INTAKE_STRAINER) || hasNearbyStrainer(level, intake.targetPos);
         return new HoseConnectionManager.IntakeScanResult(
            true, info.availableWater(), strainer, intake.targetPos, intake.deployedLength, intake.intakeEfficiency
         );
      }
   }

   public int countAttackLines(BlockPos pumpPos) {
      int n = 0;

      for (HoseConnection c : this.connections.values()) {
         if (c.connected
            && c.sourcePos.equals(pumpPos)
            && (c.type == HoseConnectionType.ATTACK || c.type == HoseConnectionType.SUPPLY || c.type == HoseConnectionType.SPLITTER_BRANCH)) {
            n++;
         }
      }

      return n;
   }

   public int maxAttackPathLength(BlockPos pumpPos) {
      int max = 0;

      for (HoseConnection c : this.connections.values()) {
         if (c.connected && c.sourcePos.equals(pumpPos) && c.type == HoseConnectionType.ATTACK) {
            max = Math.max(max, (int)Math.ceil(c.currentPathLength));
         }
      }

      return max;
   }

   private void syncNearby(ServerLevel level, HoseConnection c) {
      HoseSyncPayload payload = HoseSyncPayload.fromConnection(c, false);

      for (ServerPlayer player : level.players()) {
         if (player.distanceToSqr(c.sourcePos.getX() + 0.5, c.sourcePos.getY() + 0.5, c.sourcePos.getZ() + 0.5) < 9216.0) {
            ModNetworking.sendHoseSync(player, payload);
         }
      }
   }

   private void syncRemove(ServerLevel level, HoseConnection c) {
      HoseSyncPayload payload = HoseSyncPayload.fromConnection(c, true);

      for (ServerPlayer player : level.players()) {
         if (player.distanceToSqr(c.sourcePos.getX() + 0.5, c.sourcePos.getY() + 0.5, c.sourcePos.getZ() + 0.5) < 9216.0) {
            ModNetworking.sendHoseSync(player, payload);
         }
      }
   }

   private void syncAllNearby(ServerLevel level, BlockPos around) {
      for (HoseConnection c : this.connections.values()) {
         if (c.sourcePos.closerThan(around, 64.0) || c.targetPos.closerThan(around, 64.0)) {
            this.syncNearby(level, c);
         }
      }
   }

   public void syncAllInDimension(ServerLevel level) {
      for (HoseConnection c : this.connections.values()) {
         if (c.connected) {
            this.syncNearby(level, c);
         }
      }
   }

   public void syncToPlayer(ServerPlayer player) {
      ServerLevel var3 = player.level();
      if (var3 instanceof ServerLevel) {
         for (HoseConnection c : this.connections.values()) {
            if (c.connected) {
               ModNetworking.sendHoseSync(player, HoseSyncPayload.fromConnection(c, false));
            }
         }
      }
   }

   private static void notifyNear(ServerLevel level, BlockPos pos, Component msg) {
      for (ServerPlayer player : level.players()) {
         if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 1024.0) {
            player.sendOverlayMessage(msg);
         }
      }
   }

   private static boolean hasNearbyStrainer(ServerLevel level, BlockPos pos) {
      for (Direction d : Direction.values()) {
         if (level.getBlockState(pos.relative(d)).is(ModBlocks.INTAKE_STRAINER)) {
            return true;
         }
      }

      return false;
   }

   private static UUID uuidFromPos(BlockPos pos) {
      return new UUID(pos.asLong(), 1213158213L);
   }

   public record IntakeScanResult(
      boolean hasIntake, int availableWater, boolean hasStrainer, @Nullable BlockPos tankOrSourcePos, int intakeLength, float efficiency
   ) {
      public static HoseConnectionManager.IntakeScanResult none() {
         return new HoseConnectionManager.IntakeScanResult(false, 0, false, null, 0, 0.0F);
      }
   }
}
