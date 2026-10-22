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
 * <p>Shader packs light the world themselves and would undo it. (0.7.164) So under Iris the sky light each pack is
 * handed (its lmcoord) is scaled by the same moonlight ({@link ShaderSkyLight}), and shaders stay on all night (Andy:
 * "my players want shaders"; True Darkness Compat's idea, MIT, none of its code). Any pack that reads Minecraft's
 * light levels follows it. Only if that hook didn't take (a newer Iris) does the old way come back: the shader pack is
 * switched off for a dark night and back on at dawn (Andy, 2026-10-14). Iris is reached by reflection, so it isn't a
 * compile dependency.
 */
public final class DarkNights {
	/** Moonlight left in each phase, from full (index 0) round to waxing gibbous (7). */
	static final float[] MOONLIGHT = {1.0F, 0.7F, 0.45F, 0.2F, 0.0F, 0.2F, 0.45F, 0.7F};
	/** Vanilla's sky light factor at the dead of night (Timelines.NIGHT_SKY_LIGHT_FACTOR); 1 at noon. */
	static final float NIGHT_SKY_FACTOR = 0.24F;

	/** Past this far into the night (0-1) on a waning moon, shader packs go off (only when {@link ShaderSkyLight} didn't take). */
	static final float SHADERS_OFF_AT = 0.3F;
	/**
	 * (0.7.49) Seconds the night has to stay dark (or light) in a row before shaders are switched. A lightning flash
	 * lights the sky for a moment, which used to flip shaders back on and off with every thunderclap (Andy: "thunder
	 * at night turns shaders on and off, can we remove that").
	 */
	static final int SHADERS_OFF_AFTER = 4, SHADERS_ON_AFTER = 15;

	/** The night and moonlight the lightmap last saw. */
	private static volatile float lastNight, lastMoon = 1.0F;
	/** We switched a shader pack off and owe it back. */
	private static boolean shadersOff;
	/** Seconds in a row the night has disagreed with the shaders' state. */
	private static int streak;

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
			streak = 0;
			return;
		}
		if (minecraft.player.tickCount % 20 != 0) {
			return;
		}
		// (0.7.164) shader packs that take our moonlight stay on
		boolean dark = minecraft.level.dimensionType().hasSkyLight() && lastNight > SHADERS_OFF_AT && lastMoon < 1.0F
				&& !ShaderSkyLight.working();
		if (dark == shadersOff || !shadersOff && !Iris.inUse()) {
			streak = 0;
			return;
		}
		if (++streak < (dark ? SHADERS_OFF_AFTER : SHADERS_ON_AFTER)) {
			return;
		}
		streak = 0;
		if (dark) {
			// (0.7.25) quietly, no message (Andy: "no shader disable message")
			if (Iris.set(false)) {
				shadersOff = true;
			}
		} else {
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

	/**
	 * (0.7.164) Moonlight for shader packs. Packs light the world from the block and sky light levels Iris hands them
	 * (gl_MultiTexCoord1, read straight or through gl_TextureMatrix[1]); the sky level is multiplied by the uniform
	 * {@code jaymo_SkyLight}, the same factor the vanilla lightmap gets. So under a new moon the open sky lights a pack's
	 * world no more than a cave roof does, whatever pack it is. Wired in by mixin.iris; all of Iris by reflection.
	 */
	public static final class ShaderSkyLight {
		static final String UNIFORM = "jaymo_SkyLight";
		/** Iris's shaders asked for the sky light, and the uniform is there to scale it. */
		private static volatile boolean scaled, registered;

		private ShaderSkyLight() {
		}

		static boolean working() {
			return scaled && registered;
		}

		/** The moonlight multiplier this frame: 1 by day, off the Overworld and under a full or event moon. */
		public static double factor() {
			return Mth.lerp(lastNight, 1.0F, lastMoon);
		}

		/** Iris's stand-in for gl_MultiTexCoord1, with the sky level (y) scaled. */
		public static String scale(String original, String light) {
			scaled = true;
			return "vec4(" + light + " * vec2(1.0, " + UNIFORM + "), 0.0, 1.0)";
		}

		/** Declares the uniform in a shader Iris has just transformed. */
		public static void declare(Object parser, Object tree) {
			try {
				ClassLoader loader = tree.getClass().getClassLoader();
				Class<?> parserClass = Class.forName("io.github.douira.glsl_transformer.ast.transform.ASTParser", false, loader);
				Class<?> pointClass = Class.forName("io.github.douira.glsl_transformer.ast.transform.ASTInjectionPoint", false, loader);
				Object beforeFunctions = pointClass.getField("BEFORE_FUNCTIONS").get(null);
				tree.getClass().getMethod("parseAndInjectNode", parserClass, pointClass, String.class)
						.invoke(tree, parser, beforeFunctions, "uniform float " + UNIFORM + ";");
			} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
				scaled = false;
				com.jaymoskingdoms.JaymosKingdoms.LOGGER.warn("Couldn't give a shader pack the moonlight; dark nights will switch shaders off", e);
			}
		}

		/** Adds the uniform to Iris's common ones, updated every frame. */
		public static void register(Object uniforms) {
			try {
				ClassLoader loader = uniforms.getClass().getClassLoader();
				Class<?> frequencyClass = Class.forName("net.irisshaders.iris.gl.uniform.UniformUpdateFrequency", false, loader);
				Object perFrame = frequencyClass.getField("PER_FRAME").get(null);
				Class.forName("net.irisshaders.iris.gl.uniform.UniformHolder", false, loader)
						.getMethod("uniform1f", frequencyClass, String.class, java.util.function.DoubleSupplier.class)
						.invoke(uniforms, perFrame, UNIFORM, (java.util.function.DoubleSupplier) ShaderSkyLight::factor);
				registered = true;
			} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
				registered = false;
				com.jaymoskingdoms.JaymosKingdoms.LOGGER.warn("Couldn't add the moonlight uniform to Iris; dark nights will switch shaders off", e);
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
