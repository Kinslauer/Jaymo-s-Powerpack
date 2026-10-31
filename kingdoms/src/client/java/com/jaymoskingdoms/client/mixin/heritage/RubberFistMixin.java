package com.jaymoskingdoms.client.mixin.heritage;

import com.jaymoskingdoms.client.RubberEngulf;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.7.421) Where the game drew your reaching first-person fist, for the swallow's rope to leave from (see RubberEngulf). */
@Mixin(AvatarRenderer.class)
abstract class RubberFistMixin {
	@Inject(method = "renderHand", at = @At("TAIL"))
	private void jaymoskingdoms$rubberFist(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, Identifier skinTexture, ModelPart arm,
		boolean hasSleeve, CallbackInfo ci) {
		RubberEngulf.captureFist(poseStack, arm, arm == ((AvatarRenderer<?>) (Object) this).getModel().rightArm);
	}
}
