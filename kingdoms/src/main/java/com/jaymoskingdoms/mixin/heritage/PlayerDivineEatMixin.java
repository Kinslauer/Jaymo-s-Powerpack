package com.jaymoskingdoms.mixin.heritage;

import com.jaymoskingdoms.DivineBorn;
import com.jaymoskingdoms.PallidHollows;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.7.320) A Divine-born's food bar is always full, but they eat and drink for the pleasure of it ({@link DivineBorn}). So
 * does a Pallid ({@link PallidHollows}): no hunger, but Kitchen's meals are still what mends a body.
 */
@Mixin(Player.class)
abstract class PlayerDivineEatMixin {
	@Inject(method = "canEat", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$eatForPleasure(boolean canAlwaysEat, CallbackInfoReturnable<Boolean> cir) {
		if (DivineBorn.eatsForPleasure((Player) (Object) this) || PallidHollows.is((Player) (Object) this)) {
			cir.setReturnValue(true);
		}
	}
}
