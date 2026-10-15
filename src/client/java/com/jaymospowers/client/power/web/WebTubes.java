package com.jaymospowers.client.power.web;

import com.jaymospowers.JaymosPowers;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * Four-sided lengths of symbiote flesh drawn straight into a buffer: round-ish cords (equal width and thickness) or flat
 * blades (wide and thin), tapering from one end to the other, in one of the four swatches of
 * {@code web_symbiote_flesh.png} ({@code tools/gen_web_hulk.py}): wet black, black with its blue sheen, bone white,
 * and the tongue's red. Coordinates are whatever the pose stack is in (a model part's, in blocks).
 */
final class WebTubes {
	static final Identifier TEXTURE = JaymosPowers.id("textures/entity/web_symbiote_flesh.png");
	/** The swatches. */
	static final int BLACK = 0, SHEEN = 1, BONE = 2, RED = 3;

	private WebTubes() {
	}

	/** A cord from {@code a} (radius {@code ra}) to {@code b} (radius {@code rb}). */
	static void cord(VertexConsumer buffer, PoseStack.Pose pose, Vec3 a, Vec3 b, double ra, double rb, int swatch, int light) {
		prism(buffer, pose, a, b, null, ra, ra, rb, rb, swatch, light);
	}

	/**
	 * A four-sided length from {@code a} to {@code b}: half-width {@code w} along {@code side} (any vector across it; null
	 * picks one) and half-thickness {@code t} across that, each tapering from the {@code a} end to the {@code b} end.
	 */
	static void prism(VertexConsumer buffer, PoseStack.Pose pose, Vec3 a, Vec3 b, Vec3 side, double wa, double ta, double wb, double tb,
		int swatch, int light) {
		Vec3 dir = b.subtract(a);
		if (dir.lengthSqr() < 1.0E-8) {
			return;
		}
		dir = dir.normalize();
		Vec3 u = side == null ? null : side.subtract(dir.scale(side.dot(dir)));
		if (u == null || u.lengthSqr() < 1.0E-6) {
			u = Math.abs(dir.y) < 0.95 ? dir.cross(new Vec3(0.0, 1.0, 0.0)) : dir.cross(new Vec3(1.0, 0.0, 0.0));
		}
		u = u.normalize();
		Vec3 v = dir.cross(u).normalize();
		// the four edges, going round
		Vec3[] ea = {u.scale(wa), v.scale(ta), u.scale(-wa), v.scale(-ta)};
		Vec3[] eb = {u.scale(wb), v.scale(tb), u.scale(-wb), v.scale(-tb)};
		Vec3[] normals = {u.add(v).normalize(), v.subtract(u).normalize(), u.add(v).reverse().normalize(), u.subtract(v).normalize()};
		float su = (swatch % 2) * 0.5F, sv = (swatch / 2) * 0.5F;
		for (int k = 0; k < 4; k++) {
			int n = (k + 1) % 4;
			Vec3 p0 = a.add(ea[k]), p1 = a.add(ea[n]), p2 = b.add(eb[n]), p3 = b.add(eb[k]);
			float u0 = su + 0.05F + k * 0.1F, u1 = u0 + 0.1F;
			vertex(buffer, pose, p0, u0, sv + 0.05F, normals[k], light);
			vertex(buffer, pose, p1, u1, sv + 0.05F, normals[k], light);
			vertex(buffer, pose, p2, u1, sv + 0.45F, normals[k], light);
			vertex(buffer, pose, p3, u0, sv + 0.45F, normals[k], light);
		}
		// the ends, so a blunt end (or a thick blade's back) isn't hollow
		if (wa > 0.02 || ta > 0.02) {
			cap(buffer, pose, a, ea, dir.reverse(), su, sv, light);
		}
		if (wb > 0.02 || tb > 0.02) {
			cap(buffer, pose, b, eb, dir, su, sv, light);
		}
	}

	private static void cap(VertexConsumer buffer, PoseStack.Pose pose, Vec3 at, Vec3[] edges, Vec3 normal, float su, float sv, int light) {
		boolean out = normal.dot(edges[0].cross(edges[1])) > 0.0;
		for (int k = 0; k < 4; k++) {
			Vec3 p = at.add(edges[out ? k : 3 - k]);
			vertex(buffer, pose, p, su + 0.1F + (k == 1 || k == 2 ? 0.3F : 0.0F), sv + 0.1F + (k >= 2 ? 0.3F : 0.0F), normal, light);
		}
	}

	static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 p, float u, float v, Vec3 n, int light) {
		buffer.addVertex(pose, (float)p.x, (float)p.y, (float)p.z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
			.setNormal(pose, (float)n.x, (float)n.y, (float)n.z);
	}
}
