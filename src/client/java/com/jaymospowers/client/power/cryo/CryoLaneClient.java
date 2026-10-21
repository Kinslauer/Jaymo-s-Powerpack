package com.jaymospowers.client.power.cryo;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.cryo.CryoSlide;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * (0.14.0) The Ice Slide's lane, as everyone sees it: under every skater ({@link CryoSlide#SLIDING}) on the ground a
 * strip of ice is laid, flat on the ground ({@code textures/entity/cryo/lane.png}, made by {@code tools/gen_cryo.py}),
 * and it melts away behind them: fully there for most of {@link CryoSlide#LANE_TICKS}, fading out at the end. (Only a
 * look on land: nothing solid is ever replaced. On water the server freezes a real bridge of frosted ice.)
 */
final class CryoLaneClient {
	static final Identifier TEXTURE = JaymosPowers.id("textures/entity/cryo/lane.png");
	/** Half the lane's width (blocks), how high over the ground it's drawn, and how long it fades at the end (ticks). */
	static final float HALF = 0.6F;
	static final float LIFT = 0.015F;
	static final int FADE = 60;
	/** The most patches a skater's lane keeps, and the least distance between two (blocks). */
	static final int MOST = 260;
	static final double SPACING = 0.35;
	/** Blocks from the camera that are drawn. */
	static final double RANGE = 64.0;

	/** One patch of lane: where (on the ground) and when it was laid (client ticks). */
	private record Patch(double x, double y, double z, long born) {
	}

	private static final Map<Integer, ArrayDeque<Patch>> LANES = new HashMap<>();
	private static long ticks;
	private static ClientLevel seen;

	private CryoLaneClient() {
	}

	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (minecraft.level == null || minecraft.level != seen) {
				LANES.clear();
				seen = minecraft.level;
				if (minecraft.level == null) {
					return;
				}
			}
			if (!minecraft.isPaused()) {
				ticks++;
				lay(minecraft.level);
			}
		});
		LevelRenderEvents.COLLECT_SUBMITS.register(CryoLaneClient::render);
	}

	private static void lay(ClientLevel level) {
		for (Player player : level.players()) {
			if (!CryoSlide.active(player) || !player.onGround() || player.isInWater()) {
				continue;
			}
			ArrayDeque<Patch> lane = LANES.computeIfAbsent(player.getId(), k -> new ArrayDeque<>());
			Patch last = lane.peekLast();
			if (last != null && Math.abs(last.y - player.getY()) < 0.6
				&& (last.x - player.getX()) * (last.x - player.getX()) + (last.z - player.getZ()) * (last.z - player.getZ()) < SPACING * SPACING) {
				continue;
			}
			lane.addLast(new Patch(player.getX(), player.getY(), player.getZ(), ticks));
			while (lane.size() > MOST) {
				lane.removeFirst();
			}
		}
		for (Iterator<ArrayDeque<Patch>> it = LANES.values().iterator(); it.hasNext();) {
			ArrayDeque<Patch> lane = it.next();
			while (!lane.isEmpty() && ticks - lane.peekFirst().born > CryoSlide.LANE_TICKS) {
				lane.removeFirst();
			}
			if (lane.isEmpty()) {
				it.remove();
			}
		}
	}

	private static void render(LevelRenderContext context) {
		if (LANES.isEmpty()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return;
		}
		Vec3 camera = context.levelState().cameraRenderState.pos;
		// gather first (x, y, z off the camera, alpha, light), then draw in one go
		List<float[]> patches = new ArrayList<>();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (ArrayDeque<Patch> lane : LANES.values()) {
			for (Patch patch : lane) {
				double dx = patch.x - camera.x;
				double dy = patch.y - camera.y;
				double dz = patch.z - camera.z;
				if (Math.abs(dx) > RANGE || Math.abs(dy) > RANGE || Math.abs(dz) > RANGE) {
					continue;
				}
				long age = ticks - patch.born;
				float alpha = 0.55F * Math.min(1.0F, (CryoSlide.LANE_TICKS - age) / (float)FADE);
				if (alpha <= 0.01F) {
					continue;
				}
				int light = LightCoordsUtil.getLightCoords(minecraft.level, cursor.set(patch.x, patch.y + 0.1, patch.z));
				patches.add(new float[] {(float)dx, (float)(dy + LIFT), (float)dz, alpha, Float.intBitsToFloat(light)});
			}
		}
		if (patches.isEmpty()) {
			return;
		}
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.entityTranslucent(TEXTURE, false),
			(pose, buffer) -> {
				for (float[] p : patches) {
					int color = (int)(p[3] * 255.0F) << 24 | 0xFFFFFF;
					int light = Float.floatToRawIntBits(p[4]);
					// both faces, so it shows whichever way the renderer culls
					quad(buffer, pose, p[0], p[1], p[2], color, light, false);
					quad(buffer, pose, p[0], p[1], p[2], color, light, true);
				}
			});
	}

	private static void quad(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, int color, int light, boolean flip) {
		float a = flip ? HALF : -HALF;
		float b = flip ? -HALF : HALF;
		vertex(buffer, pose, x + a, y, z - HALF, color, flip ? 1.0F : 0.0F, 0.0F, light);
		vertex(buffer, pose, x + a, y, z + HALF, color, flip ? 1.0F : 0.0F, 1.0F, light);
		vertex(buffer, pose, x + b, y, z + HALF, color, flip ? 0.0F : 1.0F, 1.0F, light);
		vertex(buffer, pose, x + b, y, z - HALF, color, flip ? 0.0F : 1.0F, 0.0F, light);
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, int color, float u, float v, int light) {
		buffer.addVertex(pose, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0F, 1.0F, 0.0F);
	}
}
