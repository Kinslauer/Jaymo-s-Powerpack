package com.jaymospowers.client.mixin;

import com.jaymospowers.client.ModelPoses;
import com.jaymospowers.client.compat.CritterArmoryFreshAnimationsCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The renderer animates its model once more just before the render layers (LivingEntityRenderer.submit), and layers
 * that read the model's parts (other mods' holstered weapons and belts, our band) read it then: our poses go on here
 * too, not only when the model is finally drawn (see ModelPoses).
 */
@Mixin(LivingEntityRenderer.class)
abstract class PoseLayersLivingEntityRendererMixin {
	@Shadow
	protected EntityModel<?> model;

	@Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;"
		+ "Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Ljava/lang/Object;)V", shift = At.Shift.AFTER))
	private void jaymospowers$poseForLayers(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera,
		CallbackInfo ci) {
		ModelPoses.apply(this.model, state);
		if (CritterArmoryFreshAnimationsCompat.active() && this.model instanceof net.minecraft.client.model.QuadrupedModel) {
			// Fresh Animations re-animates the body after its pose was captured for the render layers: keep the
			// animated pose on the state, so Critter Armory's armour models take it when they are posed from the
			// state later (the body parts Fresh Animations moves, in setupAnim order).
			final java.lang.reflect.Field[] parts = CRITTER_ARMOUR_BODY_PARTS;
			final PartPose[] pose = new PartPose[parts.length];
			for (int i = 0; i < parts.length; i++) {
				try {
					pose[i] = ((ModelPart) parts[i].get(this.model)).storePose();
				} catch (IllegalAccessException e) {
					pose[i] = null;
				}
			}
			state.setData(CritterArmoryFreshAnimationsCompat.CRITTER_ARMOR_EMF_POSES, pose);
		}
	}

	private static final java.lang.reflect.Field[] CRITTER_ARMOUR_BODY_PARTS = critterArmourBodyParts();

	private static java.lang.reflect.Field[] critterArmourBodyParts() {
		try {
			final java.lang.reflect.Field[] parts = new java.lang.reflect.Field[6];
			Class<?> quadruped = Class.forName("net.minecraft.client.model.QuadrupedModel");
			String[] names = { "head", "body", "rightHindLeg", "leftHindLeg", "rightFrontLeg", "leftFrontLeg" };
			for (int i = 0; i < names.length; i++) {
				java.lang.reflect.Field field = quadruped.getDeclaredField(names[i]);
				field.setAccessible(true);
				parts[i] = field;
			}
			return parts;
		} catch (Exception e) {
			com.jaymospowers.JaymosPowers.LOGGER.warn("Couldn't read QuadrupedModel's parts; Critter Armory's armour keeps the vanilla pose under Fresh Animations", e);
			return new java.lang.reflect.Field[0];
		}
	}
}
