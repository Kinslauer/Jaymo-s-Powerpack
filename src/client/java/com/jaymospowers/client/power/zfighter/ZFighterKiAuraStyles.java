package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * (0.17.46, Andy's NOEA picks) The three looks of the ki aura, picked on the training screen. Sparking is
 * {@link ZFighterKiAura}'s flame shell as it always was, plus {@link #sparks} crackling on it. {@link #smooth} is a calm
 * oval shell of light with no tongues and a slow shimmer. {@link #godly} is tall thin wisps that hug the body and rise in
 * slow strands, softer and more see-through, with tiny {@link #motes} drifting up. When each shows and in what colour is
 * the flame aura's, whichever he picks. Textures: {@code tools/gen_ki_aura_styles.py}.
 */
final class ZFighterKiAuraStyles {
	private static final RenderType SMOOTH_TYPE = type("textures/entity/zfighter_ki_aura_smooth.png");
	private static final RenderType GODLY_TYPE = type("textures/entity/zfighter_ki_aura_godly.png");
	private static final RenderType SPARK_TYPE = type("textures/entity/zfighter_ki_aura_sparks.png");

	/** Smooth: radius at each ring, feet to top, and how bright each ring is (an oval, fading at both ends). */
	static final float[] SMOOTH_RADIUS = {0.26F, 0.50F, 0.64F, 0.70F, 0.70F, 0.64F, 0.50F, 0.30F, 0.06F};
	static final float[] SMOOTH_RING_LIGHT = {0.10F, 0.60F, 0.90F, 1.0F, 1.0F, 0.95F, 0.80F, 0.45F, 0.0F};
	/** Smooth: where its top is (model units, y down), columns round it, layers' size, brightness, turn (degrees a tick) and rise. */
	static final float SMOOTH_TOP = -0.8F;
	static final int SMOOTH_COLUMNS = 24;
	static final float[] SMOOTH_SIZE = {0.86F, 1.0F};
	static final float[] SMOOTH_LIGHT = {0.50F, 0.30F};
	static final float[] SMOOTH_SPIN = {0.5F, -0.35F};
	static final float[] SMOOTH_RISE = {0.007F, 0.005F};
	/** Smooth: the slow shimmer's depth and speed. */
	static final float SMOOTH_SHIMMER = 0.18F, SMOOTH_SHIMMER_SPEED = 0.07F;

	/** Godly: radius at each ring (hugging the body), feet to the strands' roots, and how bright each is. */
	static final float[] GODLY_RADIUS = {0.20F, 0.34F, 0.40F, 0.42F, 0.40F, 0.36F, 0.30F, 0.22F, 0.15F, 0.08F, 0.0F};
	static final float[] GODLY_RING_LIGHT = {0.10F, 0.50F, 0.75F, 0.90F, 0.90F, 0.85F, 0.75F, 0.60F, 0.40F, 0.20F, 0.0F};
	/** Godly: the top of the shortest strand and how much higher the longest rises, columns round it. */
	static final float GODLY_TOP = -1.5F, GODLY_STRAND = 0.9F;
	static final int GODLY_COLUMNS = 12;
	/** Godly: how fast a strand's height wanders, how far its tip leans, and how fast each column flickers. */
	static final float STRAND_SPEED = 0.05F, STRAND_LEAN = 0.12F, FLICKER = 0.8F, FLICKER_DEPTH = 0.3F;
	static final float[] GODLY_SIZE = {0.9F, 1.1F};
	static final float[] GODLY_LIGHT = {0.42F, 0.26F};
	static final float[] GODLY_SPIN = {1.0F, -0.7F};
	static final float[] GODLY_RISE = {0.03F, 0.02F};
	/** Godly's motes: how many, their size, how fast they rise (share of the height a tick), and how far from the body. */
	static final int MOTES = 16;
	static final float MOTE_SIZE = 0.05F, MOTE_RISE = 0.006F, MOTE_OUT = 0.14F, MOTE_LIGHT = 0.7F;

	/** Sparking's sparks: how many, how long each lasts in place (ticks), how many are lit, their length and width, the share whitened. */
	static final int SPARKS = 9, SPARK_TICKS = 4;
	static final float SPARK_SHOWN = 0.65F, SPARK_LENGTH = 0.34F, SPARK_WIDTH = 0.16F, SPARK_WHITE = 0.55F, SPARK_LIGHT = 0.95F;
	/** Sparking's sparks sit on the flame shell between these rings. */
	static final int SPARK_FROM = 2, SPARK_SPAN = 4;

	private ZFighterKiAuraStyles() {
	}

	private static RenderType type(String path) {
		Identifier texture = JaymosPowers.id(path);
		return RenderTypes.energySwirl(texture, 0.0F, 0.0F);
	}

	// ---- Smooth ----

	static void smooth(PoseStack poseStack, SubmitNodeCollector collector, int color, float strength, float size, float age) {
		for (int layer = 0; layer < SMOOTH_SIZE.length; layer++) {
			float shimmer = 1.0F - SMOOTH_SHIMMER + SMOOTH_SHIMMER * Mth.sin(age * SMOOTH_SHIMMER_SPEED + layer * 2.0F);
			float light = Mth.clamp(SMOOTH_LIGHT[layer] * strength * shimmer, 0.0F, 1.0F);
			float s = SMOOTH_SIZE[layer] * size;
			float spin = age * SMOOTH_SPIN[layer] * Mth.DEG_TO_RAD, rise = age * SMOOTH_RISE[layer];
			collector.order(6 + layer).submitCustomGeometry(poseStack, SMOOTH_TYPE,
				(pose, buffer) -> shell(pose, buffer, SMOOTH_RADIUS, SMOOTH_RING_LIGHT, SMOOTH_COLUMNS, SMOOTH_TOP, null, 0.0F, color, light, s, spin,
					rise, age));
		}
	}

	// ---- Godly ----

	static void godly(PoseStack poseStack, SubmitNodeCollector collector, int color, float strength, float size, float age) {
		for (int layer = 0; layer < GODLY_SIZE.length; layer++) {
			float light = Mth.clamp(GODLY_LIGHT[layer] * strength, 0.0F, 1.0F);
			float s = GODLY_SIZE[layer] * size;
			float spin = age * GODLY_SPIN[layer] * Mth.DEG_TO_RAD, rise = age * GODLY_RISE[layer];
			int seed = layer * 5;
			collector.order(6 + layer).submitCustomGeometry(poseStack, GODLY_TYPE,
				(pose, buffer) -> shell(pose, buffer, GODLY_RADIUS, GODLY_RING_LIGHT, GODLY_COLUMNS, GODLY_TOP, new float[] {seed}, 1.0F, color, light,
					s, spin, rise, age));
		}
		float light = Mth.clamp(MOTE_LIGHT * strength, 0.0F, 1.0F);
		collector.order(8).submitCustomGeometry(poseStack, SPARK_TYPE, (pose, buffer) -> motes(pose, buffer, color, light, size, age));
	}

	/**
	 * One shell of columns round the body, a strip of quads each, feet to top. {@code strands} (a one-number seed) makes
	 * each column rise to its own wandering height, lean and flicker; null keeps the shell a smooth oval.
	 */
	private static void shell(PoseStack.Pose pose, VertexConsumer buffer, float[] radius, float[] ringLight, int columns, float top, float[] strands,
		float strandShare, int color, float light, float size, float spin, float rise, float age) {
		int rings = radius.length;
		float[][] x = new float[columns + 1][rings], y = new float[columns + 1][rings], z = new float[columns + 1][rings];
		float[] flicker = new float[columns + 1];
		for (int c = 0; c <= columns; c++) {
			int col = c % columns;
			float a = Mth.TWO_PI * col / columns + spin;
			float cos = Mth.cos(a), sin = Mth.sin(a);
			float seed = strands == null ? 0.0F : strands[0];
			float height = 0.0F, lean = 0.0F;
			flicker[c] = 1.0F;
			if (strands != null) {
				height = GODLY_STRAND * strandShare * (0.5F + 0.5F * Mth.sin(age * STRAND_SPEED * (1.0F + 0.17F * ((col * 3 + (int)seed) % 5)) + col * 2.7F + seed));
				lean = STRAND_LEAN * Mth.sin(age * 0.11F + col * 1.9F + seed);
				flicker[c] = 1.0F - FLICKER_DEPTH + FLICKER_DEPTH * Mth.sin(age * FLICKER + col * 2.3F + seed);
			}
			for (int r = 0; r < rings; r++) {
				float t = (float)r / (rings - 1);
				// the body's oval sits on the same ground whatever the size
				float yy = ZFighterKiAura.BOTTOM - (ZFighterKiAura.BOTTOM - Mth.lerp(t, ZFighterKiAura.BOTTOM, top - height)) * size;
				float rr = radius[r] * size;
				float sway = strands == null ? 0.0F : lean * t * t * size;
				x[c][r] = cos * rr + sway;
				z[c][r] = sin * rr;
				y[c][r] = yy;
			}
		}
		for (int c = 0; c < columns; c++) {
			float u0 = 2.0F * c / columns, u1 = 2.0F * (c + 1) / columns;
			float f0 = flicker[c], f1 = flicker[c + 1];
			for (int r = 0; r < rings - 1; r++) {
				float v0 = (1.0F - (float)r / (rings - 1)) + rise, v1 = (1.0F - (float)(r + 1) / (rings - 1)) + rise;
				int lo0 = ZFighterKiAura.Layer.tint(color, light * ringLight[r] * f0), lo1 = ZFighterKiAura.Layer.tint(color, light * ringLight[r] * f1);
				int hi0 = ZFighterKiAura.Layer.tint(color, light * ringLight[r + 1] * f0), hi1 = ZFighterKiAura.Layer.tint(color, light * ringLight[r + 1] * f1);
				ZFighterKiAura.Layer.vertex(pose, buffer, x[c][r], y[c][r], z[c][r], u0, v0, lo0);
				ZFighterKiAura.Layer.vertex(pose, buffer, x[c + 1][r], y[c + 1][r], z[c + 1][r], u1, v0, lo1);
				ZFighterKiAura.Layer.vertex(pose, buffer, x[c + 1][r + 1], y[c + 1][r + 1], z[c + 1][r + 1], u1, v1, hi1);
				ZFighterKiAura.Layer.vertex(pose, buffer, x[c][r + 1], y[c][r + 1], z[c][r + 1], u0, v1, hi0);
			}
		}
	}

	/** Tiny dots rising round the wisps, each fading in at his feet and out at the top. */
	private static void motes(PoseStack.Pose pose, VertexConsumer buffer, int color, float light, float size, float age) {
		float height = ZFighterKiAura.BOTTOM - GODLY_TOP + GODLY_STRAND * 0.5F;
		for (int i = 0; i < MOTES; i++) {
			float p = (age * MOTE_RISE * (0.8F + 0.4F * hash(i, 1)) + hash(i, 2)) % 1.0F;
			float a = Mth.TWO_PI * hash(i, 3) + age * 0.02F * (hash(i, 4) - 0.5F);
			float out = (GODLY_RADIUS[3] + MOTE_OUT * hash(i, 5)) * size + 0.03F * Mth.sin(age * 0.1F + i);
			float cx = Mth.cos(a) * out, cz = Mth.sin(a) * out;
			float cy = ZFighterKiAura.BOTTOM - p * height * size;
			float fade = Mth.sin(p * Mth.PI);
			int tint = ZFighterKiAura.Layer.tint(color, light * fade);
			float s = MOTE_SIZE * size;
			// two crossed quads on the dot (the strip's last cell)
			quad(pose, buffer, cx, cy, cz, 1.0F, 0.0F, 0.0F, 0.0F, -1.0F, 0.0F, s, s, 4.0F / 5.0F, 1.0F, tint);
			quad(pose, buffer, cx, cy, cz, 0.0F, 0.0F, 1.0F, 0.0F, -1.0F, 0.0F, s, s, 4.0F / 5.0F, 1.0F, tint);
		}
	}

	// ---- Sparking ----

	/** Little forked bolts that jump about on the flame shell, each staying put for {@link #SPARK_TICKS} then jumping. */
	static void sparks(PoseStack poseStack, SubmitNodeCollector collector, int color, float strength, float size, float age) {
		int bright = ARGB.color(255, Mth.lerpInt(SPARK_WHITE, ARGB.red(color), 255), Mth.lerpInt(SPARK_WHITE, ARGB.green(color), 255),
			Mth.lerpInt(SPARK_WHITE, ARGB.blue(color), 255));
		float light = Mth.clamp(SPARK_LIGHT * strength, 0.0F, 1.0F);
		collector.order(8).submitCustomGeometry(poseStack, SPARK_TYPE, (pose, buffer) -> {
			for (int i = 0; i < SPARKS; i++) {
				int epoch = (int)Math.floor(age / SPARK_TICKS + hash(i, 9) * 3.0F);
				if (hash(i, epoch * 3 + 10) > SPARK_SHOWN) {
					continue;
				}
				float ringAt = SPARK_FROM + hash(i, epoch * 3 + 11) * SPARK_SPAN;
				int r0 = (int)ringAt;
				float f = ringAt - r0;
				float radius = Mth.lerp(f, ZFighterKiAura.RADIUS[r0], ZFighterKiAura.RADIUS[r0 + 1]) * size * 1.03F;
				float t = (r0 + f) / (ZFighterKiAura.RADIUS.length - 1);
				float y = ZFighterKiAura.BOTTOM - (ZFighterKiAura.BOTTOM - Mth.lerp(t, ZFighterKiAura.BOTTOM, ZFighterKiAura.TOP)) * size;
				float a = Mth.TWO_PI * hash(i, epoch * 3 + 12);
				float cos = Mth.cos(a), sin = Mth.sin(a);
				float roll = Mth.TWO_PI * hash(i, epoch * 3 + 13);
				// along the bolt: rolled within the plane that touches the shell there
				float cr = Mth.cos(roll), sr = Mth.sin(roll);
				float ax = -sin * cr, ay = -sr, az = cos * cr;
				int cell = (int)(hash(i, epoch * 3 + 14) * 4.0F) % 4;
				int tint = ZFighterKiAura.Layer.tint(bright, light * (0.6F + 0.4F * hash(i, epoch * 3 + 15)));
				float half = SPARK_LENGTH * size * 0.5F, wide = SPARK_WIDTH * size * 0.5F;
				// one quad in the shell's surface (its width across the bolt), one standing out from it
				quad(pose, buffer, cos * radius, y, sin * radius, ax, ay, az, sin * sr, -cr, -cos * sr, half, wide, cell / 5.0F, (cell + 1) / 5.0F, tint);
				quad(pose, buffer, cos * radius, y, sin * radius, ax, ay, az, cos, 0.0F, sin, half, wide, cell / 5.0F, (cell + 1) / 5.0F, tint);
			}
		});
	}

	/** A quad round (cx, cy, cz): {@code half} along axis A, {@code wide} along axis B; the strip's cell runs u0-u1 across, bottom to top along A. */
	private static void quad(PoseStack.Pose pose, VertexConsumer buffer, float cx, float cy, float cz, float ax, float ay, float az, float bx, float by,
		float bz, float half, float wide, float u0, float u1, int tint) {
		ZFighterKiAura.Layer.vertex(pose, buffer, cx - ax * half - bx * wide, cy - ay * half - by * wide, cz - az * half - bz * wide, u0, 1.0F, tint);
		ZFighterKiAura.Layer.vertex(pose, buffer, cx - ax * half + bx * wide, cy - ay * half + by * wide, cz - az * half + bz * wide, u1, 1.0F, tint);
		ZFighterKiAura.Layer.vertex(pose, buffer, cx + ax * half + bx * wide, cy + ay * half + by * wide, cz + az * half + bz * wide, u1, 0.0F, tint);
		ZFighterKiAura.Layer.vertex(pose, buffer, cx + ax * half - bx * wide, cy + ay * half - by * wide, cz + az * half - bz * wide, u0, 0.0F, tint);
	}

	/** A steady pseudo-random number from 0 to 1 for this (index, step), so a spark or mote stays put between frames. */
	private static float hash(int a, int b) {
		int h = a * 374761393 + b * 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		h ^= h >>> 16;
		return (h & 0xFFFFFF) / 16777216.0F;
	}
}
