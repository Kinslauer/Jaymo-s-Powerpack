package com.jaymoskingdoms.client.inhabitants.fts;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.inhabitants.fts.PlagueKnife;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;

/**
 * The Plague Bearer's thrown knife drawn as the original's throwing dagger (From the Shadows Reborn by Sonmok14 and
 * L_Ender, AGPL v3; the model {@code throwing_dagger} and its texture, see licenses/from-the-shadows/CREDITS-gear.md),
 * point first along its flight.
 */
final class KnifeRenderer extends GeoEntityRenderer<PlagueKnife, EntityRenderState> {
	static final DataTicket<Float> YAW = DataTicket.create("jaymoskingdoms_knife_yaw", Float.class);
	static final DataTicket<Float> PITCH = DataTicket.create("jaymoskingdoms_knife_pitch", Float.class);

	KnifeRenderer(EntityRendererProvider.Context context) {
		super(context, new DefaultedEntityGeoModel<PlagueKnife>(JaymosKingdoms.id("throwing_dagger")));
	}

	@Override
	public void addRenderData(PlagueKnife knife, Void related, EntityRenderState state, float partialTick) {
		super.addRenderData(knife, related, state, partialTick);
		state.addGeckolibData(YAW, Mth.lerp(partialTick, knife.yRotO, knife.getYRot()));
		state.addGeckolibData(PITCH, Mth.lerp(partialTick, knife.xRotO, knife.getXRot()));
	}

	@Override
	public void adjustRenderPose(RenderPassInfo<EntityRenderState> info) {
		super.adjustRenderPose(info);
		PoseStack poseStack = info.poseStack();
		poseStack.mulPose(new org.joml.Matrix4f().rotation(Axis.YP.rotationDegrees(info.getOrDefaultGeckolibData(YAW, 0.0F) - 90.0F)));
		poseStack.mulPose(new org.joml.Matrix4f().rotation(Axis.ZP.rotationDegrees(info.getOrDefaultGeckolibData(PITCH, 0.0F) + 90.0F)));
	}
}
