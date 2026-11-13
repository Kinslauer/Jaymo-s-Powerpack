package com.jaymospowers.client.rough;

import com.jaymospowers.rough.HotAirBalloon;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A hot air balloon drawn from vanilla blocks: a basket of dark oak planks with a rope-fence at each corner, a campfire for
 * the burner, and over it a striped envelope of red and white wool, a shell of rings rounding out and in again. It sways
 * a little as it floats, and leans into the burner's heat when the burner is on.
 */
public final class HotAirBalloonRenderer extends EntityRenderer<HotAirBalloon, HotAirBalloonRenderer.State> {
	public static final class State extends EntityRenderState {
		final List<MovingBlockRenderState> blocks = new ArrayList<>();
		float yaw, sway;
	}

	/** One block of the shape: where it sits from the basket's middle, and what it is. */
	private record Cell(int x, int y, int z, BlockState state) {
	}

	/** Radius of each ring of the envelope from its lowest ring up (blocks from the middle). */
	private static final int[] RINGS = {1, 2, 3, 3, 3, 3, 2, 1};
	/** How far above the basket's floor the envelope starts. */
	private static final int LIFT = 3;
	private static final List<Cell> SHAPE = shape();

	public HotAirBalloonRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.9F;
	}

	private static List<Cell> shape() {
		List<Cell> cells = new ArrayList<>();
		BlockState planks = Blocks.DARK_OAK_PLANKS.defaultBlockState();
		for (int x = -1; x <= 0; x++) {
			for (int z = -1; z <= 0; z++) {
				cells.add(new Cell(x, 0, z, planks));
			}
		}
		BlockState rope = Blocks.OAK_FENCE.defaultBlockState();
		for (int[] corner : new int[][] {{-1, -1}, {0, -1}, {-1, 0}, {0, 0}}) {
			cells.add(new Cell(corner[0] + (corner[0] < 0 ? -0 : 0), 1, corner[1], rope));
			cells.add(new Cell(corner[0], 2, corner[1], rope));
		}
		cells.add(new Cell(0, 1, 0, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false)));
		for (int ring = 0; ring < RINGS.length; ring++) {
			int r = RINGS[ring];
			for (int x = -r; x <= r; x++) {
				for (int z = -r; z <= r; z++) {
					double d = Math.sqrt(x * x + z * z);
					if (d > r + 0.4 || d < r - 1.1 && ring > 0 && ring < RINGS.length - 1) {
						continue;
					}
					double angle = Math.atan2(z, x);
					boolean red = Math.floorMod((int) Math.floor((angle + Math.PI) / (Math.PI / 4.0)), 2) == 0;
					cells.add(new Cell(x, LIFT + ring, z, wool(red ? "red_wool" : "white_wool")));
				}
			}
		}
		return cells;
	}

	/** Coloured wool has no field on Blocks: looked up by name. */
	private static BlockState wool(String name) {
		return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(name)).defaultBlockState();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected AABB getBoundingBoxForCulling(HotAirBalloon entity, float partialTicks) {
		return super.getBoundingBoxForCulling(entity, partialTicks).inflate(3.0, 5.0, 3.0).move(0.0, 3.0, 0.0);
	}

	@Override
	public void extractRenderState(HotAirBalloon entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.yaw = entity.getYRot();
		float t = entity.tickCount + partialTicks;
		state.sway = (float) Math.sin(t * 0.04 + entity.getId()) * 1.5F + (entity.burning() ? 2.5F : 0.0F);
		state.blocks.clear();
		BlockPos at = BlockPos.containing(entity.getX(), entity.getY() + 1.0, entity.getZ());
		boolean lit = entity.burning();
		for (Cell cell : SHAPE) {
			MovingBlockRenderState block = new MovingBlockRenderState();
			block.blockState = cell.state().is(Blocks.CAMPFIRE) ? cell.state().setValue(CampfireBlock.LIT, lit) : cell.state();
			block.blockPos = at;
			block.randomSeedPos = at.offset(cell.x(), cell.y(), cell.z());
			if (entity.level() instanceof ClientLevel level) {
				block.biome = level.getBiome(at);
				block.cardinalLighting = level.cardinalLighting();
				block.lightEngine = level.getLightEngine();
			}
			state.blocks.add(block);
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.rotateDegrees(Axis.YP, -state.yaw);
		poseStack.rotateDegrees(Axis.ZP, state.sway);
		poseStack.translate(0.0F, 0.1F, 0.0F);
		// each block is drawn on its own cell, the middle of the basket on the entity
		for (int i = 0; i < state.blocks.size(); i++) {
			Cell cell = SHAPE.get(i);
			poseStack.pushPose();
			poseStack.translate(cell.x(), cell.y(), cell.z());
			collector.submitMovingBlock(poseStack, state.blocks.get(i), state.outlineColor);
			poseStack.popPose();
		}
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}
