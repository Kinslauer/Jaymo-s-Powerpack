package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Moons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.MoonPhase;

/**
 * (0.7.12) Total Darkness / True Darkness's idea (LGPL-3.0, grondag; remade here, none of its code): moonlight follows
 * the moon. Under a full moon a night looks as it always has; under a new moon the sky gives no light at all, and
 * whatever has no torch or fire near it is pitch black. Unlit caves already are in 26.3. Only where there's a sky
 * (the Overworld), and a lunar event's moon (Blood, Harvest, Blue...) always shines full. Applied to the lightmap
 * each time it's worked out (mixin.moons.DarkNightsMixin).
 */
public final class DarkNights {
	/** Moonlight left in each phase, from full (index 0) round to waxing gibbous (7). */
	static final float[] MOONLIGHT = {1.0F, 0.7F, 0.45F, 0.2F, 0.0F, 0.2F, 0.45F, 0.7F};
	/** Vanilla's sky light factor at the dead of night (Timelines.NIGHT_SKY_LIGHT_FACTOR); 1 at noon. */
	static final float NIGHT_SKY_FACTOR = 0.24F;

	private DarkNights() {
	}

	public static void apply(LightmapRenderState state, float partialTicks) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || !minecraft.level.dimensionType().hasSkyLight() || state.skyFactor >= 1.0F) {
			return;
		}
		float moon = MOONLIGHT[0];
		if (Moons.clientShown == Moons.Moon.NONE) {
			MoonPhase phase = minecraft.gameRenderer.mainCamera().attributeProbe().getValue(EnvironmentAttributes.MOON_PHASE, partialTicks);
			moon = MOONLIGHT[Math.floorMod(phase.index(), MOONLIGHT.length)];
		}
		// how far into the night it is: 0 by day, 1 once the sky is at its darkest
		float night = Mth.clamp((1.0F - state.skyFactor) / (1.0F - NIGHT_SKY_FACTOR), 0.0F, 1.0F);
		state.skyFactor *= Mth.lerp(night, 1.0F, moon);
	}
}
