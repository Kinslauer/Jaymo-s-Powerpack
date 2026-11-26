package com.jaymoskingdoms.client.mixin;

import com.jaymoskingdoms.Anglers;
import net.minecraft.client.renderer.entity.IllagerRenderer;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A pillager or vindicator fishing ({@link Anglers}) has his arms free, not folded: his rod arm is drawn on its own, so his
 * cast, wait, fight and landing poses ({@code MobAnimations}) can move it.
 */
@Mixin(IllagerRenderer.class)
abstract class AnglerIllagerPoseMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/monster/illager/AbstractIllager;Lnet/minecraft/client/renderer/entity/state/IllagerRenderState;F)V",
		at = @At("TAIL"))
	private void jaymoskingdoms$armsFree(AbstractIllager illager, IllagerRenderState state, float partialTicks, CallbackInfo ci) {
		if (Anglers.angling(illager)) {
			state.armPose = AbstractIllager.IllagerArmPose.ATTACKING;
		}
	}
}
