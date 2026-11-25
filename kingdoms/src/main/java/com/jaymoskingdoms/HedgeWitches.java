package com.jaymoskingdoms;

import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * A hedge witch that isn't after you sells the dark thorns ({@link LeechKit}) for emeralds: use her with an empty hand. She
 * keeps no stall for one she has been hurt by or is hunting. Her stock is kept while she is loaded.
 */
final class HedgeWitches {
	/** What she sells: the thorn's id, its price (emeralds), and how many times before she is out. */
	private record Ware(String id, int emeralds, int uses) {
	}

	private static final Ware[] WARES = {
		new Ware("empty_thorn", 1, 16), new Ware("corroding_thorn", 6, 6), new Ware("stripping_thorn", 6, 6),
		new Ware("rotting_thorn", 6, 6), new Ware("raising_thorn", 14, 3)};
	/** How the price climbs with demand (the game's own trades use 0.05). */
	static final float PRICE_MULTIPLIER = 0.05F;
	/** How far from her a player may stand and keep trading (blocks). */
	static final double TRADE_RANGE = 6.0;

	private static final Map<Witch, MerchantOffers> STOCK = new WeakHashMap<>();

	private HedgeWitches() {
	}

	static void register() {
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !(entity instanceof Witch witch) || !player.getMainHandItem().isEmpty() || player.isSpectator() || player.isSecondaryUseActive()) {
				return InteractionResult.PASS;
			}
			if (!(world instanceof ServerLevel) || !(player instanceof ServerPlayer sp)) {
				return InteractionResult.PASS;
			}
			if (!witch.isAlive() || witch.getTarget() != null || witch.getLastHurtByMob() != null) {
				return InteractionResult.PASS;
			}
			MerchantOffers offers = STOCK.computeIfAbsent(witch, w -> stock());
			if (offers.isEmpty()) {
				return InteractionResult.PASS;
			}
			Stall stall = new Stall(witch, offers);
			stall.setTradingPlayer(sp);
			stall.openTradingScreen(sp, witch.getDisplayName(), 1);
			return InteractionResult.SUCCESS;
		});
	}

	private static MerchantOffers stock() {
		MerchantOffers offers = new MerchantOffers();
		for (Ware w : WARES) {
			Item item = BuiltInRegistries.ITEM.getValue(JaymosKingdoms.id(w.id()));
			offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, w.emeralds()), new ItemStack(item), w.uses(), 0, PRICE_MULTIPLIER));
		}
		return offers;
	}

	/** A witch's wares, open to one player: what the trade screen deals with. */
	private static final class Stall implements Merchant {
		private final Witch witch;
		private final MerchantOffers offers;
		private @Nullable Player trading;

		Stall(Witch witch, MerchantOffers offers) {
			this.witch = witch;
			this.offers = offers;
		}

		@Override
		public void setTradingPlayer(@Nullable Player player) {
			this.trading = player;
		}

		@Override
		public @Nullable Player getTradingPlayer() {
			return this.trading;
		}

		@Override
		public MerchantOffers getOffers() {
			return this.offers;
		}

		@Override
		public void overrideOffers(MerchantOffers offers) {
		}

		@Override
		public void notifyTrade(MerchantOffer offer) {
			offer.increaseUses();
			Level level = this.witch.level();
			level.playSound(null, this.witch.getX(), this.witch.getY(), this.witch.getZ(), SoundEvents.WITCH_CELEBRATE, SoundSource.NEUTRAL, 1.0F, 1.0F);
		}

		@Override
		public void notifyTradeUpdated(ItemStack stack) {
		}

		@Override
		public int getVillagerXp() {
			return 0;
		}

		@Override
		public void overrideXp(int xp) {
		}

		@Override
		public boolean showProgressBar() {
			return false;
		}

		@Override
		public SoundEvent getNotifyTradeSound() {
			return SoundEvents.WITCH_CELEBRATE;
		}

		@Override
		public boolean isClientSide() {
			return false;
		}

		@Override
		public boolean stillValid(Player player) {
			return this.witch.isAlive() && this.trading == player && player.distanceTo(this.witch) <= TRADE_RANGE;
		}
	}
}
