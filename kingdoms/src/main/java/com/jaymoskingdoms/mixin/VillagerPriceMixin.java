package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Prejudice;
import com.jaymoskingdoms.Purists;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.7.130) A purist king's traders charge those not of his blood more, and their own kind less ({@link Purists#prices});
 * (0.7.219) a trader who looks down on a non-human blood charges it more ({@link Prejudice#prices}). */
@Mixin(Villager.class)
abstract class VillagerPriceMixin {
	@Inject(method = "updateSpecialPrices", at = @At("TAIL"))
	private void jaymoskingdoms$purist(Player player, CallbackInfo ci) {
		Purists.prices((Villager)(Object)this, player);
		// (0.7.219) and one who looks down on your blood
		Prejudice.prices((Villager)(Object)this, player);
	}
}
