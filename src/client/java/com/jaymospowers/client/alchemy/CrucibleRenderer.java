package com.jaymospowers.client.alchemy;

import com.jaymospowers.alchemy.crucible.CrucibleEntity;
import com.jaymospowers.alchemy.crucible.Crucibles;
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

/**
 * The liquid in a crucible: its surface, tinted by the mix of essences in it, rising with how full it is; it ripples a
 * little once the pot is hot.
 */
public class CrucibleRenderer implements BlockEntityRenderer<CrucibleEntity, CrucibleRenderer.State> {
	/** How far a hot surface bobs (block units), and how fast. */
	private static final float RIPPLE = 0.006F, RIPPLE_SPEED = 0.35F;

	public static class State extends BlockEntityRenderState {
		boolean water, hot;
		float surface, time;
		int colour;
	}

	private final SpriteGetter sprites;

	public CrucibleRenderer(BlockEntityRendererProvider.Context context) {
		this.sprites = context.sprites();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(CrucibleEntity pot, State state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(pot, state, partialTicks, camera, breakProgress);
		state.water = pot.water();
		state.hot = pot.hot();
		state.surface = (float) CrucibleEntity.surface(pot.fill());
		state.colour = Crucibles.mix(pot.essences());
		state.time = pot.getLevel() == null ? 0.0F : pot.getLevel().getGameTime() + partialTicks;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (!state.water) {
			return;
		}
		float y = state.surface + (state.hot ? (float) Math.sin(state.time * RIPPLE_SPEED) * RIPPLE : 0.0F);
		CrucibleLiquid.box(poseStack, collector, CrucibleLiquid.sprite(sprites), 3 / 16.0F, 4 / 16.0F, 3 / 16.0F, 13 / 16.0F, y, 13 / 16.0F,
			state.colour, state.lightCoords, false);
	}
}
