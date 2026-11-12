package com.jaymoskitchen.client.archery;

import com.jaymoskitchen.JaymosKitchen;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Andy: "anyone with an arrow sticking out of their head should have some kind of visible wound until its removed." Where
 * an arrow stuck in a head goes in, the skin shows a small dark puncture with a bruised rim and, on the head's sides, a thin
 * dried trickle running down from it. Drawn with the arrow (StuckArrowsLayer for creatures and corpses, the game's own
 * arrow layer for players), so it lasts exactly as long as the arrow does: pulled from a corpse, or fallen out of a player.
 * Kept dark and dull, in line with the pack's toned-down blood.
 */
public final class HeadWounds {
	/** The wound's look: an 8x8 puncture on top, an 8x8 trickle under it. */
	private static final Identifier TEXTURE = JaymosKitchen.id("textures/entity/arrow_wound.png");
	private static final RenderType TYPE = RenderTypes.entityTranslucent(TEXTURE);
	/** The puncture's width on the skin, in model pixels. */
	private static final float WOUND = 3.0F;
	/** How far the trickle runs down below the puncture at most, in model pixels. */
	private static final float TRICKLE = 4.0F;
	/** How far off the skin it's drawn, in model pixels, so it doesn't flicker into it. */
	public static final float LIFT = 0.05F;
	/** On a player's head: out past the skin's outer layer (the hat, half a pixel out), so hair drawn there doesn't hide it. */
	public static final float OVER_HAT = 0.55F;
	/** A face narrower than this (an ear, a thin brim) gets no wound. */
	private static final float NARROWEST = 2.0F;

	private HeadWounds() {
	}

	/** Whether a part with this name is (part of) a head. */
	public static boolean isHead(String name) {
		return name.toLowerCase(java.util.Locale.ROOT).contains("head");
	}

	/**
	 * The wound of one stuck arrow. {@code poseStack} stands where the arrow's point is drawn: inside {@code cube}, at its
	 * share {@code midX}, {@code midY}, {@code midZ} of the way across, in the part's frame (as the arrow layers leave it just
	 * before the arrow turns). The arrow's shaft runs out from there away from the cube's middle, so the wound goes where
	 * that line leaves the cube. It's drawn {@code lift} model pixels off the skin.
	 */
	public static void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, ModelPart.Cube cube, float midX,
		float midY, float midZ, float lift) {
		float[] min = {cube.minX, cube.minY, cube.minZ};
		float[] max = {cube.maxX, cube.maxY, cube.maxZ};
		float[] share = {midX, midY, midZ};
		float[] point = new float[3];
		float[] out = new float[3];
		for (int i = 0; i < 3; i++) {
			point[i] = Mth.lerp(share[i], min[i], max[i]);
			out[i] = share[i] * 2.0F - 1.0F;
		}
		// where the shaft leaves the cube: the first face its line meets
		int axis = -1;
		float along = Float.MAX_VALUE;
		for (int i = 0; i < 3; i++) {
			if (Math.abs(out[i]) < 1.0E-4F) {
				continue;
			}
			float t = ((out[i] > 0.0F ? max[i] : min[i]) - point[i]) / out[i];
			if (t < along) {
				along = t;
				axis = i;
			}
		}
		if (axis < 0) {
			return;
		}
		float side = out[axis] > 0.0F ? 1.0F : -1.0F;
		float[] at = new float[3];
		for (int i = 0; i < 3; i++) {
			at[i] = point[i] + out[i] * along;
		}
		at[axis] = side > 0.0F ? max[axis] + lift : min[axis] - lift;

		// across the face: on a side, sideways and down (a model's y runs down); on the top or bottom, x and z
		boolean flat = axis == 1;
		int across = axis == 0 ? 2 : 0;
		int down = flat ? 2 : 1;
		float wide = max[across] - min[across], tall = max[down] - min[down];
		if (wide < NARROWEST || tall < NARROWEST) {
			return;
		}
		float size = Math.min(WOUND, Math.min(wide, tall));
		float half = size / 2.0F;
		at[across] = Mth.clamp(at[across], min[across] + half, max[across] - half);
		at[down] = Mth.clamp(at[down], min[down] + half, max[down] - half);
		float trickle = flat ? 0.0F : Math.min(TRICKLE, max[down] - (at[down] + half));

		float[] normal = new float[3];
		normal[axis] = side;
		float[] origin = point.clone();
		collector.submitCustomGeometry(poseStack, TYPE, (pose, buffer) -> {
			quad(pose, buffer, light, origin, at, normal, across, down, -half, half, -half, half, 0.0F, 0.5F);
			if (trickle >= 0.5F) {
				quad(pose, buffer, light, origin, at, normal, across, down, -half, half, half, half + trickle, 0.5F,
					0.5F + 0.5F * trickle / TRICKLE);
			}
		});
	}

	/**
	 * A quad on the face through {@code at}, spanning {@code a0..a1} across and {@code d0..d1} down (model pixels from
	 * {@code at}), showing the texture's rows {@code v0..v1}; placed relative to {@code origin}, where the pose stands.
	 */
	private static void quad(PoseStack.Pose pose, VertexConsumer buffer, int light, float[] origin, float[] at, float[] normal,
		int across, int down, float a0, float a1, float d0, float d1, float v0, float v1) {
		float[][] corners = {corner(origin, at, across, down, a0, d0), corner(origin, at, across, down, a0, d1),
			corner(origin, at, across, down, a1, d1), corner(origin, at, across, down, a1, d0)};
		float[][] uvs = {{0.0F, v0}, {0.0F, v1}, {1.0F, v1}, {1.0F, v0}};
		// wind it to face out of the skin
		float[] e1 = sub(corners[1], corners[0]), e2 = sub(corners[2], corners[0]);
		float facing = (e1[1] * e2[2] - e1[2] * e2[1]) * normal[0] + (e1[2] * e2[0] - e1[0] * e2[2]) * normal[1]
			+ (e1[0] * e2[1] - e1[1] * e2[0]) * normal[2];
		for (int k = 0; k < 4; k++) {
			int i = facing >= 0.0F ? k : 3 - k;
			buffer.addVertex(pose, corners[i][0], corners[i][1], corners[i][2]).setColor(0xFFFFFFFF).setUv(uvs[i][0], uvs[i][1])
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, normal[0], normal[1], normal[2]);
		}
	}

	/** One corner, in blocks from the pose's origin. */
	private static float[] corner(float[] origin, float[] at, int across, int down, float a, float d) {
		float[] c = at.clone();
		c[across] += a;
		c[down] += d;
		for (int i = 0; i < 3; i++) {
			c[i] = (c[i] - origin[i]) / 16.0F;
		}
		return c;
	}

	private static float[] sub(float[] a, float[] b) {
		return new float[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
	}
}
