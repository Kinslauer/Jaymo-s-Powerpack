package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebNet;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Draws every web line in view (the local player's from their own state, so it shows the same tick it fires; other
 * players' from the synced line attachments), each from its own hand, to the anchor, with a little
 * splat of strands where it sticks. Hooked into Fabric's level render "collect submits" phase.
 */
final class WebLineRenderer {
	private static final int COLOR = 0xFFEDEDED;
	private static final int SPLAT_COLOR = 0xFFD8D8D8;
	private static final int SEGMENTS = 12;
	/** Lines are drawn this much thicker than vanilla's fishing line. */
	private static final float WIDTH_SCALE = 1.4F;

	private WebLineRenderer() {
	}

	static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = context.levelState().cameraRenderState.pos;
		float width = minecraft.gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth * WIDTH_SCALE;
		for (Player player : level.players()) {
			for (int handIndex = WebNet.MAIN; handIndex <= WebNet.OFF; handIndex++) {
				Vec3 end = WebArms.lineEnd(minecraft, level, player, handIndex, partialTick);
				if (end == null) {
					continue;
				}
				Vec3 hand = handPos(minecraft, player, handIndex, partialTick);
				if (hand.distanceToSqr(end) > 96.0 * 96.0) {
					continue;
				}
				draw(context.poseStack(), context, hand.subtract(camera), end.subtract(camera), width);
			}
		}
	}

	/**
	 * Where the web leaves the hand: in first person, the screen corner the hand is in (as the fishing line does it);
	 * otherwise the posed hand at the end of the arm (see WebArms), or roughly where the hand would be.
	 */
	private static Vec3 handPos(Minecraft minecraft, Player player, int handIndex, float partialTick) {
		boolean mainRight = player.getMainArm() == HumanoidArm.RIGHT;
		int side = (handIndex == WebNet.MAIN) == mainRight ? 1 : -1;
		if (player == minecraft.player && minecraft.options.getCameraType().isFirstPerson()) {
			float fov = minecraft.options.fov().get().floatValue();
			Camera camera = minecraft.gameRenderer.mainCamera();
			Vec3 offset = camera.getNearPlane(fov).getPointOnPlane(side * 0.525F, -0.1F).scale(960.0 / fov);
			return player.getEyePosition(partialTick).add(offset);
		}
		Vec3 posed = WebArms.handPos(player, handIndex, partialTick);
		if (posed != null) {
			return posed;
		}
		float bodyYaw = Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
		double sin = Mth.sin(bodyYaw);
		double cos = Mth.cos(bodyYaw);
		double right = side * 0.35 * player.getScale();
		double forward = 0.3 * player.getScale();
		return player.getEyePosition(partialTick)
			.add(-cos * right - sin * forward, -0.35 * player.getScale(), -sin * right + cos * forward);
	}

	private static void draw(PoseStack poseStack, LevelRenderContext context, Vec3 from, Vec3 to, float width) {
		Vec3 delta = to.subtract(from);
		double length = delta.length();
		if (length < 1.0E-3) {
			return;
		}
		Vec3 dir = delta.scale(1.0 / length);
		// two directions across the line, for the splat at the anchor
		Vec3 across = Math.abs(dir.y) < 0.95 ? dir.cross(new Vec3(0.0, 1.0, 0.0)).normalize() : dir.cross(new Vec3(1.0, 0.0, 0.0)).normalize();
		Vec3 across2 = dir.cross(across).normalize();
		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, buffer) -> {
			for (int i = 0; i < SEGMENTS; i++) {
				Vec3 a = from.add(delta.scale((double)i / SEGMENTS));
				Vec3 b = from.add(delta.scale((double)(i + 1) / SEGMENTS));
				segment(buffer, pose, a, b, dir, COLOR, width);
			}
			// splat: six short strands fanning out where the web sticks
			for (int k = 0; k < 6; k++) {
				double angle = k * Math.PI / 3.0 + 0.4;
				Vec3 spoke = across.scale(Math.cos(angle)).add(across2.scale(Math.sin(angle))).scale(0.35).subtract(dir.scale(0.12));
				Vec3 tip = to.add(spoke);
				segment(buffer, pose, to, tip, spoke.normalize(), SPLAT_COLOR, width * 0.7F);
			}
		});
	}

	private static void segment(VertexConsumer buffer, PoseStack.Pose pose, Vec3 a, Vec3 b, Vec3 normal, int color, float width) {
		float nx = (float)normal.x;
		float ny = (float)normal.y;
		float nz = (float)normal.z;
		buffer.addVertex(pose, (float)a.x, (float)a.y, (float)a.z).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(width);
		buffer.addVertex(pose, (float)b.x, (float)b.y, (float)b.z).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(width);
	}
}
