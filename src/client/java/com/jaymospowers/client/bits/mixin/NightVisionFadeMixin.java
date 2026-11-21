package com.jaymospowers.client.bits.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Night vision fades out smoothly over its last {@link #FADE_TICKS} (10 s) in place of vanilla's flicker (a sine wobble between
 * 0.4 and 1). Runs before the owl mask's dimming (priority), which then still caps it.
 */
@Mixin(value = GameRenderer.class, priority = 500)
abstract class NightVisionFadeMixin {
	/** How long the fade lasts (ticks): the same last stretch the flicker used to take over. */
	private static final int FADE_TICKS = 200;

	@ModifyReturnValue(method = "nightVisionScale", at = @At("RETURN"))
	private static float jaymospowers$fade(float original, LivingEntity camera, float partialTicks) {
		MobEffectInstance effect = camera.getEffect(MobEffects.NIGHT_VISION);
		if (effect == null || effect.isInfiniteDuration() || effect.getDuration() > FADE_TICKS) {
			return original;
		}
		return Mth.clamp((effect.getDuration() - partialTicks) / FADE_TICKS, 0.0F, 1.0F);
	}
}
