package com.peterwolf.forestfire.fire.incident;

public enum IncidentStatus {
   UNREPORTED,
   REPORTED,
   RESPONDING,
   ACTIVE,
   CONTAINED,
   CONTROLLED,
   OVERHAUL,
   CLOSED,
   ESCAPED;

   public boolean isOpen() {
      return this != CLOSED;
   }

   public boolean allowsFireGrowth() {
      return this == UNREPORTED || this == REPORTED || this == RESPONDING || this == ACTIVE || this == ESCAPED;
   }
}
