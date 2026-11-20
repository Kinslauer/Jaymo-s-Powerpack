package com.peterwolf.forestfire.firefighting.intake;

import com.peterwolf.forestfire.config.ForestFireConfig;
import com.peterwolf.forestfire.firefighting.hose.HosePath;

public final class IntakeEfficiencyCalculator {
   private IntakeEfficiencyCalculator() {
   }

   public static float calculate(HosePath path, double verticalLift, boolean hasStrainer) {
      ForestFireConfig.Data cfg = ForestFireConfig.get();
      float efficiency = 1.0F;
      int length = path.blockLength();
      if (cfg.intakeLengthPressureLoss) {
         efficiency -= length * 0.012F;
      }

      double liftRatio = verticalLift / Math.max(1.0, (double)cfg.maximumIntakeVerticalLift);
      efficiency -= (float)(liftRatio * 0.35);
      int bends = Math.max(0, path.points().size() - 2);
      efficiency -= bends * 0.015F;
      if (!hasStrainer) {
         efficiency -= 0.05F;
      }

      return Math.max(0.15F, Math.min(1.0F, efficiency));
   }

   public static double verticalLift(double pumpY, double waterY) {
      return Math.max(0.0, pumpY - waterY);
   }
}
