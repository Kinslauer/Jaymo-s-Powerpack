package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.money.Coinage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.inventory.MerchantContainer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.498) Paying from the purse in the trade screen: when a trade is picked, the screen moves the price from the pack
 * into the payment slot. Where the pack lacks the coin the price asks for, the coins are made from the purse or from a
 * larger coin ({@link Coinage#fill}), change going back into the purse.
 */
@Mixin(MerchantMenu.class)
abstract class MerchantMenuCoinMixin {
	@Shadow
	@Final
	private Merchant trader;
	@Shadow
	@Final
	private MerchantContainer tradeContainer;

	@Inject(method = "moveFromInventoryToPaymentSlot", at = @At("TAIL"))
	private void jaymoskingdoms$purse(int paymentSlot, ItemCost cost, CallbackInfo ci) {
		if (this.trader.getTradingPlayer() instanceof ServerPlayer player) {
			ItemStack now = this.tradeContainer.getItem(paymentSlot);
			ItemStack filled = Coinage.fill(player, cost, this.jaymoskingdoms$count(cost), now);
			if (filled != now) {
				this.tradeContainer.setItem(paymentSlot, filled);
			}
		}
	}

	/** (0.7.498) What the price really is now: the base cost plus demand and special-price changes, which the offer holds. */
	private int jaymoskingdoms$count(ItemCost cost) {
		for (MerchantOffer offer : this.trader.getOffers()) {
			if (offer.getItemCostA() == cost) {
				return offer.getCostA().getCount();
			}
			if (offer.getItemCostB().filter(b -> b == cost).isPresent()) {
				return offer.getCostB().getCount();
			}
		}
		return cost.count();
	}
}
