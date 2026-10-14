package com.jaymospowers.client.power.zfighter.mixin;

import com.jaymospowers.client.power.zfighter.ZFighterPowerClient;
import com.jaymospowers.client.power.zfighter.ZFighterVerdantLook;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A flying Z Fighter's pose goes on his render state; a Verdant's skin is painted green (in first person too). */
@Mixin(AvatarRenderer.class)
abstract class ZFighterAvatarRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$zfighterFlight(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		ZFighterPowerClient.extract(entity, state, partialTicks);
	}

	@Inject(
		method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)Lnet/minecraft/resources/Identifier;",
		at = @At("RETURN"), cancellable = true)
	private void jaymospowers$verdantSkin(AvatarRenderState state, CallbackInfoReturnable<Identifier> cir) {
		ZFighterVerdantLook.Look look = state.getData(ZFighterVerdantLook.KEY);
		if (look != null) {
			cir.setReturnValue(ZFighterVerdantLook.skin(cir.getReturnValue(), look.green()));
		}
	}

	@ModifyVariable(method = "renderHand", at = @At("HEAD"), argsOnly = true)
	private Identifier jaymospowers$verdantHand(Identifier skin) {
		Integer green = ZFighterVerdantLook.local();
		return green == null ? skin : ZFighterVerdantLook.skin(skin, green);
	}
}
