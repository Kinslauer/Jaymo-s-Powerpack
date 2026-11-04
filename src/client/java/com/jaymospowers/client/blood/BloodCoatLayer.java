package com.jaymospowers.client.blood;

import com.jaymospowers.client.wet.ArmourGrimeTextures;
import com.jaymospowers.core.blood.Bloodied;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

/**
 * (0.20.39) Andy liked Hematic's blood on creatures (ideas only). A body splashed with blood ({@link Bloodied#COAT}) is
 * drawn with it over its skin: splotches from our own mask ({@code textures/blood/body_blood.png}), more as it gets
 * bloodier, laid only where the skin has pixels ({@link ArmourGrimeTextures#skin}), in its own blood's colour. A
 * player's outer skin layer stays clean, so nothing floats off the body. It wears off by itself and washes off in water.
 */
public final class BloodCoatLayer {
	static final RenderStateDataKey<Bloodied.Coat> KEY = RenderStateDataKey.create(() -> "jaymospowers:blood_coat");

	private BloodCoatLayer() {
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> helper.register((RenderLayer) layer(renderer)));
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static RenderLayer layer(LivingEntityRenderer<?, ?, ?> renderer) {
		return new Layer(renderer);
	}

	/** From every living renderer's extractRenderState. */
	public static void extract(LivingEntity entity, LivingEntityRenderState state) {
		state.setData(KEY, Bloodied.coat(entity));
	}

	static final class Layer<S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends RenderLayer<S, M> {
		private final LivingEntityRenderer<?, S, M> renderer;

		Layer(LivingEntityRenderer<?, S, M> renderer) {
			super(renderer);
			this.renderer = renderer;
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
			Bloodied.Coat coat = state.getData(KEY);
			if (coat == null || state.isInvisible) {
				return;
			}
			Identifier skin;
			try {
				skin = this.renderer.getTextureLocation(state);
			} catch (RuntimeException e) {
				return;
			}
			Identifier painted = skin == null ? null : ArmourGrimeTextures.skin(skin, coat);
			if (painted != null) {
				renderColoredCutoutModel(this.getParentModel(), painted, poseStack, collector, lightCoords, state, -1, 1);
			}
		}
	}
}
