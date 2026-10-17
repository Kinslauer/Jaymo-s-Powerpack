package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.power.zfighter.ZFighterNet;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Ki drawn as orbs: plain glowing balls of the ki's colour round a white-hot heart, and Darktide's Drown as a ball of
 * water ({@link #waterOrb}); no end crystal cages. Every blast in flight and the ki gathering in the hands; the
 * server sends where each one is every tick ({@link ZFighterNet.KiOrbs}). They stand in for most of the old particles.
 */
public final class ZFighterOrbs {
	/** Ticks without word before an orb is dropped. */
	static final int STALE = 2;
	/** Water orbs: how much slower than ki their streaks flow, how far they slosh out of round, how see-through (0-255). */
	static final float WATER_SPIN = 0.3F, WATER_WOBBLE = 0.06F;
	static final int WATER_RIM_ALPHA = 140, WATER_HEART_ALPHA = 110;
	/** Ki orbs: the heart's share of the radius, how much the ball breathes, how fast its energy streams. */
	static final float ORB_HEART = 0.55F, ORB_PULSE = 0.04F, ORB_FLOW = 0.05F;
	/**
	 * Ki Discs: how fast they spin (radians a tick) forming in the hand and once thrown (moving faster than
	 * {@link #DISC_FLYING} blocks a tick), how thick they are (a share of the radius), the white heart's share of the
	 * radius, and the saw teeth round the edge (how many, how far they stick out) that show the spin.
	 */
	static final float DISC_HELD_SPIN = 0.55F, DISC_FLIGHT_SPIN = 1.6F;
	static final double DISC_FLYING = 0.3;
	static final float DISC_THICKNESS = 0.07F, DISC_HEART = 0.45F;
	static final int DISC_SEGMENTS = 24, DISC_TEETH = 8;
	static final float DISC_TOOTH = 0.16F;

	private static final class Live {
		Vec3 prev;
		Vec3 at;
		float prevSize;
		float size;
		int color;
		boolean water;
		boolean disc;
		float spin;
		float prevSpin;
		int quiet;
		int age;
	}

	private static final Map<Integer, Live> ORBS = new HashMap<>();

	private ZFighterOrbs() {
	}

	static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ZFighterNet.KiOrbs.TYPE, (fx, context) -> {
			for (ZFighterNet.Orb orb : fx.orbs()) {
				Vec3 at = new Vec3(orb.x(), orb.y(), orb.z());
				Live live = ORBS.get(orb.id());
				if (live == null) {
					live = new Live();
					live.at = at;
					live.size = orb.size();
					ORBS.put(orb.id(), live);
				}
				live.prev = live.at;
				live.prevSize = live.size;
				live.at = at;
				live.size = orb.size();
				live.color = orb.color() & 0xFFFFFF;
				live.water = (orb.color() & ZFighterNet.Orb.WATER) != 0;
				live.disc = (orb.color() & ZFighterNet.Orb.DISC) != 0;
				live.quiet = 0;
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (minecraft.level == null) {
				ORBS.clear();
				return;
			}
			if (minecraft.isPaused()) {
				return;
			}
			ORBS.values().removeIf(live -> ++live.quiet > STALE);
			for (Live live : ORBS.values()) {
				live.age++;
				live.prevSpin = live.spin;
				if (live.disc) {
					// a lazy turn while it forms over the palm, a blur once it's thrown
					live.spin += live.at.distanceToSqr(live.prev) > DISC_FLYING * DISC_FLYING ? DISC_FLIGHT_SPIN : DISC_HELD_SPIN;
				}
			}
		});
		LevelRenderEvents.COLLECT_SUBMITS.register(ZFighterOrbs::render);
	}

	private static void render(LevelRenderContext context) {
		if (ORBS.isEmpty()) {
			return;
		}
		float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = context.levelState().cameraRenderState.pos;
		for (Map.Entry<Integer, Live> entry : ORBS.entrySet()) {
			Live live = entry.getValue();
			Vec3 at = live.prev.lerp(live.at, partial).subtract(camera);
			// each orb starts its spin somewhere of its own, so a barrage doesn't turn in step
			float time = live.age + partial + (entry.getKey() & 63) * 7.0F;
			float size = Mth.lerp(partial, live.prevSize, live.size);
			if (live.disc) {
				disc(context.submitNodeCollector(), context.poseStack(), at, size, live.color, Mth.lerp(partial, live.prevSpin, live.spin), time);
			} else if (live.water) {
				waterOrb(context.submitNodeCollector(), context.poseStack(), at, size, live.color, time);
			} else {
				orb(context.submitNodeCollector(), context.poseStack(), at, size, live.color, time, 1.0F);
			}
		}
	}

	/**
	 * One ki orb {@code size} blocks across at {@code at} (camera-relative): a plain ball of the ki's colour, see-through
	 * at the rim, round a white-hot heart, energy streaming over it. {@code color} (RGB) its colour, {@code fade} (0-1)
	 * how solid. Also used for the ends of the ki beams.
	 */
	static void orb(SubmitNodeCollector collector, PoseStack poseStack, Vec3 at, float size, int color, float time, float fade) {
		if (size <= 0.01F || fade <= 0.01F) {
			return;
		}
		float f = Mth.clamp(fade, 0.0F, 1.0F);
		float r = size * 0.5F * (1.0F + ORB_PULSE * Mth.sin(time * 0.4F));
		int heart = 0xFF000000 | whiten(color, 0.75F);
		int glow = (int)(0xB0 * f) << 24 | (color & 0xFFFFFF);
		int rim = (int)(0x70 * f) << 24 | lighten(color);
		float spin = time * ORB_FLOW;
		// the heart shrinks away as the orb fades, the rest thins out
		float heartR = r * ORB_HEART * f;
		if (heartR > 0.005F) {
			collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(BeaconRenderer.BEAM_LOCATION, false),
				(pose, buffer) -> ZFighterSpirit.sphere(buffer, pose, at, heartR, heart, spin * 1.7F, spin * 1.5F));
		}
		collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(BeaconRenderer.BEAM_LOCATION, true),
			(pose, buffer) -> ZFighterSpirit.sphere(buffer, pose, at, r * 0.85F, glow, -spin, spin));
		collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(BeaconRenderer.BEAM_LOCATION, true),
			(pose, buffer) -> ZFighterSpirit.sphere(buffer, pose, at, r, rim, spin, -spin * 0.7F));
	}

	/**
	 * A ball of water (Darktide's Drown): a plain see-through orb of the water's colour round a deeper heart, its
	 * streaks turning slowly and the whole thing wobbling like a held blob of water, so who's drowning inside still shows.
	 */
	static void waterOrb(SubmitNodeCollector collector, PoseStack poseStack, Vec3 at, float size, int color, float time) {
		if (size <= 0.01F) {
			return;
		}
		float r = size * 0.5F;
		int rim = WATER_RIM_ALPHA << 24 | lighten(color);
		int heart = WATER_HEART_ALPHA << 24 | (color & 0xFFFFFF);
		float flow = time * ORB_FLOW * WATER_SPIN;
		poseStack.pushPose();
		poseStack.translate(at.x, at.y, at.z);
		// a slow slosh: squashed one way, then the other
		float slosh = Mth.sin(time * 0.18F) * WATER_WOBBLE;
		float slosh2 = Mth.cos(time * 0.13F) * WATER_WOBBLE;
		poseStack.scale(1.0F + slosh, 1.0F - slosh - slosh2, 1.0F + slosh2);
		// the heart turns the other way, as currents do
		collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(BeaconRenderer.BEAM_LOCATION, true),
			(pose, buffer) -> ZFighterSpirit.sphere(buffer, pose, Vec3.ZERO, r * 0.7F, heart, -flow * 1.6F, flow));
		collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(BeaconRenderer.BEAM_LOCATION, true),
			(pose, buffer) -> ZFighterSpirit.sphere(buffer, pose, Vec3.ZERO, r, rim, flow, -flow * 0.7F));
		poseStack.popPose();
	}

	/**
	 * A Ki Disc {@code size} blocks across at {@code at} (camera-relative), lying level and turned {@code spin} radians:
	 * a thin white-hot heart, a see-through blade of the ki's colour fading to its edge, a bright rim, and saw teeth round
	 * it so the spin shows.
	 */
	static void disc(SubmitNodeCollector collector, PoseStack poseStack, Vec3 at, float size, int color, float spin, float time) {
		if (size <= 0.01F) {
			return;
		}
		float r = size * 0.5F;
		float half = Math.max(0.012F, r * DISC_THICKNESS);
		int heart = 0xFF000000 | whiten(color, 0.8F);
		int blade = 0xC0 << 24 | whiten(color, 0.25F);
		int edge = 0x50 << 24 | lighten(color);
		int rim = 0xE0 << 24 | whiten(color, 0.55F);
		float flow = time * ORB_FLOW;
		float x = (float)at.x, y = (float)at.y, z = (float)at.z;
		collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(BeaconRenderer.BEAM_LOCATION, false), (pose, buffer) -> {
			ring(buffer, pose, x, y + half * 0.5F, z, 0.0F, r * DISC_HEART, heart, heart, spin, flow);
			ring(buffer, pose, x, y - half * 0.5F, z, 0.0F, r * DISC_HEART, heart, heart, spin, flow);
		});
		collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(BeaconRenderer.BEAM_LOCATION, true), (pose, buffer) -> {
			ring(buffer, pose, x, y + half, z, r * DISC_HEART * 0.6F, r, blade, edge, spin, -flow);
			ring(buffer, pose, x, y - half, z, r * DISC_HEART * 0.6F, r, blade, edge, spin, -flow);
			band(buffer, pose, x, y, z, r, half, rim, spin, flow);
			teeth(buffer, pose, x, y, z, r, rim & 0xFFFFFF | 0x90 << 24, spin);
		});
	}

	/** A flat ring (or a full disc, {@code inner} 0) at height {@code y}, seen from above and below. */
	private static void ring(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, float inner, float outer, int in, int out,
		float spin, float flow) {
		for (int i = 0; i < DISC_SEGMENTS; i++) {
			float a0 = spin + Mth.TWO_PI * i / DISC_SEGMENTS;
			float a1 = spin + Mth.TWO_PI * (i + 1) / DISC_SEGMENTS;
			float u0 = i / (float)DISC_SEGMENTS * 2.0F, u1 = (i + 1) / (float)DISC_SEGMENTS * 2.0F;
			float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
			quad(buffer, pose,
				x + c0 * inner, y, z + s0 * inner, in, u0, flow,
				x + c0 * outer, y, z + s0 * outer, out, u0, flow + 1.0F,
				x + c1 * outer, y, z + s1 * outer, out, u1, flow + 1.0F,
				x + c1 * inner, y, z + s1 * inner, in, u1, flow);
		}
	}

	/** The disc's edge: a thin upright band round it. */
	private static void band(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, float r, float half, int color, float spin,
		float flow) {
		for (int i = 0; i < DISC_SEGMENTS; i++) {
			float a0 = spin + Mth.TWO_PI * i / DISC_SEGMENTS;
			float a1 = spin + Mth.TWO_PI * (i + 1) / DISC_SEGMENTS;
			float u0 = i / (float)DISC_SEGMENTS * 4.0F, u1 = (i + 1) / (float)DISC_SEGMENTS * 4.0F;
			float c0 = Mth.cos(a0) * r, s0 = Mth.sin(a0) * r, c1 = Mth.cos(a1) * r, s1 = Mth.sin(a1) * r;
			quad(buffer, pose,
				x + c0, y - half, z + s0, color, u0, flow,
				x + c0, y + half, z + s0, color, u0, flow + 0.2F,
				x + c1, y + half, z + s1, color, u1, flow + 0.2F,
				x + c1, y - half, z + s1, color, u1, flow);
		}
	}

	/** Saw teeth swept back from the edge, flat in the disc's plane: they turn with it, so the spin reads at a glance. */
	private static void teeth(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, float r, int color, float spin) {
		float out = r * (1.0F + DISC_TOOTH);
		for (int i = 0; i < DISC_TEETH; i++) {
			float a0 = spin + Mth.TWO_PI * i / DISC_TEETH;
			float a1 = a0 + Mth.TWO_PI / DISC_TEETH * 0.6F;
			float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
			// a triangle: two corners on the edge, the point out past the leading one
			quad(buffer, pose,
				x + c0 * r, y, z + s0 * r, color, 0.0F, 0.0F,
				x + c1 * out, y, z + s1 * out, color & 0xFFFFFF, 0.5F, 1.0F,
				x + c1 * r, y, z + s1 * r, color, 1.0F, 0.0F,
				x + c1 * r, y, z + s1 * r, color, 1.0F, 0.0F);
		}
	}

	/** One quad, drawn both ways round so it shows from either side. */
	private static void quad(VertexConsumer buffer, PoseStack.Pose pose,
		float x0, float y0, float z0, int k0, float u0, float v0,
		float x1, float y1, float z1, int k1, float u1, float v1,
		float x2, float y2, float z2, int k2, float u2, float v2,
		float x3, float y3, float z3, int k3, float u3, float v3) {
		vertex(buffer, pose, x0, y0, z0, k0, u0, v0);
		vertex(buffer, pose, x1, y1, z1, k1, u1, v1);
		vertex(buffer, pose, x2, y2, z2, k2, u2, v2);
		vertex(buffer, pose, x3, y3, z3, k3, u3, v3);
		vertex(buffer, pose, x3, y3, z3, k3, u3, v3);
		vertex(buffer, pose, x2, y2, z2, k2, u2, v2);
		vertex(buffer, pose, x1, y1, z1, k1, u1, v1);
		vertex(buffer, pose, x0, y0, z0, k0, u0, v0);
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, int color, float u, float v) {
		buffer.addVertex(pose, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880)
			.setNormal(pose, 0.0F, 1.0F, 0.0F);
	}

	/** {@code rgb} halfway to white, for the glass. */
	private static int lighten(int rgb) {
		int r = (rgb >> 16 & 0xFF) + 255 >> 1;
		int g = (rgb >> 8 & 0xFF) + 255 >> 1;
		int b = (rgb & 0xFF) + 255 >> 1;
		return r << 16 | g << 8 | b;
	}

	/** {@code rgb} {@code amount} (0-1) of the way to white. */
	private static int whiten(int rgb, float amount) {
		int r = (int)Mth.lerp(amount, rgb >> 16 & 0xFF, 255);
		int g = (int)Mth.lerp(amount, rgb >> 8 & 0xFF, 255);
		int b = (int)Mth.lerp(amount, rgb & 0xFF, 255);
		return r << 16 | g << 8 | b;
	}
}
