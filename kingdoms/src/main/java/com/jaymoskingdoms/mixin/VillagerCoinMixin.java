package com.jaymoskingdoms.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.498) Opening their trades, villagers and wandering traders speak of coin or emeralds
 * ({@link com.jaymoskingdoms.MoneyTalk#tradeOpened}). A villager's offers are priced in coin when it updates its special
 * prices ({@link VillagerPriceMixin}); a merchant that is no villager (the wandering trader) has no such step, so its offers
 * are priced here instead: both call {@code setTradingPlayer} before {@code openTradingScreen} sends the offers
 * ({@code Merchant.openTradingScreen}), so they arrive already in coin.
 */
@Mixin(AbstractVillager.class)
abstract class VillagerCoinMixin {
	@Inject(method = "setTradingPlayer", at = @At("HEAD"))
	private void jaymoskingdoms$moneyTalk(net.minecraft.world.entity.player.@org.jspecify.annotations.Nullable Player player, CallbackInfo ci) {
		AbstractVillager self = (AbstractVillager)(Object)this;
		if (player instanceof ServerPlayer sp && self.getTradingPlayer() != player) {
			if (!(self instanceof Villager)) {
				com.jaymoskingdoms.Currencies.localize(self, sp);
			}
			com.jaymoskingdoms.MoneyTalk.tradeOpened(self, sp);
		}
	}
}
