package com.jaymospowers.client.blood;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.mixin.LivingEntityRendererLayersAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;
import net.conczin.mca.client.render.VillagerStateHolder;
import net.conczin.mca.client.render.layer.ClothingLayer;
import net.conczin.mca.client.render.layer.SkinLayer;
import net.conczin.mca.client.render.layer.VillagerLayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * {@link BloodCoatLayer}'s side for bodies MCA draws (only loaded with MCA). MCA draws a villager, and a player in an MCA
 * body, as a bare skin on one model and its clothes on another a little bigger, and never draws the body's own model. So
 * the blood and dirt are painted over each skin and clothes texture MCA draws this body with, and laid on that layer's
 * own model, posed and sized the way MCA's is, one step after MCA's draws so it always lies on top. Bare skin shows its
 * blood, clothes show theirs, and the two never fight over the same place.
 */
final class McaBlood {
	/** Per renderer, MCA's skin and clothes layers on it, looked up once. */
	private static final Map<Object, List<VillagerLayer<?, ?>>> LAYERS = new WeakHashMap<>();
	private static boolean broken;

	private McaBlood() {
	}

	/**
	 * Lays {@code paints} of each texture MCA draws {@code state} with over that layer's model. False when MCA isn't
	 * drawing this body itself (the caller then draws on the body's own model).
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	static boolean submit(LivingEntityRenderer<?, ?, ?> renderer, LivingEntityRenderState state, Function<Identifier, List<Identifier>> paints,
		PoseStack poseStack, SubmitNodeCollector collector, int light) {
		if (broken || !(state instanceof HumanoidRenderState body) || !(state instanceof VillagerStateHolder holder)
			|| !holder.mca$isVillagerRendererActive()) {
			return false;
		}
		List<VillagerLayer<?, ?>> layers = LAYERS.computeIfAbsent(renderer, McaBlood::layers);
		if (layers.isEmpty()) {
			return false;
		}
		int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
		try {
			for (VillagerLayer layer : layers) {
				Identifier texture = layer.getSkin(body);
				if (!layer.canUse(texture)) {
					continue;
				}
				for (Identifier painted : paints.apply(texture)) {
					collector.order(1).submitModel(layer.model, body, poseStack, RenderTypes.entityCutout(painted), light, overlay, state.outlineColor);
				}
			}
		} catch (RuntimeException e) {
			broken = true;
			JaymosPowers.LOGGER.warn("Couldn't lay blood over MCA's layers; MCA bodies are drawn with it on their own model", e);
		}
		return true;
	}

	private static List<VillagerLayer<?, ?>> layers(Object renderer) {
		List<VillagerLayer<?, ?>> found = new ArrayList<>();
		for (RenderLayer<?, ?> layer : ((LivingEntityRendererLayersAccessor) renderer).jaymospowers$layers()) {
			if (layer instanceof SkinLayer<?, ?> || layer instanceof ClothingLayer<?, ?>) {
				found.add((VillagerLayer<?, ?>) layer);
			}
		}
		return found;
	}
}
