package com.jaymoskingdoms.mixin.heritage;

import com.jaymoskingdoms.Heritage;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.7.97) A Deepborn moves like the deep dark itself ({@link Heritage}): their vibrations are dampened, as if on wool,
 * so sculk sensors, shriekers and the Warden never hear them.
 */
@Mixin(Entity.class)
abstract class EntityVibrationMixin {
	@Inject(method = "dampensVibrations", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$deepborn(CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof Player player && Heritage.is(player, Heritage.Kind.DEEPBORN)) {
			cir.setReturnValue(true);
		}
	}
}
