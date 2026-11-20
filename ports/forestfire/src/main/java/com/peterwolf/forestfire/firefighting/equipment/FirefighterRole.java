package com.peterwolf.forestfire.firefighting.equipment;

import java.util.Locale;

public enum FirefighterRole {
   INCIDENT_COMMANDER("Incident Commander"),
   PUMP_OPERATOR("Pump Operator"),
   HOSE_OPERATOR("Hose Operator"),
   CREW_MEMBER("Crew Member"),
   SCOUT("Scout"),
   AIR_SUPPORT("Air Support Operator");

   public final String displayName;

   private FirefighterRole(String displayName) {
      this.displayName = displayName;
   }

   public static FirefighterRole byId(String raw) {
      try {
         return valueOf(raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
      } catch (IllegalArgumentException var2) {
         return CREW_MEMBER;
      }
   }
}
