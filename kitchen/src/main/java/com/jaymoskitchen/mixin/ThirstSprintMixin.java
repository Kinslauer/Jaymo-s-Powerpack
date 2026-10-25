package com.jaymoskitchen.mixin;

import com.jaymoskitchen.climate.Thirst;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.5.25) Too thirsty to sprint ({@link Thirst#NO_SPRINT}), as with too little food: the client goes by it too. */
@Mixin(Player.class)
abstract class ThirstSprintMixin {
	@Inject(method = "hasEnoughFoodToDoExhaustiveManoeuvres", at = @At("RETURN"), cancellable = true)
	private void jaymoskitchen$thirsty(CallbackInfoReturnable<Boolean> cir) {
		Player player = (Player) (Object) this;
		if (cir.getReturnValueZ() && !player.getAbilities().mayfly && Thirst.water(player) <= Thirst.NO_SPRINT) {
			cir.setReturnValue(false);
		}
	}
}
