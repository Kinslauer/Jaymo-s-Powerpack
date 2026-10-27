package com.jaymoskitchen.client.mixin;

import com.jaymoskitchen.client.delight.SkilletFlipItemRenderer;
import com.jaymoskitchen.client.delight.SkilletFlipState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (From Farmer's Delight, MIT.) The arm holding a flipping pan bobs with the flip, in third person. */
@Mixin(HumanoidModel.class)
public class HumanoidModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V",
		at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;useItemHand:Lnet/minecraft/world/InteractionHand;", opcode = Opcodes.GETFIELD))
	private <T extends HumanoidRenderState> void jaymoskitchen$flipArm(T state, CallbackInfo ci) {
		SkilletFlipItemRenderer.applyArm(Minecraft.getInstance().level, (HumanoidModel<?>) (Object) this, (SkilletFlipState) state, state.mainArm);
	}
}
