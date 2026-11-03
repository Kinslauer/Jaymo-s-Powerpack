package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.money.Coinage;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.storage.ValueInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.486) A villager or wandering trader saved before the coinage has its offers in emeralds: on load they are made
 * coins ({@link Coinage#convert}), once; the second time round there is nothing left to convert. The money changer's own
 * rates (emeralds for coppers) are left alone: {@link Coinage#convert} skips offers marked as his.
 */
@Mixin(AbstractVillager.class)
abstract class VillagerCoinMixin {
	@Shadow
	protected MerchantOffers offers;

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void jaymoskingdoms$coins(ValueInput input, CallbackInfo ci) {
		// the profession is not read yet here (Villager reads its data after super), so the changer's offers are told by their mark
		if (this.offers != null) {
			Coinage.convert(this.offers);
		}
	}
}
