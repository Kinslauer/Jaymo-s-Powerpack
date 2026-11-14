package com.jaymoskingdoms.client;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.jspecify.annotations.Nullable;

/**
 * Jaymo's Kitchen (optional) marks the render state of a body lying dead with the parts it has lost
 * ({@code Severed.KEY}: head, right arm, left arm, right leg, left leg, lowest bit first). Vanilla-modelled bodies are
 * drawn without them by Kitchen itself; our GeckoLib mobs read the bits here and hide their own bones.
 */
public final class KitchenParts {
	private static final @Nullable RenderStateDataKey<Integer> KEY = key();

	private KitchenParts() {
	}

	@SuppressWarnings("unchecked")
	private static @Nullable RenderStateDataKey<Integer> key() {
		if (!FabricLoader.getInstance().isModLoaded("jaymoskitchen")) {
			return null;
		}
		try {
			return (RenderStateDataKey<Integer>) Class.forName("com.jaymoskitchen.client.corpse.Severed").getField("KEY").get(null);
		} catch (ReflectiveOperationException | ClassCastException e) {
			return null;
		}
	}

	/** The parts {@code state}'s body has lost (0 for the living, and without Kitchen). */
	public static int missing(Object state) {
		if (KEY == null || !(state instanceof EntityRenderState entity)) {
			return 0;
		}
		Integer lost = entity.getData(KEY);
		return lost == null ? 0 : lost;
	}
}
