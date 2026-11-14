package com.jaymoskingdoms.mixin.hordes;

import com.jaymoskingdoms.ZombieHordes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The dead in a horde cross water and never turn into drowned there ({@code HordeMoves}). */
@Mixin(Zombie.class)
abstract class HordeZombieWaterMixin {
	@Inject(method = "convertsInWater", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$hordeWades(CallbackInfoReturnable<Boolean> cir) {
		if (ZombieHordes.inHorde((Zombie) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
