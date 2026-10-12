package com.jaymospowers.client.tinkering;

import com.jaymospowers.tinkering.smeltery.Molten;
import com.jaymospowers.tinkering.smeltery.SmelteryEntity;
import com.jaymospowers.tinkering.smeltery.Tank;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Draws the smeltery's molten pool inside it, layer on layer, rising with how full it is. */
public class SmelteryRenderer implements BlockEntityRenderer<SmelteryEntity, SmelteryRenderer.State> {
	public static class State extends BlockEntityRenderState {
		final List<Molten> molten = new ArrayList<>();
		final List<Float> heights = new ArrayList<>();
		float x0, y0, z0, x1, z1;
	}

	private final SpriteGetter sprites;

	public SmelteryRenderer(BlockEntityRendererProvider.Context context) {
		this.sprites = context.sprites();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(SmelteryEntity smeltery, State state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(smeltery, state, partialTicks, camera, breakProgress);
		state.molten.clear();
		state.heights.clear();
		Tank tank = smeltery.tank();
		if (!smeltery.formed() || tank.layers().isEmpty()) {
			return;
		}
		BlockPos pos = smeltery.getBlockPos(), low = smeltery.low(), high = smeltery.high();
		state.x0 = low.getX() - pos.getX();
		state.y0 = low.getY() - pos.getY();
		state.z0 = low.getZ() - pos.getZ();
		state.x1 = high.getX() + 1 - pos.getX();
		state.z1 = high.getZ() + 1 - pos.getZ();
		float height = high.getY() + 1 - low.getY();
		int capacity = Math.max(1, Math.max(tank.capacity(), tank.total()));
		for (Tank.Layer layer : tank.layers()) {
			state.molten.add(layer.molten);
			state.heights.add(height * layer.amount / capacity);
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		float y = state.y0;
		float inset = 0.001F;
		for (int i = 0; i < state.molten.size(); i++) {
			float top = y + state.heights.get(i);
			MoltenQuads.box(poseStack, collector, MoltenQuads.sprite(sprites, state.molten.get(i)), state.x0 + inset, y, state.z0 + inset,
				state.x1 - inset, top, state.z1 - inset, i == state.molten.size() - 1);
			y = top;
		}
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}
}
