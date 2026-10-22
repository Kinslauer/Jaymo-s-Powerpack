package com.jaymoskitchen.client.corpse;

import com.jaymoskitchen.corpse.SpiderLeg;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.18) A crushed spider's loose leg ({@link SpiderLeg}): a spider leg in two halves broken at the knee (the shape
 * Kingdoms gives spiders' legs; texture 64x32, the leg's strip at 18,0), in the skin the spider wore, which its own
 * renderer picks (a cave spider's, a Kingdoms kind's). It tumbles as it flies, lies flat at rest, and sinks into the
 * ground at the end.
 */
public class SpiderLegRenderer extends EntityRenderer<SpiderLeg, SpiderLegRenderer.State> {
	static final Identifier SPIDER = Identifier.withDefaultNamespace("textures/entity/spider/spider.png");
	/** How far the knee is bent (degrees, at least and at most: each leg its own). */
	static final float BEND_MIN = 25.0F, BEND_MAX = 80.0F;
	/** How deep it has sunk at the end (blocks, for a spider's size). */
	static final float SINK_DEPTH = 0.2F;

	private static @Nullable ModelPart thigh, shin;
	/** Each leg's skin, asked of its spider's renderer once. */
	private final java.util.Map<SpiderLeg, Identifier> textures = new java.util.WeakHashMap<>();

	public static class State extends EntityRenderState {
		Identifier texture = SPIDER;
		float size;
		float yaw;
		float spin;
		float bend;
		float sunk;
	}

	public SpiderLegRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.15F;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(SpiderLeg leg, State state, float partialTicks) {
		super.extractRenderState(leg, state, partialTicks);
		state.size = leg.size();
		state.yaw = leg.getYRot();
		state.spin = Mth.lerp(partialTicks, leg.spinO, leg.spin);
		state.bend = BEND_MIN + (BEND_MAX - BEND_MIN) * (Math.floorMod(leg.getId() * 7919, 97) / 96.0F);
		state.sunk = leg.sunk(partialTicks);
		state.texture = this.textures.computeIfAbsent(leg, l -> texture(l, partialTicks));
		this.shadowRadius = 0.15F * state.size * (1.0F - state.sunk);
	}

	/** The skin of the spider the leg came off, as its renderer draws it; a plain spider's if there's none to ask. */
	@SuppressWarnings("unchecked")
	private Identifier texture(SpiderLeg leg, float partialTicks) {
		Entity body = leg.clientBody();
		if (body == null) {
			return SPIDER;
		}
		EntityRenderer<? super Entity, ?> renderer = this.entityRenderDispatcher.getRenderer(body);
		if (renderer instanceof LivingEntityRenderer<?, ?, ?> living
			&& renderer.createRenderState(body, partialTicks) instanceof LivingEntityRenderState bodyState) {
			return ((LivingEntityRenderer<?, LivingEntityRenderState, ?>) living).getTextureLocation(bodyState);
		}
		return SPIDER;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		build();
		float size = state.size;
		poseStack.pushPose();
		poseStack.translate(0.0F, 0.06F * size - SINK_DEPTH * size * state.sunk, 0.0F);
		poseStack.rotateDegrees(Axis.YP, -state.yaw);
		// tumbling end over end; at rest (a half turn) it lies flat, the knee bent along the ground
		poseStack.rotateDegrees(Axis.ZP, state.spin);
		poseStack.scale(-size / 16.0F, -size / 16.0F, size / 16.0F);
		// (model space, pixels; the knee in the middle, the hip end at -8)
		var look = RenderTypes.entityCutout(state.texture);
		poseStack.pushPose();
		poseStack.translate(-8.0F, 0.0F, 0.0F);
		collector.submitModelPart(thigh, poseStack, look, state.lightCoords, OverlayTexture.NO_OVERLAY, null);
		poseStack.popPose();
		poseStack.pushPose();
		poseStack.rotateDegrees(Axis.YP, state.bend);
		collector.submitModelPart(shin, poseStack, look, state.lightCoords, OverlayTexture.NO_OVERLAY, null);
		poseStack.popPose();
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	/** The two halves, each 8 long from its own origin along +x. */
	private static void build() {
		if (thigh != null) {
			return;
		}
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("thigh", CubeListBuilder.create().texOffs(18, 0).addBox(0.0F, -1.0F, -1.0F, 8.0F, 2.0F, 2.0F), PartPose.ZERO);
		root.addOrReplaceChild("shin", CubeListBuilder.create().texOffs(18, 0).addBox(0.0F, -1.0F, -1.0F, 8.0F, 2.0F, 2.0F), PartPose.ZERO);
		ModelPart baked = LayerDefinition.create(mesh, 64, 32).bakeRoot();
		thigh = baked.getChild("thigh");
		shin = baked.getChild("shin");
	}
}
