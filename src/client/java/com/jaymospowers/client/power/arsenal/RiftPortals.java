package com.jaymospowers.client.power.arsenal;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a rift: an elliptical tear in the air. The ordinary one is a disc of swirling nether-portal light with a bright
 * rim; the <b>void</b> one (a corrupted arsenal) is a window onto the End's starfield (the end-gateway shader) ringed in
 * deep purple fading to black. Shared by Reach Through, the Barrage rifts and the Hundred Blades sky.
 */
final class RiftPortals {
	private static final int SEGMENTS = 24;
	static final int FULL_BRIGHT = 0xF000F0;
	/** A void-drawn weapon is lit as if in near darkness: a dark tint, with its sheen (glint) still showing. */
	static final int VOID_LIGHT = 0x200020;

	// ARGB colours
	private static final int PORTAL_FILL = 0xF0E0C8FF;
	private static final int PORTAL_RIM = 0xC0F2D8FF;
	private static final int VOID_RIM_INNER = 0xF07A28D0;
	private static final int VOID_RIM_OUTER = 0xA0140620;
	private static final double RIM = 1.18;
	private static final double VOID_RIM = 1.12;
	private static final double VOID_HAZE = 1.4;

	private RiftPortals() {
	}

	private static TextureAtlasSprite portalSprite() {
		return Minecraft.getInstance().getModelManager().getBlockStateModelSet().getParticleMaterial(Blocks.NETHER_PORTAL.defaultBlockState()).sprite();
	}

	/**
	 * One rift centred on {@code at} (relative to the pose stack's origin), spanning {@code right} × {@code up} with
	 * radii {@code rx}/{@code ry}, facing {@code normal}; {@code spin} turns the swirl.
	 */
	static void disc(SubmitNodeCollector collector, PoseStack poseStack, Vec3 at, Vec3 right, Vec3 up, Vec3 normal, double rx, double ry,
		double spin, boolean voidRift) {
		if (rx <= 0.005 || ry <= 0.005) {
			return;
		}
		TextureAtlasSprite sprite = portalSprite();
		float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
		if (voidRift) {
			// the void itself: the End's starfield, seen through the tear (position-only quads, both windings)
			collector.submitCustomGeometry(poseStack, RenderTypes.endGateway(), (pose, buffer) -> {
				for (int i = 0; i < SEGMENTS; i++) {
					double a0 = i * Mth.TWO_PI / SEGMENTS;
					double a1 = (i + 1) * Mth.TWO_PI / SEGMENTS;
					Vec3 p0 = ellipse(at, right, up, rx, ry, a0);
					Vec3 p1 = ellipse(at, right, up, rx, ry, a1);
					position(buffer, pose, at);
					position(buffer, pose, p0);
					position(buffer, pose, p1);
					position(buffer, pose, at);
					position(buffer, pose, at);
					position(buffer, pose, p1);
					position(buffer, pose, p0);
					position(buffer, pose, at);
				}
			});
			// deep purple lip, fading out into black haze
			collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(sprite.atlasLocation()), (pose, buffer) -> {
				for (int i = 0; i < SEGMENTS; i++) {
					double a0 = i * Mth.TWO_PI / SEGMENTS + spin;
					double a1 = (i + 1) * Mth.TWO_PI / SEGMENTS + spin;
					double b0 = i * Mth.TWO_PI / SEGMENTS;
					double b1 = (i + 1) * Mth.TWO_PI / SEGMENTS;
					Vec3 p0 = ellipse(at, right, up, rx, ry, b0);
					Vec3 p1 = ellipse(at, right, up, rx, ry, b1);
					Vec3 q0 = ellipse(at, right, up, rx * VOID_RIM, ry * VOID_RIM, b0);
					Vec3 q1 = ellipse(at, right, up, rx * VOID_RIM, ry * VOID_RIM, b1);
					Vec3 h0 = ellipse(at, right, up, rx * VOID_HAZE, ry * VOID_HAZE, b0);
					Vec3 h1 = ellipse(at, right, up, rx * VOID_HAZE, ry * VOID_HAZE, b1);
					float ua = Mth.lerp((float) (0.5 + 0.5 * Math.cos(a0)), u0, u1);
					float ub = Mth.lerp((float) (0.5 + 0.5 * Math.cos(a1)), u0, u1);
					quad(buffer, pose, p0, q0, q1, p1, normal, VOID_RIM_INNER, ua, v0, ua, v1, ub, v1, ub, v0);
					quad(buffer, pose, q0, h0, h1, q1, normal, VOID_RIM_OUTER, ua, v0, ua, v1, ub, v1, ub, v0);
				}
			});
			return;
		}
		collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(sprite.atlasLocation()), (pose, buffer) -> {
			float cu = (u0 + u1) * 0.5F, cv = (v0 + v1) * 0.5F;
			float du = (u1 - u0) * 0.5F, dv = (v1 - v0) * 0.5F;
			for (int i = 0; i < SEGMENTS; i++) {
				double a0 = i * Mth.TWO_PI / SEGMENTS;
				double a1 = (i + 1) * Mth.TWO_PI / SEGMENTS;
				// inner disc: the portal texture, its UVs turning so the swirl spins
				Vec3 p0 = ellipse(at, right, up, rx, ry, a0);
				Vec3 p1 = ellipse(at, right, up, rx, ry, a1);
				quad(buffer, pose, at, p0, p1, at, normal, PORTAL_FILL,
					cu, cv, cu + du * (float) Math.cos(a0 + spin), cv + dv * (float) Math.sin(a0 + spin),
					cu + du * (float) Math.cos(a1 + spin), cv + dv * (float) Math.sin(a1 + spin), cu, cv);
				// rim: a bright band just outside the disc
				Vec3 q0 = ellipse(at, right, up, rx * RIM, ry * RIM, a0);
				Vec3 q1 = ellipse(at, right, up, rx * RIM, ry * RIM, a1);
				quad(buffer, pose, p0, q0, q1, p1, normal, PORTAL_RIM, u0, v0, u1, v0, u1, v1, u0, v1);
			}
		});
	}

	static Vec3 ellipse(Vec3 center, Vec3 right, Vec3 up, double rx, double ry, double angle) {
		return center.add(right.scale(Math.cos(angle) * rx)).add(up.scale(Math.sin(angle) * ry));
	}

	private static void position(VertexConsumer buffer, PoseStack.Pose pose, Vec3 p) {
		buffer.addVertex(pose, (float) p.x, (float) p.y, (float) p.z);
	}

	/** A quad drawn from both sides (a rift is seen from the front and the back). */
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
		buffer.addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
			.setColor(color)
			.setUv(u, v)
			.setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(FULL_BRIGHT)
			.setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
	}
}
