package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Cannon;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * (0.5.64) A field cannon drawn from blocks, scaled down and put together like the wagons: a dark oak carriage (two
 * cheeks, an axle, a trail on the ground behind with a cushion in the kingdom's colour for its gunner), two spoked
 * wheels with iron hubs, and a cast-iron barrel (two squares crossed, so it reads as round) with copper bands, raised
 * to its mark and kicking back when it fires. Original, no other mod's model.
 */
public final class CannonRenderer extends EntityRenderer<Cannon, CannonRenderer.State> {
	/** How far the barrel kicks back when it fires (blocks). */
	private static final float RECOIL = 0.35F;

	public static final class State extends EntityRenderState {
		int color;
		float yaw;
		float pitch;
		float recoil;
		final MovingBlockRenderState block = new MovingBlockRenderState();
	}

	private static final Map<String, BlockState> STATES = new HashMap<>();

	public CannonRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.7F;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected AABB getBoundingBoxForCulling(Cannon entity, float partialTicks) {
		return entity.getBoundingBox().inflate(2.0);
	}

	@Override
	public void extractRenderState(Cannon entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.color = entity.colorId();
		state.yaw = entity.getYRot(partialTicks);
		state.pitch = Mth.lerp(partialTicks, entity.pitchO, entity.barrelPitch());
		float since = entity.sinceFired + partialTicks;
		state.recoil = since < Cannon.RECOIL_TICKS ? RECOIL * (1.0F - since / Cannon.RECOIL_TICKS) : 0.0F;
		MovingBlockRenderState block = state.block;
		BlockPos at = entity.blockPosition();
		block.blockPos = at.above();
		block.randomSeedPos = at;
		if (entity.level() instanceof ClientLevel level) {
			block.biome = level.getBiome(at);
			block.cardinalLighting = level.cardinalLighting();
			block.lightEngine = level.getLightEngine();
		}
	}

	private static BlockState block(String name) {
		return STATES.computeIfAbsent(name, n -> BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(n)).defaultBlockState());
	}

	private static String dye(int color) {
		return (color >= 0 && color < DyeColor.values().length ? DyeColor.byId(color) : DyeColor.RED).getName();
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		BlockState wood = block("dark_oak_planks"), log = block("stripped_dark_oak_log"), spokes = block("spruce_planks");
		BlockState iron = block("iron_block"), cast = block("netherite_block"), band = block("copper_block");
		poseStack.pushPose();
		poseStack.rotateDegrees(Axis.YP, -state.yaw);
		// the carriage: two cheeks, the axle, the trail down to the ground behind and the gunner's cushion on it
		for (float x : new float[] {-0.34F, 0.22F}) {
			this.box(state, wood, poseStack, collector, x, 0.35F, -0.55F, x + 0.12F, 0.95F, 0.45F);
		}
		this.box(state, log, poseStack, collector, -0.55F, 0.4F, -0.05F, 0.55F, 0.5F, 0.05F);
		this.box(state, wood, poseStack, collector, -0.22F, 0.12F, -1.4F, 0.22F, 0.38F, -0.5F);
		this.box(state, block(dye(state.color) + "_wool"), poseStack, collector, -0.2F, 0.38F, -1.2F, 0.2F, 0.46F, -0.75F);
		// the wheels: two squares crossed, an iron hub
		for (float x : new float[] {-0.5F, 0.5F}) {
			for (int i = 0; i < 2; i++) {
				poseStack.pushPose();
				poseStack.translate(x, 0.45F, 0.0F);
				poseStack.rotateDegrees(Axis.XP, i * 45.0F);
				float s = 0.45F * 1.42F;
				poseStack.translate(-0.05F, -s / 2.0F, -s / 2.0F);
				poseStack.scale(0.1F, s, s);
				this.draw(state, spokes, poseStack, collector);
				poseStack.popPose();
			}
			this.box(state, iron, poseStack, collector, x - 0.08F, 0.37F, -0.08F, x + 0.08F, 0.53F, 0.08F);
		}
		// the barrel, about its trunnions, raised to its mark and kicked back when it fires
		poseStack.pushPose();
		poseStack.translate(0.0F, (float) Cannon.PIVOT, 0.0F);
		poseStack.rotateDegrees(Axis.XN, state.pitch);
		poseStack.translate(0.0F, 0.0F, -state.recoil);
		float muzzle = (float) Cannon.MUZZLE;
		this.tube(state, cast, poseStack, collector, -0.6F, muzzle - 0.15F, 0.36F);
		this.tube(state, band, poseStack, collector, muzzle - 0.15F, muzzle, 0.42F);
		this.tube(state, band, poseStack, collector, -0.6F, -0.45F, 0.42F);
		this.tube(state, cast, poseStack, collector, -0.78F, -0.6F, 0.16F);
		// the trunnions it swings on
		this.box(state, iron, poseStack, collector, -0.24F, -0.06F, -0.06F, 0.24F, 0.06F, 0.06F);
		poseStack.popPose();
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	/** A round-ish length of barrel from {@code z0} to {@code z1} along its axis, {@code width} across: two squares crossed. */
	private void tube(State state, BlockState look, PoseStack poseStack, SubmitNodeCollector collector, float z0, float z1, float width) {
		for (int i = 0; i < 2; i++) {
			poseStack.pushPose();
			poseStack.rotateDegrees(Axis.ZP, i * 45.0F);
			poseStack.translate(-width / 2.0F, -width / 2.0F, z0);
			poseStack.scale(width, width, z1 - z0);
			this.draw(state, look, poseStack, collector);
			poseStack.popPose();
		}
	}

	/** A box from its corner {@code (x, y, z)} to {@code (x2, y2, z2)}. */
	private void box(State state, BlockState look, PoseStack poseStack, SubmitNodeCollector collector, float x, float y, float z, float x2, float y2, float z2) {
		poseStack.pushPose();
		poseStack.translate(x, y, z);
		poseStack.scale(x2 - x, y2 - y, z2 - z);
		this.draw(state, look, poseStack, collector);
		poseStack.popPose();
	}

	private void draw(State state, BlockState blockState, PoseStack poseStack, SubmitNodeCollector collector) {
		if (blockState.getRenderShape() != RenderShape.MODEL) {
			return;
		}
		// (each its own: the collector draws them later, not now)
		MovingBlockRenderState block = new MovingBlockRenderState();
		block.blockState = blockState;
		block.blockPos = state.block.blockPos;
		block.randomSeedPos = state.block.randomSeedPos;
		block.biome = state.block.biome;
		block.cardinalLighting = state.block.cardinalLighting;
		block.lightEngine = state.block.lightEngine;
		collector.submitMovingBlock(poseStack, block, state.outlineColor);
	}
}
