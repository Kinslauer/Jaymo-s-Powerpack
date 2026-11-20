package com.peterwolf.forestfire.world;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;

public final class FirebreakTracker {
   private final Map<Long, FirebreakTracker.Entry> breaks = new ConcurrentHashMap<>();

   public void mark(BlockPos pos, FirebreakTracker.Type type, int durationTicks) {
      this.breaks.put(pos.asLong(), new FirebreakTracker.Entry(type, durationTicks));
   }

   public void markWet(BlockPos pos, int durationTicks) {
      this.mark(pos, FirebreakTracker.Type.WET_LINE, durationTicks);
   }

   public boolean isFirebreak(BlockPos pos) {
      return this.breaks.containsKey(pos.asLong());
   }

   public void tick() {
      this.tick(1);
   }

   public void tick(int elapsedGameTicks) {
      int elapsed = Math.max(1, elapsedGameTicks);
      Iterator<Map.Entry<Long, FirebreakTracker.Entry>> it = this.breaks.entrySet().iterator();

      while (it.hasNext()) {
         Map.Entry<Long, FirebreakTracker.Entry> entry = it.next();
         FirebreakTracker.Entry value = entry.getValue();
         if (value.durationTicks >= 0) {
            value.durationTicks -= elapsed;
            if (value.durationTicks <= 0) {
               it.remove();
            }
         }
      }
   }

   public boolean blocksSpread(BlockPos from, BlockPos to, float windSpeed, float flameHeat) {
      FirebreakTracker.Entry entry = this.breaks.get(to.asLong());
      if (entry == null) {
         entry = this.breaks.get(from.asLong());
      }

      if (entry == null) {
         return false;
      } else {
         float base = switch (entry.type) {
            case CLEARED_GRASS -> 0.55F;
            case BARE_DIRT -> 0.75F;
            case TRENCH -> 0.85F;
            case GRAVEL, ROAD -> 0.9F;
            case WET_LINE -> 0.7F;
         };
         float jumpChance = 1.0F - base + windSpeed * 0.25F + flameHeat * 0.15F;
         return entry.type == FirebreakTracker.Type.WET_LINE && flameHeat > 0.8F && windSpeed > 0.6F ? false : jumpChance < 0.55F;
      }
   }

   private static final class Entry {
      final FirebreakTracker.Type type;
      int durationTicks;

      Entry(FirebreakTracker.Type type, int durationTicks) {
         this.type = type;
         this.durationTicks = durationTicks;
      }
   }

   public static enum Type {
      CLEARED_GRASS,
      BARE_DIRT,
      TRENCH,
      GRAVEL,
      ROAD,
      WET_LINE;
   }
}
