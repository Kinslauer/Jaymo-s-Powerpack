package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.power.zfighter.ZFighterNet;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Ki beams drawn like a beacon's: the beacon beam texture scrolling down a bright core in the beam's colour, wrapped in
 * a wider see-through glow. They swell in, then thin away over their life. The Frost Needle is a hair-thin core; the
 * Spiral Lance's coil is the server's particles round its core. An end crystal orb burns at each end.
 */
public final class ZFighterBeams {
	/** The core's share of the beam's width, the glow's alpha, and how fast the texture runs along it. */
	static final float CORE = 0.45F;
	static final int GLOW_ALPHA = 0x70;
	static final float SCROLL = 0.6F;
	static final int SIDES = 8;

	private record Live(Vec3 from, Vec3 to, float radius, int color, int style, int ticks, int[] age) {
	}

	private static final List<Live> BEAMS = new ArrayList<>();

	private ZFighterBeams() {
	}

	static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ZFighterNet.BeamFx.TYPE, (fx, context) -> BEAMS.add(new Live(new Vec3(fx.fx(), fx.fy(), fx.fz()),
			new Vec3(fx.tx(), fx.ty(), fx.tz()), fx.radius(), fx.color(), fx.style(), fx.ticks(), new int[1])));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (minecraft.level == null) {
				BEAMS.clear();
				return;
			}
			if (minecraft.isPaused()) {
				return;
			}
			for (Iterator<Live> it = BEAMS.iterator(); it.hasNext(); ) {
				Live beam = it.next();
				if (++beam.age()[0] > beam.ticks()) {
					it.remove();
				}
			}
		});
		LevelRenderEvents.COLLECT_SUBMITS.register(ZFighterBeams::render);
	}

	private static void render(LevelRenderContext context) {
		if (BEAMS.isEmpty()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = context.levelState().cameraRenderState.pos;
		float time = minecraft.level == null ? 0.0F : (minecraft.level.getGameTime() % 4000L) + partial;
		for (Live beam : BEAMS) {
			float life = Mth.clamp((beam.age()[0] + partial) / beam.ticks(), 0.0F, 1.0F);
			// swells in over the first ticks, then thins away
			float width = life < 0.15F ? life / 0.15F : 1.0F - (life - 0.15F) / 0.85F * 0.85F;
			float radius = beam.radius() * width;
			if (radius <= 0.01F) {
				continue;
			}
			boolean needle = beam.style() == 2;
			Vec3 a = beam.from().subtract(camera);
			Vec3 b = beam.to().subtract(camera);
			int core = 0xFF000000 | mix(beam.color(), 0xFFFFFF, 0.55F);
			int glow = GLOW_ALPHA << 24 | beam.color();
			float v0 = -time * SCROLL;
			float v1 = v0 + (float)a.distanceTo(b) * 0.5F;
			float coreRadius = needle ? radius : radius * CORE;
			context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.beaconBeam(BeaconRenderer.BEAM_LOCATION, false),
				(pose, buffer) -> tube(buffer, pose, a, b, coreRadius, core, v0, v1));
			if (!needle) {
				context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.beaconBeam(BeaconRenderer.BEAM_LOCATION, true),
					(pose, buffer) -> tube(buffer, pose, a, b, radius, glow, v0 * 0.7F, v1 * 0.7F));
			}
			// an end crystal orb where it leaves the hands and where it ends (see ZFighterOrbs)
			float fade = 1.0F - life;
			float orb = needle ? radius * 6.0F + 0.2F : radius * 2.6F;
			ZFighterOrbs.orb(context.submitNodeCollector(), context.poseStack(), a, orb, beam.color(), time, fade);
			ZFighterOrbs.orb(context.submitNodeCollector(), context.poseStack(), b, orb * 1.2F, beam.color(), time + 20.0F, fade);
		}
	}

	/** A round tube of {@link #SIDES} faces from {@code a} to {@code b}, the texture running along it. */
	private static void tube(VertexConsumer buffer, PoseStack.Pose pose, Vec3 a, Vec3 b, float radius, int color, float v0, float v1) {
		Vec3 dir = b.subtract(a);
		if (dir.lengthSqr() < 1.0E-6) {
			return;
		}
		dir = dir.normalize();
		Vec3 u = (Math.abs(dir.y) < 0.95 ? dir.cross(new Vec3(0.0, 1.0, 0.0)) : dir.cross(new Vec3(1.0, 0.0, 0.0))).normalize();
		Vec3 w = dir.cross(u).normalize();
		for (int k = 0; k < SIDES; k++) {
			double t0 = k * Mth.TWO_PI / SIDES;
			double t1 = (k + 1) * Mth.TWO_PI / SIDES;
			Vec3 n0 = u.scale(Math.cos(t0)).add(w.scale(Math.sin(t0)));
			Vec3 n1 = u.scale(Math.cos(t1)).add(w.scale(Math.sin(t1)));
			float u0 = k / (float)SIDES;
			float u1 = (k + 1) / (float)SIDES;
			vertex(buffer, pose, a.add(n0.scale(radius)), color, u0, v0);
			vertex(buffer, pose, b.add(n0.scale(radius)), color, u0, v1);
			vertex(buffer, pose, b.add(n1.scale(radius)), color, u1, v1);
			vertex(buffer, pose, a.add(n1.scale(radius)), color, u1, v0);
			// and the other way round, so it shows from inside or out whichever way the faces are culled
			vertex(buffer, pose, a.add(n1.scale(radius)), color, u1, v0);
			vertex(buffer, pose, b.add(n1.scale(radius)), color, u1, v1);
			vertex(buffer, pose, b.add(n0.scale(radius)), color, u0, v1);
			vertex(buffer, pose, a.add(n0.scale(radius)), color, u0, v0);
		}
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 p, int color, float u, float v) {
		buffer.addVertex(pose, (float)p.x, (float)p.y, (float)p.z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(15728880).setNormal(pose, 0.0F, 1.0F, 0.0F);
	}

	private static int mix(int a, int b, float t) {
		int r = Math.round(Mth.lerp(t, a >> 16 & 0xFF, b >> 16 & 0xFF));
		int g = Math.round(Mth.lerp(t, a >> 8 & 0xFF, b >> 8 & 0xFF));
		int bl = Math.round(Mth.lerp(t, a & 0xFF, b & 0xFF));
		return r << 16 | g << 8 | bl;
	}
}
