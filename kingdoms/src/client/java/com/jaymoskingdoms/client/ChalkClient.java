package com.jaymoskingdoms.client;

import com.jaymoskingdoms.ChalkMarks;
import com.jaymoskingdoms.JaymosKingdoms;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Chalk marks in the world ({@link ChalkMarks}): each is a small flat quad on the face of a wall, drawn with the glyph's
 * texture (the unlettered are sent a scribble in its place). A reader who looks straight at one is told what it means.
 */
final class ChalkClient {
	/** A mark's size (blocks) and how far off the wall it floats. */
	static final double SIZE = 0.6, LIFT = 0.006;
	private static List<ChalkMarks.Seen> marks = List.of();
	private static boolean reads;

	private ChalkClient() {
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(ChalkMarks.View.TYPE, (payload, context) -> {
			marks = List.copyOf(payload.marks());
			reads = payload.reads();
		});
		ClientPlayNetworking.registerGlobalReceiver(ChalkMarks.Draw.TYPE, (payload, context) -> {
			if (context.client().gui.screen() == null) {
				context.client().gui.setScreen(new ChalkScreen(payload.pos(), payload.face(), payload.reads()));
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> marks = List.of());
		LevelRenderEvents.COLLECT_SUBMITS.register(ChalkClient::render);
		ClientTickEvents.END_CLIENT_TICK.register(ChalkClient::tick);
	}

	static Identifier texture(int glyph) {
		return JaymosKingdoms.id("textures/chalk/" + ChalkMarks.NAMES.get(Math.max(0, Math.min(glyph, ChalkMarks.SCRIBBLE))) + ".png");
	}

	private static void tick(Minecraft mc) {
		if (!reads || mc.player == null || mc.level == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
			|| mc.gui.screen() != null) {
			return;
		}
		for (ChalkMarks.Seen s : marks) {
			if (s.pos() == hit.getBlockPos().asLong() && s.face() == hit.getDirection().get3DDataValue() && s.glyph() != ChalkMarks.SCRIBBLE) {
				mc.gui.hud.setOverlayMessage(Component.translatable("chalk.jaymoskingdoms.meaning." + ChalkMarks.NAMES.get(s.glyph())), false);
				return;
			}
		}
	}

	private static void render(LevelRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || marks.isEmpty()) {
			return;
		}
		Vec3 camera = context.levelState().cameraRenderState.pos;
		for (ChalkMarks.Seen s : marks) {
			BlockPos pos = BlockPos.of(s.pos());
			Direction face = Direction.from3DDataValue(s.face());
			Vec3 centre = Vec3.atCenterOf(pos).add(face.getStepX() * (0.5 + LIFT), face.getStepY() * (0.5 + LIFT), face.getStepZ() * (0.5 + LIFT));
			if (centre.distanceToSqr(camera) > ChalkMarks.SEEN_RANGE * ChalkMarks.SEEN_RANGE) {
				continue;
			}
			BlockPos front = pos.relative(face);
			int light = LightCoordsUtil.pack(mc.level.getBrightness(LightLayer.BLOCK, front), mc.level.getBrightness(LightLayer.SKY, front));
			Vec3 n = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
			Vec3 up = face.getAxis().isHorizontal() ? new Vec3(0, 1, 0) : new Vec3(0, 0, -1);
			Vec3 right = n.scale(-1).cross(up).normalize();
			double h = SIZE / 2;
			Vec3 c = centre.subtract(camera);
			Vec3 tl = c.subtract(right.scale(h)).add(up.scale(h)), tr = c.add(right.scale(h)).add(up.scale(h));
			Vec3 br = c.add(right.scale(h)).subtract(up.scale(h)), bl = c.subtract(right.scale(h)).subtract(up.scale(h));
			context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.entityCutout(texture(s.glyph())), (pose, buffer) -> {
				vertex(buffer, pose, tl, 0, 0, light, n);
				vertex(buffer, pose, bl, 0, 1, light, n);
				vertex(buffer, pose, br, 1, 1, light, n);
				vertex(buffer, pose, tr, 1, 0, light, n);
				vertex(buffer, pose, tr, 1, 0, light, n);
				vertex(buffer, pose, br, 1, 1, light, n);
				vertex(buffer, pose, bl, 0, 1, light, n);
				vertex(buffer, pose, tl, 0, 0, light, n);
			});
		}
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 at, float u, float v, int light, Vec3 normal) {
		buffer.addVertex(pose, (float)at.x, (float)at.y, (float)at.z).setColor(0xFFFFFFFF).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(light).setNormal(pose, (float)normal.x, (float)normal.y, (float)normal.z);
	}
}
