package com.jaymoskitchen.cooking;

import com.jaymoskitchen.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * A player's active hands-on cooking session at a station. Sealed so the server knows
 * exactly which method-specific state it's dealing with.
 */
public sealed interface HandsOnSession permits ChopState, StirState {

    UUID player();
    Dish dish();
    Dish.Method method();
    BlockPos stationPos();
    int startTick();

    /** Whether the session is still active (player hasn't walked away or abandoned). */
    default boolean isActive(ServerLevelAccessor level, int now) {
        return now - startTick() <= method().limitTicks();
    }

    /** Quality achieved when the session finishes. Computed by the server on finish. */
    Quality finishQuality();

    /** Server-side: advance the session one tick. Returns true if the session is done. */
    boolean tick(ServerLevelAccessor level);

    /** Server-side: apply player input from a {@link com.jaymoskitchen.net.payloads.HandsOnInputPayload}. */
    void applyInput(com.jaymoskitchen.net.payloads.HandsOnInputPayload input);

    /** The minimum ticks before this session can finish (anti-cheat). */
    default int minTicks() {
        return switch (method()) {
            case CHOP -> Minigames.CUTS * Minigames.MIN_TICKS_PER_CUT;
            case STIR -> Minigames.STIR_TICKS;
            default -> 20;
        };
    }

    /** Server-side wrapper to avoid importing ServerLevel into the sealed interface. */
    interface ServerLevelAccessor {
        boolean isClientSide();
        ServerPlayer getPlayer(UUID uuid);
        void sendChatMsg(net.minecraft.network.chat.Component msg);
    }

    /** Chopping state: vegetables slide under the knife, press when a mark is under the blade. */
    record ChopState(UUID player, Dish dish, BlockPos stationPos, int startTick,
                     int nextCut, int perfects, int goods, int misses,
                     List<Float> cutPositions, float slidePos, float slideSpeed)
            implements HandsOnSession {
        @Override public Dish.Method method() { return Dish.Method.CHOP; }
        @Override public Quality finishQuality() {
            if (misses >= 4) return Quality.CHARRED;
            if (misses >= 2) return Quality.BURNT;
            if (misses == 0 && perfects >= Minigames.CUTS - 2) return Quality.PERFECT;
            if (perfects + goods >= Minigames.CUTS) return Quality.GOOD;
            return Quality.BURNT;
        }
        @Override public boolean tick(ServerLevelAccessor level) {
            // Server doesn't sim chop (client sims); just check time limits
            return false;
        }
        @Override public void applyInput(com.jaymoskitchen.net.payloads.HandsOnInputPayload input) {
            if (input instanceof com.jaymoskitchen.net.payloads.ChopInput ci) {
                // Server validates: is this cut valid at this slide position?
                float mark = cutPositions.get(nextCut);
                float err = Math.abs(ci.cutOffset() - mark);
                if (err <= Minigames.CHOP_PERFECT) perfects++;
                else if (err <= Minigames.CHOP_GOOD) goods++;
                else misses++;
                nextCut++;
                slidePos += slideSpeed;
            }
        }
    }

    /** Stirring state: circle the mouse round the pot. Too slow sticks, too fast slops. */
    record StirState(UUID player, Dish dish, BlockPos stationPos, int startTick,
                     int stirred, int stuck, int spilled, int boilTicks,
                     boolean placed, float spoonAngle)
            implements HandsOnSession {
        @Override public Dish.Method method() { return Dish.Method.STIR; }
        @Override public Quality finishQuality() {
            if (stuck >= Minigames.STUCK_CHARRED) return Quality.CHARRED;
            if (spilled >= 4) return Quality.BURNT;
            if (stirred >= Minigames.STIR_TICKS) return Quality.PERFECT;
            if (stirred >= Minigames.STIR_TICKS * 0.75F) return Quality.GOOD;
            return Quality.BURNT;
        }
        @Override public boolean tick(ServerLevelAccessor level) {
            return false;
        }
        @Override public void applyInput(com.jaymoskitchen.net.payloads.HandsOnInputPayload input) {
            if (input instanceof com.jaymoskitchen.net.payloads.StirInput si) {
                float speed = Math.abs(si.speed());
                boolean boiling = si.boiling();
                float min = boiling ? Minigames.BOIL_MIN : Minigames.STIR_MIN;
                float max = boiling ? Minigames.BOIL_MAX : Minigames.STIR_MAX;
                if (speed < min) stuck++;
                else if (speed > max) spilled++;
                else stirred++;
            }
        }
    }
}
