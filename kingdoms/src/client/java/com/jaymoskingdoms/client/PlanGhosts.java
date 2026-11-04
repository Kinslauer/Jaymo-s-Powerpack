package com.jaymoskingdoms.client;

import com.jaymoskingdoms.PlanSites;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.488) Laid plans' ghosts ({@link PlanSites}): every block of a laid-out building not yet in, drawn see-through
 * in a pale blue where it's to go, near enough to see. A ghost goes once the right block is there; one where something
 * else stands isn't drawn (that block has to come out first).
 */
public final class PlanGhosts {
	/** How far from the eye ghosts are drawn (blocks); the most drawn at once (nearest first). */
	static final int RANGE = 48, MAX_SHOWN = 6000;
	/** How often what's shown is looked at again (ticks). */
	static final int LOOK_TICKS = 5;
	/** The ghosts' colour (ARGB): pale blue, see-through. */
	static final int COLOUR = 0x6698C0FF;
	private static final int FULL_BRIGHT = 0xF000F0;

	/** One laid plan as sent: its blocks' places and states (filled in as pieces come). */
	private static final class Ghost {
		final BlockPos anchor;
		final List<int[]> rel = new ArrayList<>(), states = new ArrayList<>();
		int parts;

		Ghost(BlockPos anchor) {
			this.anchor = anchor;
		}
	}

	private static final Map<UUID, Ghost> GHOSTS = new HashMap<>();
	/** What's drawn this moment: places and states. */
	private static long[] shownPos = new long[0];
	private static BlockState[] shownStates = new BlockState[0];
	private static final Map<BlockState, List<BakedQuad>> QUADS = new HashMap<>();
	private static final Direction[] SIDES = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null};

	private PlanGhosts() {
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(PlanSites.Ghost.TYPE, (payload, context) -> {
			Ghost g = GHOSTS.get(payload.id());
			if (payload.part() == 0 || g == null || !g.anchor.equals(payload.anchor())) {
				g = new Ghost(payload.anchor());
				GHOSTS.put(payload.id(), g);
			}
			g.rel.add(payload.rel());
			g.states.add(payload.states());
			g.parts = payload.parts();
		});
		ClientPlayNetworking.registerGlobalReceiver(PlanSites.Gone.TYPE, (payload, context) -> GHOSTS.remove(payload.id()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			GHOSTS.clear();
			shownPos = new long[0];
			shownStates = new BlockState[0];
		});
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (minecraft.level == null || minecraft.player == null) {
				return;
			}
			if (minecraft.player.tickCount % LOOK_TICKS == 0) {
				look(minecraft);
			}
		});
		LevelRenderEvents.COLLECT_SUBMITS.register(PlanGhosts::render);
	}

	/** Works out which ghosts to draw: unfilled, open to the air, within range, nearest first. */
	private static void look(Minecraft minecraft) {
		if (GHOSTS.isEmpty()) {
			shownPos = new long[0];
			shownStates = new BlockState[0];
			QUADS.clear();
			return;
		}
		BlockPos eye = minecraft.player.blockPosition();
		List<long[]> found = new ArrayList<>();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (Ghost g : GHOSTS.values()) {
			for (int k = 0; k < g.rel.size(); k++) {
				int[] rel = g.rel.get(k), states = g.states.get(k);
				for (int i = 0; i < rel.length; i++) {
					int r = rel[i];
					at.set(g.anchor.getX() + (r & 0xFF), g.anchor.getY() + (r >> 8 & 0xFF), g.anchor.getZ() + (r >> 16 & 0xFF));
					int dx = at.getX() - eye.getX(), dy = at.getY() - eye.getY(), dz = at.getZ() - eye.getZ();
					int d = dx * dx + dy * dy + dz * dz;
					if (d > RANGE * RANGE) {
						continue;
					}
					BlockState want = Block.stateById(states[i]);
					BlockState here = minecraft.level.getBlockState(at);
					if (PlanSites.matches(here, want) || !here.canBeReplaced() || want.getRenderShape() != RenderShape.MODEL) {
						continue;
					}
					found.add(new long[] {at.asLong(), states[i], d});
				}
			}
		}
		if (found.size() > MAX_SHOWN) {
			found.sort((a, b) -> Long.compare(a[2], b[2]));
			found = found.subList(0, MAX_SHOWN);
		}
		long[] pos = new long[found.size()];
		BlockState[] states = new BlockState[found.size()];
		for (int i = 0; i < pos.length; i++) {
			pos[i] = found.get(i)[0];
			states[i] = Block.stateById((int)found.get(i)[1]);
		}
		shownPos = pos;
		shownStates = states;
	}

	/** {@code state}'s model, flattened to quads (kept). */
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
		long[] pos = shownPos;
		BlockState[] states = shownStates;
		if (pos.length == 0) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		Vec3 camera = context.levelState().cameraRenderState.pos;
		PoseStack poseStack = context.poseStack();
		poseStack.pushPose();
		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS), (pose, consumer) -> {
			QuadInstance instance = new QuadInstance();
			instance.setColor(COLOUR);
			instance.setLightCoords(FULL_BRIGHT);
			instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);
			for (int i = 0; i < pos.length; i++) {
				PoseStack.Pose p = pose.copy();
				// (a hair small, so it doesn't flicker against what's beside it)
				p.translate((float)(BlockPos.getX(pos[i]) - camera.x) + 0.01F, (float)(BlockPos.getY(pos[i]) - camera.y) + 0.01F,
					(float)(BlockPos.getZ(pos[i]) - camera.z) + 0.01F);
				p.scale(0.98F, 0.98F, 0.98F);
				for (BakedQuad quad : quads(minecraft, states[i])) {
					consumer.putBakedQuad(p, quad, instance);
				}
			}
		});
		poseStack.popPose();
	}
}
