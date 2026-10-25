package com.jaymoskingdoms.client.whaling;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.whaling.Harpoon;
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
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.206) Draws a harpoon ({@link Harpoon}): an iron head on an ash shaft, along its flight, or (lodged) half buried in
 * what it struck, its shaft toward its line; and the line itself, from the shaft's end to where it's made fast, hanging
 * slack or drawn straight.
 */
public final class HarpoonRenderer extends EntityRenderer<Harpoon, HarpoonRenderer.State> {
	static final Identifier TEXTURE = JaymosKingdoms.id("textures/entity/whale/harpoon.png");
	/** How far its tip reaches ahead of its middle, and its tail behind (blocks); how deep a lodged one is buried. */
	static final double TIP = 17.0 / 16.0, TAIL = 12.0 / 16.0, BURIED = 0.6;

	public static final class State extends EntityRenderState {
		float yRot, xRot;
		/** Where it's drawn, from where the entity is. */
		Vec3 shift = Vec3.ZERO;
		final EntityRenderState.LeashState line = new EntityRenderState.LeashState();
		boolean hasLine;
	}

	private final ModelPart model;

	HarpoonRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = createLayer().bakeRoot();
	}

	static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("shaft", CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -0.5F, -12.0F, 1.0F, 1.0F, 24.0F), PartPose.ZERO);
		root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 25).addBox(-1.5F, -0.5F, -17.0F, 3.0F, 1.0F, 5.0F), PartPose.ZERO);
		root.addOrReplaceChild("socket", CubeListBuilder.create().texOffs(20, 25).addBox(-1.0F, -1.0F, -13.0F, 2.0F, 2.0F, 2.0F), PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, 32);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(Harpoon harpoon, State state, float partialTicks) {
		super.extractRenderState(harpoon, state, partialTicks);
		Vec3 here = new Vec3(state.x, state.y, state.z);
		Entity in = harpoon.lodged(), anchor = harpoon.anchor();
		Vec3 center = here;
		Vec3 dir = null;
		Vec3 knot = anchor != null ? harpoon.knot(anchor, partialTicks) : null;
		if (in != null) {
			Vec3 spot = harpoon.spot(in, partialTicks);
			// lodged: the shaft points back along its line (or straight out of the beast's side), its head buried
			Vec3 out = knot != null ? knot.subtract(spot) : spot.subtract(in.getPosition(partialTicks).add(0.0, in.getBbHeight() * 0.5, 0.0));
			dir = out.lengthSqr() > 1.0E-4 ? out.normalize().reverse() : new Vec3(0.0, -1.0, 0.0);
			center = spot.subtract(dir.scale(TIP - BURIED));
		}
		if (dir != null) {
			state.yRot = (float)(Mth.atan2(dir.x, dir.z) * Mth.RAD_TO_DEG);
			state.xRot = (float)(Mth.atan2(dir.y, dir.horizontalDistance()) * Mth.RAD_TO_DEG);
		} else {
			state.yRot = harpoon.getYRot(partialTicks);
			state.xRot = harpoon.getXRot(partialTicks);
			float yaw = state.yRot * Mth.DEG_TO_RAD, pitch = state.xRot * Mth.DEG_TO_RAD;
			dir = new Vec3(Mth.sin(yaw) * Mth.cos(pitch), Mth.sin(pitch), Mth.cos(yaw) * Mth.cos(pitch));
		}
		state.shift = center.subtract(here);
		state.hasLine = knot != null;
		if (knot != null) {
			Vec3 tail = center.subtract(dir.scale(TAIL));
			EntityRenderState.LeashState line = state.line;
			line.offset = tail.subtract(here);
			line.start = tail;
			line.end = knot;
			line.slack = harpoon.line() > tail.distanceTo(knot) + 0.5;
			Level level = harpoon.level();
			BlockPos a = BlockPos.containing(tail), b = BlockPos.containing(knot);
			line.startBlockLight = level.getBrightness(LightLayer.BLOCK, a);
			line.startSkyLight = level.getBrightness(LightLayer.SKY, a);
			line.endBlockLight = level.getBrightness(LightLayer.BLOCK, b);
			line.endSkyLight = level.getBrightness(LightLayer.SKY, b);
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.translate(state.shift.x, state.shift.y, state.shift.z);
		poseStack.rotateDegrees(Axis.YP, state.yRot + 180.0F);
		poseStack.rotateDegrees(Axis.XP, state.xRot);
		collector.submitModelPart(this.model, poseStack, RenderTypes.entityCutout(TEXTURE), state.lightCoords, OverlayTexture.NO_OVERLAY, null);
		poseStack.popPose();
		if (state.hasLine) {
			collector.submitLeash(poseStack, state.line);
		}
		super.submit(state, poseStack, collector, camera);
	}

	/** Its line reaches far past it: drawn whenever either end is in view. */
	@Override
	protected AABB getBoundingBoxForCulling(Harpoon harpoon, float partialTicks) {
		AABB box = super.getBoundingBoxForCulling(harpoon, partialTicks).inflate(2.0);
		Entity anchor = harpoon.anchor();
		return anchor == null ? box : box.minmax(anchor.getBoundingBox());
	}
}
