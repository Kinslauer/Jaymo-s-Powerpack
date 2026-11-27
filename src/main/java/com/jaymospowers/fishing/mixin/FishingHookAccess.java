package com.jaymospowers.fishing.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Hooks a fishing hook into an entity, synced to clients ({@code FishOnLine}: the hook rides in the hooked fish's mouth). */
@Mixin(FishingHook.class)
public interface FishingHookAccess {
    @Invoker("setHookedEntity")
    void jaymospowers$hookIn(@Nullable Entity hookedIn);
}
