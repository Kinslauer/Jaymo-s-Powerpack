package com.jaymoskingdoms.client;

import com.jaymoskingdoms.AnglerFloat;
import com.jaymoskingdoms.Anglers;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * An NPC angler's float ({@link AnglerFloat}, {@link Anglers}): vanilla's bobber, and its line from the tip of the angler's
 * rod, hanging in a curve while it waits and drawn straight while a fish is on.
 */
public final class AnglerFloatRenderer extends EntityRenderer<AnglerFloat, AnglerFloatRenderer.State> {
	private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/fishing/fishing_hook.png");
	private static final RenderType BOBBER = RenderTypes.entityCutoutCull(TEXTURE);
	/** The rod's tip from the angler's eyes: out to the side of the rod hand, forward, and down (blocks, at scale 1); higher in a fight. */
	static final double SIDE = 0.35, FORWARD = 1.25, DOWN = 0.35, FIGHT_LIFT = 0.45;
	static final int SEGMENTS = 16;

	public static final class State extends EntityRenderState {
		/** From the float to the rod's tip. */
		Vec3 line = Vec3.ZERO;
		boolean hasLine, taut;
	}

	AnglerFloatRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	static void register() {
		EntityRenderers.register(AnglerFloat.TYPE, AnglerFloatRenderer::new);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(AnglerFloat bob, State state, float partialTicks) {
		super.extractRenderState(bob, state, partialTicks);
		Entity owner = bob.owner();
		state.hasLine = owner instanceof LivingEntity;
		state.taut = bob.taut();
		if (owner instanceof LivingEntity angler) {
			state.line = tip(angler, partialTicks).subtract(bob.getPosition(partialTicks).add(0.0, 0.25, 0.0));
		}
	}

	/** The tip of {@code angler}'s rod, as vanilla finds a player's hand for another player's line: by the rod arm, out in front. */
	static Vec3 tip(LivingEntity angler, float partialTicks) {
		int side = angler.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
		float yaw = Mth.lerp(partialTicks, angler.yBodyRotO, angler.yBodyRot) * Mth.DEG_TO_RAD;
		double s = Mth.sin(yaw), c = Mth.cos(yaw), scale = angler.getScale();
		double across = side * SIDE * scale, ahead = FORWARD * scale;
		double up = -DOWN * scale + ("jaymoskingdoms:angler_fight".equals(Anglers.pose(angler)) ? FIGHT_LIFT * scale : 0.0);
		return angler.getEyePosition(partialTicks).add(-c * across - s * ahead, up, -s * across + c * ahead);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.pushPose();
		poseStack.scale(0.5F, 0.5F, 0.5F);
		poseStack.rotate(camera.orientation);
		int light = state.lightCoords;
		collector.submitCustomGeometry(poseStack, BOBBER, (pose, consumer) -> {
			vertex(consumer, pose, light, 0.0F, 0, 0, 1);
			vertex(consumer, pose, light, 1.0F, 0, 1, 1);
			vertex(consumer, pose, light, 1.0F, 1, 1, 0);
			vertex(consumer, pose, light, 0.0F, 1, 0, 0);
		});
		poseStack.popPose();
		if (state.hasLine) {
			float x = (float)state.line.x, y = (float)state.line.y, z = (float)state.line.z;
			float width = Minecraft.getInstance().gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth;
			boolean taut = state.taut;
			collector.submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, consumer) -> {
				for (int i = 0; i < SEGMENTS; i++) {
					float a = i / (float)SEGMENTS, b = (i + 1) / (float)SEGMENTS;
					string(x, y, z, consumer, pose, a, b, width, taut);
					string(x, y, z, consumer, pose, b, a, width, taut);
				}
			});
		}
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, int light, float x, int y, int u, int v) {
		consumer.addVertex(pose, x - 0.5F, y - 0.5F, 0.0F).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
			.setNormal(pose, 0.0F, 1.0F, 0.0F);
	}

	/** One end of a piece of line, {@code t} of the way to the rod: in vanilla's hanging curve, or straight when it's taut. */
	private static void string(float x, float y, float z, VertexConsumer consumer, PoseStack.Pose pose, float t, float next, float width, boolean taut) {
		float px = x * t, py = height(y, t, taut), pz = z * t;
		float dx = x * next - px, dy = height(y, next, taut) - py, dz = z * next - pz;
		float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
		if (len < 1.0E-5F) {
			len = 1.0F;
		}
		consumer.addVertex(pose, px, py, pz).setColor(-16777216).setNormal(pose, dx / len, dy / len, dz / len).setLineWidth(width);
	}

	private static float height(float y, float t, boolean taut) {
		return (taut ? y * t : y * (t * t + t) * 0.5F) + 0.25F;
	}

	/** Its line reaches back to the angler: drawn while either end is in view. */
	@Override
	protected AABB getBoundingBoxForCulling(AnglerFloat bob, float partialTicks) {
		AABB box = super.getBoundingBoxForCulling(bob, partialTicks).inflate(1.0);
		Entity owner = bob.owner();
		return owner == null ? box : box.minmax(owner.getBoundingBox());
	}
}
