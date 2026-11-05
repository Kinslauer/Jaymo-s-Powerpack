package com.jaymospowers.mixin;

import com.jaymospowers.core.BlastGore;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.20.67) While a vanilla blast hurts things, BlastGore knows where it went off and how big it is (how close each body was). */
@Mixin(ServerExplosion.class)
abstract class BlastGoreMixin {
	@Inject(method = "explode", at = @At("HEAD"))
	private void jaymospowers$begin(CallbackInfoReturnable<Integer> cir) {
		BlastGore.begin((ServerExplosion) (Object) this);
	}

	@Inject(method = "explode", at = @At("RETURN"))
	private void jaymospowers$end(CallbackInfoReturnable<Integer> cir) {
		BlastGore.end();
	}
}
