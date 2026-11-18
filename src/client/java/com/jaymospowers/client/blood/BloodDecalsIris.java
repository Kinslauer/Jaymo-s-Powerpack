package com.jaymospowers.client.blood;

import com.jaymospowers.JaymosPowers;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.fabricmc.loader.api.FabricLoader;

/** Tells Iris which of its programs draws the blood decal pipeline. Without that, Iris logs "Missing program ... in
 * override list" and the decals don't draw under a shader pack. Iris's public API is looked up by name so the game runs
 * without Iris. */
final class BloodDecalsIris {
	private static final String API = "net.irisshaders.iris.api.v0.IrisApi";
	private static final String PROGRAM = "net.irisshaders.iris.api.v0.IrisProgram";
	/** The shader program the decals are drawn with: Iris's translucent particle program, like vanilla's own. */
	private static final String PROGRAM_NAME = "PARTICLES_TRANSLUCENT";

	private BloodDecalsIris() {
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	static void assign(RenderPipeline pipeline) {
		if (!FabricLoader.getInstance().isModLoaded("iris")) {
			return;
		}
		try {
			Class<?> api = Class.forName(API);
			Class<?> program = Class.forName(PROGRAM);
			api.getMethod("assignPipeline", RenderPipeline.class, program)
				.invoke(api.getMethod("getInstance").invoke(null), pipeline, Enum.valueOf((Class<Enum>) program, PROGRAM_NAME));
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			JaymosPowers.LOGGER.warn("Couldn't give the blood decal pipeline to Iris", e);
		}
	}
}
