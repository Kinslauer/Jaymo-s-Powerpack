package com.peterwolf.forestfire.firefighting.water;

import com.peterwolf.forestfire.config.ForestFireConfig;

public enum TankSize {
   SMALL,
   MEDIUM,
   LARGE;

   public int capacity() {
      ForestFireConfig.Data cfg = ForestFireConfig.get();

      return switch (this) {
         case SMALL -> cfg.smallTankCapacity;
         case MEDIUM -> cfg.mediumTankCapacity;
         case LARGE -> cfg.largeTankCapacity;
      };
   }
}
