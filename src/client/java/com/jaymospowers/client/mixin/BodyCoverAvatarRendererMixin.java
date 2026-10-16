package com.jaymospowers.client.mixin;

import com.jaymospowers.client.BodyCover;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.12.5) What a costume or the symbiote's suit covers goes on the render state after everything else has dressed it
 * (this priority over the costume's 2100 and the Rig's 2200); see BodyCover.
 */
@Mixin(value = AvatarRenderer.class, priority = 2300)
abstract class BodyCoverAvatarRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$bodyCover(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		BodyCover.extract(entity, state);
	}

	/** First person: no skin sleeve under the costume's or the suit's sleeve. */
	@ModifyVariable(method = "renderHand", at = @At("HEAD"), argsOnly = true)
	private boolean jaymospowers$coveredSleeve(boolean hasSleeve) {
		return hasSleeve && !BodyCover.ownArms();
	}
}
