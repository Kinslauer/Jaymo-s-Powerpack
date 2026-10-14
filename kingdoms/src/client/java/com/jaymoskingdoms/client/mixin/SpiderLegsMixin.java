package com.jaymoskingdoms.client.mixin;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.monster.spider.SpiderModel;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.7.17) Spiders move like spiders, Mo' Bends' idea (MIT; remade here): each leg gets a knee (two 8-long halves, the
 * lower one a child "shin"), held high with the foot planted, and lifting the foot higher as it steps; the abdomen
 * breathes and sways as it walks, and idle legs twitch now and then. Same part names as the game's, so cave spiders
 * and anything that looks parts up by name still work.
 */
@Mixin(SpiderModel.class)
abstract class SpiderLegsMixin {
	/** How far the upper half of each leg is raised (radians), and how far its shin bends down from it. */
	@Unique
	private static final float KNEE_LIFT = 0.45F, SHIN_BEND = 1.0F;
	/** Extra shin bend (radians) per unit of the game's own step lift, so feet clear the ground. */
	@Unique
	private static final float STEP_BEND = 0.9F;
	@Unique
	private static final String[] LEGS = {"right_hind_leg", "left_hind_leg", "right_middle_hind_leg", "left_middle_hind_leg",
		"right_middle_front_leg", "left_middle_front_leg", "right_front_leg", "left_front_leg"};

	@Inject(method = "createSpiderBodyLayer", at = @At("HEAD"), cancellable = true)
	private static void jaymoskingdoms$kneedLegs(CallbackInfoReturnable<LayerDefinition> cir) {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(32, 4).addBox(-4.0F, -4.0F, -8.0F, 8.0F, 8.0F, 8.0F), PartPose.offset(0.0F, 15.0F, -3.0F));
		root.addOrReplaceChild("body0", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -3.0F, -3.0F, 6.0F, 6.0F, 6.0F), PartPose.offset(0.0F, 15.0F, 0.0F));
		root.addOrReplaceChild("body1", CubeListBuilder.create().texOffs(0, 12).addBox(-5.0F, -4.0F, -6.0F, 10.0F, 8.0F, 12.0F), PartPose.offset(0.0F, 15.0F, 9.0F));
		float[][] poses = {
			{-4.0F, 2.0F, (float) (Math.PI / 4), (float) (-Math.PI / 4)}, {4.0F, 2.0F, (float) (-Math.PI / 4), (float) (Math.PI / 4)},
			{-4.0F, 1.0F, (float) (Math.PI / 8), -0.58119464F}, {4.0F, 1.0F, (float) (-Math.PI / 8), 0.58119464F},
			{-4.0F, 0.0F, (float) (-Math.PI / 8), -0.58119464F}, {4.0F, 0.0F, (float) (Math.PI / 8), 0.58119464F},
			{-4.0F, -1.0F, (float) (-Math.PI / 4), (float) (-Math.PI / 4)}, {4.0F, -1.0F, (float) (Math.PI / 4), (float) (Math.PI / 4)}};
		for (int i = 0; i < LEGS.length; i++) {
			boolean right = i % 2 == 0;
			CubeListBuilder thigh = right ? CubeListBuilder.create().texOffs(18, 0).addBox(-7.0F, -1.0F, -1.0F, 8.0F, 2.0F, 2.0F)
				: CubeListBuilder.create().texOffs(18, 0).mirror().addBox(-1.0F, -1.0F, -1.0F, 8.0F, 2.0F, 2.0F);
			CubeListBuilder shin = right ? CubeListBuilder.create().texOffs(18, 0).addBox(-8.0F, -1.0F, -1.0F, 8.0F, 2.0F, 2.0F)
				: CubeListBuilder.create().texOffs(18, 0).mirror().addBox(0.0F, -1.0F, -1.0F, 8.0F, 2.0F, 2.0F);
			float[] p = poses[i];
			PartDefinition leg = root.addOrReplaceChild(LEGS[i], thigh, PartPose.offsetAndRotation(p[0], 15.0F, p[1], 0.0F, p[2], p[3]));
			leg.addOrReplaceChild("shin", shin, PartPose.offset(right ? -7.0F : 7.0F, 0.0F, 0.0F));
		}
		cir.setReturnValue(LayerDefinition.create(mesh, 64, 32));
	}

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)V", at = @At("TAIL"))
	private void jaymoskingdoms$bendLegs(LivingEntityRenderState state, CallbackInfo ci) {
		ModelPart root = ((EntityModel<?>) (Object) this).root();
		float age = state.ageInTicks;
		float speed = state.walkAnimationSpeed;
		float pos = state.walkAnimationPos * 0.6662F;
		for (int i = 0; i < LEGS.length; i++) {
			ModelPart leg = root.getChild(LEGS[i]);
			if (!leg.hasChild("shin")) {
				return;
			}
			boolean right = i % 2 == 0;
			float side = right ? 1.0F : -1.0F;
			// the game's step lift for this leg, as it set it (same phases as SpiderModel.setupAnim)
			float phase = switch (i / 2) {
				case 0 -> 0.0F;
				case 1 -> (float) Math.PI;
				case 2 -> (float) (Math.PI / 2);
				default -> (float) (Math.PI * 3.0 / 2.0);
			};
			float step = Math.abs(Mth.sin(pos + phase) * 0.4F) * speed;
			// idle: a slow twitch, each leg on its own beat, fading out as it walks
			float twitch = Mth.sin(age * 0.13F + i * 1.7F) * 0.04F * (1.0F - Math.min(1.0F, speed * 2.0F));
			leg.zRot += side * (KNEE_LIFT + twitch);
			leg.getChild("shin").zRot = -side * (SHIN_BEND + step * STEP_BEND);
		}
		// the abdomen breathes, and sways as it walks
		ModelPart abdomen = root.getChild("body1");
		abdomen.y += Mth.sin(age * 0.08F) * 0.35F;
		abdomen.xRot += Mth.sin(age * 0.08F) * 0.04F;
		abdomen.yRot += Mth.sin(pos) * 0.12F * speed;
	}
}
