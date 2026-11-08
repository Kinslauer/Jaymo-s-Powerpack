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
 * Intercepts vanilla fishing so a bite hooks a live fish entity instead of dropping an item, and starts the
 * tug-of-war fight ({@link FishingReal}). While a fish is on the line the hook isn't retrieved and no loot drops.
 */
@Mixin(FishingHook.class)
public abstract class FishingHookMixin {
    private static final int RNG_TICKS = 4;
    /** How often (ticks) a hook in a pool high above the sea says there are no fish there. */
    private static final int NO_FISH_EVERY = 200;

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
        net.minecraft.server.level.ServerPlayer angler = (net.minecraft.server.level.ServerPlayer) player;
        if (hook.tickCount % NO_FISH_EVERY == 0
                && hook.blockPosition().getY() > hook.level().getSeaLevel() + com.jaymospowers.fishing.FishWaters.HIGH_POOL) {
            // (0.21.61) a pool high above the sea holds no fish
            angler.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.jaymospowers.fishing.no_fish_here")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        if (!FishingReal.shouldBite(angler, hook)) {
            return;
        }
        Level level = hook.level();
        // (0.21.61) which fish come depends on the water: river, lake, cold, open or warm sea (FishWaters)
        int kind = com.jaymospowers.fishing.FishWaters.pick((net.minecraft.server.level.ServerLevel) level, hook.blockPosition(),
                FishingReal.isMetalRod(angler), level.getRandom());
        // a shark only comes up for a metal rod, in water deep enough for it
        LivingEntity shark = null;
        if (kind == com.jaymospowers.fishing.FishingReal.FISH_SHARK) {
            shark = com.jaymospowers.compat.KingdomsCompat.sharkDrawnIn(
                    (net.minecraft.server.level.ServerLevel) level, hook.getX(), hook.getY(), hook.getZ());
            if (shark == null) {
                kind = com.jaymospowers.fishing.FishingReal.FISH_COD;
            }
        }
        LivingEntity fish;
        if (kind == com.jaymospowers.fishing.FishingReal.FISH_SHARK) {
            // fight the shark itself, right at the hook
            fish = shark;
            fish.setPos(new net.minecraft.world.phys.Vec3(hook.getX(), hook.getY(), hook.getZ()));
            fish.setYRot(hook.getYRot());
            fish.setXRot(hook.getXRot());
        } else {
            fish = switch (kind) {
                case 0, FishingReal.FISH_TUNA -> new Cod((EntityType<? extends Cod>) BuiltInRegistries.ENTITY_TYPE.get(Identifier.withDefaultNamespace("cod")).orElseThrow().value(), level);
                case 1 -> new Salmon((EntityType<? extends Salmon>) BuiltInRegistries.ENTITY_TYPE.get(Identifier.withDefaultNamespace("salmon")).orElseThrow().value(), level);
                case 2 -> new Pufferfish((EntityType<? extends Pufferfish>) BuiltInRegistries.ENTITY_TYPE.get(Identifier.withDefaultNamespace("pufferfish")).orElseThrow().value(), level);
                default -> new TropicalFish((EntityType<? extends TropicalFish>) BuiltInRegistries.ENTITY_TYPE.get(Identifier.withDefaultNamespace("tropical_fish")).orElseThrow().value(), level);
            };
            fish.setPos(new net.minecraft.world.phys.Vec3(hook.getX(), hook.getY(), hook.getZ()));
            fish.setYRot(hook.getYRot());
            fish.setXRot(hook.getXRot());
            if (kind == FishingReal.FISH_TUNA) {
                // a tuna is a big cod
                net.minecraft.world.entity.ai.attributes.AttributeInstance scale =
                        fish.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE);
                if (scale != null) {
                    scale.setBaseValue(FishingReal.TUNA_SCALE);
                }
            }
        }
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

    /** The metal rod counts as a fishing rod: don't discard a metal-rod hook just because the player isn't holding a vanilla rod. */
    @Inject(method = "shouldStopFishing", at = @At("HEAD"), cancellable = true)
    private void onShouldStopFishing(Player owner, CallbackInfoReturnable<Boolean> cir) {
        if (FishingReal.isMetalRodItem(owner.getItemInHand(InteractionHand.MAIN_HAND))
                || FishingReal.isMetalRodItem(owner.getItemInHand(InteractionHand.OFF_HAND))) {
            cir.setReturnValue(false);
            cir.cancel();
        }
    }
}
