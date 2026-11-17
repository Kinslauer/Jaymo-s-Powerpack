package com.jaymoskingdoms.client;

import com.jaymoskingdoms.ChoppedLog;
import com.jaymoskingdoms.WoodVolume;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.25) A chopped log: the log it was, with the bites the axe has cut out of it ({@link WoodVolume}; Andy, 2026-11-16:
 * "i want the trunk to carve out, retaining the interior texture and all"): its bark outside and its end grain inside
 * the cut, the way Immersive Tree Chop draws one (Litewer, MIT; {@code docs/licenses/Immersive-Tree-Chop-MIT.txt}). A
 * log nothing has been cut from is drawn whole, with the game's own cracks while someone's axe is at it.
 */
public final class ChoppedLogRenderer implements BlockEntityRenderer<ChoppedLog.Entity, ChoppedLogRenderer.State> {
	public static final class State extends BlockEntityRenderState {
		final MovingBlockRenderState block = new MovingBlockRenderState();
		/** The log's cracks (null: none), how deep (0-9), and whether its model is see-through. */
		@Nullable List<BlockStateModelPart> cracks;
		int crackStage;
		boolean cutout;
		/** A carved log's faces (null: whole), each side's texture and light (by {@link Direction#ordinal}), and its grain's axis. */
		@Nullable List<WoodVolume.Face> faces;
		final TextureAtlasSprite[] sprites = new TextureAtlasSprite[6];
		final int[] light = new int[6];
		int axis;
	}

	/** How much darker the wood inside a cut is drawn (1 the same). */
	static final float INSIDE_SHADE = 0.85F;
	/** The side a face looks out of, by its axis and which way. */
	private static final Direction[][] SIDES = {{Direction.WEST, Direction.EAST}, {Direction.DOWN, Direction.UP}, {Direction.NORTH, Direction.SOUTH}};

	public ChoppedLogRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(ChoppedLog.Entity log, State state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(log, state, partialTicks, camera, breakProgress);
		state.block.blockState = log.log();
		state.block.blockPos = log.getBlockPos();
		state.block.randomSeedPos = log.getBlockPos();
		if (log.getLevel() instanceof ClientLevel level) {
			state.block.biome = level.getBiome(log.getBlockPos());
			state.block.cardinalLighting = level.cardinalLighting();
			state.block.lightEngine = level.getLightEngine();
		}
		state.cracks = null;
		state.faces = null;
		if (log.log().getRenderShape() != RenderShape.MODEL) {
			return;
		}
		BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(log.log());
		List<BlockStateModelPart> parts = new ArrayList<>();
		model.collectParts(RandomSource.create(log.log().getSeed(log.getBlockPos())), parts);
		if (!log.wood().whole() && log.getLevel() instanceof ClientLevel level && sprites(parts, state.sprites)) {
			state.faces = log.wood().faces();
			state.axis = log.log().hasProperty(ChoppedLog.AXIS) ? log.log().getValue(ChoppedLog.AXIS).ordinal() : 1;
			for (Direction side : Direction.values()) {
				state.light[side.ordinal()] = LightCoordsUtil.getLightCoords(level, log.getBlockPos().relative(side));
			}
		} else if (breakProgress != null) {
			// (the game draws a block's cracks on its own model, empty here: so a swing in progress cracks the log)
			state.cracks = List.copyOf(parts);
			state.crackStage = breakProgress.progress();
			state.cutout = model.hasMaterialFlag(1);
		}
	}

	/** Each side's texture off the log's own model, into {@code out}; false if a side has none (drawn whole instead). */
	private static boolean sprites(List<BlockStateModelPart> parts, TextureAtlasSprite[] out) {
		for (Direction side : Direction.values()) {
			out[side.ordinal()] = null;
			for (BlockStateModelPart part : parts) {
				List<BakedQuad> quads = part.getQuads(side);
				if (!quads.isEmpty()) {
					out[side.ordinal()] = quads.get(0).materialInfo().sprite();
					break;
				}
			}
			if (out[side.ordinal()] == null) {
				return false;
			}
		}
		return true;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.block.blockState == null || state.block.blockState.getRenderShape() != RenderShape.MODEL) {
			return;
		}
		poseStack.pushPose();
		if (state.faces != null) {
			carved(state, poseStack, collector);
		} else {
			collector.submitMovingBlock(poseStack, state.block, 0);
			if (state.cracks != null) {
				collector.submitBreakingBlockModel(poseStack, state.cracks, state.crackStage, state.cutout);
			}
		}
		poseStack.popPose();
	}

	/**
	 * The carved wood, face by face: bark on what's left of the log's own sides, and inside every cut (and on its ends)
	 * the end grain, each laid by where it is in the block so the texture runs unbroken across the cut.
	 */
	private static void carved(State state, PoseStack poseStack, SubmitNodeCollector collector) {
		List<WoodVolume.Face> faces = state.faces;
		int grain = state.axis;
		// (copied: the state is filled again for the next frame)
		TextureAtlasSprite[] sprites = state.sprites.clone();
		int[] light = state.light.clone();
		TextureAtlasSprite end = sprites[SIDES[grain][1].ordinal()];
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS), (pose, buffer) -> {
			for (WoodVolume.Face face : faces) {
				float plane = face.plane() / (float)WoodVolume.N;
				float u0 = face.u0() / (float)WoodVolume.N, v0 = face.v0() / (float)WoodVolume.N;
				float u1 = face.u1() / (float)WoodVolume.N, v1 = face.v1() / (float)WoodVolume.N;
				float[][] corners = {WoodVolume.point(face.axis(), plane, u0, v0), WoodVolume.point(face.axis(), plane, u1, v0),
					WoodVolume.point(face.axis(), plane, u1, v1), WoodVolume.point(face.axis(), plane, u0, v1)};
				if (face.positive() != (face.axis() == 2)) {
					// (wound so the face looks outward)
					float[] swap = corners[1];
					corners[1] = corners[3];
					corners[3] = swap;
				}
				Direction side = SIDES[face.axis()][face.positive() ? 1 : 0];
				boolean bark = face.outside() && face.axis() != grain;
				TextureAtlasSprite sprite = bark ? sprites[side.ordinal()] : end;
				int shade = face.outside() ? 255 : Math.round(255 * INSIDE_SHADE);
				int argb = 0xFF000000 | shade << 16 | shade << 8 | shade;
				// across the grain the texture goes by the two ways across it; along it, the grain runs down the texture
				int a = face.axis() == grain ? (grain + 1) % 3 : 3 - face.axis() - grain;
				int b = face.axis() == grain ? (grain + 2) % 3 : grain;
				for (float[] corner : corners) {
					float u = corner[a], v = face.axis() == grain ? corner[b] : 1.0F - corner[b];
					buffer.addVertex(pose, corner[0], corner[1], corner[2]).setColor(argb).setUv(sprite.getU(u), sprite.getV(v))
						.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light[side.ordinal()])
						.setNormal(pose, side.getStepX(), side.getStepY(), side.getStepZ());
				}
			}
		});
	}
}
