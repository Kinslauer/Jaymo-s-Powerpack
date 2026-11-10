package com.jaymospowers.client.power.chrono.mixin;

import com.jaymospowers.client.power.chrono.FutureDeadLooks;
import net.minecraft.client.renderer.entity.AbstractZombieRenderer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The future's dead wear the clothes they died in: each grown zombie and husk there gets one of our undead skins
 * ({@link FutureDeadLooks}), picked by its id so it keeps it. The husk's own renderer picks its texture itself, so its
 * skin is put on in {@code FutureHuskRendererMixin}. Drowned and Kitchen's zombie players keep their own looks.
 */
@Mixin(AbstractZombieRenderer.class)
abstract class FutureZombieRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/monster/zombie/Zombie;Lnet/minecraft/client/renderer/entity/state/ZombieRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$futureCivilian(Zombie zombie, ZombieRenderState state, float partialTicks, CallbackInfo ci) {
		FutureDeadLooks.extractSkin(zombie, state);
	}

	@Inject(
		method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/ZombieRenderState;)Lnet/minecraft/resources/Identifier;",
		at = @At("HEAD"), cancellable = true)
	private void jaymospowers$futureCivilianSkin(ZombieRenderState state, CallbackInfoReturnable<Identifier> cir) {
		Identifier skin = FutureDeadLooks.skin(state);
		if (skin != null) {
			cir.setReturnValue(skin);
		}
	}
}
