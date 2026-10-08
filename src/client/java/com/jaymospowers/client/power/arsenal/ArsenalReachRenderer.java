package com.jaymospowers.client.power.arsenal;

import com.jaymospowers.power.arsenal.ArsenalReach;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Draws Reach Through for everyone in view: a disc of swirling nether-portal light (the animated portal texture,
 * turning) with a bright rim, torn open in front of the weapon hand; and, while it's being drawn out, the weapon itself
 * sliding out of the portal into the hand.
 */
final class ArsenalReachRenderer {
	private static final int SEGMENTS = 24;
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final ItemStackRenderState ITEM = new ItemStackRenderState();

	private ArsenalReachRenderer() {
	}

	static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = context.levelState().cameraRenderState.pos;
		for (Player player : level.players()) {
			ArsenalReach.Reach reach = player.getAttached(ArsenalReach.REACH);
			if (reach == null) {
				continue;
			}
			float t = level.getGameTime() - reach.start() + partialTick;
			if (t < 0.0F || t > ArsenalReach.CLOSE_END) {
				continue;
			}
			Vec3 center = ArsenalReach.portalCenter(player, partialTick);
			if (center.distanceToSqr(camera) > 64.0 * 64.0) {
				continue;
			}
			float bodyYaw = Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot);
			portal(context, minecraft, center.subtract(camera), bodyYaw, ArsenalReach.openness(t), t);
			if (t > ArsenalReach.REACH_END && t < ArsenalReach.PULL_END && !reach.weapon().isEmpty()) {
				emerging(context, minecraft, player, reach, center, camera, bodyYaw, t, partialTick);
			}
		}
	}

	/** The portal: a turning disc of portal texture, facing the player, with a glowing rim. */
	private static void portal(LevelRenderContext context, Minecraft minecraft, Vec3 at, float bodyYaw, float open, float t) {
		if (open <= 0.01F) {
			return;
		}
		TextureAtlasSprite sprite = minecraft.getModelManager().getBlockStateModelSet().getParticleMaterial(Blocks.NETHER_PORTAL.defaultBlockState()).sprite();
		Vec3 right = ArsenalReach.right(bodyYaw);
		Vec3 up = new Vec3(0.0, 1.0, 0.0);
		Vec3 normal = ArsenalReach.forward(bodyYaw).scale(-1.0);
		double radius = ArsenalReach.PORTAL_RADIUS * open;
		// a little taller than wide, like a tear in the air
		double rx = radius * 0.85;
		double ry = radius * 1.1;
		double spin = t * 0.12;
		float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
		PoseStack poseStack = context.poseStack();
		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(sprite.atlasLocation()), (pose, buffer) -> {
			for (int i = 0; i < SEGMENTS; i++) {
				double a0 = i * Mth.TWO_PI / SEGMENTS;
				double a1 = (i + 1) * Mth.TWO_PI / SEGMENTS;
				// inner disc: the portal texture, its UVs turning so the swirl spins
				Vec3 p0 = ellipse(at, right, up, rx, ry, a0);
				Vec3 p1 = ellipse(at, right, up, rx, ry, a1);
				float cu = (u0 + u1) * 0.5F, cv = (v0 + v1) * 0.5F;
				float du = (u1 - u0) * 0.5F, dv = (v1 - v0) * 0.5F;
				quad(buffer, pose, at, p0, p1, at, normal, 0xF0E0C8FF,
					cu, cv, cu + du * (float)Math.cos(a0 + spin), cv + dv * (float)Math.sin(a0 + spin),
					cu + du * (float)Math.cos(a1 + spin), cv + dv * (float)Math.sin(a1 + spin), cu, cv);
				// rim: a bright band just outside the disc
				Vec3 q0 = ellipse(at, right, up, rx * 1.18, ry * 1.18, a0);
				Vec3 q1 = ellipse(at, right, up, rx * 1.18, ry * 1.18, a1);
				quad(buffer, pose, p0, q0, q1, p1, normal, 0xC0F2D8FF,
					u0, v0, u1, v0, u1, v1, u0, v1);
			}
		});
	}

	private static Vec3 ellipse(Vec3 center, Vec3 right, Vec3 up, double rx, double ry, double angle) {
		return center.add(right.scale(Math.cos(angle) * rx)).add(up.scale(Math.sin(angle) * ry));
	}

	/** A quad drawn from both sides (the portal is seen from the front and the back). */
	private static void quad(VertexConsumer buffer, PoseStack.Pose pose, Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 normal, int color,
		float ua, float va, float ub, float vb, float uc, float vc, float ud, float vd) {
		vertex(buffer, pose, a, normal, color, ua, va);
		vertex(buffer, pose, b, normal, color, ub, vb);
		vertex(buffer, pose, c, normal, color, uc, vc);
		vertex(buffer, pose, d, normal, color, ud, vd);
		Vec3 back = normal.scale(-1.0);
		vertex(buffer, pose, d, back, color, ud, vd);
		vertex(buffer, pose, c, back, color, uc, vc);
		vertex(buffer, pose, b, back, color, ub, vb);
		vertex(buffer, pose, a, back, color, ua, va);
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 p, Vec3 n, int color, float u, float v) {
		buffer.addVertex(pose, (float)p.x, (float)p.y, (float)p.z)
			.setColor(color)
			.setUv(u, v)
			.setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(FULL_BRIGHT)
			.setNormal(pose, (float)n.x, (float)n.y, (float)n.z);
	}

	/** The weapon sliding out of the portal toward the hand, growing as it comes into this world. */
	private static void emerging(LevelRenderContext context, Minecraft minecraft, Player player, ArsenalReach.Reach reach, Vec3 center,
		Vec3 camera, float bodyYaw, float t, float partialTick) {
		float x = (t - ArsenalReach.REACH_END) / (ArsenalReach.PULL_END - ArsenalReach.REACH_END);
		float p = x * x * (3.0F - 2.0F * x);
		Vec3 forward = ArsenalReach.forward(bodyYaw);
		Vec3 start = center.add(forward.scale(0.15));
		Vec3 end;
		if (player == minecraft.player && minecraft.options.getCameraType().isFirstPerson()) {
			// first person: into the bottom corner of the screen, where the hand is
			Vec3 look = player.getViewVector(partialTick);
			Vec3 side = look.cross(new Vec3(0.0, 1.0, 0.0));
			side = side.lengthSqr() < 1.0E-4 ? ArsenalReach.right(bodyYaw) : side.normalize();
			end = player.getEyePosition(partialTick).add(look.scale(0.6)).add(side.scale(ArsenalReach.side(player) * 0.35)).add(0.0, -0.3, 0.0);
		} else {
			end = ArsenalArmPose.handPos(player, partialTick);
		}
		Vec3 at = start.lerp(end, p).subtract(camera);
		PoseStack poseStack = context.poseStack();
		poseStack.pushPose();
		poseStack.translate(at.x, at.y, at.z);
		poseStack.rotateDegrees(Axis.YP, -bodyYaw);
		// tumbling out of the portal, settling as it reaches the hand
		poseStack.rotateDegrees(Axis.XP, (1.0F - p) * 160.0F);
		float scale = 0.35F + 0.4F * p;
		poseStack.scale(scale, scale, scale);
		minecraft.getItemModelResolver().updateForTopItem(ITEM, reach.weapon(), ItemDisplayContext.FIXED, minecraft.level, null, player.getId());
		ITEM.submit(poseStack, context.submitNodeCollector(), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}
}
