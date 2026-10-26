package com.jaymospowers.client.alchemy;

import com.jaymospowers.alchemy.crucible.JarEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The essence in a jar: a column of liquid in the essence's colour, as tall as the jar is full. */
public class JarRenderer implements BlockEntityRenderer<JarEntity, JarRenderer.State> {
	public static class State extends BlockEntityRenderState {
		float fill;
		int colour;
	}

	private final SpriteGetter sprites;

	public JarRenderer(BlockEntityRendererProvider.Context context) {
		this.sprites = context.sprites();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(JarEntity jar, State state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(jar, state, partialTicks, camera, breakProgress);
		state.fill = jar.fill();
		state.colour = jar.essence() == null ? 0 : jar.essence().colour;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.fill <= 0) {
			return;
		}
		// inside the glass: floor at 1/16, up to 11/16 when full (the glass walls are 3..13, so this sits 1/16 in)
		float top = 1 / 16.0F + 10 / 16.0F * Math.max(0.04F, state.fill);
		CrucibleLiquid.box(poseStack, collector, CrucibleLiquid.sprite(sprites), 4 / 16.0F, 1 / 16.0F, 4 / 16.0F, 12 / 16.0F, top, 12 / 16.0F,
			state.colour, state.lightCoords, true);
	}
}
