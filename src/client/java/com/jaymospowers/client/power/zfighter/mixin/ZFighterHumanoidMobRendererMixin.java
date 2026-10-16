package com.jaymospowers.client.power.zfighter.mixin;

import com.jaymospowers.client.power.zfighter.ZFighterApe;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A villager raging as the wild Great Ape (VillagerApe): the ape goes on its render state, its own body and gear hidden. */
@Mixin(HumanoidMobRenderer.class)
abstract class ZFighterHumanoidMobRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Mob;Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$villagerApe(Mob entity, HumanoidRenderState state, float partialTicks, CallbackInfo ci) {
		ZFighterApe.extractMob(entity, state);
	}
}
