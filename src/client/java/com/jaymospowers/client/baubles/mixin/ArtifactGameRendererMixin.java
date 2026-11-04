package com.jaymospowers.client.baubles.mixin;

import com.jaymospowers.baubles.artifacts.ArtifactBaubles;
import com.jaymospowers.baubles.artifacts.ArtifactEffects;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * (Artifacts) The owl mask' sight is a faint one, as in Artifacts, so nights stay dark: only their own
 * (ambient) night vision is dimmed; a potion's is full.
 */
@Mixin(GameRenderer.class)
public abstract class ArtifactGameRendererMixin {
	@ModifyReturnValue(method = "nightVisionScale", at = @At("RETURN"))
	private static float jaymospowers$owlMask(float original, LivingEntity camera, float a) {
		MobEffectInstance effect = camera.getEffect(MobEffects.NIGHT_VISION);
		if (effect == null || !effect.isAmbient() || !ArtifactEffects.wears(camera, ArtifactBaubles.OWL_MASK)) {
			return original;
		}
		return Math.min(original, ArtifactEffects.OWL_MASK_STRENGTH);
	}
}
