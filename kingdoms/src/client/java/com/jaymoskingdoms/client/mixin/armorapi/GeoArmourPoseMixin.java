package com.jaymoskingdoms.client.mixin.armorapi;

import com.jaymoskingdoms.client.GeoArmourFit;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Armor Model API's posed armour model (it draws Rogues &amp; Warriors' Warrior Helmet and the other 3D sets): once it
 * has copied the body, it takes what the body has as it is drawn ({@link GeoArmourFit}). Skipped without the API.
 */
@Pseudo
@Mixin(targets = "net.rpg_foundation.armor_api.client.model.PoseCopyingModel", remap = false)
abstract class GeoArmourPoseMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"), require = 0)
	private void jaymoskingdoms$fit(HumanoidRenderState state, CallbackInfo ci) {
		GeoArmourFit.fit((HumanoidModel<?>) (Object) this, state);
	}
}
