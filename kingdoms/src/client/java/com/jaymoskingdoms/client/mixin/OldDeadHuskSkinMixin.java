package com.jaymoskingdoms.client.mixin;

import com.jaymoskingdoms.client.OldDeadSkins;
import net.minecraft.client.renderer.entity.HuskRenderer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Overworld's husks wear their old-villager skins ({@link OldDeadSkins}; picked in OldDeadSkinMixin). */
@Mixin(HuskRenderer.class)
abstract class OldDeadHuskSkinMixin {
	@Inject(
		method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/ZombieRenderState;)Lnet/minecraft/resources/Identifier;",
		at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$oldDeadHuskSkin(ZombieRenderState state, CallbackInfoReturnable<Identifier> cir) {
		Identifier skin = OldDeadSkins.skin(state);
		if (skin != null) {
			cir.setReturnValue(skin);
		}
	}
}
