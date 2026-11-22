package com.jaymoskingdoms.client;

import com.jaymoskingdoms.ScryingOrb;
import com.jaymoskingdoms.ScryingOrbBlock;
import com.jaymoskingdoms.ScryingOrbBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The scrying orb's vision ({@link ScryingOrb}): while an orb is awake, the land it watches hangs over it as a miniature
 * (the blocks at {@link #SCALE}, turning slowly as one piece), its people and creatures as tiny coloured blocks (folk
 * blue, monsters red, animals green), dimmer when it's night where it looks. The orb's stand and glass are its block
 * model. Drawn with the block models' own quads (as PlanGhosts draws its ghosts), so it works with Sodium and Iris.
 * Everyone near the orb is sent the vision ({@link ScryingOrb#VIEWERS}); an orb not heard from for {@link #STALE} ticks
 * shows nothing.
 */
public final class ScryingOrbRenderer implements BlockEntityRenderer<ScryingOrbBlockEntity, ScryingOrbRenderer.State> {
	/** The miniature's scale, where its lowest layer sits over the block (blocks), and how fast it turns (degrees a tick). */
	static final float SCALE = 1.0F / 24.0F, BASE = 0.7F, SPIN = 0.15F;
	/** A vision not refreshed for this long (ticks) is dropped. */
	static final int STALE = 60;
	static final int FULL_BRIGHT = 0xF000F0, DAY = 0xFFFFFFFF, NIGHT = 0xFF8C96C8;
	/** How small a figure is beside a block, and the blocks that stand for them (kind 1 folk, 2 monsters, 3 animals). */
	static final float FIGURE = 0.6F;
	private static final BlockState[] FIGURES = {Blocks.WHITE_CONCRETE.defaultBlockState(), Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState(),
		Blocks.RED_CONCRETE.defaultBlockState(), Blocks.LIME_CONCRETE.defaultBlockState()};
	private static final Direction[] SIDES = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null};

	/** One orb's vision as last sent: each drawn block (position in the view, state) and the figures. */
	private record Vision(int[] cells, BlockState[] states, int[] figures, boolean night, long received) {
	}

	private static final Map<Long, Vision> VISIONS = new HashMap<>();
	private static final Map<BlockState, List<BakedQuad>> QUADS = new HashMap<>();

	public static final class State extends BlockEntityRenderState {
		long at;
		boolean active;
		float angle;
	}

	public ScryingOrbRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(ScryingOrbBlockEntity orb, State state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(orb, state, partialTicks, camera, breakProgress);
		state.at = orb.getBlockPos().asLong();
		state.active = orb.getBlockState().getValue(ScryingOrbBlock.ACTIVE);
		state.angle = orb.getLevel() == null ? 0.0F : ((orb.getLevel().getGameTime() % 2400L) + partialTicks) * SPIN;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		Vision vision = state.active ? VISIONS.get(state.at) : null;
		if (vision == null || vision.cells.length == 0 && vision.figures.length == 0) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		int r = ScryingOrb.RADIUS;
		poseStack.pushPose();
		// over the orb's middle, turning, and shrunk: the view's centre column on the block's centre, its lowest layer at BASE
		poseStack.translate(0.5F, BASE, 0.5F);
		poseStack.mulPose(Axis.YP.rotationDegrees(state.angle));
		poseStack.scale(SCALE, SCALE, SCALE);
		collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS), (pose, consumer) -> {
			QuadInstance instance = new QuadInstance();
			instance.setLightCoords(FULL_BRIGHT);
			instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);
			instance.setColor(vision.night ? NIGHT : DAY);
			int size = ScryingOrb.SIZE;
			for (int i = 0; i < vision.cells.length; i++) {
				int cell = vision.cells[i];
				PoseStack.Pose p = pose.copy();
				p.translate(cell % size - r - 0.5F, cell / (size * size), cell / size % size - r - 0.5F);
				for (BakedQuad quad : quads(minecraft, vision.states[i])) {
					consumer.putBakedQuad(p, quad, instance);
				}
			}
			for (int i = 0; i + 3 < vision.figures.length; i += 4) {
				int kind = (vision.figures[i + 3] & 0xFF) >> 6;
				PoseStack.Pose p = pose.copy();
				p.translate(vision.figures[i] - FIGURE / 2.0F, vision.figures[i + 1] + r, vision.figures[i + 2] - FIGURE / 2.0F);
				p.scale(FIGURE, FIGURE * 1.6F, FIGURE);
				for (BakedQuad quad : quads(minecraft, FIGURES[Math.clamp(kind, 0, FIGURES.length - 1)])) {
					consumer.putBakedQuad(p, quad, instance);
				}
			}
		});
		poseStack.popPose();
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
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

	/** A vision from the server: the blocks worth drawing (not air, not outside the sphere, with a model) kept. */
	private static Vision vision(ScryingOrb.OrbView view, long now) {
		BlockState[] palette = new BlockState[view.palette().length];
		for (int i = 0; i < palette.length; i++) {
			palette[i] = BlockState.stateById(view.palette()[i]);
		}
		List<Integer> cells = new ArrayList<>();
		List<BlockState> states = new ArrayList<>();
		byte[] indices = view.indices();
		for (int i = 0; i < indices.length; i++) {
			int slot = indices[i];
			if (slot <= 0 || slot >= palette.length) {
				continue;
			}
			BlockState state = palette[slot];
			if (state == null || state.isAir() || state.getRenderShape() != RenderShape.MODEL) {
				continue;
			}
			cells.add(i);
			states.add(state);
		}
		return new Vision(cells.stream().mapToInt(Integer::intValue).toArray(), states.toArray(BlockState[]::new), view.figures(), view.night(), now);
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(ScryingOrb.OrbView.TYPE, (payload, context) -> context.client().execute(() -> {
			long now = context.client().level == null ? 0L : context.client().level.getGameTime();
			VISIONS.put(payload.orb().asLong(), vision(payload, now));
		}));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (minecraft.level == null) {
				VISIONS.clear();
				QUADS.clear();
				return;
			}
			long now = minecraft.level.getGameTime();
			VISIONS.values().removeIf(v -> now - v.received() > STALE);
		});
	}
}
