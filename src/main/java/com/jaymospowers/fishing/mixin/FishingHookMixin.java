package com.jaymospowers.fishing.mixin;

import com.jaymospowers.fishing.FishingReal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.fish.Cod;
import net.minecraft.world.entity.animal.fish.Pufferfish;
import net.minecraft.world.entity.animal.fish.Salmon;
import net.minecraft.world.entity.animal.fish.TropicalFish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts vanilla fishing so a bite hooks a real fish (one that swam to the baited hook, {@code FishBites}) instead of dropping an item, and starts the
 * tug-of-war fight ({@link FishingReal}). While a fish is on the line the hook isn't retrieved and no loot drops.
 */
@Mixin(FishingHook.class)
public abstract class FishingHookMixin {
    private static final int RNG_TICKS = 4;

    private FishingHook hook() {
        return (FishingHook) (Object) this;
    }

    /** Decide whether this hook gets a bite this tick; on a bite, spawn a live fish and start the fight. */
    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        FishingHook hook = (FishingHook) (Object) this;
        if (hook.level().isClientSide()) {
            return;
        }
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
        // (0.21.61, Andy: "they have to ACTUALLY be there") a real fish near a baited hook swims to it and takes it
        net.minecraft.server.level.ServerPlayer angler = (net.minecraft.server.level.ServerPlayer) player;
        if (com.jaymospowers.fishing.FishBite.tick(angler, hook)) {
            // (Track A) a fish is nibbling or has taken the hook, or the angler wading near it spooks the fish
            return;
        }
        com.jaymospowers.fishing.FishBites.bare(hook, angler);
        com.jaymospowers.fishing.FishBites.tick((net.minecraft.server.level.ServerLevel) hook.level(), hook, angler);
    }

    /** While a fish is on the line, don't retrieve the hook or drop vanilla loot. */
    @Inject(method = "retrieve", at = @At("HEAD"), cancellable = true)
    private void onRetrieve(ItemStack rod, CallbackInfoReturnable<Integer> cir) {
        FishingHook hook = (FishingHook) (Object) this;
        Entity owner = hook.getOwner();
        if (owner instanceof net.minecraft.server.level.ServerPlayer player
                && (FishingReal.fight(player).fighting() || com.jaymospowers.fishing.FishBite.strike(player, hook))) {
            // (Track A) a pull on a taken hook is the strike: the fight starts and the hook stays out
            cir.setReturnValue(0);
            cir.cancel();
        } else if (owner instanceof net.minecraft.server.level.ServerPlayer player) {
            com.jaymospowers.fishing.FishBite.clear(player);
        }
    }

    /** Never let vanilla start its own bite or drop its own loot: every catch goes through the fight. */
    @Inject(method = "catchingFish", at = @At("HEAD"), cancellable = true)
    private void onCatchingFish(BlockPos blockPos, CallbackInfo ci) {
        ci.cancel();
    }

    /** The metal rod counts as a fishing rod: don't discard a metal-rod hook just because the player isn't holding a vanilla rod. */
    @Inject(method = "shouldStopFishing", at = @At("HEAD"), cancellable = true)
    private void onShouldStopFishing(Player owner, CallbackInfoReturnable<Boolean> cir) {
        // (0.21.64) and so do Track B's rods (any FishingRodItem; vanilla only keeps a hook for its own rod)
        if ((com.jaymospowers.fishing.HookBait.isRod(owner.getItemInHand(InteractionHand.MAIN_HAND))
                || com.jaymospowers.fishing.HookBait.isRod(owner.getItemInHand(InteractionHand.OFF_HAND)))
                && hook().distanceToSqr(owner) <= 1024.0) {
            cir.setReturnValue(false);
            cir.cancel();
        }
    }
}
