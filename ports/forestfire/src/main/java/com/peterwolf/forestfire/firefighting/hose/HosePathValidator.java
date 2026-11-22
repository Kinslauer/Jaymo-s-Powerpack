package com.peterwolf.forestfire.firefighting.hose;

import com.peterwolf.forestfire.block.ModBlocks;
import com.peterwolf.forestfire.config.ForestFireConfig;
import com.peterwolf.forestfire.firefighting.intake.IntakeSourceResolver;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class HosePathValidator {
   private static final double ENDPOINT_GRACE = 1.35;
   private static final float BLOCK_FRACTION_LIMIT = 0.55F;

   private HosePathValidator() {
   }

   public static HosePathValidator.Result validate(
      ServerLevel level, HosePath path, int availableHose, int maxLength, boolean requireWaterEndpoint, BlockPos waterOrNull
   ) {
      if (path != null && !path.isEmpty() && path.points().size() >= 2) {
         int required = Math.max(1, path.blockLength());
         if (required > maxLength) {
            return HosePathValidator.Result.fail(HosePathValidator.Failure.TOO_FAR, "Cannot connect hose: exceeds maximum length (" + maxLength + ")");
         } else if (availableHose < required && ForestFireConfig.get().automaticHoseConsumesItems) {
            return HosePathValidator.Result.fail(
               HosePathValidator.Failure.INSUFFICIENT_HOSE, "Cannot connect hose: insufficient hose length (need " + required + ", have " + availableHose + ")"
            );
         } else {
            List<HoseControlPoint> pts = path.points();
            int hardBlocks = 0;
            int checks = 0;

            for (int i = 1; i < pts.size(); i++) {
               HosePathValidator.SegmentScan scan = scanSegment(level, pts.get(i - 1).position(), pts.get(i).position());
               checks += scan.samples();
               hardBlocks += scan.blockedSamples();
            }

            if (checks > 0 && (float)hardBlocks / checks > 0.55F && hardBlocks >= 3) {
               return HosePathValidator.Result.fail(HosePathValidator.Failure.PATH_BLOCKED, "Cannot connect hose: path blocked by solid wall");
            } else {
               if (requireWaterEndpoint) {
                  if (waterOrNull == null || !level.isLoaded(waterOrNull)) {
                     return HosePathValidator.Result.fail(HosePathValidator.Failure.INVALID_ENDPOINT, "Cannot connect intake: invalid water source");
                  }

                  if (!IntakeSourceResolver.isValidSource(level, waterOrNull)) {
                     return HosePathValidator.Result.fail(HosePathValidator.Failure.INVALID_ENDPOINT, "Cannot connect intake: invalid water source");
                  }

                  double verticalLift = Math.max(0.0, path.start().y - path.end().y);
                  if (verticalLift > ForestFireConfig.get().maximumIntakeVerticalLift + 1.0) {
                     return HosePathValidator.Result.fail(
                        HosePathValidator.Failure.INVALID_ENDPOINT,
                        "Cannot connect intake: vertical lift too high ("
                           + (int)Math.ceil(verticalLift)
                           + " > "
                           + ForestFireConfig.get().maximumIntakeVerticalLift
                           + ")"
                     );
                  }

                  if (required > ForestFireConfig.get().maximumIntakeHoseLength) {
                     return HosePathValidator.Result.fail(
                        HosePathValidator.Failure.TOO_FAR, "Cannot connect intake: hose too long (max " + ForestFireConfig.get().maximumIntakeHoseLength + ")"
                     );
                  }
               }

               return HosePathValidator.Result.success(path, required);
            }
         }
      } else {
         return HosePathValidator.Result.fail(HosePathValidator.Failure.NO_PATH, "Cannot connect hose: no path");
      }
   }

   private static HosePathValidator.SegmentScan scanSegment(ServerLevel level, Vec3 a, Vec3 b) {
      double dist = a.distanceTo(b);
      if (dist < 0.05) {
         return new HosePathValidator.SegmentScan(0, 0);
      } else {
         int steps = Math.max(2, (int)Math.ceil(dist * 1.5));
         int samples = 0;
         int blocked = 0;

         for (int i = 1; i < steps; i++) {
            double t = (double)i / steps;
            double x = a.x + (b.x - a.x) * t;
            double y = a.y + (b.y - a.y) * t;
            double z = a.z + (b.z - a.z) * t;
            Vec3 p = new Vec3(x, y, z);
            if (!(p.distanceTo(a) < 1.35) && !(p.distanceTo(b) < 1.35)) {
               BlockPos pos = BlockPos.containing(x, y + 0.15, z);
               if (level.isLoaded(pos)) {
                  samples++;
                  if (isWallObstacle(level, pos)) {
                     blocked++;
                  }
               }
            }
         }

         return new HosePathValidator.SegmentScan(samples, blocked);
      }
   }

   public static boolean isWallObstacle(ServerLevel level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      return isNonBlocking(state) ? false : state.isSolid() && !isThinOrPassableShape(state);
   }

   private static boolean isNonBlocking(BlockState state) {
      if (!state.isAir() && !state.canBeReplaced()) {
         if (!state.getFluidState().isEmpty() || state.getFluidState().is(FluidTags.WATER)) {
            return true;
         } else if (state.is(ModBlocks.PORTABLE_PUMP)
            || state.is(ModBlocks.HOSE_SPLITTER)
            || state.is(ModBlocks.INTAKE_STRAINER)
            || state.is(ModBlocks.GROUND_NOZZLE)
            || state.is(ModBlocks.PORTABLE_SPRINKLER)
            || state.is(ModBlocks.WATER_TANK_SMALL)
            || state.is(ModBlocks.WATER_TANK_MEDIUM)
            || state.is(ModBlocks.WATER_TANK_LARGE)) {
            return true;
         } else {
            return !state.is(BlockTags.LEAVES)
                  && !state.is(BlockTags.FLOWERS)
                  && !state.is(BlockTags.REPLACEABLE)
                  && !state.is(BlockTags.FENCES)
                  && !state.is(BlockTags.FENCE_GATES)
                  && !state.is(BlockTags.WALLS)
                  && !state.is(BlockTags.DOORS)
                  && !state.is(BlockTags.TRAPDOORS)
                  && !state.is(BlockTags.SLABS)
                  && !state.is(BlockTags.STAIRS)
               ? !state.isSolid()
               : true;
         }
      } else {
         return true;
      }
   }

   private static boolean isThinOrPassableShape(BlockState state) {
      try {
         return !state.isCollisionShapeFullBlock(null, BlockPos.ZERO);
      } catch (Exception var2) {
         return false;
      }
   }

   public static enum Failure {
      NONE,
      TOO_FAR,
      INSUFFICIENT_HOSE,
      PATH_BLOCKED,
      INVALID_ENDPOINT,
      PORT_OCCUPIED,
      ALREADY_CONNECTED,
      CROSS_DIMENSION,
      NO_PATH;
   }

   public record Result(boolean ok, HosePathValidator.Failure failure, String message, HosePath path, int requiredLength) {
      public static HosePathValidator.Result fail(HosePathValidator.Failure f, String message) {
         return new HosePathValidator.Result(false, f, message, HosePath.empty(), 0);
      }

      public static HosePathValidator.Result success(HosePath path, int required) {
         return new HosePathValidator.Result(true, HosePathValidator.Failure.NONE, "ok", path, required);
      }
   }

   private record SegmentScan(int samples, int blockedSamples) {
   }
}
