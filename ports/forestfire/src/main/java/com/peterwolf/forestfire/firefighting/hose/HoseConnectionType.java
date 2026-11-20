package com.peterwolf.forestfire.firefighting.hose;

public enum HoseConnectionType {
   INTAKE,
   ATTACK,
   SUPPLY,
   SPLITTER_BRANCH;

   public static HoseConnectionType byName(String name) {
      if (name != null && !name.isEmpty()) {
         try {
            return valueOf(name);
         } catch (IllegalArgumentException var2) {
            return ATTACK;
         }
      } else {
         return ATTACK;
      }
   }
}
