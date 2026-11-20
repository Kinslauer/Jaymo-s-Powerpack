package com.peterwolf.forestfire.firefighting.intake;

import com.peterwolf.forestfire.block.ModBlocks;
import com.peterwolf.forestfire.firefighting.water.PortableWaterTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

public final class IntakeSourceResolver {
   private IntakeSourceResolver() {
   }

   public static boolean isValidSource(ServerLevel level, BlockPos pos) {
      return resolve(level, pos).valid();
   }

   public static IntakeSourceResolver.SourceInfo resolve(ServerLevel level, BlockPos pos) {
      if (!level.isLoaded(pos)) {
         return new IntakeSourceResolver.SourceInfo(IntakeSourceResolver.SourceKind.NONE, pos, 0, "Unavailable");
      } else {
         BlockState state = level.getBlockState(pos);
         FluidState fluid = level.getFluidState(pos);
         if (state.is(ModBlocks.INTAKE_STRAINER)) {
            return !fluid.is(FluidTags.WATER) && !hasAdjacentWater(level, pos)
               ? new IntakeSourceResolver.SourceInfo(IntakeSourceResolver.SourceKind.NONE, pos, 0, "Strainer not in water")
               : new IntakeSourceResolver.SourceInfo(IntakeSourceResolver.SourceKind.STRAINER_IN_WATER, pos, 50, "Strainer intake");
         } else if (state.is(Blocks.WATER_CAULDRON)) {
            return new IntakeSourceResolver.SourceInfo(IntakeSourceResolver.SourceKind.WATER_CAULDRON, pos, 5, "Water cauldron");
         } else if (!fluid.is(FluidTags.WATER) && !state.is(Blocks.WATER)) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof PortableWaterTankBlockEntity tank) {
               int water = tank.getWater();
               return water > 0
                  ? new IntakeSourceResolver.SourceInfo(IntakeSourceResolver.SourceKind.PORTABLE_TANK, pos, water, "Portable water tank")
                  : new IntakeSourceResolver.SourceInfo(IntakeSourceResolver.SourceKind.NONE, pos, 0, "Tank empty");
            } else {
               return (state.is(ModBlocks.WATER_TANK_SMALL) || state.is(ModBlocks.WATER_TANK_MEDIUM) || state.is(ModBlocks.WATER_TANK_LARGE))
                     && be instanceof PortableWaterTankBlockEntity tank2
                     && tank2.getWater() > 0
                  ? new IntakeSourceResolver.SourceInfo(IntakeSourceResolver.SourceKind.PORTABLE_TANK, pos, tank2.getWater(), "Portable water tank")
                  : new IntakeSourceResolver.SourceInfo(IntakeSourceResolver.SourceKind.NONE, pos, 0, "Invalid");
            }
         } else {
            String label = "Water source";
            int nearby = countNearbyWater(level, pos, 3);
            if (nearby > 40) {
               label = "Lake / ocean";
            } else if (nearby > 12) {
               label = "River / pond";
            }

            return new IntakeSourceResolver.SourceInfo(IntakeSourceResolver.SourceKind.WATER_BLOCK, pos, 10 + nearby, label);
         }
      }
   }

   public static boolean stillValid(ServerLevel level, BlockPos pos) {
      IntakeSourceResolver.SourceInfo info = resolve(level, pos);
      return info.valid();
   }

   @Nullable
   public static BlockPos findNearestSource(ServerLevel level, BlockPos origin, int radius) {
      BlockPos best = null;
      int bestDist = Integer.MAX_VALUE;
      MutableBlockPos cursor = new MutableBlockPos();

      for (int dx = -radius; dx <= radius; dx++) {
         for (int dy = -3; dy <= 2; dy++) {
            for (int dz = -radius; dz <= radius; dz++) {
               cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
               if (level.isLoaded(cursor) && isValidSource(level, cursor)) {
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

   private static boolean hasAdjacentWater(ServerLevel level, BlockPos pos) {
      for (Direction dir : Direction.values()) {
         BlockPos n = pos.relative(dir);
         if (level.getFluidState(n).is(FluidTags.WATER) || level.getBlockState(n).is(Blocks.WATER)) {
            return true;
         }
      }

      return false;
   }

   private static int countNearbyWater(ServerLevel level, BlockPos origin, int r) {
      int n = 0;
      MutableBlockPos c = new MutableBlockPos();

      for (int dx = -r; dx <= r; dx++) {
         for (int dy = -2; dy <= 1; dy++) {
            for (int dz = -r; dz <= r; dz++) {
               c.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
               if (level.isLoaded(c) && (level.getFluidState(c).is(FluidTags.WATER) || level.getBlockState(c).is(Blocks.WATER))) {
                  n++;
               }
            }
         }
      }

      return n;
   }

   public record SourceInfo(IntakeSourceResolver.SourceKind kind, BlockPos pos, int availableWater, String label) {
      public boolean valid() {
         return this.kind != IntakeSourceResolver.SourceKind.NONE && this.availableWater > 0;
      }
   }

   public static enum SourceKind {
      NONE,
      WATER_BLOCK,
      WATER_CAULDRON,
      PORTABLE_TANK,
      STRAINER_IN_WATER;
   }
}
