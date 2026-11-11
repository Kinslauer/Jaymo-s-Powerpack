package com.jaymospowers.client.fx;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.fx.MagicFx;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * {@link MagicFx} on this side: the one drawing of each type of effect.
 * <ul>
 * <li>Beam, blast, ring and orb are all the beacon beam's texture, scrolling: a tube, a swelling shell, a running band
 * and a ball. The sigil is our own circle of marks, turning on the ground.</li>
 * <li>{@link Mote}, {@link Miasma}, {@link Spark} and {@link Rune} are the particles: a glint of light, a puff of murk,
 * a crackle and a written mark.</li>
 * </ul>
 * {@link #beam}, {@link #blast}, {@link #ring}, {@link #orb} and {@link #sigil} make the same shapes with no packet, for
 * what plays on the client alone (a film, a look drawn from synced state).
 */
public final class MagicFxClient {
	/** A beam: its core's share of the width, its glow's alpha, how fast the texture runs, and its sides. */
	static final float BEAM_CORE = 0.45F, BEAM_SCROLL = 0.6F;
	static final int BEAM_GLOW_ALPHA = 0x70, BEAM_SIDES = 8;
	/** The share of a beam's life it swells in over, and the size of the glow at each end (times its radius). */
	static final float BEAM_SWELL = 0.15F, BEAM_END = 2.4F;
	/** A blast: its shell's and inner glow's alpha at the start, and the heart's share of the radius and of the life. */
	static final int BLAST_SHELL_ALPHA = 0xA8, BLAST_INNER_ALPHA = 0x60;
	static final float BLAST_HEART = 0.4F, BLAST_HEART_LIFE = 0.45F;
	/** Motes a blast throws per block of radius, and how fast they leave (blocks/tick per block of radius). */
	static final float BLAST_MOTES_PER_BLOCK = 5.0F, BLAST_MOTE_SPEED = 0.07F;
	/** A ring: the band's share of the radius, its wall's height (share of the radius, and at least), and its segments. */
	static final float RING_BAND = 0.3F, RING_WALL = 0.1F, RING_WALL_MIN = 0.25F;
	static final int RING_SEGMENTS = 40, RING_ALPHA = 0xC0;
	/** An orb: how much it breathes, its heart's share, and how fast its light streams. */
	static final float ORB_PULSE = 0.05F, ORB_HEART = 0.55F, ORB_FLOW = 0.03F;
	/** A sigil: how far off the ground it lies, its turn per tick (radians), the ticks it fades in and out over. */
	static final float SIGIL_LIFT = 0.03F, SIGIL_SPIN = 0.02F, SIGIL_FADE = 6.0F;
	static final int SIGIL_ALPHA = 0xD0;
	static final Identifier SIGIL_TEXTURE = JaymosPowers.id("textures/fx/sigil.png");
	/** Spheres are this many faces round and down (a blast's shell is thin and brief, so fewer). */
	static final int LON = 16, LAT = 10;
	/** The most shapes alive at once: past it the oldest go. */
	static final int MAX_SHAPES = 256;

	private static final class Live {
		final MagicFx.Shape shape;
		int age;

		Live(MagicFx.Shape shape) {
			this.shape = shape;
		}
	}

	private static final List<Live> SHAPES = new ArrayList<>();

	private MagicFxClient() {
	}

	public static void init() {
		ParticleProviderRegistry.getInstance().register(MagicFx.MOTE, sprites -> (options, level, x, y, z, xd, yd, zd, random) ->
			new Mote(level, x, y, z, xd, yd, zd, options.color(), options.size(), sprites));
		ParticleProviderRegistry.getInstance().register(MagicFx.MIASMA, sprites -> (options, level, x, y, z, xd, yd, zd, random) ->
			new Miasma(level, x, y, z, xd, yd, zd, options.color(), options.size(), sprites));
		ParticleProviderRegistry.getInstance().register(MagicFx.SPARK, sprites -> (options, level, x, y, z, xd, yd, zd, random) ->
			new Spark(level, x, y, z, xd, yd, zd, options.color(), options.size(), sprites));
		ParticleProviderRegistry.getInstance().register(MagicFx.RUNE, sprites -> (options, level, x, y, z, xd, yd, zd, random) ->
			new Rune(level, x, y, z, xd, yd, zd, options.color(), options.size(), sprites));
		ClientPlayNetworking.registerGlobalReceiver(MagicFx.Shape.TYPE, (shape, context) -> add(shape));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (minecraft.level == null) {
				SHAPES.clear();
				return;
			}
			if (minecraft.isPaused()) {
				return;
			}
			for (Iterator<Live> it = SHAPES.iterator(); it.hasNext(); ) {
				Live live = it.next();
				if (++live.age > live.shape.ticks()) {
					it.remove();
				}
			}
		});
		LevelRenderEvents.COLLECT_SUBMITS.register(MagicFxClient::render);
	}

	/** A beam from {@code from} to {@code to}, made here (no packet): see {@link MagicFx#beam}. */
	public static void beam(Vec3 from, Vec3 to, float radius, int color, int ticks) {
		Vec3 d = to.subtract(from);
		add(new MagicFx.Shape(MagicFx.BEAM, from.x, from.y, from.z, (float)d.x, (float)d.y, (float)d.z, radius, color & 0xFFFFFF, Math.max(1, ticks)));
	}

	/** A blast at {@code at}, made here: see {@link MagicFx#blast}. */
	public static void blast(Vec3 at, float radius, int color) {
		add(new MagicFx.Shape(MagicFx.BLAST, at.x, at.y, at.z, 0.0F, 0.0F, 0.0F, radius, color & 0xFFFFFF, MagicFx.BLAST_TICKS));
	}

	/** A shockwave on the ground at {@code at}, made here: see {@link MagicFx#ring}. */
	public static void ring(Vec3 at, float radius, int color) {
		add(new MagicFx.Shape(MagicFx.RING, at.x, at.y, at.z, 0.0F, 0.0F, 0.0F, radius, color & 0xFFFFFF, MagicFx.RING_TICKS));
	}

	/** An orb at {@code at} for {@code ticks}, made here: see {@link MagicFx#orb}. */
	public static void orb(Vec3 at, float radius, int color, int ticks) {
		add(new MagicFx.Shape(MagicFx.ORB, at.x, at.y, at.z, 0.0F, 0.0F, 0.0F, radius, color & 0xFFFFFF, Math.max(1, ticks)));
	}

	/** A sigil on the ground at {@code at} for {@code ticks}, made here: see {@link MagicFx#sigil}. */
	public static void sigil(Vec3 at, float radius, int color, int ticks) {
		add(new MagicFx.Shape(MagicFx.SIGIL, at.x, at.y, at.z, 0.0F, 0.0F, 0.0F, radius, color & 0xFFFFFF, Math.max(1, ticks)));
	}

	private static void add(MagicFx.Shape shape) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return;
		}
		if (SHAPES.size() >= MAX_SHAPES) {
			SHAPES.remove(0);
		}
		SHAPES.add(new Live(shape));
		if (shape.kind() == MagicFx.BLAST) {
			// motes thrown out of it, so the burst leaves something in the air as the shell thins
			RandomSource random = minecraft.level.getRandom();
			int motes = Mth.clamp(Math.round(shape.size() * BLAST_MOTES_PER_BLOCK), 4, MagicFx.BLAST_MOTES);
			for (int i = 0; i < motes; i++) {
				Vec3 way = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
				if (way.lengthSqr() < 1.0E-6) {
					continue;
				}
				way = way.normalize().scale(shape.size() * BLAST_MOTE_SPEED * (0.5 + random.nextDouble()));
				minecraft.level.addParticle(MagicFx.wisp(shape.color(), 1.0F + shape.size() * 0.15F), shape.x(), shape.y(), shape.z(), way.x, way.y, way.z);
			}
		}
	}

	private static void render(LevelRenderContext context) {
		if (SHAPES.isEmpty()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = context.levelState().cameraRenderState.pos;
		float time = minecraft.level == null ? 0.0F : (minecraft.level.getGameTime() % 4000L) + partial;
		SubmitNodeCollector collector = context.submitNodeCollector();
		PoseStack poseStack = context.poseStack();
		for (Live live : SHAPES) {
			MagicFx.Shape s = live.shape;
			float life = Mth.clamp((live.age + partial) / s.ticks(), 0.0F, 1.0F);
			Vec3 at = new Vec3(s.x(), s.y(), s.z()).subtract(camera);
			switch (s.kind()) {
				case MagicFx.BEAM -> drawBeam(collector, poseStack, at, at.add(s.dx(), s.dy(), s.dz()), s.size(), s.color(), time, life);
				case MagicFx.BLAST -> drawBlast(collector, poseStack, at, s.size(), s.color(), time, life);
				case MagicFx.RING -> drawRing(collector, poseStack, at, new Vec3(s.dx(), s.dy(), s.dz()), s.size(), s.color(), time, life);
				case MagicFx.ORB -> drawOrb(collector, poseStack, at, s.size(), s.color(), time, fade(live.age + partial, s.ticks(), 3.0F));
				case MagicFx.SIGIL -> drawSigil(collector, poseStack, at, s.size(), s.color(), time, fade(live.age + partial, s.ticks(), SIGIL_FADE));
				default -> {
				}
			}
		}
	}

	/** 0-1: in over the first {@code ramp} ticks, out over the last. */
	private static float fade(float age, int ticks, float ramp) {
		float r = Math.min(ramp, ticks * 0.5F);
		if (r <= 0.0F) {
			return 1.0F;
		}
		return Mth.clamp(Math.min(age / r, (ticks - age) / r), 0.0F, 1.0F);
	}

	/**
	 * One beam, camera-relative, {@code life} 0-1 through its time: it swells in, then thins away. Public so a held
	 * beam can be drawn by its owner every frame ({@code life} {@link #BEAM_SWELL}: full width).
	 */
	public static void drawBeam(SubmitNodeCollector collector, PoseStack poseStack, Vec3 a, Vec3 b, float radius, int color, float time, float life) {
		float width = life < BEAM_SWELL ? life / BEAM_SWELL : 1.0F - (life - BEAM_SWELL) / (1.0F - BEAM_SWELL) * 0.85F;
		float r = radius * width;
		if (r <= 0.004F) {
			return;
		}
		boolean dark = MagicFx.dark(color);
		// light has a white-hot core; murk has a core of its own colour, deeper
		int core = 0xFF000000 | (dark ? color & 0xFFFFFF : mix(color, 0xFFFFFF, 0.55F));
		int glow = BEAM_GLOW_ALPHA << 24 | (dark ? mix(color, 0x000000, 0.3F) : color);
		float v0 = -time * BEAM_SCROLL;
		float v1 = v0 + (float)a.distanceTo(b) * 0.5F;
		collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(BeaconRenderer.BEAM_LOCATION, false),
			(pose, buffer) -> tube(buffer, pose, a, b, r * BEAM_CORE, core, v0, v1));
		collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(BeaconRenderer.BEAM_LOCATION, true),
			(pose, buffer) -> tube(buffer, pose, a, b, r, glow, v0 * 0.7F, v1 * 0.7F));
		float fade = 1.0F - life;
		drawOrb(collector, poseStack, a, r * BEAM_END, color, time, fade);
		drawOrb(collector, poseStack, b, r * BEAM_END * 1.2F, color, time + 20.0F, fade);
	}

	/** One blast: the shell runs out to {@code radius} fast and slows, thinning as it goes; the heart flares and is gone. */
	public static void drawBlast(SubmitNodeCollector collector, PoseStack poseStack, Vec3 at, float radius, int color, float time, float life) {
		float out = 1.0F - (1.0F - life) * (1.0F - life) * (1.0F - life);
		float r = radius * out;
		float thin = (1.0F - life) * (1.0F - life);
		boolean dark = MagicFx.dark(color);
		int shell = (int)(BLAST_SHELL_ALPHA * thin) << 24 | (dark ? color & 0xFFFFFF : mix(color, 0xFFFFFF, 0.25F));
		int inner = (int)(BLAST_INNER_ALPHA * thin) << 24 | (color & 0xFFFFFF);
		float flow = time * ORB_FLOW;
		if (r > 0.02F && thin > 0.01F) {
			collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(BeaconRenderer.BEAM_LOCATION, true),
				(pose, buffer) -> sphere(buffer, pose, at, r * 0.82F, inner, -flow, flow));
			collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(BeaconRenderer.BEAM_LOCATION, true),
				(pose, buffer) -> sphere(buffer, pose, at, r, shell, flow, -flow * 0.7F));
		}
		if (life < BLAST_HEART_LIFE) {
			float h = radius * BLAST_HEART * (1.0F - life / BLAST_HEART_LIFE);
			int heart = 0xFF000000 | (dark ? color & 0xFFFFFF : mix(color, 0xFFFFFF, 0.8F));
			collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(BeaconRenderer.BEAM_LOCATION, false),
				(pose, buffer) -> sphere(buffer, pose, at, h, heart, flow * 1.7F, flow * 1.5F));
		}
	}

	/**
	 * One shockwave across the plane facing {@code normal} (zero: flat): a band of light running out, clear behind and
	 * bright at its front, with a low wall of light standing on the front so it shows from the side.
	 */
	public static void drawRing(SubmitNodeCollector collector, PoseStack poseStack, Vec3 at, Vec3 normal, float radius, int color, float time, float life) {
		float out = 1.0F - (1.0F - life) * (1.0F - life);
		float r1 = radius * out;
		float r0 = Math.max(0.0F, r1 - radius * RING_BAND);
		float thin = 1.0F - life;
		if (r1 <= 0.02F || thin <= 0.01F) {
			return;
		}
		Vec3 n = normal.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 1.0, 0.0) : normal.normalize();
		Vec3 u = (Math.abs(n.y) < 0.95 ? n.cross(new Vec3(0.0, 1.0, 0.0)) : n.cross(new Vec3(1.0, 0.0, 0.0))).normalize();
		Vec3 w = n.cross(u).normalize();
		int rgb = MagicFx.dark(color) ? color & 0xFFFFFF : mix(color, 0xFFFFFF, 0.3F);
		int front = (int)(RING_ALPHA * thin) << 24 | rgb;
		int back = rgb;
		float wall = Math.max(RING_WALL_MIN, radius * RING_WALL) * (0.4F + 0.6F * thin);
		float v0 = -time * BEAM_SCROLL * 0.5F;
		Vec3 lift = n.scale(0.04);
		collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(BeaconRenderer.BEAM_LOCATION, true), (pose, buffer) -> {
			for (int k = 0; k < RING_SEGMENTS; k++) {
				double t0 = k * Mth.TWO_PI / RING_SEGMENTS;
				double t1 = (k + 1) * Mth.TWO_PI / RING_SEGMENTS;
				Vec3 d0 = u.scale(Math.cos(t0)).add(w.scale(Math.sin(t0)));
				Vec3 d1 = u.scale(Math.cos(t1)).add(w.scale(Math.sin(t1)));
				float u0 = k / (float)RING_SEGMENTS * 4.0F;
				float u1 = (k + 1) / (float)RING_SEGMENTS * 4.0F;
				Vec3 i0 = at.add(d0.scale(r0)).add(lift);
				Vec3 i1 = at.add(d1.scale(r0)).add(lift);
				Vec3 o0 = at.add(d0.scale(r1)).add(lift);
				Vec3 o1 = at.add(d1.scale(r1)).add(lift);
				// the band on the ground, clear at its back edge
				quad(buffer, pose, i0, back, u0, v0, o0, front, u0, v0 + 1.0F, o1, front, u1, v0 + 1.0F, i1, back, u1, v0);
				// the wall on its front, clear at the top
				Vec3 h0 = o0.add(n.scale(wall));
				Vec3 h1 = o1.add(n.scale(wall));
				quad(buffer, pose, o0, front, u0, v0, h0, back, u0, v0 + 0.5F, h1, back, u1, v0 + 0.5F, o1, front, u1, v0);
			}
		});
	}

	/** One orb: a white-hot heart in a ball of the colour, see-through at the rim. {@code fade} 0-1 how solid. */
	public static void drawOrb(SubmitNodeCollector collector, PoseStack poseStack, Vec3 at, float radius, int color, float time, float fade) {
		float f = Mth.clamp(fade, 0.0F, 1.0F);
		if (radius <= 0.01F || f <= 0.01F) {
			return;
		}
		boolean dark = MagicFx.dark(color);
		float r = radius * (1.0F + ORB_PULSE * Mth.sin(time * 0.4F));
		int heart = 0xFF000000 | (dark ? mix(color, 0x000000, 0.4F) : mix(color, 0xFFFFFF, 0.75F));
		int glow = (int)(0xB0 * f) << 24 | (color & 0xFFFFFF);
		int rim = (int)(0x70 * f) << 24 | (dark ? color & 0xFFFFFF : mix(color, 0xFFFFFF, 0.3F));
		float spin = time * ORB_FLOW;
		float heartR = r * ORB_HEART * f;
		if (heartR > 0.005F) {
			collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(BeaconRenderer.BEAM_LOCATION, false),
				(pose, buffer) -> sphere(buffer, pose, at, heartR, heart, spin * 1.7F, spin * 1.5F));
		}
		collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(BeaconRenderer.BEAM_LOCATION, true),
			(pose, buffer) -> sphere(buffer, pose, at, r * 0.85F, glow, -spin, spin));
		collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(BeaconRenderer.BEAM_LOCATION, true),
			(pose, buffer) -> sphere(buffer, pose, at, r, rim, spin, -spin * 0.7F));
	}

	/** One sigil: the circle of marks flat on the ground, turning, and a smaller one inside it turning the other way. */
	public static void drawSigil(SubmitNodeCollector collector, PoseStack poseStack, Vec3 at, float radius, int color, float time, float fade) {
		if (radius <= 0.02F || fade <= 0.01F) {
			return;
		}
		int rgb = MagicFx.dark(color) ? color & 0xFFFFFF : mix(color, 0xFFFFFF, 0.25F);
		int tint = (int)(SIGIL_ALPHA * fade) << 24 | rgb;
		Vec3 c = at.add(0.0, SIGIL_LIFT, 0.0);
		collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(SIGIL_TEXTURE, true), (pose, buffer) -> {
			flat(buffer, pose, c, radius, time * SIGIL_SPIN, tint);
			flat(buffer, pose, c.add(0.0, 0.01, 0.0), radius * 0.55F, -time * SIGIL_SPIN * 1.6F, tint);
		});
	}

	/** The whole texture on a square lying flat, {@code radius} to each side's middle, turned {@code angle}. */
	private static void flat(VertexConsumer buffer, PoseStack.Pose pose, Vec3 c, float radius, float angle, int color) {
		float cos = Mth.cos(angle) * radius;
		float sin = Mth.sin(angle) * radius;
		// the corners: (-1,-1), (-1,1), (1,1), (1,-1), turned
		Vec3 p0 = c.add(-cos + sin, 0.0, -sin - cos);
		Vec3 p1 = c.add(-cos - sin, 0.0, -sin + cos);
		Vec3 p2 = c.add(cos - sin, 0.0, sin + cos);
		Vec3 p3 = c.add(cos + sin, 0.0, sin - cos);
		quad(buffer, pose, p0, color, 0.0F, 0.0F, p1, color, 0.0F, 1.0F, p2, color, 1.0F, 1.0F, p3, color, 1.0F, 0.0F);
	}

	/** A round tube from {@code a} to {@code b}, the texture running along it; both sides drawn. */
	public static void tube(VertexConsumer buffer, PoseStack.Pose pose, Vec3 a, Vec3 b, float radius, int color, float v0, float v1) {
		Vec3 dir = b.subtract(a);
		if (dir.lengthSqr() < 1.0E-6) {
			return;
		}
		dir = dir.normalize();
		Vec3 u = (Math.abs(dir.y) < 0.95 ? dir.cross(new Vec3(0.0, 1.0, 0.0)) : dir.cross(new Vec3(1.0, 0.0, 0.0))).normalize();
		Vec3 w = dir.cross(u).normalize();
		for (int k = 0; k < BEAM_SIDES; k++) {
			double t0 = k * Mth.TWO_PI / BEAM_SIDES;
			double t1 = (k + 1) * Mth.TWO_PI / BEAM_SIDES;
			Vec3 n0 = u.scale(Math.cos(t0)).add(w.scale(Math.sin(t0))).scale(radius);
			Vec3 n1 = u.scale(Math.cos(t1)).add(w.scale(Math.sin(t1))).scale(radius);
			float u0 = k / (float)BEAM_SIDES;
			float u1 = (k + 1) / (float)BEAM_SIDES;
			quad(buffer, pose, a.add(n0), color, u0, v0, b.add(n0), color, u0, v1, b.add(n1), color, u1, v1, a.add(n1), color, u1, v0);
		}
	}

	/** A ball, the texture wrapped round it and turning; both sides drawn. */
	public static void sphere(VertexConsumer buffer, PoseStack.Pose pose, Vec3 c, float radius, int color, float uShift, float vShift) {
		for (int i = 0; i < LAT; i++) {
			float t0 = Mth.PI * i / LAT;
			float t1 = Mth.PI * (i + 1) / LAT;
			for (int j = 0; j < LON; j++) {
				float p0 = Mth.TWO_PI * j / LON;
				float p1 = Mth.TWO_PI * (j + 1) / LON;
				float u0 = j / (float)LON * 2.0F + uShift;
				float u1 = (j + 1) / (float)LON * 2.0F + uShift;
				float v0 = i / (float)LAT + vShift;
				float v1 = (i + 1) / (float)LAT + vShift;
				quad(buffer, pose, point(c, radius, t0, p0), color, u0, v0, point(c, radius, t1, p0), color, u0, v1, point(c, radius, t1, p1), color, u1,
					v1, point(c, radius, t0, p1), color, u1, v0);
			}
		}
	}

	private static Vec3 point(Vec3 c, float radius, float theta, float phi) {
		float s = Mth.sin(theta);
		return c.add(radius * s * Mth.cos(phi), radius * Mth.cos(theta), radius * s * Mth.sin(phi));
	}

	/** Four corners, each with its own colour, drawn both ways round so it shows from either side. */
	private static void quad(VertexConsumer buffer, PoseStack.Pose pose, Vec3 a, int ca, float ua, float va, Vec3 b, int cb, float ub, float vb, Vec3 c,
		int cc, float uc, float vc, Vec3 d, int cd, float ud, float vd) {
		vertex(buffer, pose, a, ca, ua, va);
		vertex(buffer, pose, b, cb, ub, vb);
		vertex(buffer, pose, c, cc, uc, vc);
		vertex(buffer, pose, d, cd, ud, vd);
		vertex(buffer, pose, d, cd, ud, vd);
		vertex(buffer, pose, c, cc, uc, vc);
		vertex(buffer, pose, b, cb, ub, vb);
		vertex(buffer, pose, a, ca, ua, va);
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 p, int color, float u, float v) {
		buffer.addVertex(pose, (float)p.x, (float)p.y, (float)p.z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(15728880).setNormal(pose, 0.0F, 1.0F, 0.0F);
	}

	static int mix(int a, int b, float t) {
		int r = Math.round(Mth.lerp(t, a >> 16 & 0xFF, b >> 16 & 0xFF));
		int g = Math.round(Mth.lerp(t, a >> 8 & 0xFF, b >> 8 & 0xFF));
		int bl = Math.round(Mth.lerp(t, a & 0xFF, b & 0xFF));
		return r << 16 | g << 8 | bl;
	}

	/**
	 * A mote: loose power in the air. A small glint, full bright, a little whiter than its colour. It keeps the speed
	 * it was thrown with for a moment, slows, lifts a touch, twinkles and shrinks away.
	 */
	static final class Mote extends SingleQuadParticle {
		/** Half a quad for size 1 (a redstone dust's own size), its life in ticks (between these) and its lift a tick. */
		static final float SIZE = 0.1F, LIFT = 0.0015F, WHITEN = 0.3F;
		static final int LIFE_MIN = 10, LIFE_MAX = 24;
		/** How far the twinkle swings its size, and how fast. */
		static final float TWINKLE = 0.22F, TWINKLE_SPEED = 0.9F;

		private final SpriteSet sprites;
		private final float fullSize, phase;

		Mote(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, int color, float size, SpriteSet sprites) {
			super(level, x, y, z, sprites.first());
			this.sprites = sprites;
			this.xd = xd;
			this.yd = yd;
			this.zd = zd;
			this.gravity = 0.0F;
			this.friction = 0.9F;
			this.hasPhysics = false;
			this.fullSize = SIZE * size * (0.75F + this.random.nextFloat() * 0.5F);
			this.quadSize = this.fullSize;
			this.lifetime = Math.round((LIFE_MIN + this.random.nextInt(LIFE_MAX - LIFE_MIN + 1)) * Mth.clamp(size, 1.0F, 2.0F));
			this.phase = this.random.nextFloat() * Mth.TWO_PI;
			int c = mix(color, 0xFFFFFF, WHITEN);
			this.setColor((c >> 16 & 0xFF) / 255.0F, (c >> 8 & 0xFF) / 255.0F, (c & 0xFF) / 255.0F);
			this.alpha = 1.0F;
			this.setSpriteFromAge(sprites);
		}

		@Override
		public void tick() {
			super.tick();
			this.yd += LIFT;
			this.setSpriteFromAge(this.sprites);
			float t = this.age / (float)this.lifetime;
			this.alpha = t < 0.7F ? 1.0F : Mth.clamp(1.0F - (t - 0.7F) / 0.3F, 0.0F, 1.0F);
		}

		@Override
		public float getQuadSize(float partial) {
			float age = this.age + partial;
			float t = Mth.clamp(age / this.lifetime, 0.0F, 1.0F);
			// in fast, then slowly down to a third
			float grow = Mth.clamp(age / 2.0F, 0.3F, 1.0F);
			return this.fullSize * grow * (1.0F - 0.65F * t) * (1.0F + TWINKLE * Mth.sin(this.phase + age * TWINKLE_SPEED));
		}

		@Override
		protected int getLightCoords(float partial) {
			return 0xF000F0;
		}

		@Override
		protected SingleQuadParticle.Layer getLayer() {
			return SingleQuadParticle.Layer.TRANSLUCENT;
		}
	}

	/**
	 * A puff of miasma: dark or heavy power. A soft cloud lit by the world, turning slowly, swelling as it thins.
	 */
	static final class Miasma extends SingleQuadParticle {
		/** Half a quad for size 1, how much it swells over its life, its life in ticks (between these), its alpha. */
		static final float SIZE = 0.2F, SWELL = 0.7F, ALPHA = 0.7F, LIFT = 0.0008F, TURN = 0.03F;
		static final int LIFE_MIN = 22, LIFE_MAX = 40;

		private final SpriteSet sprites;
		private final float fullSize, spin;

		Miasma(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, int color, float size, SpriteSet sprites) {
			super(level, x, y, z, sprites.first());
			this.sprites = sprites;
			this.xd = xd;
			this.yd = yd;
			this.zd = zd;
			this.gravity = 0.0F;
			this.friction = 0.88F;
			this.hasPhysics = false;
			this.fullSize = SIZE * size * (0.8F + this.random.nextFloat() * 0.4F);
			this.quadSize = this.fullSize;
			this.lifetime = LIFE_MIN + this.random.nextInt(LIFE_MAX - LIFE_MIN + 1);
			this.roll = this.random.nextFloat() * Mth.TWO_PI;
			this.oRoll = this.roll;
			this.spin = (this.random.nextFloat() - 0.5F) * 2.0F * TURN;
			float shade = 0.85F + this.random.nextFloat() * 0.15F;
			this.setColor((color >> 16 & 0xFF) / 255.0F * shade, (color >> 8 & 0xFF) / 255.0F * shade, (color & 0xFF) / 255.0F * shade);
			this.alpha = ALPHA;
			this.setSpriteFromAge(sprites);
		}

		@Override
		public void tick() {
			super.tick();
			this.yd += LIFT;
			this.oRoll = this.roll;
			this.roll += this.spin;
			this.setSpriteFromAge(this.sprites);
			float t = this.age / (float)this.lifetime;
			this.alpha = ALPHA * (t < 0.15F ? t / 0.15F : 1.0F - (t - 0.15F) / 0.85F);
		}

		@Override
		public float getQuadSize(float partial) {
			float t = Mth.clamp((this.age + partial) / this.lifetime, 0.0F, 1.0F);
			return this.fullSize * (1.0F + SWELL * t);
		}

		@Override
		protected SingleQuadParticle.Layer getLayer() {
			return SingleQuadParticle.Layer.TRANSLUCENT;
		}
	}

	/**
	 * A spark: power crackling. A jagged fleck, full bright and near white, that jumps to a new shape and angle every
	 * tick, darts a short way and is gone.
	 */
	static final class Spark extends SingleQuadParticle {
		/** Half a quad for size 1, its life in ticks (between these), how much of the speed it was sent with it keeps. */
		static final float SIZE = 0.12F, WHITEN = 0.5F;
		static final int LIFE_MIN = 4, LIFE_MAX = 9;
		static final double SPEED = 0.5;

		private final SpriteSet sprites;

		Spark(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, int color, float size, SpriteSet sprites) {
			super(level, x, y, z, sprites.get(level.getRandom()));
			this.sprites = sprites;
			this.xd = xd * SPEED;
			this.yd = yd * SPEED;
			this.zd = zd * SPEED;
			this.gravity = 0.0F;
			this.friction = 0.8F;
			this.hasPhysics = false;
			this.quadSize = SIZE * size * (0.7F + this.random.nextFloat() * 0.7F);
			this.lifetime = LIFE_MIN + this.random.nextInt(LIFE_MAX - LIFE_MIN + 1);
			this.roll = this.random.nextFloat() * Mth.TWO_PI;
			this.oRoll = this.roll;
			int c = mix(color, 0xFFFFFF, WHITEN);
			this.setColor((c >> 16 & 0xFF) / 255.0F, (c >> 8 & 0xFF) / 255.0F, (c & 0xFF) / 255.0F);
			this.alpha = 1.0F;
		}

		@Override
		public void tick() {
			super.tick();
			// a new fork at a new angle each tick: it never turns smoothly
			this.setSprite(this.sprites.get(this.random));
			this.roll = this.random.nextFloat() * Mth.TWO_PI;
			this.oRoll = this.roll;
			this.alpha = this.age >= this.lifetime - 2 ? 0.55F : 1.0F;
		}

		@Override
		protected int getLightCoords(float partial) {
			return 0xF000F0;
		}

		@Override
		protected SingleQuadParticle.Layer getLayer() {
			return SingleQuadParticle.Layer.TRANSLUCENT;
		}
	}

	/**
	 * A rune: a working being written. One of our marks, full bright. Sent with a direction it starts that far from the
	 * point and is drawn in to it along a falling curve (the enchanting table's letters' own path); sent with none it
	 * hangs, drifts up a little and fades.
	 */
	static final class Rune extends SingleQuadParticle {
		/** Half a quad for size 1, its life in ticks (between these), and how far a still one drifts up a tick. */
		static final float SIZE = 0.09F, WHITEN = 0.2F;
		static final int LIFE_MIN = 22, LIFE_MAX = 34;
		static final double DRIFT = 0.006;

		private final double toX, toY, toZ, fromX, fromY, fromZ;
		private final boolean drawn;
		private final float fullSize;

		Rune(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, int color, float size, SpriteSet sprites) {
			super(level, x, y, z, sprites.get(level.getRandom()));
			this.toX = x;
			this.toY = y;
			this.toZ = z;
			this.fromX = xd;
			this.fromY = yd;
			this.fromZ = zd;
			this.drawn = xd * xd + yd * yd + zd * zd > 1.0E-4;
			this.xd = 0.0;
			this.yd = 0.0;
			this.zd = 0.0;
			this.gravity = 0.0F;
			this.hasPhysics = false;
			this.fullSize = SIZE * size * (0.8F + this.random.nextFloat() * 0.4F);
			this.quadSize = this.fullSize;
			this.lifetime = LIFE_MIN + this.random.nextInt(LIFE_MAX - LIFE_MIN + 1);
			int c = mix(color, 0xFFFFFF, WHITEN);
			this.setColor((c >> 16 & 0xFF) / 255.0F, (c >> 8 & 0xFF) / 255.0F, (c & 0xFF) / 255.0F);
			this.alpha = 1.0F;
			if (this.drawn) {
				this.setPos(x + xd, y + yd, z + zd);
				this.xo = this.x;
				this.yo = this.y;
				this.zo = this.z;
			}
		}

		@Override
		public void tick() {
			this.xo = this.x;
			this.yo = this.y;
			this.zo = this.z;
			if (this.age++ >= this.lifetime) {
				this.remove();
				return;
			}
			float t = this.age / (float)this.lifetime;
			if (this.drawn) {
				// out at the start, in at the end, dipping on the way
				float left = 1.0F - t;
				float fall = left * left;
				this.setPos(this.toX + this.fromX * left, this.toY + this.fromY * left - (1.0F - fall) * left * 0.6F, this.toZ + this.fromZ * left);
			} else {
				this.setPos(this.x, this.y + DRIFT, this.z);
			}
			this.alpha = t < 0.15F ? t / 0.15F : t > 0.75F ? Mth.clamp((1.0F - t) / 0.25F, 0.0F, 1.0F) : 1.0F;
		}

		@Override
		public float getQuadSize(float partial) {
			return this.fullSize;
		}

		@Override
		protected int getLightCoords(float partial) {
			return 0xF000F0;
		}

		@Override
		protected SingleQuadParticle.Layer getLayer() {
			return SingleQuadParticle.Layer.TRANSLUCENT;
		}
	}
}
