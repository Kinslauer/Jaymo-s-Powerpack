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
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Ki drawn as end crystal orbs: the crystal's two spinning glass cages round its turning core (the end crystal's own
 * texture), tinted the ki's colour and lit from within. Every blast in flight and the ki gathering in the hands; the
 * server sends where each one is every tick ({@link ZFighterNet.KiOrbs}). They stand in for most of the old particles.
 */
public final class ZFighterOrbs {
	static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/end_crystal/end_crystal.png");
	static final int FULL_BRIGHT = 0xF000F0;
	/** Ticks without word before an orb is dropped. */
	static final int STALE = 2;
	/** Degrees a tick each cage turns. */
	static final float SPIN = 9.0F;
	private static final float SIN_45 = (float)Math.sin(Math.PI / 4.0);

	private static ModelPart glass;
	private static ModelPart core;

	private static final class Live {
		Vec3 prev;
		Vec3 at;
		float prevSize;
		float size;
		int color;
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
				live.color = orb.color();
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
			orb(context.submitNodeCollector(), context.poseStack(), at, Mth.lerp(partial, live.prevSize, live.size), live.color, time, 1.0F);
		}
	}

	/**
	 * One end crystal orb {@code size} blocks across at {@code at} (camera-relative), {@code color} (RGB) its tint,
	 * {@code fade} (0-1) how solid. Also used for the ends of the ki beams.
	 */
	static void orb(SubmitNodeCollector collector, PoseStack poseStack, Vec3 at, float size, int color, float time, float fade) {
		if (size <= 0.01F || fade <= 0.01F) {
			return;
		}
		if (glass == null) {
			glass = part(0);
			core = part(32);
		}
		RenderType type = RenderTypes.entityTranslucentEmissive(TEXTURE);
		int alpha = (int)(Mth.clamp(fade, 0.0F, 1.0F) * 255.0F);
		int cage = (alpha * 3 / 4) << 24 | lighten(color);
		int heart = alpha << 24 | (color & 0xFFFFFF);
		poseStack.pushPose();
		poseStack.translate(at.x, at.y, at.z);
		// the cube is 8 pixels (half a block) across
		float scale = size * 2.0F;
		poseStack.scale(scale, scale, scale);
		turn(poseStack, time);
		collector.submitModelPart(glass, poseStack, type, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, cage);
		poseStack.scale(0.875F, 0.875F, 0.875F);
		turn(poseStack, time);
		collector.submitModelPart(glass, poseStack, type, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, cage);
		poseStack.scale(0.765625F, 0.765625F, 0.765625F);
		turn(poseStack, time);
		collector.submitModelPart(core, poseStack, type, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, heart);
		poseStack.popPose();
	}

	/** As the end crystal turns its cages: round the up axis, tipped onto a corner. */
	private static void turn(PoseStack poseStack, float time) {
		poseStack.rotate(Axis.YP.rotationDegrees(time * SPIN));
		poseStack.rotate(new Quaternionf().setAngleAxis((float)(Math.PI / 3.0), SIN_45, 0.0F, SIN_45));
	}

	/** {@code rgb} halfway to white, for the glass. */
	private static int lighten(int rgb) {
		int r = (rgb >> 16 & 0xFF) + 255 >> 1;
		int g = (rgb >> 8 & 0xFF) + 255 >> 1;
		int b = (rgb & 0xFF) + 255 >> 1;
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
