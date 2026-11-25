package com.jaymospowers.client.body;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.LecternScreen;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.LecternRenderer;
import net.minecraft.client.renderer.blockentity.state.LecternRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A lectern's book opens big in the world while you read it (after Hands-On Vanilla's idea; the code is our own): vanilla's
 * own lectern drawing, the book it already draws, grown about where it lies and lifted up off the stand, and settling
 * back when you close the screen. Only the reader sees it grow: nothing tells anyone else who is reading.
 */
public class LecternReadingRenderer implements BlockEntityRenderer<LecternBlockEntity, LecternReadingRenderer.State> {
	// ===== the knobs ================================================================================================
	/** How many times bigger the book is read at, and how far it is lifted (blocks) while it is. */
	static final float BIG = 2.2F, LIFT = 0.38F;
	/** How fast it opens and closes (share a tick). */
	static final float EASE = 0.2F;
	// ================================================================================================================

	public static class State extends LecternRenderState {
		float open;
	}

	private final LecternRenderer vanilla;
	/** Per lectern: how open it is, and the game time it was last drawn. */
	private final Map<BlockPos, float[]> opening = new HashMap<>();

	public LecternReadingRenderer(BlockEntityRendererProvider.Context context) {
		this.vanilla = new LecternRenderer(context);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(LecternBlockEntity lectern, State state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		this.vanilla.extractRenderState(lectern, state, partialTicks, camera, breakProgress);
		Minecraft minecraft = Minecraft.getInstance();
		boolean reading = lectern.hasBook() && minecraft.gui.screen() instanceof LecternScreen
			&& minecraft.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(lectern.getBlockPos());
		float time = lectern.getLevel() == null ? 0.0F : lectern.getLevel().getGameTime() + partialTicks;
		float[] now = this.opening.computeIfAbsent(lectern.getBlockPos().immutable(), p -> new float[] {0.0F, time});
		float dt = Mth.clamp(time - now[1], 0.0F, 3.0F);
		now[1] = time;
		now[0] += ((reading ? 1.0F : 0.0F) - now[0]) * Math.min(1.0F, EASE * dt);
		state.open = now[0];
		if (!reading && now[0] < 0.005F) {
			this.opening.remove(lectern.getBlockPos());
			state.open = 0.0F;
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.open <= 0.0F) {
			this.vanilla.submit(state, poseStack, collector, camera);
			return;
		}
		// about the spot vanilla lays the book (the middle of the stand's top), the book grown and raised
		float eased = state.open * state.open * (3.0F - 2.0F * state.open);
		poseStack.pushPose();
		poseStack.translate(0.5F, 1.0625F + LIFT * eased, 0.5F);
		float scale = Mth.lerp(eased, 1.0F, BIG);
		poseStack.scale(scale, scale, scale);
		poseStack.translate(-0.5F, -1.0625F, -0.5F);
		this.vanilla.submit(state, poseStack, collector, camera);
		poseStack.popPose();
	}
}
