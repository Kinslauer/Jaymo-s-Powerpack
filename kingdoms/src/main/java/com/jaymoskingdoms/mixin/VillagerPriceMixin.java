package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Purists;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.7.127) A purist king's traders charge those not of his blood more, and their own kind less ({@link Purists#prices}). */
@Mixin(Villager.class)
abstract class VillagerPriceMixin {
	@Inject(method = "updateSpecialPrices", at = @At("TAIL"))
	private void jaymoskingdoms$purist(Player player, CallbackInfo ci) {
		Purists.prices((Villager)(Object)this, player);
	}
}
