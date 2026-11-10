package com.jaymoskingdoms.client.mixin;

import com.jaymoskingdoms.client.OldDeadSkins;
import net.minecraft.client.renderer.entity.AbstractZombieRenderer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Overworld's zombies wear their old-villager skins ({@link OldDeadSkins}); the husks' go on in OldDeadHuskSkinMixin. */
@Mixin(AbstractZombieRenderer.class)
abstract class OldDeadSkinMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/monster/zombie/Zombie;Lnet/minecraft/client/renderer/entity/state/ZombieRenderState;F)V",
		at = @At("TAIL"))
	private void jaymoskingdoms$oldDead(Zombie zombie, ZombieRenderState state, float partialTicks, CallbackInfo ci) {
		OldDeadSkins.extract(zombie, state);
	}

	@Inject(
		method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/ZombieRenderState;)Lnet/minecraft/resources/Identifier;",
		at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$oldDeadSkin(ZombieRenderState state, CallbackInfoReturnable<Identifier> cir) {
		Identifier skin = OldDeadSkins.skin(state);
		if (skin != null) {
			cir.setReturnValue(skin);
		}
	}
}
