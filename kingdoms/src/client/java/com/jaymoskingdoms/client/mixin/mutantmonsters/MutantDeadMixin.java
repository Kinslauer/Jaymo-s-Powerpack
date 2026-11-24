package com.jaymoskingdoms.client.mixin.mutantmonsters;

import com.jaymoskingdoms.client.MutantDead;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mutant Monsters' mutant zombie drawn in the old dead skin of where it rose ({@link MutantDead}). Skipped without the
 * mod.
 */
@Pseudo
@Mixin(targets = "fuzs.mutantmonsters.common.client.renderer.entity.MutantZombieRenderer", remap = false)
abstract class MutantDeadMixin {
	@Inject(method = "extractRenderState(Lfuzs/mutantmonsters/common/world/entity/mutant/MutantZombie;Lfuzs/mutantmonsters/common/client/renderer/entity/state/MutantZombieRenderState;F)V",
		at = @At("TAIL"), require = 0)
	private void jaymoskingdoms$oldDead(@Coerce LivingEntity mutant, @Coerce LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		MutantDead.extract(mutant, state);
	}

	@Inject(method = "getTextureLocation(Lfuzs/mutantmonsters/common/client/renderer/entity/state/MutantZombieRenderState;)Lnet/minecraft/resources/Identifier;",
		at = @At("HEAD"), cancellable = true, require = 0)
	private void jaymoskingdoms$oldDeadSkin(@Coerce LivingEntityRenderState state, CallbackInfoReturnable<Identifier> cir) {
		Identifier skin = MutantDead.skin(state);
		if (skin != null) {
			cir.setReturnValue(skin);
		}
	}
}
