package com.jaymospowers.client;

import com.jaymospowers.core.BlockCluster;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** A torn-out chunk of blocks: each block drawn where it sits in the chunk, the whole thing tumbling as it flies. */
public final class BlockClusterRenderer extends EntityRenderer<BlockCluster, BlockClusterRenderer.State> {
	public static final class State extends EntityRenderState {
		final List<MovingBlockRenderState> blocks = new ArrayList<>();
		final List<Vec3> offsets = new ArrayList<>();
		float tumble;
		float yaw;
		boolean steady;
		float scale = 1.0F;
		/** Resting debris: its fixed lean (degrees), and how far it has turned in flight; NaN if it isn't debris. */
		float restYaw = Float.NaN, restTilt, spin;
		/**
		 * Ridden as an earthboard: drawn right under its rider's feet this frame (an offset from where the entity is,
		 * which trails the rider by the server's lag), turned with the rider's heading since boarding ({@code turn}),
		 * and banked and tipped with them (see GravityBoardPose); {@code pivot}: its top over its middle. Null if not ridden.
		 */
		@org.jspecify.annotations.Nullable Vec3 follow;
		float heading, turn, lean, tilt, bob, pivot;
	}

	/** Each ridden board's rider heading when first seen ridden (its turn is drawn from there). */
	private static final it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap BOARDED = new it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap();

	public BlockClusterRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.7F;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	/** Drawn bigger (see {@link BlockCluster#SCALE}): culled by the size it's drawn at, not its little box. */
	@Override
	protected AABB getBoundingBoxForCulling(BlockCluster entity, float partialTicks) {
		AABB box = super.getBoundingBoxForCulling(entity, partialTicks);
		Float scale = entity.getAttached(BlockCluster.SCALE);
		return scale != null && scale >= 1.0F ? box.inflate(2.0 * scale) : box;
	}

	@Override
	public void extractRenderState(BlockCluster entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		List<BlockCluster.Piece> pieces = entity.pieces();
		AABB bounds = BlockCluster.bounds(pieces);
		Vec3 middle = bounds.getCenter();
		state.blocks.clear();
		state.offsets.clear();
		BlockPos at = BlockPos.containing(entity.getX(), entity.getBoundingBox().maxY, entity.getZ());
		for (BlockCluster.Piece piece : pieces) {
			MovingBlockRenderState block = new MovingBlockRenderState();
			block.blockState = piece.blockState();
			block.blockPos = at;
			block.randomSeedPos = at.offset(piece.x(), piece.y(), piece.z());
			if (entity.level() instanceof ClientLevel level) {
				block.biome = level.getBiome(at);
				block.cardinalLighting = level.cardinalLighting();
				block.lightEngine = level.getLightEngine();
			}
			state.blocks.add(block);
			state.offsets.add(new Vec3(piece.x() - middle.x, piece.y() - middle.y, piece.z() - middle.z));
		}
		// a thrown chunk tumbles; a held one turns slowly
		double speed = entity.getDeltaMovement().length();
		state.tumble = (entity.tickCount + partialTicks) * (float)Math.min(25.0, 2.0 + speed * 18.0);
		state.yaw = entity.getId() * 37.0F;
		state.steady = entity.getAttached(BlockCluster.STEADY) != null || entity.board();
		BlockCluster.Rest rest = entity.getAttached(BlockCluster.REST);
		state.restYaw = rest == null ? Float.NaN : rest.yaw();
		state.restTilt = rest == null ? 0.0F : rest.tilt();
		state.spin = net.minecraft.util.Mth.lerp(partialTicks, entity.spinO, entity.spin);
		Float scale = entity.getAttached(BlockCluster.SCALE);
		state.scale = scale == null ? 1.0F : scale;
		ridden(entity, state, partialTicks, bounds);
	}

	/** An earthboard under its rider: where and how it's drawn this frame (see {@link State#follow}). */
	private static void ridden(BlockCluster entity, State state, float partialTicks, AABB bounds) {
		state.follow = null;
		Player rider = null;
		for (Player player : entity.level().players()) {
			Integer board = player.getAttached(com.jaymospowers.power.gravity.GravityPower.ROCK_FLIGHT);
			if (board != null && board == entity.getId()) {
				rider = player;
				break;
			}
		}
		if (rider == null) {
			BOARDED.remove(entity.getId());
			return;
		}
		// (as GravityRockFlight.drop: its top level with the feet)
		double drop = 0.7 + bounds.getYsize() * 0.5;
		Vec3 under = rider.getPosition(partialTicks).subtract(0.0, drop, 0.0);
		state.follow = under.subtract(entity.getPosition(partialTicks));
		float heading = net.minecraft.util.Mth.rotLerp(partialTicks, rider.yBodyRotO, rider.yBodyRot);
		if (!BOARDED.containsKey(entity.getId())) {
			BOARDED.put(entity.getId(), heading);
		}
		state.heading = heading;
		state.turn = net.minecraft.util.Mth.wrapDegrees(heading - BOARDED.get(entity.getId()));
		com.jaymospowers.client.power.gravity.GravityBoardPose.Ride ride = com.jaymospowers.client.power.gravity.GravityBoardPose.ride(rider, partialTicks);
		state.lean = ride.lean();
		state.tilt = ride.tilt();
		state.bob = ride.bob();
		state.pivot = (float)(bounds.getYsize() * 0.5);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		if (state.follow != null) {
			poseStack.translate(state.follow.x, state.follow.y + state.bob, state.follow.z);
		}
		poseStack.translate(0.0F, 0.7F * state.scale, 0.0F);
		poseStack.scale(state.scale, state.scale, state.scale);
		if (state.follow != null) {
			// ridden: about the middle of its top (under the feet), into the rider's frame (+Z ahead, +X their left),
			// banked with their lean and tipped nose-up with the tilt, then back, turned as they've turned since boarding
			poseStack.translate(0.0F, state.pivot, 0.0F);
			poseStack.rotateDegrees(Axis.YP, -state.heading);
			poseStack.rotateDegrees(Axis.ZP, -state.lean);
			poseStack.rotateDegrees(Axis.XP, -state.tilt);
			poseStack.rotateDegrees(Axis.YP, state.heading);
			poseStack.rotateDegrees(Axis.YP, -state.turn);
			poseStack.translate(0.0F, -state.pivot, 0.0F);
		} else if (!Float.isNaN(state.restYaw)) {
			// debris: leaning where it lies, turning over only while it flies
			poseStack.rotateDegrees(Axis.YP, state.restYaw);
			poseStack.rotateDegrees(Axis.XP, state.restTilt + state.spin);
		} else if (!state.steady) {
			poseStack.rotateDegrees(Axis.YP, state.yaw);
			poseStack.rotateDegrees(Axis.XP, state.tumble * 0.6F);
			poseStack.rotateDegrees(Axis.ZP, state.tumble * 0.25F);
			// the whole chunk shrunk a touch, so it reads as one lump rather than a wall section
			poseStack.scale(0.8F, 0.8F, 0.8F);
		}
		for (int i = 0; i < state.blocks.size(); i++) {
			MovingBlockRenderState block = state.blocks.get(i);
			if (block.blockState.getRenderShape() != RenderShape.MODEL) {
				continue;
			}
			Vec3 off = state.offsets.get(i);
			poseStack.pushPose();
			poseStack.translate(off.x, off.y, off.z);
			collector.submitMovingBlock(poseStack, block, state.outlineColor);
			poseStack.popPose();
		}
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}
