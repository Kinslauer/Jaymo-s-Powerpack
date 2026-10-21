package com.jaymoskingdoms.client;

import com.jaymoskingdoms.PlotLines;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** (batch 4) The lines strung between plot stakes, each drawn once (from its lower-numbered end) as a taut cord. */
public final class StakeLineRenderer implements BlockEntityRenderer<PlotLines.Stake, StakeLineRenderer.State> {
	/** Where on a stake a line is tied: the knot near its top. */
	static final double KNOT_Y = 0.7;

	public static final class State extends BlockEntityRenderState {
		final List<EntityRenderState.LeashState> lines = new ArrayList<>();
	}

	public StakeLineRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(PlotLines.Stake stake, State state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(stake, state, partialTicks, camera, breakProgress);
		state.lines.clear();
		BlockPos here = stake.getBlockPos();
		Level level = stake.getLevel();
		for (BlockPos other : stake.lines()) {
			if (here.compareTo(other) > 0) {
				continue;
			}
			EntityRenderState.LeashState line = new EntityRenderState.LeashState();
			line.offset = new Vec3(0.5, KNOT_Y, 0.5);
			line.start = new Vec3(here.getX() + 0.5, here.getY() + KNOT_Y, here.getZ() + 0.5);
			line.end = new Vec3(other.getX() + 0.5, other.getY() + KNOT_Y, other.getZ() + 0.5);
			if (level != null) {
				line.startBlockLight = level.getBrightness(LightLayer.BLOCK, here);
				line.startSkyLight = level.getBrightness(LightLayer.SKY, here);
				line.endBlockLight = level.getBrightness(LightLayer.BLOCK, other);
				line.endSkyLight = level.getBrightness(LightLayer.SKY, other);
			}
			line.slack = false;
			state.lines.add(line);
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		for (EntityRenderState.LeashState line : state.lines) {
			collector.submitLeash(poseStack, line);
		}
	}

	/** A line reaches far past the stake itself. */
	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}
}
