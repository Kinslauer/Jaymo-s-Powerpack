package com.peterwolf.forestfire.sound;

import com.peterwolf.forestfire.ForestFireMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class ModSounds {
   public static final SoundEvent PUMP_START = register("pump_start");
   public static final SoundEvent PUMP_IDLE = register("pump_idle");
   public static final SoundEvent PUMP_LOAD = register("pump_load");
   public static final SoundEvent PUMP_FAIL = register("pump_fail");
   public static final SoundEvent PUMP_LOW_WATER = register("pump_low_water");
   public static final SoundEvent INCIDENT_ALERT = register("incident_alert");

   private ModSounds() {
   }

   public static void register() {
      ForestFireMod.LOGGER.info("Registered forest fire sounds");
   }

   private static SoundEvent register(String name) {
      Identifier id = ForestFireMod.id(name);
      return (SoundEvent)Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
   }
}
