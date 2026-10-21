package com.jaymoskingdoms.mixin.heritage;

import com.jaymoskingdoms.Heritage;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.7.106) An Endborn's stare doesn't anger an enderman ({@link Heritage}): to it, they're kin. */
@Mixin(Enderman.class)
abstract class EndermanStareMixin {
	@Inject(method = "isBeingStaredBy", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$kin(Player player, CallbackInfoReturnable<Boolean> cir) {
		if (Heritage.is(player, Heritage.Kind.ENDBORN)) {
			cir.setReturnValue(false);
		}
	}
}
