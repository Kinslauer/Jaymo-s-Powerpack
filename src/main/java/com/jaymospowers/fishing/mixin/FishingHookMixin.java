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
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts vanilla fishing so a bite hooks a live fish entity instead of dropping an item, and starts the
 * tug-of-war fight ({@link FishingReal}). While a fish is on the line the hook isn't retrieved and no loot drops.
 */
@Mixin(FishingHook.class)
public abstract class FishingHookMixin {
    private static final int RNG_TICKS = 4;
    private static final int CHANCE_COD = 40;
    private static final int CHANCE_SALMON = 75;
    private static final int CHANCE_PUFFERFISH = 90;

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
        if (!FishingReal.shouldBite(hook.level(), hook.getX(), hook.getY(), hook.getZ())) {
            return;
        }
        Level level = hook.level();
        int r = level.getRandom().nextInt(100);
        int kind = r < CHANCE_COD ? 0 : r < CHANCE_SALMON ? 1 : r < CHANCE_PUFFERFISH ? 2 : 3;
        LivingEntity fish = switch (kind) {
            case 0 -> new Cod((EntityType<? extends Cod>) BuiltInRegistries.ENTITY_TYPE.get(Identifier.withDefaultNamespace("cod")).orElseThrow().value(), level);
            case 1 -> new Salmon((EntityType<? extends Salmon>) BuiltInRegistries.ENTITY_TYPE.get(Identifier.withDefaultNamespace("salmon")).orElseThrow().value(), level);
            case 2 -> new Pufferfish((EntityType<? extends Pufferfish>) BuiltInRegistries.ENTITY_TYPE.get(Identifier.withDefaultNamespace("pufferfish")).orElseThrow().value(), level);
            default -> new TropicalFish((EntityType<? extends TropicalFish>) BuiltInRegistries.ENTITY_TYPE.get(Identifier.withDefaultNamespace("tropical_fish")).orElseThrow().value(), level);
        };
        fish.setPos(new net.minecraft.world.phys.Vec3(hook.getX(), hook.getY(), hook.getZ()));
        fish.setYRot(hook.getYRot());
        fish.setXRot(hook.getXRot());
        level.addFreshEntity(fish);
        FishingReal.startFight((net.minecraft.server.level.ServerPlayer) player, kind, fish, hook);
    }

    /** While a fish is on the line, don't retrieve the hook or drop vanilla loot. */
    @Inject(method = "retrieve", at = @At("HEAD"), cancellable = true)
    private void onRetrieve(ItemStack rod, CallbackInfoReturnable<Integer> cir) {
        FishingHook hook = (FishingHook) (Object) this;
        Entity owner = hook.getOwner();
        if (owner instanceof net.minecraft.server.level.ServerPlayer player
                && FishingReal.fight(player).fighting()) {
            cir.setReturnValue(0);
            cir.cancel();
        }
    }

    /** Never let vanilla start its own bite or drop its own loot: every catch goes through the fight. */
    @Inject(method = "catchingFish", at = @At("HEAD"), cancellable = true)
    private void onCatchingFish(BlockPos blockPos, CallbackInfo ci) {
        ci.cancel();
    }
}
