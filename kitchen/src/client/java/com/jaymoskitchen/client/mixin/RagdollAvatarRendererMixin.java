package com.jaymoskitchen.client.mixin;

import com.jaymoskitchen.client.ragdoll.ClientRagdolls;
import com.jaymoskitchen.client.ragdoll.RagdollPose;
import com.jaymoskitchen.ragdoll.LivingRagdolls;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.5.1) A limp player (this client's own, or one the server relays) is drawn as the ragdoll: the pose is worked out
 * from its points here, the limbs follow in RagdollModelMixin, and the whole body is placed instead of vanilla's
 * (and everyone else's) turns. Ahead of Jaymo's Powers' mixins (priority 2000 and below).
 */
@Mixin(value = AvatarRenderer.class, priority = 3000)
abstract class RagdollAvatarRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("TAIL"))
	private void jaymoskitchen$limpState(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		if (!(entity instanceof Player)) {
			return;
		}
		ClientRagdolls.Track track = LivingRagdolls.isLimp(entity) ? ClientRagdolls.get(entity.getId()) : null;
		state.setData(RagdollPose.KEY, track == null ? null
			: RagdollPose.of(track.rig, track.points(partialTicks), state.x, state.y, state.z));
	}

	@Inject(
		method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
		at = @At("HEAD"), cancellable = true)
	private void jaymoskitchen$limpPlace(AvatarRenderState state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
		RagdollPose pose = state.getData(RagdollPose.KEY);
		if (pose == null) {
			return;
		}
		// the stack already carries the entity's scale: the move is in blocks, so divide it back out
		Matrix4f place = new Matrix4f(pose.body);
		float scale = Math.max(entityScale, 0.01F);
		place.setTranslation(place.m30() / scale, place.m31() / scale, place.m32() / scale);
		poseStack.mulPose(place);
		// then the turn the renderer gives a model at bodyRot 0
		poseStack.rotateDegrees(Axis.YP, 180.0F);
		ci.cancel();
	}
}
