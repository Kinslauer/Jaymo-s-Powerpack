package com.jaymoskingdoms.client.mixin.tomes;

import com.jaymoskingdoms.client.TomeFloat;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.7.390) A tome afloat ({@code TomeFloat}) isn't in the hand: a player's hand is drawn empty while the book from it floats (0.7.398: either hand). */
@Mixin(ArmedEntityRenderState.class)
abstract class TomeHeldItemMixin {
	@Inject(method = "extractArmedEntityRenderState", at = @At("TAIL"))
	private static void jaymoskingdoms$bookAfloat(LivingEntity entity, ArmedEntityRenderState state, ItemModelResolver resolver, float partial, CallbackInfo ci) {
		if (!(entity instanceof Player player)) {
			return;
		}
		for (boolean off : new boolean[] {false, true}) {
			if (!TomeFloat.afloat(player, off)) {
				continue;
			}
			if ((state.mainArm == HumanoidArm.RIGHT) != off) {
				state.rightHandItemState.clear();
				state.rightHandItemStack = ItemStack.EMPTY;
				state.rightArmPose = HumanoidModel.ArmPose.EMPTY;
			} else {
				state.leftHandItemState.clear();
				state.leftHandItemStack = ItemStack.EMPTY;
				state.leftArmPose = HumanoidModel.ArmPose.EMPTY;
			}
		}
	}
}
