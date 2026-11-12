package com.jaymospowers.client.power.chrono;

import com.jaymospowers.power.chrono.ChronoDecoys;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * A Chrono's afterimage decoys ({@link ChronoDecoys}), drawn as translucent copies of the Chrono standing where the
 * server says, in the stride the Chrono has now, a little paler as they run out. Only the world's own frames of the
 * Chrono are used; when there is none (your own, in first person) one is extracted for the purpose.
 */
public final class ChronoDecoyClient {
	/** How opaque a copy is (0-255 share), the pulse on it, and its tint. */
	static final float ALPHA = 0.5F, PULSE = 0.15F;
	static final int TINT = 0xBFE8FF;
	/** Copies fade over their last this many ticks. */
	static final float FADE_TICKS = 20.0F;
	/** Your own copies closer than this to the camera (blocks) are skipped. */
	static final double NEAR_CAMERA = 1.2;
	/** Copies this far from the camera (blocks) are not drawn. */
	static final double VIEW_DISTANCE = 64.0;
	private static final int FULL_BRIGHT = 0xF000F0;

	private record Copies(List<Vec3> spots, List<Float> yaws, long until) {
	}

	private static final Int2ObjectMap<Copies> BY_OWNER = new Int2ObjectOpenHashMap<>();

	private ChronoDecoyClient() {
	}

	static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ChronoDecoys.Sync.TYPE, (payload, context) -> context.client().execute(() -> {
			ClientLevel level = context.client().level;
			if (level == null || payload.spots().isEmpty()) {
				BY_OWNER.remove(payload.owner());
			} else {
				BY_OWNER.put(payload.owner(), new Copies(payload.spots(), payload.yaws(), level.getGameTime() + payload.life()));
			}
		}));
		LevelRenderEvents.COLLECT_SUBMITS.register(ChronoDecoyClient::render);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BY_OWNER.clear());
	}

	private static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null || BY_OWNER.isEmpty()) {
			return;
		}
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float time = level.getGameTime() + partialTick;
		Vec3 camera = context.levelState().cameraRenderState.pos;
		boolean firstPerson = minecraft.options.getCameraType().isFirstPerson();
		PoseStack poseStack = context.poseStack();
		SubmitNodeCollector collector = context.submitNodeCollector();
		var dispatcher = minecraft.getEntityRenderDispatcher();
		for (var it = BY_OWNER.int2ObjectEntrySet().iterator(); it.hasNext();) {
			var entry = it.next();
			Copies copies = entry.getValue();
			Entity entity = level.getEntity(entry.getIntKey());
			if (!(entity instanceof Player owner) || !owner.isAlive() || time > copies.until()) {
				if (time > copies.until() + 40 || !(entity instanceof Player)) {
					it.remove();
				}
				continue;
			}
			AvatarRenderState state = frame(context, entry.getIntKey());
			if (state == null && dispatcher.extractEntity(owner, partialTick) instanceof AvatarRenderState extracted) {
				state = extracted;
			}
			if (state == null) {
				continue;
			}
			float fade = Mth.clamp((copies.until() - time) / FADE_TICKS, 0.0F, 1.0F);
			float alpha = ALPHA * fade * (1.0F - PULSE + PULSE * Mth.sin(time * 0.4F));
			AvatarRenderer<?> renderer = dispatcher.getRenderer(state);
			PlayerModel model = renderer.getModel();
			Identifier texture = renderer.getTextureLocation(state);
			int tint = ARGB.color(Math.round(alpha * 255.0F), TINT);
			float bodyRot = state.bodyRot;
			for (int i = 0; i < copies.spots().size(); i++) {
				Vec3 at = copies.spots().get(i);
				if (at.distanceToSqr(camera) > VIEW_DISTANCE * VIEW_DISTANCE
					|| firstPerson && owner == minecraft.player && at.add(0.0, 1.0, 0.0).distanceToSqr(camera) < NEAR_CAMERA * NEAR_CAMERA) {
					continue;
				}
				poseStack.pushPose();
				// as LivingEntityRenderer.submit lays the body out
				poseStack.translate(at.x - camera.x, at.y - camera.y, at.z - camera.z);
				poseStack.scale(state.scale, state.scale, state.scale);
				poseStack.rotateDegrees(Axis.YP, 180.0F - copies.yaws().get(i));
				poseStack.scale(-1.0F, -1.0F, 1.0F);
				poseStack.scale(0.9375F, 0.9375F, 0.9375F);
				poseStack.translate(0.0F, -1.501F, 0.0F);
				if (!com.jaymospowers.client.power.xenoform.XenoformClient.submitGhost(state, poseStack, collector, FULL_BRIGHT, tint)) {
					collector.submitModel(model, state, poseStack, RenderTypes.entityTranslucent(texture), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, tint,
						null, EntityRenderState.NO_OUTLINE);
				}
				poseStack.popPose();
			}
			state.bodyRot = bodyRot;
		}
	}

	/** The world's frame of the entity with this id this frame, if it was drawn. */
	private static AvatarRenderState frame(LevelRenderContext context, int id) {
		for (EntityRenderState state : context.levelState().entityRenderStates) {
			if (state instanceof AvatarRenderState avatar && avatar.id == id) {
				return avatar;
			}
		}
		return null;
	}
}
