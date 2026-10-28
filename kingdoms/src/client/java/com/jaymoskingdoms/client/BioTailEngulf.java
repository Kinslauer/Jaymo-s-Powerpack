package com.jaymoskingdoms.client;

import com.jaymoskingdoms.BioBuilt;
import com.jaymoskingdoms.HeritageHold;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.313) Andy: "The tail should now be what absorbs in first form. it extends overhead, and down upon the target.
 * stretching over and engulfing them into a bulge that flows down the tail and into the BB". A Bio-built taking a foe in
 * ({@link HeritageHold#ABSORBING}), as everyone sees it, over {@link BioBuilt#ABSORB_TICKS}:
 * <ol>
 * <li><b>Strike</b> ({@link #STRIKE}): the tail leaves the back, rises in an arch over their head and comes down on the
 * foe from above, its tip on the crown of it.</li>
 * <li><b>Sheath</b> ({@link #SHEATH_FROM} .. {@link #SHEATH_TO}): the tip splits open and stretches down over the foe
 * like a sock, top to toe, until it's swallowed whole (the sheath at the bottom of the tail's texture).</li>
 * <li><b>Gulp</b> ({@link #GULP_FROM} on): the sheath tightens as the foe shrinks inside it (the server's shrink,
 * {@link HeritageHold}), and the swallowed mass runs up the arch and down into the back as bulges.</li>
 * </ol>
 * Every form does it the same way: the first two with the tail they carry ({@link BioForms} hides the idle one while it's
 * out), the third unfurling it from the stinger it keeps folded on the small of the back.
 */
public final class BioTailEngulf {
	/** The shares of the swallow: the tail out, the sheath spreading down (from .. to), the gulps begin. */
	static final float STRIKE = 0.2F, SHEATH_FROM = 0.16F, SHEATH_TO = 0.55F, GULP_FROM = 0.5F;
	/**
	 * Where the tail leaves the body (blocks, at size 1: up from the feet, back from the middle), how high the arch climbs
	 * over the head and over the foe, the tail's thickness at the root and the tip, a bulge's size (a share of the foe's
	 * width, at most {@link #BULGE_MAX}) and length (a share of the tail), and how many run.
	 */
	static final float ROOT_UP = 0.86F, ROOT_BACK = 0.16F, ARCH_BEHIND = 2.2F, ARCH_OVER = 1.7F, THICK_ROOT = 0.13F, THICK_TIP = 0.08F,
		BULGE = 0.45F, BULGE_MAX = 0.32F, BULGE_LENGTH = 0.07F, GULPS = 3.0F;
	/** How far the sheath stands off the foe (a share of its size), how much it heaves; how far down the tail's texture the sheath starts. */
	static final float SHEATH_OFF = 1.1F, HEAVE = 0.05F, SHEATH_V = 0.75F;
	/** The mesh: sides round the tail, pieces along it; rings and slices of the sheath. */
	static final int SIDES = 8, PIECES = 28, RINGS = 10, SLICES = 14;

	/** Entity id → the clock (ticks) their swallow began. */
	private static final Map<Integer, Float> STARTED = new HashMap<>();

	private BioTailEngulf() {
	}

	public static void register() {
		LevelRenderEvents.COLLECT_SUBMITS.register(BioTailEngulf::render);
	}

	private static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = context.levelState().cameraRenderState.pos;
		for (Player player : level.players()) {
			float clock = player.tickCount + partialTick;
			Integer id = player.getAttached(HeritageHold.ABSORBING);
			Entity foe = id == null || !BioForms.bio(player) ? null : level.getEntity(id);
			if (!(foe instanceof LivingEntity) || player.isInvisible()) {
				STARTED.remove(player.getId());
				continue;
			}
			if (player.position().distanceToSqr(camera) > 64.0 * 64.0) {
				continue;
			}
			float started = STARTED.computeIfAbsent(player.getId(), k -> clock);
			float t = Mth.clamp((clock - started) / BioBuilt.ABSORB_TICKS, 0.0F, 1.0F);
			draw(context, level, camera, player, foe, t, clock, partialTick);
		}
	}

	/** The tail and the sheath for one swallow, {@code t} of the way through. */
	private static void draw(LevelRenderContext context, ClientLevel level, Vec3 camera, Player player, Entity foe, float t, float clock, float partialTick) {
		float size = player.getScale();
		Vec3 feet = player.getPosition(partialTick);
		float yaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
		Vec3 back = new Vec3(Mth.sin(yaw), 0.0, -Mth.cos(yaw));
		Vec3 root = feet.add(0.0, ROOT_UP * size, 0.0).add(back.scale(ROOT_BACK * size));
		Vec3 middle = foe.getPosition(partialTick).add(0.0, foe.getBbHeight() * 0.5, 0.0);
		double rx = Math.max(0.15, foe.getBbWidth() * 0.5 * SHEATH_OFF), ry = Math.max(0.15, foe.getBbHeight() * 0.5 * SHEATH_OFF);
		Vec3 crown = middle.add(0.0, ry, 0.0);
		// the arch: up and back off the body, over the head, over the foe, and straight down onto its crown
		Vec3 rise = root.add(back.scale(0.4 * size)).add(0.0, ARCH_BEHIND * size, 0.0);
		Vec3 over = crown.add(0.0, ARCH_OVER * size, 0.0);
		Vec3[] curve = {root, rise, over, crown};
		float reach = ease(Mth.clamp(t / STRIKE, 0.0F, 1.0F));
		float sheath = ease(Mth.clamp((t - SHEATH_FROM) / (SHEATH_TO - SHEATH_FROM), 0.0F, 1.0F));
		float gulp = t < GULP_FROM ? -1.0F : (t - GULP_FROM) / (1.0F - GULP_FROM);
		double bulge = Math.min(BULGE_MAX, foe.getBbWidth() * BULGE) * size;
		int light = net.minecraft.util.LightCoordsUtil.getLightCoords(level, BlockPos.containing(middle));
		Identifier texture = BioForms.texture(BioForms.palette(player), "tail");
		PoseStack poseStack = context.poseStack();
		Vec3 side = crown.subtract(root).multiply(1.0, 0.0, 1.0).cross(new Vec3(0.0, 1.0, 0.0));
		Vec3 across = side.lengthSqr() < 1.0E-6 ? back.cross(new Vec3(0.0, 1.0, 0.0)) : side.normalize();
		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.entityCutout(texture), (pose, buffer) -> {
			tail(buffer, pose, curve, camera, across, reach, sheath, rx, gulp, bulge, size, clock, light);
			if (sheath > 0.0F) {
				sheath(buffer, pose, middle.subtract(camera), rx, ry, sheath, gulp, clock, light);
			}
		});
	}

	/**
	 * The tail along the arch as far as it has reached ({@code reach}): thinner toward the tip, flaring into the sheath at
	 * the end once that spreads, and (from the gulps on, {@code gulp} 0 .. 1) bulges running back along it to the body.
	 */
	private static void tail(VertexConsumer buffer, PoseStack.Pose pose, Vec3[] curve, Vec3 camera, Vec3 across, float reach, float sheath, double rx,
		float gulp, double bulge, float size, float clock, int light) {
		if (reach <= 0.01F) {
			return;
		}
		Vec3[] centre = new Vec3[PIECES + 1];
		double[] radius = new double[PIECES + 1];
		Vec3[] u = new Vec3[PIECES + 1], v = new Vec3[PIECES + 1];
		for (int i = 0; i <= PIECES; i++) {
			double s = reach * i / (double)PIECES;
			Vec3 tangent = derivative(curve, s);
			tangent = tangent.lengthSqr() < 1.0E-8 ? new Vec3(0.0, 1.0, 0.0) : tangent.normalize();
			Vec3 a = across.cross(tangent);
			a = a.lengthSqr() < 1.0E-8 ? new Vec3(1.0, 0.0, 0.0) : a.normalize();
			u[i] = a;
			v[i] = tangent.cross(a).normalize();
			// a slow writhe along it
			double writhe = Math.sin(clock * 0.3 + s * 8.0) * 0.04 * size * Math.sin(s * Math.PI);
			centre[i] = bezier(curve, s).subtract(camera).add(across.scale(writhe));
			double r = Mth.lerp(s, THICK_ROOT, THICK_TIP) * size;
			if (sheath > 0.0F && s > 0.85) {
				// the tip splits and spreads into the sheath
				double f = (s - 0.85) / 0.15;
				r += f * f * rx * 0.7 * sheath;
			}
			if (gulp >= 0.0F) {
				for (int g = 0; g < GULPS; g++) {
					// each bulge leaves the foe and runs to the back, one after another
					double at = 1.0 - Mth.clamp(gulp * GULPS - g * 0.8F, 0.0F, 1.0F);
					if (at > 0.0 && at < 1.0) {
						double d = (s - at) / BULGE_LENGTH;
						r += bulge * Math.exp(-d * d);
					}
				}
			}
			radius[i] = r;
		}
		for (int i = 0; i < PIECES; i++) {
			float v0 = SHEATH_V * reach * i / PIECES, v1 = SHEATH_V * reach * (i + 1) / PIECES;
			for (int k = 0; k < SIDES; k++) {
				double a0 = k * Math.PI * 2.0 / SIDES, a1 = (k + 1) * Math.PI * 2.0 / SIDES;
				Vec3 n00 = ring(u[i], v[i], a0), n01 = ring(u[i], v[i], a1), n10 = ring(u[i + 1], v[i + 1], a0), n11 = ring(u[i + 1], v[i + 1], a1);
				vertex(buffer, pose, centre[i].add(n00.scale(radius[i])), n00, k / (float)SIDES, v0, light);
				vertex(buffer, pose, centre[i + 1].add(n10.scale(radius[i + 1])), n10, k / (float)SIDES, v1, light);
				vertex(buffer, pose, centre[i + 1].add(n11.scale(radius[i + 1])), n11, (k + 1) / (float)SIDES, v1, light);
				vertex(buffer, pose, centre[i].add(n01.scale(radius[i])), n01, (k + 1) / (float)SIDES, v0, light);
			}
		}
	}

	private static Vec3 ring(Vec3 u, Vec3 v, double a) {
		return u.scale(Math.cos(a)).add(v.scale(Math.sin(a)));
	}

	/**
	 * The sheath over the foe: an egg round its box (half-sizes {@code rx}, {@code ry}) from its crown down,
	 * {@code spread} of the way to its feet, the rim ragged where it's still stretching; once sealed it heaves, and
	 * (gulping) it pulls in tight.
	 */
	private static void sheath(VertexConsumer buffer, PoseStack.Pose pose, Vec3 centre, double rx, double ry, float spread, float gulp, float clock,
		int light) {
		double cap = spread * Math.PI;
		double tight = gulp < 0.0F ? 1.0 : 1.0 - 0.12 * gulp;
		Vec3[][] point = new Vec3[RINGS + 1][SLICES + 1];
		Vec3[][] normal = new Vec3[RINGS + 1][SLICES + 1];
		for (int i = 0; i <= RINGS; i++) {
			for (int j = 0; j <= SLICES; j++) {
				double phi = j * Math.PI * 2.0 / SLICES;
				double reachHere = cap * (i == RINGS && spread < 1.0F ? 0.85 + 0.15 * Math.sin(phi * 4.0 + clock * 0.5) : 1.0);
				double theta = reachHere * i / RINGS;
				Vec3 dir = new Vec3(Math.cos(phi) * Math.sin(theta), Math.cos(theta), Math.sin(phi) * Math.sin(theta));
				double heave = tight * (1.0 + HEAVE * Math.sin(clock * 0.45 + theta * 3.0 + phi * 2.0));
				point[i][j] = centre.add(dir.x * rx * heave, dir.y * ry * heave, dir.z * rx * heave);
				Vec3 n = new Vec3(dir.x / rx, dir.y / ry, dir.z / rx);
				normal[i][j] = n.lengthSqr() < 1.0E-10 ? dir : n.normalize();
			}
		}
		for (int i = 0; i < RINGS; i++) {
			for (int j = 0; j < SLICES; j++) {
				float u0 = j / (float)SLICES, u1 = (j + 1) / (float)SLICES;
				float v0 = SHEATH_V + (1.0F - SHEATH_V) * i / RINGS, v1 = SHEATH_V + (1.0F - SHEATH_V) * (i + 1) / RINGS;
				vertex(buffer, pose, point[i][j], normal[i][j], u0, v0, light);
				vertex(buffer, pose, point[i][j + 1], normal[i][j + 1], u1, v0, light);
				vertex(buffer, pose, point[i + 1][j + 1], normal[i + 1][j + 1], u1, v1, light);
				vertex(buffer, pose, point[i + 1][j], normal[i + 1][j], u0, v1, light);
			}
		}
	}

	/** The cubic Bezier through {@code c} at {@code s}, and its slope. */
	private static Vec3 bezier(Vec3[] c, double s) {
		double m = 1.0 - s;
		return c[0].scale(m * m * m).add(c[1].scale(3.0 * m * m * s)).add(c[2].scale(3.0 * m * s * s)).add(c[3].scale(s * s * s));
	}

	private static Vec3 derivative(Vec3[] c, double s) {
		double m = 1.0 - s;
		return c[1].subtract(c[0]).scale(3.0 * m * m).add(c[2].subtract(c[1]).scale(6.0 * m * s)).add(c[3].subtract(c[2]).scale(3.0 * s * s));
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 p, Vec3 n, float u, float v, int light) {
		buffer.addVertex(pose, (float)p.x, (float)p.y, (float)p.z).setColor(0xFFFFFFFF).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(light).setNormal(pose, (float)n.x, (float)n.y, (float)n.z);
	}

	private static float ease(float x) {
		return x * x * (3.0F - 2.0F * x);
	}
}
