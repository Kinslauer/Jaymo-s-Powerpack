package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Downed;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.7.70) A downed guard ({@link Downed}) dies with nothing to loot, and is carried over the shoulder. */
@Mixin(LivingEntity.class)
abstract class DownedMixin {
	/** A downed guard carried by a player sits over their shoulder, not on their head. */
	@Inject(method = "getPassengerRidingPosition", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$carriedDowned(Entity passenger, CallbackInfoReturnable<Vec3> cir) {
		if ((Object) this instanceof Player carrier && Downed.isDowned(passenger)) {
			cir.setReturnValue(Downed.carriedAt(carrier));
		}
	}

	@Inject(method = "dropAllDeathLoot", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$downedNoLoot(ServerLevel level, DamageSource source, CallbackInfo ci) {
		if (Downed.isDowned((LivingEntity) (Object) this)) {
			ci.cancel();
		}
	}
}
