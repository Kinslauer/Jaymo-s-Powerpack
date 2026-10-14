package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Moons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.MoonPhase;

/**
 * (0.7.17) Total Darkness / True Darkness's idea (LGPL-3.0, grondag; remade here, none of its code): moonlight follows
 * the moon. Under a full moon a night looks as it always has; under a new moon the sky gives no light at all, and
 * whatever has no torch or fire near it is pitch black. Unlit caves already are in 26.3. Only where there's a sky
 * (the Overworld), and a lunar event's moon (Blood, Harvest, Blue...) always shines full. Applied to the lightmap
 * each time it's worked out (mixin.moons.DarkNightsMixin).
 *
 * <p>Shader packs light the world themselves and would undo it, so on a night darker than full moon an Iris shader
 * pack is switched off at dusk, with a word on screen, and back on at dawn (Andy, 2026-10-14). Iris is reached by
 * reflection, so it isn't a compile dependency.
 */
public final class DarkNights {
	/** Moonlight left in each phase, from full (index 0) round to waxing gibbous (7). */
	static final float[] MOONLIGHT = {1.0F, 0.7F, 0.45F, 0.2F, 0.0F, 0.2F, 0.45F, 0.7F};
	/** Vanilla's sky light factor at the dead of night (Timelines.NIGHT_SKY_LIGHT_FACTOR); 1 at noon. */
	static final float NIGHT_SKY_FACTOR = 0.24F;

	/** Past this far into the night (0-1) on a waning moon, shader packs go off. */
	static final float SHADERS_OFF_AT = 0.3F;

	/** The night and moonlight the lightmap last saw. */
	private static volatile float lastNight, lastMoon = 1.0F;
	/** We switched a shader pack off and owe it back. */
	private static boolean shadersOff;

	private DarkNights() {
	}

	/** Each client tick: shader packs off for a dark night, back on when it's over. */
	public static void tick(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.player == null) {
			// left the world: give the shaders back
			if (shadersOff) {
				Iris.set(true);
				shadersOff = false;
			}
			return;
		}
		if (minecraft.player.tickCount % 20 != 0) {
			return;
		}
		boolean dark = minecraft.level.dimensionType().hasSkyLight() && lastNight > SHADERS_OFF_AT && lastMoon < 1.0F;
		if (dark && !shadersOff && Iris.inUse()) {
			// (0.7.25) quietly, no message (Andy: "no shader disable message")
			if (Iris.set(false)) {
				shadersOff = true;
			}
		} else if (!dark && shadersOff) {
			Iris.set(true);
			shadersOff = false;
		}
	}

	/** Iris's public API, by reflection. */
	private static final class Iris {
		private static final boolean LOADED = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("iris");

		static boolean inUse() {
			if (!LOADED) {
				return false;
			}
			try {
				Object api = Class.forName("net.irisshaders.iris.api.v0.IrisApi").getMethod("getInstance").invoke(null);
				return (boolean) api.getClass().getMethod("isShaderPackInUse").invoke(api);
			} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
				return false;
			}
		}

		static boolean set(boolean on) {
			if (!LOADED) {
				return false;
			}
			try {
				Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
				Object api = apiClass.getMethod("getInstance").invoke(null);
				Object config = apiClass.getMethod("getConfig").invoke(api);
				Class.forName("net.irisshaders.iris.api.v0.IrisApiConfig").getMethod("setShadersEnabledAndApply", boolean.class).invoke(config, on);
				return true;
			} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
				com.jaymoskingdoms.JaymosKingdoms.LOGGER.warn("Couldn't switch Iris shaders {}", on ? "on" : "off", e);
				return false;
			}
		}
	}

	public static void apply(LightmapRenderState state, float partialTicks) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || !minecraft.level.dimensionType().hasSkyLight() || state.skyFactor >= 1.0F) {
			lastNight = 0.0F;
			return;
		}
		float moon = MOONLIGHT[0];
		if (Moons.clientShown == Moons.Moon.NONE) {
			MoonPhase phase = minecraft.gameRenderer.mainCamera().attributeProbe().getValue(EnvironmentAttributes.MOON_PHASE, partialTicks);
			moon = MOONLIGHT[Math.floorMod(phase.index(), MOONLIGHT.length)];
		}
		// how far into the night it is: 0 by day, 1 once the sky is at its darkest
		float night = Mth.clamp((1.0F - state.skyFactor) / (1.0F - NIGHT_SKY_FACTOR), 0.0F, 1.0F);
		lastNight = night;
		lastMoon = moon;
		state.skyFactor *= Mth.lerp(night, 1.0F, moon);
	}
}
