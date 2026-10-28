package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.zfighter.ZFighterAura;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * (0.17.46, Andy's NOEA picks) The three looks of the ki aura, picked on the training screen.
 * <p>
 * (0.17.84) Andy: "all auras need to be more of a wisp making contact and dissipating rather than being a solid mesh",
 * with four pictures: thin blue-white tongues clinging to a fighter and flaking off him, a blaze of flame tongues
 * licking up past a fighter and breaking into sparks, fighters flying with their aura streaming behind them like a
 * comet, and a soft wavering glow hugging a fighter's outline, flecked with sparkles. So no style is a shell any more:
 * each is many {@link #wisps}, single tongues of light (texture {@code tools/gen_ki_wisp.py}) born touching the body,
 * licking up and off it, drifting away and fading out, each reborn somewhere else when it's gone. In flight they bend
 * back along him and stretch out into a tail ({@code trail}). The styles differ only in their wisps' numbers:
 * <ul>
 * <li>Sparking: a blaze of quick flame tongues, with {@link #sparks} crackling in it;</li>
 * <li>Smooth: short, wide, slow tongues close to the body, a wavering glow round his outline, with {@link #motes};</li>
 * <li>Godly: tall, thin, whitened strands hugging the body and peeling off slowly, with {@link #motes}.</li>
 * </ul>
 */
final class ZFighterKiAuraStyles {
	private static final RenderType WISP_TYPE = type("textures/entity/zfighter_ki_wisp.png");
	private static final RenderType SPARK_TYPE = type("textures/entity/zfighter_ki_aura_sparks.png");

	/** The body the wisps cling to (model units, y down): feet, top of the head, and its half-width at each step up it. */
	static final float FEET = 1.5F, CROWN = -0.5F;
	static final float[] BODY = {0.17F, 0.21F, 0.23F, 0.27F, 0.33F, 0.39F, 0.40F, 0.31F, 0.25F};
	/** How far out from the body a wisp is born. */
	static final float ROOT_OUT = 0.05F;
	/** Segments along each wisp. */
	static final int SEGMENTS = 5;

	/**
	 * Each style's wisps (Sparking, Smooth, Godly): how many; how long each lives (ticks); its full length and width; how
	 * far it drifts up and off the body over its life; how far it leans outward; how much it waves and how fast; how
	 * bright; how much whiter than the colour; and how high on the body they're born (a power: above 1 favours the feet).
	 */
	static final int[] COUNT = {70, 72, 40};
	static final float[] LIFE = {10.0F, 26.0F, 20.0F};
	static final float[] LENGTH = {0.75F, 0.42F, 0.85F};
	static final float[] WIDTH = {0.22F, 0.42F, 0.09F};
	static final float[] RISE = {0.55F, 0.16F, 0.7F};
	static final float[] LEAN = {0.35F, 0.12F, 0.08F};
	static final float[] WAVE = {0.07F, 0.04F, 0.09F};
	static final float[] WAVE_SPEED = {0.9F, 0.22F, 0.4F};
	static final float[] LIGHT = {0.6F, 0.3F, 0.55F};
	static final float[] WHITE = {0.15F, 0.25F, 0.45F};
	static final float[] LOW = {1.15F, 0.9F, 1.0F};
	/** A wisp's life: it reaches full length by this share of it and starts to shrink after this one. */
	static final float GROWN = 0.25F, SHRINK = 0.6F;
	/**
	 * Flying: bent back toward his feet (the way he's laid out at speed), this much longer and drifting this much further,
	 * and drawn in toward his line by this share at their far ends, so the tail narrows like a comet's.
	 */
	static final float TRAIL_LENGTH = 3.0F, TRAIL_DRIFT = 4.0F, TRAIL_NARROW = 0.75F;

	/** Motes (Smooth and Godly): how many, their size, how fast they rise (share of the height a tick), and how far out. */
	static final int MOTES = 16;
	static final float MOTE_SIZE = 0.05F, MOTE_RISE = 0.006F, MOTE_OUT = 0.14F, MOTE_LIGHT = 0.7F;

	/** Sparking's sparks: how many, how long each lasts in place (ticks), how many are lit, their length and width, the share whitened. */
	static final int SPARKS = 9, SPARK_TICKS = 4;
	static final float SPARK_SHOWN = 0.65F, SPARK_LENGTH = 0.34F, SPARK_WIDTH = 0.16F, SPARK_WHITE = 0.55F, SPARK_LIGHT = 0.95F;

	private ZFighterKiAuraStyles() {
	}

	private static RenderType type(String path) {
		Identifier texture = JaymosPowers.id(path);
		return RenderTypes.energySwirl(texture, 0.0F, 0.0F);
	}

	/** The whole aura in {@code style}: its wisps, then its sparks or motes. {@code trail}: 0 standing to 1 flying flat out. */
	static void draw(PoseStack poseStack, SubmitNodeCollector collector, int style, int color, float strength, float size, float trail, float age) {
		int s = Mth.clamp(style, 0, COUNT.length - 1);
		int tinted = ARGB.color(255, Mth.lerpInt(WHITE[s], ARGB.red(color), 255), Mth.lerpInt(WHITE[s], ARGB.green(color), 255),
			Mth.lerpInt(WHITE[s], ARGB.blue(color), 255));
		float light = Mth.clamp(LIGHT[s] * strength, 0.0F, 1.0F);
		collector.order(6).submitCustomGeometry(poseStack, WISP_TYPE, (pose, buffer) -> wisps(pose, buffer, s, tinted, light, size, trail, age));
		if (style == ZFighterAura.SPARKING) {
			sparks(poseStack, collector, color, strength, size, age);
		} else {
			float moteLight = Mth.clamp(MOTE_LIGHT * strength, 0.0F, 1.0F);
			collector.order(8).submitCustomGeometry(poseStack, SPARK_TYPE, (pose, buffer) -> motes(pose, buffer, tinted, moteLight, size, age));
		}
	}

	// ---- Wisps ----

	/** The body's half-width {@code t} of the way from his feet to the top of his head. */
	private static float body(float t) {
		float at = Mth.clamp(t, 0.0F, 1.0F) * (BODY.length - 1);
		int i = Math.min((int)at, BODY.length - 2);
		return Mth.lerp(at - i, BODY[i], BODY[i + 1]);
	}

	/**
	 * Every wisp of style {@code s}: each lives {@link #LIFE} ticks from a staggered start, born at a fresh spot on the body
	 * each life (so the aura never repeats), grows out, drifts up and away and fades, as two crossed ribbons of
	 * {@link #SEGMENTS} quads (one flat to the body, one standing out from it) so it reads from every side.
	 */
	private static void wisps(PoseStack.Pose pose, VertexConsumer buffer, int s, int color, float light, float size, float trail, float age) {
		float height = FEET - CROWN;
		for (int i = 0; i < COUNT[s]; i++) {
			float clock = age / (LIFE[s] * (0.8F + 0.4F * hash(i, 1))) + hash(i, 2);
			int life = (int)Math.floor(clock);
			float p = clock - life;
			float env = Mth.clamp(p / 0.12F, 0.0F, 1.0F) * (float)Math.pow(1.0F - p, 1.4F);
			if (env < 0.02F) {
				continue;
			}
			// where on the body it touched
			float t = (float)Math.pow(hash(i, life * 5 + 3), LOW[s]);
			float a = Mth.TWO_PI * hash(i, life * 5 + 4);
			float cos = Mth.cos(a), sin = Mth.sin(a);
			float out = (body(t) + ROOT_OUT) * size;
			float rootY = FEET - t * height * size;
			// which way it licks: up and a little out, bent back toward his feet the faster he flies
			float lean = LEAN[s] * (0.6F + 0.8F * hash(i, life * 5 + 5));
			float dx = cos * lean * (1.0F - trail), dy = Mth.lerp(trail, -1.0F, 1.0F), dz = sin * lean * (1.0F - trail) + 0.25F * trail;
			float norm = Mth.sqrt(dx * dx + dy * dy + dz * dz);
			dx /= norm;
			dy /= norm;
			dz /= norm;
			float length = LENGTH[s] * size * (0.7F + 0.6F * hash(i, life * 5 + 6)) * (1.0F + TRAIL_LENGTH * trail)
				* Math.min(1.0F, p / GROWN) * (p > SHRINK ? 1.0F - 0.5F * (p - SHRINK) / (1.0F - SHRINK) : 1.0F);
			float drift = RISE[s] * size * (1.0F + TRAIL_DRIFT * trail) * (float)Math.pow(p, 1.3F);
			float in = 1.0F - TRAIL_NARROW * trail * p;
			float rx = cos * out * in + dx * drift, ry = rootY + dy * drift, rz = sin * out * in + dz * drift;
			// across it: along the body's surface, and straight out from it
			float tx = -sin, tz = cos;
			float width = WIDTH[s] * size * (0.75F + 0.5F * hash(i, life * 5 + 7)) * (1.0F + 0.4F * trail);
			int bright = ZFighterKiAura.Layer.tint(color, light * env);
			float wavePhase = age * WAVE_SPEED[s] + i * 1.7F;
			float[] px = new float[SEGMENTS + 1], py = new float[SEGMENTS + 1], pz = new float[SEGMENTS + 1];
			for (int k = 0; k <= SEGMENTS; k++) {
				float f = (float)k / SEGMENTS;
				float wave = WAVE[s] * size * f * Mth.sin(wavePhase + f * 3.2F);
				// (flying: the far end drawn in toward his line)
				float narrow = 1.0F - TRAIL_NARROW * trail * f * (1.0F - p);
				px[k] = (rx + dx * length * f) * narrow + tx * wave;
				py[k] = ry + dy * length * f;
				pz[k] = (rz + dz * length * f) * narrow + tz * wave;
			}
			ribbon(pose, buffer, px, py, pz, tx, 0.0F, tz, width, bright);
			ribbon(pose, buffer, px, py, pz, cos, 0.0F, sin, width, bright);
		}
	}

	/** One ribbon down the points, {@code width} across along (bx, by, bz); the texture's root at the first point, its tip at the last. */
	private static void ribbon(PoseStack.Pose pose, VertexConsumer buffer, float[] px, float[] py, float[] pz, float bx, float by, float bz, float width,
		int tint) {
		float half = width * 0.5F;
		for (int k = 0; k < SEGMENTS; k++) {
			float v0 = 1.0F - (float)k / SEGMENTS, v1 = 1.0F - (float)(k + 1) / SEGMENTS;
			ZFighterKiAura.Layer.vertex(pose, buffer, px[k] - bx * half, py[k] - by * half, pz[k] - bz * half, 0.0F, v0, tint);
			ZFighterKiAura.Layer.vertex(pose, buffer, px[k] + bx * half, py[k] + by * half, pz[k] + bz * half, 1.0F, v0, tint);
			ZFighterKiAura.Layer.vertex(pose, buffer, px[k + 1] + bx * half, py[k + 1] + by * half, pz[k + 1] + bz * half, 1.0F, v1, tint);
			ZFighterKiAura.Layer.vertex(pose, buffer, px[k + 1] - bx * half, py[k + 1] - by * half, pz[k + 1] - bz * half, 0.0F, v1, tint);
		}
	}

	// ---- Motes ----

	/** Tiny dots rising round the body, each fading in at his feet and out above his head. */
	private static void motes(PoseStack.Pose pose, VertexConsumer buffer, int color, float light, float size, float age) {
		float height = FEET - CROWN + 0.4F;
		for (int i = 0; i < MOTES; i++) {
			float p = (age * MOTE_RISE * (0.8F + 0.4F * hash(i, 11)) + hash(i, 12)) % 1.0F;
			float a = Mth.TWO_PI * hash(i, 13) + age * 0.02F * (hash(i, 14) - 0.5F);
			float out = (body(p) + MOTE_OUT * hash(i, 15)) * size + 0.03F * Mth.sin(age * 0.1F + i);
			float cx = Mth.cos(a) * out, cz = Mth.sin(a) * out;
			float cy = FEET - p * height * size;
			float fade = Mth.sin(p * Mth.PI);
			int tint = ZFighterKiAura.Layer.tint(color, light * fade);
			float s = MOTE_SIZE * size;
			// two crossed quads on the dot (the strip's last cell)
			quad(pose, buffer, cx, cy, cz, 1.0F, 0.0F, 0.0F, 0.0F, -1.0F, 0.0F, s, s, 4.0F / 5.0F, 1.0F, tint);
			quad(pose, buffer, cx, cy, cz, 0.0F, 0.0F, 1.0F, 0.0F, -1.0F, 0.0F, s, s, 4.0F / 5.0F, 1.0F, tint);
		}
	}

	// ---- Sparking ----

	/** Little forked bolts that jump about in the blaze, each staying put for {@link #SPARK_TICKS} then jumping. */
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
				float t = 0.15F + 0.75F * hash(i, epoch * 3 + 11);
				float radius = (body(t) + ROOT_OUT + 0.08F) * size;
				float y = FEET - t * (FEET - CROWN) * size;
				float a = Mth.TWO_PI * hash(i, epoch * 3 + 12);
				float cos = Mth.cos(a), sin = Mth.sin(a);
				float roll = Mth.TWO_PI * hash(i, epoch * 3 + 13);
				// along the bolt: rolled within the plane that touches the body there
				float cr = Mth.cos(roll), sr = Mth.sin(roll);
				float ax = -sin * cr, ay = -sr, az = cos * cr;
				int cell = (int)(hash(i, epoch * 3 + 14) * 4.0F) % 4;
				int tint = ZFighterKiAura.Layer.tint(bright, light * (0.6F + 0.4F * hash(i, epoch * 3 + 15)));
				float half = SPARK_LENGTH * size * 0.5F, wide = SPARK_WIDTH * size * 0.5F;
				// one quad in the body's surface (its width across the bolt), one standing out from it
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

	/** A steady pseudo-random number from 0 to 1 for this (index, step), so a wisp, spark or mote stays put between frames. */
	private static float hash(int a, int b) {
		int h = a * 374761393 + b * 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		h ^= h >>> 16;
		return (h & 0xFFFFFF) / 16777216.0F;
	}
}
