package com.jaymospowers.client.power.web.mixin;

import com.jaymospowers.core.Powers;
import com.jaymospowers.power.web.WebDarkSense;
import com.jaymospowers.power.web.WebPower;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sense in the dark (see WebDarkSense): where the light is low, a web slinger's lightmap is lifted a little, never past {@code STRENGTH}. */
@Mixin(LightmapRenderStateExtractor.class)
abstract class WebDarkSenseMixin {
	@Inject(method = "extract", at = @At("TAIL"))
	private void jaymospowers$darkSense(LightmapRenderState state, float partialTicks, CallbackInfo ci) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || player.level() == null || !Powers.has(player, WebPower.INSTANCE) || !WebDarkSense.on(player) || player.isSpectator()) {
			return;
		}
		BlockPos eye = BlockPos.containing(player.getEyePosition());
		int light = player.level().getMaxLocalRawBrightness(eye);
		float dark = Mth.clamp((WebDarkSense.DARK_BELOW - light) / (float)WebDarkSense.DARK_BELOW, 0.0F, 1.0F);
		state.nightVisionEffectIntensity = Math.max(state.nightVisionEffectIntensity, WebDarkSense.STRENGTH * dark);
	}
}
