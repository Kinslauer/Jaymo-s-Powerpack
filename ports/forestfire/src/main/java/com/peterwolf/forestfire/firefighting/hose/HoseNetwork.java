package com.peterwolf.forestfire.firefighting.hose;

import com.peterwolf.forestfire.block.ModBlocks;
import com.peterwolf.forestfire.config.ForestFireConfig;
import com.peterwolf.forestfire.firefighting.nozzle.NozzleValveState;
import com.peterwolf.forestfire.firefighting.pump.PortablePumpBlockEntity;
import com.peterwolf.forestfire.firefighting.water.PortableWaterTankBlockEntity;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

public final class HoseNetwork {
   private HoseNetwork() {
   }

   public static HoseNetwork.PumpLinks scan(ServerLevel level, BlockPos pumpPos) {
      HoseNetwork.PumpLinks links = new HoseNetwork.PumpLinks();
      links.pumpPos = pumpPos;
      HoseConnectionManager.IntakeScanResult autoIntake = HoseConnectionManager.get(level).scanIntake(level, pumpPos);
      if (autoIntake.hasIntake()) {
         links.hasIntake = true;
         links.availableWater = links.availableWater + autoIntake.availableWater();
         links.hasStrainer = autoIntake.hasStrainer();
         links.tankPos = autoIntake.tankOrSourcePos();
      }

      for (Direction dir : Direction.values()) {
         BlockPos next = pumpPos.relative(dir);
         if (level.isLoaded(next)) {
            if (isWaterSource(level, next)) {
               links.hasIntake = true;
               links.availableWater += 10;
            }

            if (level.getBlockEntity(next) instanceof PortableWaterTankBlockEntity tank && tank.getWater() > 0) {
               links.hasIntake = true;
               links.availableWater = links.availableWater + tank.getWater();
               links.tankPos = next;
            }

            if (level.getBlockState(next).is(ModBlocks.INTAKE_STRAINER) || level.getBlockState(next).is(Blocks.IRON_BARS)) {
               links.hasStrainer = true;
            }

            if (level.getBlockState(next).is(ModBlocks.HOSE_SPLITTER) || level.getBlockState(next).is(ModBlocks.PORTABLE_SPRINKLER)) {
               links.activeNozzles++;
            }
         }
      }

      int local = PortablePumpBlockEntity.countNearbyWater(level, pumpPos);
      if (local > 0) {
         links.hasIntake = true;
         links.availableWater += local * 5;
      }

      if (!links.hasStrainer && autoIntake.tankOrSourcePos() != null) {
         BlockPos src = autoIntake.tankOrSourcePos();
         if (level.getBlockState(src).is(ModBlocks.INTAKE_STRAINER)) {
            links.hasStrainer = true;
         } else {
            for (Direction dirx : Direction.values()) {
               if (level.getBlockState(src.relative(dirx)).is(ModBlocks.INTAKE_STRAINER)) {
                  links.hasStrainer = true;
                  break;
               }
            }
         }
      }

      links.outputLength = HoseConnectionManager.get(level).maxAttackPathLength(pumpPos);
      int autoLines = HoseConnectionManager.get(level).countAttackLines(pumpPos);
      links.activeNozzles = Math.max(1, Math.max(links.activeNozzles, autoLines));
      return links;
   }

   @Nullable
   public static PortablePumpBlockEntity findPumpForEndpoint(ServerLevel level, UUID endpointId) {
      HoseConnection c = HoseConnectionManager.get(level).findAttackByEndpoint(endpointId);
      if (c != null && c.connected) {
         return level.getBlockEntity(c.sourcePos) instanceof PortablePumpBlockEntity pump ? pump : null;
      } else {
         return null;
      }
   }

   @Nullable
   public static PortablePumpBlockEntity findAnyPump(ServerLevel level, BlockPos pos) {
      if (level.getBlockEntity(pos) instanceof PortablePumpBlockEntity pump) {
         return pump;
      } else {
         for (HoseConnection c : HoseConnectionManager.get(level).all()) {
            if (c.connected
               && c.type == HoseConnectionType.ATTACK
               && (c.sourcePos.equals(pos) || c.targetPos.equals(pos))
               && level.getBlockEntity(c.sourcePos) instanceof PortablePumpBlockEntity pump) {
               return pump;
            }
         }

         return null;
      }
   }

   @Nullable
   public static PortablePumpBlockEntity findSupplyingPump(ServerLevel level, BlockPos pos) {
      PortablePumpBlockEntity any = findAnyPump(level, pos);
      return any != null && any.canSupplyNozzle() ? any : null;
   }

   public static boolean isWaterSource(ServerLevel level, BlockPos pos) {
      return level.getFluidState(pos).is(Fluids.WATER) || level.getBlockState(pos).is(Blocks.WATER) || level.getBlockState(pos).is(Blocks.WATER_CAULDRON);
   }

   public static int distanceToPump(ServerLevel level, BlockPos pos) {
      if (level.getBlockState(pos).is(ModBlocks.PORTABLE_PUMP)) {
         return 0;
      } else {
         int best = Integer.MAX_VALUE;

         for (HoseConnection c : HoseConnectionManager.get(level).all()) {
            if (c.connected
               && c.type == HoseConnectionType.ATTACK
               && (c.sourcePos.equals(pos) || c.targetPos.equals(pos) || c.sourcePos.closerThan(pos, 1.5) || c.targetPos.closerThan(pos, 1.5))) {
               best = Math.min(best, (int)Math.ceil(c.currentPathLength));
            }
         }

         return best == Integer.MAX_VALUE ? ForestFireConfig.get().maxHoseLength : best;
      }
   }

   public static int countConnectedNozzles(ServerLevel level, BlockPos pumpPos) {
      return countOpenNozzlesForPump(level, pumpPos);
   }

   public static int countOpenNozzlesForPump(ServerLevel level, BlockPos pumpPos) {
      int n = 0;

      for (HoseConnection c : HoseConnectionManager.get(level).all()) {
         if (c.connected && c.type == HoseConnectionType.ATTACK && c.sourcePos.equals(pumpPos) && c.nozzleEndpointId != null) {
            HoseEndpoint ep = HoseEndpointManager.get(level).get(c.nozzleEndpointId).orElse(null);
            if (ep != null && ep.connected && ep.valve == NozzleValveState.OPEN && ep.mode.allowsFlow()) {
               n++;
            }
         }
      }

      return n;
   }

   public static final class PumpLinks {
      public boolean hasIntake;
      public int availableWater;
      public int outputLength;
      public int activeNozzles;
      public boolean hasStrainer;
      @Nullable
      public BlockPos tankPos;
      @Nullable
      public BlockPos pumpPos;
   }
}
