package com.jaymoskingdoms.client.mixin.deadlooks;

import com.jaymoskingdoms.client.TissouPack;
import net.minecraft.client.renderer.entity.DrownedRenderer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While Tissou's Zombie Pack is on, a grown drowned is drawn with the game's own drowned texture under our name
 * ({@link TissouPack#DROWNED}), so the pack's drowned skins (Entity Texture Features hangs them on the game's path) stay
 * out of the present.
 */
@Mixin(DrownedRenderer.class)
abstract class TissouDrownedMixin {
	@Inject(
		method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/ZombieRenderState;)Lnet/minecraft/resources/Identifier;",
		at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$gameDrowned(ZombieRenderState state, CallbackInfoReturnable<Identifier> cir) {
		if (TissouPack.on() && !state.isBaby) {
			cir.setReturnValue(TissouPack.DROWNED);
		}
	}
}
