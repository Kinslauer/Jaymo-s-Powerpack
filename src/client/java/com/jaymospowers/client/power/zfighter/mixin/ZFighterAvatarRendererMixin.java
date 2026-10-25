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

/** A flying Z Fighter's pose goes on his render state; a fused body's skin is both of theirs; a Verdant's is painted green (in first person too). */
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
		// (0.16.16) a fused body's skin is both of theirs (before a Verdant's green goes on)
		Identifier skin = com.jaymospowers.client.power.zfighter.ZFighterFusionLook.skin(state, cir.getReturnValue());
		ZFighterVerdantLook.Look look = state.getData(ZFighterVerdantLook.KEY);
		cir.setReturnValue(look != null ? ZFighterVerdantLook.skin(skin, look.green()) : skin);
	}

	@ModifyVariable(method = "renderHand", at = @At("HEAD"), argsOnly = true)
	private Identifier jaymospowers$verdantHand(Identifier skin) {
		Integer green = ZFighterVerdantLook.local();
		return green == null ? skin : ZFighterVerdantLook.skin(skin, green);
	}
}
