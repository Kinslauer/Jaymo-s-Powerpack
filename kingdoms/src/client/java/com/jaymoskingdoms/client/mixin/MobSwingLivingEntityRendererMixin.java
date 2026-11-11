package com.jaymoskingdoms.client.mixin;

import com.jaymoskingdoms.client.HiddenBladeClient;
import com.jaymoskingdoms.client.MobAnimations;
import com.jaymoskingdoms.client.PickpocketPose;
import com.jaymoskingdoms.client.PullerPose;
import com.jaymoskingdoms.client.VerdantLook;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mobs' Better Combat swings (see MobAnimations): the frame's animation goes on the render state, and onto the model as
 * it's posed for the layers (so held items and armour follow the arm).
 */
@Mixin(LivingEntityRenderer.class)
abstract class MobSwingLivingEntityRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
		at = @At("TAIL"))
	private void jaymoskingdoms$swingState(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		MobAnimations.extract(entity, state, partialTicks);
		PullerPose.extract(entity, state, partialTicks);
		PickpocketPose.extract(entity, state, partialTicks);
		com.jaymoskingdoms.client.BioAbsorbPose.extract(entity, state, partialTicks);
		com.jaymoskingdoms.client.BloodGrabPose.extract(entity, state, partialTicks);
		com.jaymoskingdoms.client.RubberEngulf.extract(entity, state, partialTicks);
		com.jaymoskingdoms.client.HandshakePose.extract(entity, state, partialTicks);
		// a lore film's actor: its pose, and what a finisher took off it
		com.jaymoskingdoms.client.films.FilmPose.extract(entity, state, partialTicks);
		VerdantLook.extract(entity, state);
		com.jaymoskingdoms.client.EmberLook.extract(entity, state);
		com.jaymoskingdoms.client.ValorLook.extract(entity, state);
		com.jaymoskingdoms.client.ValorWingsLook.extract(entity, state);
		com.jaymoskingdoms.client.HeritageLooks.extract(entity, state);
		// (0.7.641) a stake in a vampire
		com.jaymoskingdoms.client.StakeLayer.extract(entity, state);
		com.jaymoskingdoms.client.StakeStruggle.extract(entity, state, partialTicks);
		com.jaymoskingdoms.client.BioForms.extract(entity, state);
		com.jaymoskingdoms.client.ManaClient.extract(entity, state);
		// (0.7.479) a corpse's body, drawn outside the world: found again by its id (MCA's skin colour)
		com.jaymoskingdoms.client.DrawnBodies.drawn(entity);
		com.jaymoskingdoms.client.VillagerLooks.extract(entity, state);
		com.jaymoskingdoms.client.CaptainMarks.extract(entity, state);
		com.jaymoskingdoms.client.BaneBrand.extract(entity, state);
		// (0.7.490) the Beastfolk's and the fae's ears, tails and wings: real model parts
		com.jaymoskingdoms.client.BeastParts.extract(entity, state);
		// (0.7.486) the sea-blooded Tideborn's fins, tentacles, shells and spines
		com.jaymoskingdoms.client.SeaParts.extract(entity, state);
		com.jaymoskingdoms.client.TinkererOutfit.extract(entity, state);
		com.jaymoskingdoms.client.bb.BbBodies.extract(entity, state, partialTicks);
	}

	@Inject(
		method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Ljava/lang/Object;)V", shift = At.Shift.AFTER))
	private void jaymoskingdoms$swingLayers(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
		CameraRenderState camera, CallbackInfo ci) {
		PullerPose.apply(((LivingEntityRenderer<?, ?, ?>) (Object) this).getModel(), state);
		PickpocketPose.apply(((LivingEntityRenderer<?, ?, ?>) (Object) this).getModel(), state);
		com.jaymoskingdoms.client.BioAbsorbPose.apply(((LivingEntityRenderer<?, ?, ?>) (Object) this).getModel(), state);
		com.jaymoskingdoms.client.BloodGrabPose.apply(((LivingEntityRenderer<?, ?, ?>) (Object) this).getModel(), state);
		com.jaymoskingdoms.client.RubberEngulf.apply(((LivingEntityRenderer<?, ?, ?>) (Object) this).getModel(), state);
		com.jaymoskingdoms.client.BioForms.apply(((LivingEntityRenderer<?, ?, ?>) (Object) this).getModel(), state);
		com.jaymoskingdoms.client.HandshakePose.apply(((LivingEntityRenderer<?, ?, ?>) (Object) this).getModel(), state);
		com.jaymoskingdoms.client.films.FilmPose.apply(((LivingEntityRenderer<?, ?, ?>) (Object) this).getModel(), state);
		// (0.7.641) a staked vampire struggling with the stake
		com.jaymoskingdoms.client.StakeStruggle.apply(((LivingEntityRenderer<?, ?, ?>) (Object) this).getModel(), state);
		MobAnimations.apply(((LivingEntityRenderer<?, ?, ?>) (Object) this).getModel(), state);
		HiddenBladeClient.pose(((LivingEntityRenderer<?, ?, ?>) (Object) this).getModel(), state);
		// (0.7.321) last: a goblin's body is the goblin's (so held items and the parts on it follow)
		com.jaymoskingdoms.client.GoblinBody.apply(((LivingEntityRenderer<?, ?, ?>) (Object) this).getModel(), state);
	}
}
