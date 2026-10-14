package com.jaymoskingdoms.client;

import com.jaymoskingdoms.sailing.Hull;
import com.jaymoskingdoms.sailing.OarBlock;
import com.jaymoskingdoms.sailing.SailBlock;
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
 * (0.7.7) A ship under sail, block for block, turned to its heading, heeling in its turns and rocking on the swell
 * (harder in a storm). Chests are drawn as barrels while it sails (a chest is drawn by its block entity, which stays
 * in the hold until the ship anchors).
 */
public final class ShipRenderer extends EntityRenderer<Ship, ShipRenderer.State> {
	/** The swell: its roll and pitch (degrees) in fair weather and in a storm, and how fast it rolls. */
	static final float ROLL_FAIR = 1.2F, ROLL_STORM = 4.5F, PITCH_FAIR = 0.6F, PITCH_STORM = 2.5F, SWELL_SPEED = 0.06F;
	/** Below this much sail set (0 to 1), the sails are drawn furled on their yards. */
	static final float FURLED_BELOW = 0.1F;
	/**
	 * (0.7.18) Oars run out: how far each stroke sweeps fore and aft and how steeply it dips (degrees), how much deeper the
	 * blade bites on the pull, and how much longer the oar is drawn than its model (stretched along its length).
	 */
	static final float OAR_SWEEP = 28.0F, OAR_DIP = 36.0F, OAR_BITE = 12.0F, OAR_LENGTH = 2.5F;
	/** Where an oar turns in its lock, in its (north-facing) model's pixels. */
	static final float PIVOT_X = 8.0F, PIVOT_Y = 5.5F, PIVOT_Z = 1.0F;

	public static final class State extends EntityRenderState {
		final List<MovingBlockRenderState> blocks = new ArrayList<>();
		final List<Ship.Piece> pieces = new ArrayList<>();
		float yaw, heel, pitch;
		Model.Simple dryMask;
		/** (0.7.18) Oars run out, drawn on their own: each one's piece, its facing's turn, its sweep and dip, and its look. */
		final List<Ship.Piece> oars = new ArrayList<>();
		final List<float[]> oarAngles = new ArrayList<>();
		final List<MovingBlockRenderState> oarBlocks = new ArrayList<>();
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
		state.oars.clear();
		state.oarAngles.clear();
		state.oarBlocks.clear();
		Hull h = ship.hull();
		if (h == null || !(ship.level() instanceof ClientLevel level)) {
			return;
		}
		// sails all but furled are drawn rolled up on their yards
		boolean furled = ship.sailSet() < FURLED_BELOW;
		int rowing = ship.rowing();
		// the stroke: the blade sweeps aft through the water on the pull and comes forward lifted on the return
		float phase = (ship.tickCount + partialTicks) % Ship.STROKE / Ship.STROKE * Mth.TWO_PI;
		float sweep = Mth.cos(phase) * OAR_SWEEP * rowing, dip = OAR_DIP + OAR_BITE * Mth.sin(phase);
		for (Ship.Piece piece : h.pieces) {
			BlockState shown = piece.blockState();
			if (rowing != 0 && shown.getBlock() instanceof OarBlock) {
				Direction facing = shown.getValue(OarBlock.FACING);
				shown = shown.setValue(OarBlock.OAR, OarBlock.Oar.LOCK);
				MovingBlockRenderState loom = new MovingBlockRenderState();
				loom.blockState = shown.setValue(OarBlock.OAR, OarBlock.Oar.LOOM).setValue(OarBlock.FACING, Direction.NORTH);
				Vec3 at = ship.toWorld(piece.x(), piece.y() + 0.5, piece.z());
				loom.blockPos = BlockPos.containing(at);
				loom.randomSeedPos = new BlockPos(piece.x(), piece.y(), piece.z());
				loom.biome = level.getBiome(loom.blockPos);
				loom.cardinalLighting = level.cardinalLighting();
				loom.lightEngine = level.getLightEngine();
				state.oars.add(piece);
				// the oars on her left sweep the other way round to those on her right, so all pull her ahead
				float side = facing == Direction.WEST || facing == Direction.SOUTH ? -1.0F : 1.0F;
				state.oarAngles.add(new float[] {facing.toYRot() + 180.0F, sweep * side, dip});
				state.oarBlocks.add(loom);
			}
			if (shown.getBlock() instanceof ChestBlock) {
				shown = Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP);
			}
			if (furled && shown.getBlock() instanceof SailBlock) {
				shown = shown.setValue(SailBlock.FURLED, true);
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
		state.dryMask = h.enclosed.length == 0 || ship.foundering() ? null : MASKS.computeIfAbsent(h, ShipRenderer::mask);
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
		for (int i = 0; i < state.oars.size(); i++) {
			Ship.Piece p = state.oars.get(i);
			float[] a = state.oarAngles.get(i);
			poseStack.pushPose();
			poseStack.translate(p.x(), p.y(), p.z());
			// turned to face outboard as its block does, then swung in its lock
			poseStack.rotateDegrees(Axis.YP, -a[0]);
			poseStack.translate(-0.5F, 0.0F, -0.5F);
			poseStack.translate(PIVOT_X / 16.0F, PIVOT_Y / 16.0F, PIVOT_Z / 16.0F);
			poseStack.rotateDegrees(Axis.YP, a[1]);
			poseStack.rotateDegrees(Axis.XP, -a[2]);
			poseStack.scale(1.0F, 1.0F, OAR_LENGTH);
			poseStack.translate(-PIVOT_X / 16.0F, -PIVOT_Y / 16.0F, -PIVOT_Z / 16.0F);
			collector.submitMovingBlock(poseStack, state.oarBlocks.get(i), state.outlineColor);
			poseStack.popPose();
		}
		if (state.dryMask != null) {
			collector.submitModel(state.dryMask, Unit.INSTANCE, poseStack, RenderTypes.waterMask(), state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
		}
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}
