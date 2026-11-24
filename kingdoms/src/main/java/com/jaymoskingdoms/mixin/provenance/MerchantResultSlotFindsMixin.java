package com.jaymoskingdoms.mixin.provenance;

import com.jaymoskingdoms.Provenance;
import com.jaymoskingdoms.money.Money;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A trade taken: what was bought is stamped as bought from that merchant ({@link Provenance#bought}); and a famous find
 * (a dragon's hoard, a sunken ship, an old city) sold for coin fetches a collector's price over it
 * ({@link Provenance#famousBonus}), paid once the trade has gone through.
 */
@Mixin(MerchantResultSlot.class)
abstract class MerchantResultSlotFindsMixin {
	@Shadow
	@Final
	private MerchantContainer slots;
	@Shadow
	@Final
	private Merchant merchant;
	@Unique
	private ItemStack jaymoskingdoms$famous = ItemStack.EMPTY;
	@Unique
	private int jaymoskingdoms$famousCount;
	@Unique
	private long jaymoskingdoms$bonus;

	@Inject(method = "onTake", at = @At("HEAD"))
	private void jaymoskingdoms$beforeTake(Player player, ItemStack carried, CallbackInfo ci) {
		Provenance.bought(carried, player, this.merchant instanceof Entity e ? e : null);
		this.jaymoskingdoms$famous = ItemStack.EMPTY;
		this.jaymoskingdoms$bonus = 0L;
		for (int slot = 0; slot < 2; slot++) {
			ItemStack paid = this.slots.getItem(slot);
			long bonus = Provenance.famousBonus(paid, carried);
			if (bonus > 0L) {
				this.jaymoskingdoms$famous = paid;
				this.jaymoskingdoms$famousCount = paid.getCount();
				this.jaymoskingdoms$bonus = bonus;
				break;
			}
		}
	}

	@Inject(method = "onTake", at = @At("TAIL"))
	private void jaymoskingdoms$afterTake(Player player, ItemStack carried, CallbackInfo ci) {
		ItemStack famous = this.jaymoskingdoms$famous;
		long bonus = this.jaymoskingdoms$bonus;
		this.jaymoskingdoms$famous = ItemStack.EMPTY;
		this.jaymoskingdoms$bonus = 0L;
		if (bonus > 0L && player instanceof ServerPlayer sp && (famous.isEmpty() || famous.getCount() < this.jaymoskingdoms$famousCount)) {
			// (the find went over the counter: the collector's price on top)
			Money.pay(sp, bonus);
			sp.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.jaymoskingdoms.provenance.collector",
				Money.text(sp, bonus)).withStyle(net.minecraft.ChatFormatting.GOLD));
		}
	}
}
