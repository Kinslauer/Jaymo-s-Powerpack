package com.jaymospowers.tinkering.mixin;

import com.jaymospowers.tinkering.smeltery.SmelteryEntity;
import net.minecraft.world.entity.monster.Enderman;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (GTNH picks, the forge's dangers) An enderman cooking in a smeltery can't teleport out: the smeltery's burn has no
 * living attacker, so {@code Enderman.hurtServer} would teleport it away 9 times in 10. Every teleport it makes (hurt,
 * daylight, projectiles) ends in {@code teleport(double, double, double)}.
 */
@Mixin(Enderman.class)
abstract class EndermanCookingMixin {
	@Inject(method = "teleport(DDD)Z", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$heldInSmeltery(double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
		if (SmelteryEntity.cooking((Enderman) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
