package com.jaymospowers.client.mixin.wornpose;

import com.jaymospowers.client.WornPose;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Jaymo's Kitchen's quiver worn on the back (and each arrow's fletching drawn with it): once it has set itself up, it
 * takes the body as Fresh Animations moved it and MCA's head size ({@link WornPose}), so it stays on the back while the
 * body sways. Kitchen has no Fresh Animations or MCA of its own, so the pose is laid on from here.
 */
@Pseudo
@Mixin(targets = {"com.jaymoskitchen.client.quiver.QuiverModel", "com.jaymoskitchen.client.quiver.QuiverModel$Fletching"}, remap = false)
abstract class KitchenQuiverPoseMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"), require = 0)
	private void jaymospowers$wornPose(AvatarRenderState state, CallbackInfo ci) {
		WornPose.fit((HumanoidModel<?>) (Object) this, state);
	}
}
