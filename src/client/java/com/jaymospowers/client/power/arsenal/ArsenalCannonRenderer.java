package com.jaymospowers.client.power.arsenal;

import com.jaymospowers.power.arsenal.ArsenalCannons;
import com.jaymospowers.power.arsenal.ArsenalVoid;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Cannon Battery, seen by everyone: a small rift per cannon in the fan behind its holder, and an iron barrel pushing
 * out of each, all pointed at the holder's crosshair. A barrel kicks back when it fires, then sinks into its rift.
 */
final class ArsenalCannonRenderer {
	private static final double VIEW_DISTANCE = 96.0;
	private static final int SIDES = 12;
	private static final double BARREL_RADIUS = 0.2;
	/** The thicker breech end (inside the rift) and the muzzle band. */
	private static final double BREECH_RADIUS = 0.27;
	private static final double MUZZLE_RADIUS = 0.25;
	private static final double MUZZLE_BAND = 0.18;
	private static final double RECOIL = 0.45;
	private static final int IRON = 0xFF6E6E78;
	private static final int VOID_IRON = 0xFF3A2C48;
	private static final int BORE = 0xFF0A0A0A;
	private static final int LIGHT = 0xD000D0;

	private ArsenalCannonRenderer() {
	}

	static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = context.levelState().cameraRenderState.pos;
		float now = level.getGameTime() + partialTick;
		for (Player player : level.players()) {
			ArsenalCannons.Battery battery = player.getAttached(ArsenalCannons.BATTERY);
			if (battery == null || player.position().distanceToSqr(camera) > VIEW_DISTANCE * VIEW_DISTANCE) {
				continue;
			}
			boolean voidRift = ArsenalVoid.isVoid(player);
			Vec3 aim = ArsenalCannons.aimPoint(player, partialTick);
			int count = battery.count(now);
			for (int i = 0; i < count; i++) {
				float age = battery.age(i, now);
				if (age < 0.0F) {
					continue;
				}
				float open = ease(age / ArsenalCannons.OPEN_TICKS);
				float out = ease((age - ArsenalCannons.OPEN_TICKS * 0.5F) / ArsenalCannons.EMERGE_TICKS);
				double kick = 0.0;
				float fired = battery.sinceFired(i, now);
				if (!Float.isNaN(fired) && fired >= 0.0F) {
					kick = fired < ArsenalCannons.RECOIL_TICKS ? RECOIL * (1.0 - fired / ArsenalCannons.RECOIL_TICKS) : 0.0;
					out *= 1.0F - ease((fired - ArsenalCannons.RECOIL_TICKS) / ArsenalCannons.SINK_TICKS);
					open *= 1.0F - ease((fired - ArsenalCannons.RECOIL_TICKS - ArsenalCannons.SINK_TICKS) / ArsenalCannons.OPEN_TICKS);
				}
				Vec3 at = ArsenalCannons.slot(player, i, partialTick);
				Vec3 dir = aim.subtract(at);
				dir = dir.lengthSqr() < 1.0E-4 ? player.getViewVector(partialTick) : dir.normalize();
				Vec3 right = dir.cross(new Vec3(0.0, 1.0, 0.0));
				right = right.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
				Vec3 up = right.cross(dir).normalize();
				Vec3 rel = at.subtract(camera);
				if (open > 0.01F) {
					double r = ArsenalCannons.RIFT_RADIUS * open;
					RiftPortals.disc(context.submitNodeCollector(), context.poseStack(), rel, right, up, dir.scale(-1.0), r, r, now * 0.15 + i, voidRift);
				}
				double length = ArsenalCannons.BARREL_LENGTH * out - kick;
				if (length > 0.05) {
					barrel(context, rel, dir, right, up, length, voidRift ? VOID_IRON : IRON);
				}
			}
		}
	}

	private static float ease(float x) {
		x = Mth.clamp(x, 0.0F, 1.0F);
		return 1.0F - (1.0F - x) * (1.0F - x);
	}

	private static TextureAtlasSprite metal() {
		return Minecraft.getInstance().getModelManager().getBlockStateModelSet().getParticleMaterial(Blocks.ANVIL.defaultBlockState()).sprite();
	}

	/** A barrel {@code length} long from {@code at} along {@code dir}: breech, tapering body, muzzle band, dark bore. */
	private static void barrel(LevelRenderContext context, Vec3 at, Vec3 dir, Vec3 right, Vec3 up, double length, int color) {
		TextureAtlasSprite sprite = metal();
		float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
		Vec3 back = at.add(dir.scale(-0.25));
		Vec3 tip = at.add(dir.scale(length));
		double bandStart = Math.max(0.0, length - MUZZLE_BAND);
		Vec3 band = at.add(dir.scale(bandStart));
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.entitySolid(sprite.atlasLocation()), (pose, buffer) -> {
			tube(buffer, pose, back, at, BREECH_RADIUS, BREECH_RADIUS, right, up, color, u0, u1, v0, v1);
			tube(buffer, pose, at, band, BREECH_RADIUS * 0.95, BARREL_RADIUS, right, up, color, u0, u1, v0, v1);
			tube(buffer, pose, band, tip, MUZZLE_RADIUS, MUZZLE_RADIUS, right, up, color, u0, u1, v0, v1);
			// the muzzle's face: an iron ring round a black bore
			for (int s = 0; s < SIDES; s++) {
				double a0 = s * Mth.TWO_PI / SIDES, a1 = (s + 1) * Mth.TWO_PI / SIDES;
				Vec3 o0 = ring(tip, right, up, MUZZLE_RADIUS, a0), o1 = ring(tip, right, up, MUZZLE_RADIUS, a1);
				Vec3 i0 = ring(tip, right, up, BARREL_RADIUS * 0.6, a0), i1 = ring(tip, right, up, BARREL_RADIUS * 0.6, a1);
				quad(buffer, pose, o0, i0, i1, o1, dir, color, u0, v0, u0, v1, u1, v1, u1, v0);
				Vec3 deep = tip.add(dir.scale(-0.02));
				quad(buffer, pose, i0, deep, deep, i1, dir, BORE, u0, v0, u0, v1, u1, v1, u1, v0);
			}
		});
	}

	/** The side of a (possibly tapering) tube from {@code a} to {@code b}. */
	private static void tube(VertexConsumer buffer, PoseStack.Pose pose, Vec3 a, Vec3 b, double ra, double rb, Vec3 right, Vec3 up, int color,
		float u0, float u1, float v0, float v1) {
		for (int s = 0; s < SIDES; s++) {
			double a0 = s * Mth.TWO_PI / SIDES, a1 = (s + 1) * Mth.TWO_PI / SIDES;
			float ua = Mth.lerp((float) s / SIDES, u0, u1), ub = Mth.lerp((float) (s + 1) / SIDES, u0, u1);
			Vec3 n = ring(Vec3.ZERO, right, up, 1.0, (a0 + a1) * 0.5);
			quad(buffer, pose, ring(a, right, up, ra, a0), ring(b, right, up, rb, a0), ring(b, right, up, rb, a1), ring(a, right, up, ra, a1), n, color,
				ua, v0, ua, v1, ub, v1, ub, v0);
		}
	}

	private static Vec3 ring(Vec3 center, Vec3 right, Vec3 up, double r, double angle) {
		return center.add(right.scale(Math.cos(angle) * r)).add(up.scale(Math.sin(angle) * r));
	}

	/** A quad drawn from both sides (whichever way it winds, it shows). */
	private static void quad(VertexConsumer buffer, PoseStack.Pose pose, Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 normal, int color,
		float ua, float va, float ub, float vb, float uc, float vc, float ud, float vd) {
		vertex(buffer, pose, a, normal, color, ua, va);
		vertex(buffer, pose, b, normal, color, ub, vb);
		vertex(buffer, pose, c, normal, color, uc, vc);
		vertex(buffer, pose, d, normal, color, ud, vd);
		vertex(buffer, pose, d, normal, color, ud, vd);
		vertex(buffer, pose, c, normal, color, uc, vc);
		vertex(buffer, pose, b, normal, color, ub, vb);
		vertex(buffer, pose, a, normal, color, ua, va);
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 p, Vec3 n, int color, float u, float v) {
		buffer.addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
			.setColor(color)
			.setUv(u, v)
			.setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(LIGHT)
			.setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
	}
}
