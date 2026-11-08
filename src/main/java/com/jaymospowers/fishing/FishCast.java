package com.jaymospowers.fishing;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * (Track A) The charged cast (Legendary Fishing's idea, our own code): with no hook out, holding use draws the rod back and
 * releasing throws, farther the longer it was held, so a long cast reaches deeper water. Sneak + use with no hook out turns
 * the rod's drag instead ({@link FishRun#turnDrag}). Both rods use this: the vanilla one through FishingRodItemMixin,
 * the metal one in {@link MetalFishingRodItem}.
 */
public final class FishCast {
    /** Ticks of holding use for a full cast. */
    public static final int CHARGE_TICKS = 30;
    /** How far a tap throws, and a full charge, against the plain rod's throw. */
    public static final float POWER_TAP = 0.55F, POWER_FULL = 1.6F;
    /** Use duration while charging (as long as a bow's: the player lets go when they like). */
    public static final int USE_TICKS = 72000;

    /** Set while a release runs the rod's own cast, so its use() throws instead of starting another charge. */
    private static final ThreadLocal<Boolean> RELEASING = ThreadLocal.withInitial(() -> false);

    private FishCast() {
    }

    /**
     * A rod's use() with no hook out: sneaking turns the drag, otherwise the charge starts. Returns null when the rod should
     * throw now (a release is running) or has a hook out, so its own use() goes on.
     */
    public static InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player.fishing != null || RELEASING.get()) {
            return null;
        }
        if (player.isShiftKeyDown()) {
            if (player instanceof ServerPlayer serverPlayer) {
                FishRun.turnDrag(serverPlayer, 0);
            }
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer && FishingReal.fight(serverPlayer).fighting()) {
            return InteractionResult.PASS;
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    /** How far the cast goes for {@code heldTicks} of charge, against the plain throw. */
    public static float power(int heldTicks) {
        float charge = Math.min(1.0F, Math.max(0, heldTicks) / (float) CHARGE_TICKS);
        return POWER_TAP + (POWER_FULL - POWER_TAP) * charge;
    }

    /** How full the charge is, 0..1 (the HUD's cast bar and the rod drawn back). */
    public static float charge(int heldTicks) {
        return Math.min(1.0F, Math.max(0, heldTicks) / (float) CHARGE_TICKS);
    }

    /**
     * Use released after {@code USE_TICKS - remainingTime} ticks: run the rod's own cast through {@code cast}, then throw the
     * new hook harder or softer by the charge.
     */
    public static boolean release(ItemStack stack, Level level, LivingEntity entity, int remainingTime, Runnable cast) {
        if (!(entity instanceof Player player) || player.fishing != null) {
            return false;
        }
        RELEASING.set(true);
        try {
            cast.run();
        } finally {
            RELEASING.set(false);
        }
        FishingHook hook = player.fishing;
        if (hook != null && !level.isClientSide()) {
            hook.setDeltaMovement(hook.getDeltaMovement().scale(power(USE_TICKS - remainingTime)));
            hook.needsSync = true;
        }
        return true;
    }
}
