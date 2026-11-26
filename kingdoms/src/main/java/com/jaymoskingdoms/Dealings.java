package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import org.jspecify.annotations.Nullable;

/**
 * A trader's counter answers your dealings with them (after Dynamic Villager Trades, orlouge, MIT: the idea only). Each
 * villager keeps a ledger of what each customer has bought from them and sold them, good by good ({@link #DEALINGS}, by
 * the id they know you by, {@link Recollections#key}). A good you deal in with them often ({@link #USUAL}) is your usual:
 * you get a regular's price on it, more the more you deal in it ({@link #EVERY_MORE}, at most {@link #MOST_OFF} of it),
 * whether you buy it from them or bring it to them. And when your usual is sold out, they've kept some back for you, once
 * a day. Left be, the ledger fades ({@link #FADE_DAYS}): stay away and you're a stranger at their counter again, and
 * nothing worse.
 */
public final class Dealings {
	/** Trades in one good before it's your usual, and each this many more takes one more off its price. */
	static final int USUAL = 6, EVERY_MORE = 6;
	/** The most a usual's price comes down (a share of what it costs). */
	static final float MOST_OFF = 0.25F;
	/** A good's count halves each this many days without dealing with them; counts stop at {@link #COUNT_MOST}. */
	static final int FADE_DAYS = 3, COUNT_MOST = 96;
	/** Customers, and goods each, a ledger keeps (the least dealt with go first). */
	static final int CUSTOMERS = 16, GOODS = 16;

	/** One customer's dealings: each good (bought {@code b:}, sold {@code s:}) and its count, the last day they dealt, the last day stock was kept back. */
	record Ledger(Map<String, Integer> goods, long day, long kept) {
		static final Codec<Ledger> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("goods").forGetter(Ledger::goods),
			Codec.LONG.fieldOf("day").forGetter(Ledger::day),
			Codec.LONG.optionalFieldOf("kept", -1L).forGetter(Ledger::kept)).apply(i, Ledger::new));
	}

	/** On a villager: customer (the id they know them by) → their ledger. Saved. */
	static final AttachmentType<Map<String, Ledger>> DEALINGS = AttachmentRegistry.create(JaymosKingdoms.id("dealings"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Ledger.CODEC)));

	private Dealings() {
	}

	/** The good {@code offer} deals in, as bought ({@code b:}) or sold ({@code s:}) by the customer; null for a money changer's. */
	static @Nullable String good(MerchantOffer offer) {
		ItemStack result = offer.getResult();
		ItemStack cost = offer.getBaseCostA();
		if (Currencies.isMoney(result)) {
			return Currencies.isMoney(cost) || cost.isEmpty() ? null : "s:" + BuiltInRegistries.ITEM.getKey(cost.getItem());
		}
		return result.isEmpty() ? null : "b:" + BuiltInRegistries.ITEM.getKey(result.getItem());
	}

	/** {@code player}'s ledger with {@code v}, faded to today. */
	private static @Nullable Ledger ledger(Villager v, ServerPlayer player, long today) {
		Ledger l = v.getAttachedOrElse(DEALINGS, Map.<String, Ledger>of()).get(Recollections.key(player, v).toString());
		if (l == null) {
			return null;
		}
		int halvings = (int)Math.min(31L, Math.max(0L, (today - l.day()) / FADE_DAYS));
		if (halvings == 0) {
			return l;
		}
		Map<String, Integer> goods = new HashMap<>();
		l.goods().forEach((k, n) -> {
			if (n >> halvings > 0) {
				goods.put(k, n >> halvings);
			}
		});
		return new Ledger(goods, l.day() + (long)halvings * FADE_DAYS, l.kept());
	}

	private static void put(Villager v, ServerPlayer player, Ledger l) {
		Map<String, Ledger> all = new HashMap<>(v.getAttachedOrElse(DEALINGS, Map.<String, Ledger>of()));
		all.put(Recollections.key(player, v).toString(), l);
		while (all.size() > CUSTOMERS) {
			all.entrySet().stream().min((a, b) -> Long.compare(a.getValue().day(), b.getValue().day())).ifPresent(e -> all.remove(e.getKey()));
		}
		v.setAttached(DEALINGS, all);
	}

	/** (from {@code VillagerTradeMixin}) {@code player} made {@code offer}'s trade with {@code v}: one more in the ledger. */
	public static void traded(Villager v, ServerPlayer player, MerchantOffer offer) {
		String good = good(offer);
		if (good == null) {
			return;
		}
		long today = Deeds.today(player.level().getServer());
		Ledger was = ledger(v, player, today);
		Map<String, Integer> goods = new HashMap<>(was == null ? Map.of() : was.goods());
		int n = Math.min(COUNT_MOST, goods.getOrDefault(good, 0) + 1);
		goods.put(good, n);
		while (goods.size() > GOODS) {
			goods.entrySet().stream().filter(e -> !e.getKey().equals(good)).min(Map.Entry.comparingByValue()).ifPresent(e -> goods.remove(e.getKey()));
		}
		put(v, player, new Ledger(goods, today, was == null ? -1L : was.kept()));
		if (n == USUAL) {
			// (a tip card: from now on it comes cheaper to them)
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.dealings.usual", Recollections.nameTo(player, v),
				(good.startsWith("b:") ? offer.getResult() : offer.getBaseCostA()).getHoverName()).withStyle(ChatFormatting.GRAY));
		}
	}

	/**
	 * (from {@code VillagerPriceMixin}, as {@code player} opens trade with {@code v}, before the prices are put in the
	 * town's coin) a regular's price on each of their usuals, and a sold-out usual kept back for them (once a day).
	 */
	public static void prices(Villager v, Player player) {
		if (!(player instanceof ServerPlayer p)) {
			return;
		}
		long today = Deeds.today(p.level().getServer());
		Ledger l = ledger(v, p, today);
		if (l == null) {
			return;
		}
		boolean kept = false;
		for (MerchantOffer offer : v.getOffers()) {
			String good = good(offer);
			int n = good == null ? 0 : l.goods().getOrDefault(good, 0);
			if (n < USUAL) {
				continue;
			}
			int cost = offer.getBaseCostA().getCount();
			int off = Math.min(1 + (n - USUAL) / EVERY_MORE, Math.max(1, Math.round(cost * MOST_OFF)));
			if (cost > 1) {
				offer.addToSpecialPriceDiff(-Math.min(off, cost - 1));
			}
			if (offer.isOutOfStock() && l.kept() != today && good.startsWith("b:")) {
				offer.resetUses();
				kept = true;
			}
		}
		if (kept) {
			put(v, p, new Ledger(l.goods(), l.day(), today));
			v.playSound(SoundEvents.VILLAGER_YES, 1.0F, v.getVoicePitch());
			p.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.dealings.kept", Recollections.nameTo(p, v)).withStyle(ChatFormatting.GRAY));
		}
	}
}
