package com.jaymospowers.client.tinkering;

import com.jaymospowers.tinkering.smeltery.FaucetEntity;
import com.jaymospowers.tinkering.smeltery.Molten;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The stream of metal falling from a faucet that's pouring, down into the cast below. */
public class FaucetRenderer implements BlockEntityRenderer<FaucetEntity, FaucetRenderer.State> {
	public static class State extends BlockEntityRenderState {
		@Nullable Molten molten;
		Direction facing = Direction.NORTH;
	}

	private final SpriteGetter sprites;

	public FaucetRenderer(BlockEntityRendererProvider.Context context) {
		this.sprites = context.sprites();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(FaucetEntity faucet, State state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(faucet, state, partialTicks, camera, breakProgress);
		state.molten = faucet.pouring();
		state.facing = faucet.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.molten == null) {
			return;
		}
		// the spout's mouth sits 10-12 blocks/16 from the drain; the stream falls from it to the table below
		float cx = 0.5F, cz = 0.5F;
		float out = 11 / 16.0F - 0.5F;
		switch (state.facing) {
			case NORTH -> cz = 0.5F - out;
			case SOUTH -> cz = 0.5F + out;
			case EAST -> cx = 0.5F + out;
			case WEST -> cx = 0.5F - out;
			default -> {
			}
		}
		float r = 1.5F / 16.0F;
		MoltenQuads.box(poseStack, collector, MoltenQuads.sprite(sprites, state.molten), cx - r, -1.0F + 1 / 16.0F, cz - r, cx + r,
			5 / 16.0F, cz + r, false);
	}
}
