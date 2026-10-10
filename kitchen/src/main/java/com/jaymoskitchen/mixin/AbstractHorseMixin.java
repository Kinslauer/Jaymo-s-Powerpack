package com.jaymoskitchen.mixin;

import com.jaymoskitchen.horse.SaddlebagContainer;
import com.jaymoskitchen.horse.Saddlebags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A horse wearing saddlebags shows their 15 slots in its inventory screen, like a donkey's chest. */
@Mixin(AbstractHorse.class)
abstract class AbstractHorseMixin {
	@Inject(method = "getInventoryColumns", at = @At("HEAD"), cancellable = true)
	private void jaymoskitchen$saddlebagColumns(CallbackInfoReturnable<Integer> cir) {
		if (Saddlebags.wearing((AbstractHorse) (Object) this) != null) {
			cir.setReturnValue(SaddlebagContainer.COLUMNS);
		}
	}

	@Inject(method = "openCustomInventoryScreen", at = @At("HEAD"), cancellable = true)
	private void jaymoskitchen$openSaddlebags(Player player, CallbackInfo ci) {
		AbstractHorse horse = (AbstractHorse) (Object) this;
		ItemStack bag = Saddlebags.wearing(horse);
		if (bag == null) {
			return;
		}
		ci.cancel();
		if (!horse.level().isClientSide() && (!horse.isVehicle() || horse.hasPassenger(player)) && horse.isTamed()) {
			player.openHorseInventory(horse, SaddlebagContainer.of(horse, bag));
		}
	}

	@Inject(method = "hasInventoryChanged", at = @At("HEAD"), cancellable = true)
	private void jaymoskitchen$saddlebagsTakenOff(Container old, CallbackInfoReturnable<Boolean> cir) {
		if (old instanceof SaddlebagContainer bags) {
			cir.setReturnValue(!bags.attached());
		}
	}
}
