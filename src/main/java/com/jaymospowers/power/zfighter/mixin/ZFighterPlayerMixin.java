package com.jaymospowers.power.zfighter.mixin;

import com.jaymospowers.power.zfighter.ZFighterHooks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fists only: a Z Fighter's swing with a weapon in hand strikes nothing (both sides, and Better Combat's attacks too). */
@Mixin(Player.class)
abstract class ZFighterPlayerMixin {
	@Inject(method = "attack", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$fistsOnly(Entity entity, CallbackInfo ci) {
		if (ZFighterHooks.refusesAttack((Player) (Object) this)) {
			ci.cancel();
		}
	}
}
