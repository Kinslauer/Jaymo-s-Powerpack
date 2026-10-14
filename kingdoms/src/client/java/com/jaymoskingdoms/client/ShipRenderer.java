package com.jaymoskingdoms.client;

import com.jaymoskingdoms.sailing.Hull;
import com.jaymoskingdoms.sailing.Ship;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Unit;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.5) A ship under sail, block for block, turned to its heading, heeling in its turns and rocking on the swell
 * (harder in a storm). Chests are drawn as barrels while it sails (a chest is drawn by its block entity, which stays
 * in the hold until the ship anchors).
 */
public final class ShipRenderer extends EntityRenderer<Ship, ShipRenderer.State> {
	/** The swell: its roll and pitch (degrees) in fair weather and in a storm, and how fast it rolls. */
	static final float ROLL_FAIR = 1.2F, ROLL_STORM = 4.5F, PITCH_FAIR = 0.6F, PITCH_STORM = 2.5F, SWELL_SPEED = 0.06F;

	public static final class State extends EntityRenderState {
		final List<MovingBlockRenderState> blocks = new ArrayList<>();
		final List<Ship.Piece> pieces = new ArrayList<>();
		float yaw, heel, pitch;
		Model.Simple dryMask;
	}

	/** Each hull's dry hold, as a mask that keeps the sea it floats in from showing inside it (as a boat's does). */
	private static final Map<Hull, Model.Simple> MASKS = new WeakHashMap<>();

	public ShipRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.0F;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected AABB getBoundingBoxForCulling(Ship entity, float partialTicks) {
		return entity.getBoundingBox();
	}

	@Override
	public void extractRenderState(Ship ship, State state, float partialTicks) {
		super.extractRenderState(ship, state, partialTicks);
		state.blocks.clear();
		state.pieces.clear();
		Hull h = ship.hull();
		if (h == null || !(ship.level() instanceof ClientLevel level)) {
			return;
		}
		for (Ship.Piece piece : h.pieces) {
			BlockState shown = piece.blockState();
			if (shown.getBlock() instanceof ChestBlock) {
				shown = Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP);
			}
			if (shown.getRenderShape() != RenderShape.MODEL) {
				continue;
			}
			Vec3 at = ship.toWorld(piece.x(), piece.y() + 0.5, piece.z());
			MovingBlockRenderState block = new MovingBlockRenderState();
			block.blockState = shown;
			block.blockPos = BlockPos.containing(at);
			block.randomSeedPos = new BlockPos(piece.x(), piece.y(), piece.z());
			block.biome = level.getBiome(block.blockPos);
			block.cardinalLighting = level.cardinalLighting();
			block.lightEngine = level.getLightEngine();
			state.blocks.add(block);
			state.pieces.add(piece);
		}
		state.dryMask = h.enclosed.length == 0 ? null : MASKS.computeIfAbsent(h, ShipRenderer::mask);
		state.yaw = Mth.rotLerp(partialTicks, ship.yRotO, ship.getYRot());
		float storm = level.getThunderLevel(partialTicks), t = (ship.tickCount + partialTicks) * SWELL_SPEED;
		// a bigger ship rocks less
		float size = 10.0F / Math.max(10.0F, h.waterline);
		state.heel = Mth.lerp(partialTicks, ship.heelO, ship.heel) + Mth.sin(t) * Mth.lerp(storm, ROLL_FAIR, ROLL_STORM) * size;
		state.pitch = Mth.sin(t * 0.73F + 1.3F) * Mth.lerp(storm, PITCH_FAIR, PITCH_STORM) * size;
	}

	/** A box over each column of the hold, from its lowest dry cell to the top of its highest. */
	private static Model.Simple mask(Hull h) {
		Map<Long, int[]> columns = new HashMap<>();
		for (long cell : h.enclosed) {
			BlockPos p = BlockPos.of(cell);
			int[] span = columns.computeIfAbsent(BlockPos.asLong(p.getX(), 0, p.getZ()), k -> new int[] {Integer.MAX_VALUE, Integer.MIN_VALUE});
			span[0] = Math.min(span[0], p.getY());
			span[1] = Math.max(span[1], p.getY());
		}
		MeshDefinition mesh = new MeshDefinition();
		CubeListBuilder boxes = CubeListBuilder.create();
		for (Map.Entry<Long, int[]> e : columns.entrySet()) {
			BlockPos c = BlockPos.of(e.getKey());
			int[] span = e.getValue();
			boxes.addBox((c.getX() - 0.5F) * 16.0F, span[0] * 16.0F, (c.getZ() - 0.5F) * 16.0F, 16.0F, (span[1] - span[0] + 1) * 16.0F, 16.0F);
		}
		mesh.getRoot().addOrReplaceChild("hold", boxes, PartPose.ZERO);
		return new Model.Simple(LayerDefinition.create(mesh, 16, 16).bakeRoot(), texture -> RenderTypes.waterMask());
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.rotateDegrees(Axis.YP, -state.yaw);
		poseStack.rotateDegrees(Axis.ZP, state.heel);
		poseStack.rotateDegrees(Axis.XP, state.pitch);
		for (int i = 0; i < state.blocks.size(); i++) {
			Ship.Piece p = state.pieces.get(i);
			poseStack.pushPose();
			// a piece's place is its column's middle; a block is drawn from its corner
			poseStack.translate(p.x() - 0.5F, p.y(), p.z() - 0.5F);
			collector.submitMovingBlock(poseStack, state.blocks.get(i), state.outlineColor);
			poseStack.popPose();
		}
		if (state.dryMask != null) {
			collector.submitModel(state.dryMask, Unit.INSTANCE, poseStack, RenderTypes.waterMask(), state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
		}
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}
