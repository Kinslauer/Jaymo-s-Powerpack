package com.jaymoskingdoms.mixin.provenance;

import com.jaymoskingdoms.StolenGoods;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.item.trading.Merchant;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Something put in a trade: the merchant looks at it next tick ({@link StolenGoods#offered}), in case it is theirs. */
@Mixin(MerchantContainer.class)
abstract class MerchantContainerStolenMixin {
	@Shadow
	@Final
	private Merchant merchant;

	@Inject(method = "updateSellItem", at = @At("TAIL"))
	private void jaymoskingdoms$looked(CallbackInfo ci) {
		if (this.merchant.getTradingPlayer() instanceof ServerPlayer player) {
			StolenGoods.offered(player);
		}
	}
}
