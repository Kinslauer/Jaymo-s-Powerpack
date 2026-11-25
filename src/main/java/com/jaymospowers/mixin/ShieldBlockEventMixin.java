package com.jaymospowers.mixin;

import com.jaymospowers.body.ShieldJolt;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.BlocksAttacks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A blow taken on a shield is told to everyone who can see the one holding it, so their shield arm can take the jolt (ShieldJolt). */
@Mixin(BlocksAttacks.class)
abstract class ShieldBlockEventMixin {
	@Inject(method = "onBlocked", at = @At("HEAD"))
	private void jaymospowers$told(ServerLevel level, LivingEntity entity, CallbackInfo ci) {
		level.broadcastEntityEvent(entity, ShieldJolt.EVENT);
	}
}
