package com.peterwolf.forestfire.firefighting.hose;

public final class HoseTensionCalculator {
   private HoseTensionCalculator() {
   }

   public static HoseTension fromSlack(double remainingSlack, int deployedLength) {
      if (deployedLength <= 0) {
         return HoseTension.MAXIMUM;
      } else {
         double ratio = remainingSlack / deployedLength;
         if (remainingSlack <= 0.15) {
            return HoseTension.MAXIMUM;
         } else if (ratio < 0.08 || remainingSlack < 1.0) {
            return HoseTension.TIGHT;
         } else {
            return ratio < 0.25 ? HoseTension.NORMAL : HoseTension.SLACK;
         }
      }
   }

   public static double pathLength(HosePath path) {
      return path == null ? 0.0 : path.pathLength();
   }
}
