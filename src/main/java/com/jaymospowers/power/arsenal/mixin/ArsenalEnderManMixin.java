package com.jaymospowers.power.arsenal.mixin;

import com.jaymospowers.power.arsenal.ArsenalVoid;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enderman;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** An Enderman setting its sights on an arsenal user stirs the void (see ArsenalVoid). */
@Mixin(Enderman.class)
abstract class ArsenalEnderManMixin {
	@Inject(method = "setTarget", at = @At("HEAD"))
	private void jaymospowers$arsenalNoticed(@Nullable LivingEntity target, CallbackInfo ci) {
		Enderman self = (Enderman) (Object) this;
		if (!self.level().isClientSide()) {
			ArsenalVoid.endermanTargets(self, ((Mob) self).getTarget(), target);
		}
	}
}
