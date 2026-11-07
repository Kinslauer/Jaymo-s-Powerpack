package com.jaymospowers.fishing.mixin;

import com.jaymospowers.fishing.FishingReal;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cod;
import net.minecraft.world.entity.animal.PufferFish;
import net.minecraft.world.entity.animal.Salmon;
import net.minecraft.world.entity.animal.TropicalFish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHookEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts vanilla fishing so a bite hooks a live fish entity instead of dropping an item, and starts the
 * tug-of-war fight ({@link FishingReal}). While a fish is on the line the hook isn't retrieved and no loot drops.
 */
@Mixin(FishingHookEntity.class)
public abstract class FishingHookMixin {
    private static final int RNG_TICKS = 4;

    /** Decide whether this hook gets a bite this tick; on a bite, spawn a live fish and start the fight. */
    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        FishingHookEntity hook = (FishingHookEntity) this;
        if (hook.tickCount <= RNG_TICKS * 5 || !hook.isInWater()) {
            return;
        }
        Entity owner = hook.getOwner();
        if (!(owner instanceof Player player)) {
            return;
        }
        if (FishingReal.fight((net.minecraft.server.level.ServerPlayer) player).fighting()) {
            return;
        }
        if (!FishingReal.shouldBite(hook.level(), hook.getX(), hook.getY(), hook.getZ())) {
            return;
        }
        Level level = hook.level();
        int r = level.getRandom().nextInt(100);
        int kind = r < 40 ? 0 : r < 75 ? 1 : r < 90 ? 2 : 3;
        LivingEntity fish = switch (kind) {
            case 0 -> new Cod(level);
            case 1 -> new Salmon(level);
            case 2 -> new PufferFish(level);
            default -> new TropicalFish(level);
        };
        fish.moveTo(hook.getX(), hook.getY(), hook.getZ(), hook.getYRot(), hook.getXRot());
        level.addFreshEntity(fish);
        FishingReal.startFight((net.minecraft.server.level.ServerPlayer) player, kind, fish, hook);
    }

    /** While a fish is on the line, don't retrieve the hook or drop vanilla loot. */
    @Inject(method = "reelIn", at = @At("HEAD"), cancellable = true)
    private void onReelIn(CallbackInfo ci) {
        FishingHookEntity hook = (FishingHookEntity) this;
        Entity owner = hook.getOwner();
        if (owner instanceof net.minecraft.server.level.ServerPlayer player
                && FishingReal.fight(player).fighting()) {
            ci.cancel();
        }
    }
}
