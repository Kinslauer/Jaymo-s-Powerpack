package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Languages;
import com.jaymoskingdoms.Treasuries;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.5.28) A trade on a realm's land pays the realm its toll ({@link Treasuries#traded}); (0.7.20) and teaches a foreigner a little ({@link Languages#traded}). */
@Mixin(AbstractVillager.class)
abstract class VillagerTradeMixin {
	@Inject(method = "notifyTrade", at = @At("HEAD"))
	private void jaymoskingdoms$toll(MerchantOffer offer, CallbackInfo ci) {
		Treasuries.traded((AbstractVillager)(Object)this, offer);
		if ((Object)this instanceof Villager v) {
			// (0.7.20) a few more words of their tongue
			Languages.traded(v);
		}
	}
}
