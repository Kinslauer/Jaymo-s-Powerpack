package com.jaymoskingdoms.client.mixin.deadlooks;

import com.jaymoskingdoms.client.OldDeadSkins;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A future zombie left to Tissou's Zombie Pack ({@link OldDeadSkins#texture}) is drawn with the game's own zombie texture,
 * whatever skin its renderer gave it (Jaymo's Powers dresses the future's dead in its own), so the pack's skins, through
 * Entity Texture Features, take it. Wrapped round the one place the body's texture is asked for, so it doesn't matter in
 * what order the mods' skin mixins on the zombie renderers apply.
 */
@Mixin(LivingEntityRenderer.class)
abstract class TissouFutureDeadMixin {
	@WrapOperation(
		method = "getRenderType(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;ZZZ)Lnet/minecraft/client/renderer/rendertype/RenderType;",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getTextureLocation(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)Lnet/minecraft/resources/Identifier;"))
	private Identifier jaymoskingdoms$tissouDead(LivingEntityRenderer<?, ?, ?> renderer, LivingEntityRenderState state,
		Operation<Identifier> original) {
		return OldDeadSkins.texture(state, original.call(renderer, state));
	}
}
