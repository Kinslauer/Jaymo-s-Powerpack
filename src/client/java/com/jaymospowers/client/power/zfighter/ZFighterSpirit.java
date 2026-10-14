package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.power.zfighter.ZFighterNet;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.crystal.EndCrystalModel;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The Spirit Bomb drawn as a great blue End Crystal: the crystal's spinning glass cubes, tinted blue and lit from within,
 * as big as the sphere (up to 30 blocks across), with a fainter, larger halo round it. The server sends where it is
 * every tick ({@link ZFighterNet.SpiritFx}); it's drawn between the last two places, and goes when they stop coming.
 */
public final class ZFighterSpirit {
	private static final Identifier CRYSTAL = Identifier.withDefaultNamespace("textures/entity/end_crystal/end_crystal.png");
	/** The crystal's outer cube is half a block across at scale 1; this many times the sphere's radius across. */
	static final float CUBE_PER_SIZE = 1.6F;
	static final float HALO = 1.18F;
	static final int TINT = 0xFF70C8FF;
	static final int HALO_TINT = 0x5590D8FF;
	static final int FULL_BRIGHT = 0xF000F0;
	/** Ticks without word before it's dropped. */
	static final int STALE = 3;

	private static final class Live {
		Vec3 prev;
		Vec3 at;
		float prevSize;
		float size;
		int quiet;
		int age;
	}

	private static final Map<Integer, Live> SPHERES = new HashMap<>();
	private static EndCrystalModel model;

	private ZFighterSpirit() {
	}

	static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ZFighterNet.SpiritFx.TYPE, (fx, context) -> {
			Vec3 at = new Vec3(fx.x(), fx.y(), fx.z());
			Live live = SPHERES.get(fx.id());
			if (live == null) {
				live = new Live();
				live.at = at;
				live.size = fx.size();
				SPHERES.put(fx.id(), live);
			}
			// one word a tick: what was the latest becomes the one before
			live.prev = live.at;
			live.prevSize = live.size;
			live.at = at;
			live.size = fx.size();
			live.quiet = 0;
		});
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (minecraft.level == null) {
				SPHERES.clear();
				return;
			}
			if (minecraft.isPaused()) {
				return;
			}
			SPHERES.values().removeIf(live -> ++live.quiet > STALE);
			for (Live live : SPHERES.values()) {
				live.age++;
			}
		});
		LevelRenderEvents.COLLECT_SUBMITS.register(ZFighterSpirit::render);
	}

	private static void render(LevelRenderContext context) {
		if (SPHERES.isEmpty()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (model == null) {
			model = new EndCrystalModel(minecraft.getEntityModels().bakeLayer(ModelLayers.END_CRYSTAL));
		}
		float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = context.levelState().cameraRenderState.pos;
		PoseStack poseStack = context.poseStack();
		for (Live live : SPHERES.values()) {
			Vec3 at = live.prev.lerp(live.at, partial).subtract(camera);
			float size = Mth.lerp(partial, live.prevSize, live.size);
			float age = live.age + partial;
			EndCrystalRenderState state = new EndCrystalRenderState();
			state.ageInTicks = age;
			state.showsBottom = false;
			state.lightCoords = FULL_BRIGHT;
			// the model hangs its glass 1.5 blocks up and bobs it; take both away so it sits on the sphere's middle
			float lift = 1.5F + EndCrystalRenderer.getY(age) * 0.5F;
			submit(context, poseStack, state, at, size * CUBE_PER_SIZE * 2.0F, lift, TINT);
			submit(context, poseStack, state, at, size * CUBE_PER_SIZE * 2.0F * HALO, lift, HALO_TINT);
		}
	}

	private static void submit(LevelRenderContext context, PoseStack poseStack, EndCrystalRenderState state, Vec3 at, float scale, float lift,
		int tint) {
		poseStack.pushPose();
		poseStack.translate(at.x, at.y, at.z);
		poseStack.scale(scale, scale, scale);
		poseStack.translate(0.0F, -lift, 0.0F);
		context.submitNodeCollector().submitModel(model, state, poseStack, RenderTypes.entityTranslucentEmissive(CRYSTAL), FULL_BRIGHT,
			OverlayTexture.NO_OVERLAY, tint, null, 0);
		poseStack.popPose();
	}
}
