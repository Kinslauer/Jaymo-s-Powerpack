package com.jaymoskitchen.client.corpse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.jspecify.annotations.Nullable;

/**
 * (0.4.37) A body that lost parts before it died (a head, an arm, a leg: see {@code CorpseEntity#missing}) is drawn
 * without them. Set on the body's render state by {@code CorpseRenderer}; the parts are hidden by name right after the
 * model animates and shown again once it's drawn (see {@code ModelFeatureRendererMixin}), so a model shared by many
 * bodies is never left missing anything.
 */
public final class Severed {
	public static final RenderStateDataKey<Integer> KEY = RenderStateDataKey.create(() -> "jaymoskitchen:missing_parts");
	/** By bit (head, right arm, left arm, right leg, left leg): the model parts that make it up. */
	private static final String[][] NAMES = {
		{"head", "hat", "nose"}, {"right_arm", "right_sleeve"}, {"left_arm", "left_sleeve"}, {"right_leg", "right_pants"},
		{"left_leg", "left_pants"}};
	private static final Map<ModelPart, Function<String, ModelPart>> LOOKUPS = new WeakHashMap<>();
	private static final List<ModelPart> HIDDEN = new ArrayList<>();

	private Severed() {
	}

	public static @Nullable Integer of(int missing) {
		return missing == 0 ? null : missing;
	}

	public static void hide(Model<?> model, Object state) {
		if (!(state instanceof EntityRenderState renderState)) {
			return;
		}
		Integer missing = renderState.getData(KEY);
		if (missing == null) {
			return;
		}
		ModelPart root = model.root();
		for (int i = 0; i < NAMES.length; i++) {
			if ((missing & 1 << i) != 0) {
				for (String name : NAMES[i]) {
					ModelPart part = find(root, name);
					if (part != null && part.visible) {
						part.visible = false;
						HIDDEN.add(part);
					}
				}
			}
		}
	}

	public static void restore() {
		if (!HIDDEN.isEmpty()) {
			for (ModelPart part : HIDDEN) {
				part.visible = true;
			}
			HIDDEN.clear();
		}
	}

	private static @Nullable ModelPart find(ModelPart root, String name) {
		if (root.hasChild(name)) {
			return root.getChild(name);
		}
		try {
			return LOOKUPS.computeIfAbsent(root, ModelPart::createPartLookup).apply(name);
		} catch (RuntimeException e) {
			return null;
		}
	}
}
