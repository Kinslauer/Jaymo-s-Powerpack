package com.jaymospowers.power.arsenal.mixin;

import com.jaymospowers.power.arsenal.ArsenalAxeThrow;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The thrown axe ({@link ArsenalAxeThrow}; the hooks follow Axe Throw's ItemMixin, MIT-0, by ekulxam): an axe in the
 * throwing grip is drawn back like a trident and thrown on release; right-clicking one in the pack takes or lets go of the grip.
 */
@Mixin(Item.class)
abstract class AxeThrowItemMixin {
	@Inject(method = "use", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$drawAxe(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		ItemStack stack = player.getItemInHand(hand);
		if (ArsenalAxeThrow.gripped(stack)) {
			if (stack.nextDamageWillBreak()) {
				cir.setReturnValue(InteractionResult.FAIL);
				return;
			}
			player.startUsingItem(hand);
			cir.setReturnValue(InteractionResult.CONSUME);
		}
	}

	@Inject(method = "getUseAnimation", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$axeDrawn(ItemStack stack, CallbackInfoReturnable<ItemUseAnimation> cir) {
		if (ArsenalAxeThrow.gripped(stack)) {
			cir.setReturnValue(ItemUseAnimation.TRIDENT);
		}
	}

	@Inject(method = "getUseDuration", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$axeHeld(ItemStack stack, LivingEntity user, CallbackInfoReturnable<Integer> cir) {
		if (ArsenalAxeThrow.gripped(stack)) {
			cir.setReturnValue(72000);
		}
	}

	@Inject(method = "onUseTick", at = @At("HEAD"))
	private void jaymospowers$axeReady(Level level, LivingEntity user, ItemStack stack, int ticksRemaining, CallbackInfo ci) {
		if (ArsenalAxeThrow.gripped(stack)) {
			ArsenalAxeThrow.drawing(user, stack, 72000 - ticksRemaining);
		}
	}

	@Inject(method = "releaseUsing", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$throwAxe(ItemStack stack, Level level, LivingEntity user, int remainingTime, CallbackInfoReturnable<Boolean> cir) {
		if (ArsenalAxeThrow.gripped(stack)) {
			cir.setReturnValue(ArsenalAxeThrow.release(stack, user, 72000 - remainingTime));
		}
	}

	@Inject(method = "overrideOtherStackedOnMe", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$grip(ItemStack self, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess carried,
		CallbackInfoReturnable<Boolean> cir) {
		if (action == ClickAction.SECONDARY && other.isEmpty() && ArsenalAxeThrow.throwable(self) && slot.allowModification(player)) {
			ArsenalAxeThrow.toggle(self, player);
			cir.setReturnValue(true);
		}
	}
}
