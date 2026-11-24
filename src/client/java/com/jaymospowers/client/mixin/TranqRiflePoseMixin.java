package com.jaymospowers.client.mixin;

import com.jaymospowers.dino.DinoItems;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The tranquilizer rifle is held like a crossbow (Amber Age's rifle did the same through Forge's client item extensions). */
@Mixin(AvatarRenderer.class)
abstract class TranqRiflePoseMixin {
	@Inject(method = "getArmPose(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;",
		at = @At("HEAD"), cancellable = true)
	private static void jaymospowers$rifleHold(Avatar avatar, ItemStack held, InteractionHand hand, CallbackInfoReturnable<HumanoidModel.ArmPose> cir) {
		if (!avatar.isSwinging() && held.is(DinoItems.TRANQ_RIFLE)) {
			cir.setReturnValue(HumanoidModel.ArmPose.CROSSBOW_HOLD);
		}
	}
}
