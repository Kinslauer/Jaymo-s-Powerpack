package com.jaymospowers.client.air;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Blocks settle into place (Reference Mods patch 14, X11, after ImmersiveVFX's idea; our own code). A block you place
 * comes down a hair big and settles to its size over {@link #TICKS}, so it lands with a little give instead of
 * snapping in. Drawn over the block (its own model, scaled about its middle, in its own light and tint) by
 * {@code BlockSettleMixin}'s placings; the block in the world is drawn as ever under it, which is why it settles down to
 * size rather than growing up to it: the world's block can't be hidden for the moment without rebuilding its chunk.
 */
public final class BlockSettle {
	/** How long it takes to settle (ticks) and how much bigger than itself it lands (share of its size). */
	static final float TICKS = 5.0F, OVERSHOOT = 0.1F;
	/** The most settling at once. */
	static final int MOST = 16;
	private static final Direction[] SIDES = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null};

	private record Placed(BlockPos pos, BlockState state, long at) {
	}

	private static final List<Placed> SETTLING = new ArrayList<>();
	private static final Map<BlockState, List<BakedQuad>> QUADS = new HashMap<>();

	private BlockSettle() {
	}

	static void init() {
		LevelRenderEvents.COLLECT_SUBMITS.register(BlockSettle::render);
	}

	/** The local player just placed {@code state} at {@code pos}. */
	public static void placed(ClientLevel level, BlockPos pos, BlockState state) {
		if (state.isAir() || state.getRenderShape() != RenderShape.MODEL || !state.getFluidState().isEmpty()) {
			return;
		}
		if (SETTLING.size() >= MOST) {
			SETTLING.removeFirst();
		}
		SETTLING.add(new Placed(pos.immutable(), state, level.getGameTime()));
	}

	private static List<BakedQuad> quads(Minecraft minecraft, BlockState state) {
		return QUADS.computeIfAbsent(state, s -> {
			BlockStateModel model = minecraft.getModelManager().getBlockStateModelSet().get(s);
			List<BlockStateModelPart> parts = new ArrayList<>();
			model.collectParts(RandomSource.create(42L), parts);
			List<BakedQuad> out = new ArrayList<>();
			for (BlockStateModelPart part : parts) {
				for (Direction side : SIDES) {
					out.addAll(part.getQuads(side));
				}
			}
			return List.copyOf(out);
		});
	}

	private static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			SETTLING.clear();
			QUADS.clear();
			return;
		}
		if (SETTLING.isEmpty()) {
			return;
		}
		float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		long now = level.getGameTime();
		SETTLING.removeIf(p -> now - p.at > TICKS || level.getBlockState(p.pos) != p.state);
		if (SETTLING.isEmpty()) {
			return;
		}
		Vec3 camera = context.levelState().cameraRenderState.pos;
		PoseStack poseStack = context.poseStack();
		List<Placed> drawn = List.copyOf(SETTLING);
		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS), (pose, consumer) -> {
			QuadInstance instance = new QuadInstance();
			instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);
			for (Placed p : drawn) {
				float t = Math.min(1.0F, (now - p.at + partial) / TICKS);
				float left = 1.0F - t;
				// eased: it settles fast at first, then gently the last of the way (and never quite flush, so it doesn't flicker)
				float scale = 1.0F + 0.004F + OVERSHOOT * left * left;
				instance.setLightCoords(LightCoordsUtil.getLightCoords(level, p.pos.above()));
				PoseStack.Pose q = pose.copy();
				q.translate((float) (p.pos.getX() + 0.5 - camera.x), (float) (p.pos.getY() + 0.5 - camera.y), (float) (p.pos.getZ() + 0.5 - camera.z));
				q.scale(scale, scale, scale);
				q.translate(-0.5F, -0.5F, -0.5F);
				for (BakedQuad quad : quads(minecraft, p.state)) {
					int tint = 0xFFFFFFFF;
					if (quad.materialInfo().isTinted()) {
						BlockTintSource source = minecraft.getBlockColors().getTintSource(p.state, quad.materialInfo().tintIndex());
						if (source != null) {
							tint = 0xFF000000 | source.colorInWorld(p.state, level, p.pos);
						}
					}
					instance.setColor(tint);
					consumer.putBakedQuad(q, quad, instance);
				}
			}
		});
	}
}
