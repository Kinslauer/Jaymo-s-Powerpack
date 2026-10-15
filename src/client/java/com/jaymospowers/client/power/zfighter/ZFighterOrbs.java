package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.power.zfighter.ZFighterNet;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Ki drawn as orbs: plain glowing balls of the ki's colour round a white-hot heart (no end crystal cage). Darktide's
 * Drown still draws water held in end crystal cages ({@link #waterOrb}). Every blast in flight and the ki gathering in the hands; the
 * server sends where each one is every tick ({@link ZFighterNet.KiOrbs}). They stand in for most of the old particles.
 */
public final class ZFighterOrbs {
	static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/end_crystal/end_crystal.png");
	static final int FULL_BRIGHT = 0xF000F0;
	/** Ticks without word before an orb is dropped. */
	static final int STALE = 2;
	/** Degrees a tick each cage turns. */
	static final float SPIN = 9.0F;
	/** Water orbs: how much slower than ki they turn, how far they slosh out of round, and how see-through (0-255). */
	static final float WATER_SPIN = 0.2F, WATER_WOBBLE = 0.06F;
	static final int WATER_CAGE_ALPHA = 140, WATER_HEART_ALPHA = 95;
	/** Ki orbs: the heart's share of the radius, how much the ball breathes, how fast its energy streams. */
	static final float ORB_HEART = 0.55F, ORB_PULSE = 0.04F, ORB_FLOW = 0.05F;
	private static final float SIN_45 = (float)Math.sin(Math.PI / 4.0);

	private static ModelPart glass;
	private static ModelPart core;

	private static final class Live {
		Vec3 prev;
		Vec3 at;
		float prevSize;
		float size;
		int color;
		boolean water;
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
			if (live.water) {
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
	 * Water held in an end crystal (Darktide's Drown): the same cages and core, but turning slowly, the whole thing
	 * wobbling like a held blob of water, pale glassy cages round a deep, see-through heart so who's drowning inside
	 * still shows.
	 */
	static void waterOrb(SubmitNodeCollector collector, PoseStack poseStack, Vec3 at, float size, int color, float time) {
		if (size <= 0.01F) {
			return;
		}
		if (glass == null) {
			glass = part(0);
			core = part(32);
		}
		RenderType type = RenderTypes.entityTranslucentEmissive(TEXTURE);
		int cage = WATER_CAGE_ALPHA << 24 | lighten(color);
		int heart = WATER_HEART_ALPHA << 24 | (color & 0xFFFFFF);
		float slow = time * WATER_SPIN;
		poseStack.pushPose();
		poseStack.translate(at.x, at.y, at.z);
		float scale = size * 2.0F;
		// a slow slosh: squashed one way, then the other
		float slosh = Mth.sin(time * 0.18F) * WATER_WOBBLE;
		float slosh2 = Mth.cos(time * 0.13F) * WATER_WOBBLE;
		poseStack.scale(scale * (1.0F + slosh), scale * (1.0F - slosh - slosh2), scale * (1.0F + slosh2));
		turn(poseStack, slow, 1.0F);
		collector.submitModelPart(glass, poseStack, type, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, cage);
		poseStack.scale(0.875F, 0.875F, 0.875F);
		// the inner cage turns the other way, as currents do
		turn(poseStack, slow, -1.6F);
		collector.submitModelPart(glass, poseStack, type, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, cage);
		poseStack.scale(0.765625F, 0.765625F, 0.765625F);
		turn(poseStack, slow, 2.2F);
		collector.submitModelPart(core, poseStack, type, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, heart);
		poseStack.popPose();
	}

	/** As the end crystal turns its cages, round the up axis, tipped onto a corner, at {@code rate} times the speed (negative: the other way). */
	private static void turn(PoseStack poseStack, float time, float rate) {
		poseStack.rotate(Axis.YP.rotationDegrees(time * SPIN * rate));
		poseStack.rotate(new Quaternionf().setAngleAxis((float)(Math.PI / 3.0), SIN_45, 0.0F, SIN_45));
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

	/** One 8-pixel cube of the end crystal texture, from texture column {@code u} (0 the glass, 32 the core). */
	private static ModelPart part(int u) {
		MeshDefinition mesh = new MeshDefinition();
		mesh.getRoot().addOrReplaceChild("cube", CubeListBuilder.create().texOffs(u, 0).addBox(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F),
			PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, 32).bakeRoot();
	}
}
