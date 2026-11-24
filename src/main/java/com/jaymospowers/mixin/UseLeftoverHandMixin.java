package com.jaymospowers.mixin;

import com.jaymospowers.core.UseLeftover;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The bowl or bottle left by the last of a stack goes into the hand that used it ({@link UseLeftover}): vanilla puts it
 * there, but CreativeCore hands {@code completeUsingItem} the used-up stack instead, so it was lost. Nothing changes when
 * the hand already holds what was left (no CreativeCore), or when the use left the stack itself (some still in it, or
 * Creative's endless items).
 */
@Mixin(LivingEntity.class)
abstract class UseLeftoverHandMixin implements UseLeftover {
	@Shadow
	protected ItemStack useItem;

	@Unique
	private ItemStack jaymospowers$useLeftover;

	@Override
	public void jaymospowers$useLeftover(ItemStack left) {
		this.jaymospowers$useLeftover = left;
	}

	@Inject(method = "completeUsingItem", at = @At("HEAD"))
	private void jaymospowers$forgetLeftover(CallbackInfo ci) {
		this.jaymospowers$useLeftover = null;
	}

	@Inject(method = "completeUsingItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;stopUsingItem()V"))
	private void jaymospowers$leftoverInHand(CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		ItemStack left = this.jaymospowers$useLeftover;
		this.jaymospowers$useLeftover = null;
		InteractionHand hand = self.getUsedItemHand();
		if (left != null && left != this.useItem && self.getItemInHand(hand) == this.useItem) {
			self.setItemInHand(hand, left);
		}
	}
}
