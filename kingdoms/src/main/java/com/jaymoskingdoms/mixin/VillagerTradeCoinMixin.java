package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.money.Coinage;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.storage.loot.LootContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.7.487) Every villager trade, vanilla's, MCA's and the mods', is built here, so this is where emeralds become coins
 * ({@link Coinage#convert}): no trade file needs editing, and Kitchen's and Powers' trades never name a Kingdoms item.
 */
@Mixin(VillagerTrade.class)
abstract class VillagerTradeCoinMixin {
	@Inject(method = "getOffer", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$coins(LootContext context, CallbackInfoReturnable<MerchantOffer> cir) {
		MerchantOffer offer = cir.getReturnValue();
		// (0.7.487) some traders still deal in emeralds (Andy: "so it is worth it to keep them around")
		if (offer != null && !Coinage.keepsEmeralds(context.getOptional(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY))) {
			cir.setReturnValue(Coinage.convert(offer));
		}
	}
}
