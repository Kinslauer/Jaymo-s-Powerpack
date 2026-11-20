package com.peterwolf.forestfire.firefighting.hose;

public enum HoseTension {
   SLACK,
   NORMAL,
   TIGHT,
   MAXIMUM;

   public static HoseTension fromRemaining(int remaining, int maxReach) {
      if (maxReach <= 0) {
         return MAXIMUM;
      } else {
         float ratio = (float)remaining / maxReach;
         if (ratio > 0.45F) {
            return SLACK;
         } else if (ratio > 0.2F) {
            return NORMAL;
         } else {
            return ratio > 0.02F ? TIGHT : MAXIMUM;
         }
      }
   }
}
