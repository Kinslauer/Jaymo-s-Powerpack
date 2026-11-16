package com.jaymospowers.mixin;

import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Enhanced Cats (shipped unchanged) checks each tick whether a tame cat's owner is near, taking the owner to be a
 * player. A cat tamed by an MCA villager has a villager for an owner, and the cast stopped the server (a cat in
 * Thalfell, 2026-11-16). Such a cat skips that check. Applied after Enhanced Cats' own mixin (priority), which adds the
 * method; nothing happens without the mod.
 */
@Mixin(value = Cat.class, priority = 1500)
abstract class CatOwnerPlayerMixin {
	@Inject(method = "handleOwnerDisconnected", at = @At("HEAD"), cancellable = true, require = 0)
	private void jaymospowers$onlyPlayerOwners(Cat cat, CallbackInfo ci) {
		if (cat.getOwner() != null && !(cat.getOwner() instanceof Player)) {
			ci.cancel();
		}
	}
}
