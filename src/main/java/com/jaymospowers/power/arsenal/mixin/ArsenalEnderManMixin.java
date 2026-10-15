package com.jaymospowers.power.arsenal.mixin;

import com.jaymospowers.power.arsenal.ArsenalVoid;
import com.jaymospowers.power.arsenal.VoidCrown;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** An Enderman setting its sights on an arsenal user stirs the void (see ArsenalVoid); a Void Crown's stare doesn't anger it. */
@Mixin(Enderman.class)
abstract class ArsenalEnderManMixin {
	@Inject(method = "setTarget", at = @At("HEAD"))
	private void jaymospowers$arsenalNoticed(@Nullable LivingEntity target, CallbackInfo ci) {
		Enderman self = (Enderman) (Object) this;
		if (!self.level().isClientSide()) {
			ArsenalVoid.endermanTargets(self, ((Mob) self).getTarget(), target);
		}
	}

	/** Like a carved pumpkin, but only for Endermen (Creakings still freeze under your gaze); hitting one still angers it. */
	@Inject(method = "isBeingStaredBy", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$voidCrownUnseen(Player player, CallbackInfoReturnable<Boolean> cir) {
		if (VoidCrown.wearing(player)) {
			cir.setReturnValue(false);
		}
	}
}
