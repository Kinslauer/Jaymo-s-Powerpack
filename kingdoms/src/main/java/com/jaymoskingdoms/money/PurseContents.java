package com.jaymoskingdoms.money;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import com.jaymoskingdoms.Currencies;
import com.jaymoskingdoms.Currency;

/**
 * (0.7.498) What a coin purse holds: loose coins by kind ({@code jaymoskingdoms:coins}), only the coins a purse can hold
 * ({@link Currency#money}; the greenskins' and pirates' coins and emeralds stay in the pack). At most {@value #PURSE_MOST}
 * in all. Whenever it changes, {@link #set} also writes how full the purse is into {@code custom_model_data} (float 0),
 * (0.7.568: the item model now reads the coin count itself, {@code jaymoskingdoms:purse_coins}; the model data stays for older readers).
 *
 * <p>Saved as a map of coin id ({@link Currency#id}) to count, like {@code {"silver": 3, "bit": 12}}. The dev format of
 * 0.7.498 ({@code copper}, {@code silver} and {@code gold} as three counts) still loads, as 1 bit, 2 silver and 5 dinars
 * each. (A dev purse of nothing but silver reads as the new format, so as that many silver.)
 */
public record PurseContents(Map<Currency, Integer> held) {
	/** Coins a purse holds (ten stacks of 64). */
	public static final int PURSE_MOST = 640;
	/** What a coin of the 0.7.498 dev format is worth now: a copper is a bit, a silver two silver, a gold five dinars. */
	private static final int OLD_SILVER = 2, OLD_GOLD = 5;

	public PurseContents {
		EnumMap<Currency, Integer> copy = new EnumMap<>(Currency.class);
		for (Map.Entry<Currency, Integer> e : held.entrySet()) {
			if (e.getKey().money() && e.getValue() != null && e.getValue() > 0) {
				copy.put(e.getKey(), e.getValue());
			}
		}
		held = Collections.unmodifiableMap(copy);
	}

	public static final PurseContents EMPTY = new PurseContents(Map.of());

	private static final Codec<Currency> KEY = Codec.STRING.comapFlatMap(PurseContents::byId, Currency::id);
	private static final Codec<PurseContents> CURRENT = Codec.unboundedMap(KEY, ExtraCodecs.NON_NEGATIVE_INT)
		.xmap(PurseContents::new, PurseContents::held)
		.validate(c -> c.coins() <= PURSE_MOST ? DataResult.success(c) : DataResult.error(() -> "A purse holds at most " + PURSE_MOST + " coins"));

	/** The 0.7.498 dev format: three counts. Only read, never written. */
	private record Old(int copper, int silver, int gold) {
		static final Codec<Old> CODEC = RecordCodecBuilder.create(i -> i.group(
				ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("copper", 0).forGetter(Old::copper),
				ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("silver", 0).forGetter(Old::silver),
				ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("gold", 0).forGetter(Old::gold))
			.apply(i, Old::new));

		PurseContents contents() {
			return EMPTY.with(Currency.DINAR, (int)Math.min(Integer.MAX_VALUE, (long)this.gold * OLD_GOLD))
				.with(Currency.SILVER, (int)Math.min(Integer.MAX_VALUE, (long)this.silver * OLD_SILVER))
				.with(Currency.BIT, this.copper);
		}
	}

	public static final Codec<PurseContents> CODEC = CURRENT.withAlternative(Old.CODEC, Old::contents);

	private static final StreamCodec<io.netty.buffer.ByteBuf, Currency> KEY_STREAM = ByteBufCodecs.idMapper(
		i -> Currency.values()[Math.max(0, Math.min(i, Currency.values().length - 1))], Currency::ordinal);
	public static final StreamCodec<RegistryFriendlyByteBuf, PurseContents> STREAM_CODEC = ByteBufCodecs
		.<RegistryFriendlyByteBuf, Currency, Integer, Map<Currency, Integer>>map(n -> new EnumMap<>(Currency.class), KEY_STREAM, ByteBufCodecs.VAR_INT,
			Currency.values().length)
		.map(PurseContents::new, PurseContents::held);

	private static DataResult<Currency> byId(String id) {
		for (Currency c : Currency.values()) {
			if (c.money() && c.id().equals(id)) {
				return DataResult.success(c);
			}
		}
		return DataResult.error(() -> "Not a coin a purse holds: " + id);
	}

	/** What {@code purse} holds (nothing if it isn't a purse). */
	public static PurseContents of(ItemStack purse) {
		return purse.getOrDefault(MoneyItems.COINS, EMPTY);
	}

	/** Stores {@code contents} in {@code purse}, with how full it is for its model. */
	public static void set(ItemStack purse, PurseContents contents) {
		purse.set(MoneyItems.COINS, contents);
		purse.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(contents.fullness()), List.of(), List.of(), List.of()));
	}

	/** A purse holding {@code contents}. */
	public static ItemStack filled(PurseContents contents) {
		ItemStack purse = new ItemStack(MoneyItems.COIN_PURSE);
		set(purse, contents);
		return purse;
	}

	/**
	 * A purse holding {@code milli} of worth paid out in {@code coin} ({@link Currencies#pay}: whole coins and copper bits for
	 * the change; what fits, so keep it under {@value #PURSE_MOST} coins). A coin a purse can't hold becomes silver.
	 */
	public static ItemStack filled(Currency coin, long milli) {
		return filled(contentsOf(coin, milli));
	}

	/** A purse holding {@code milli} of worth in silver. */
	public static ItemStack filled(long milli) {
		return filled(Currency.SILVER, milli);
	}

	/** {@code milli} of worth paid out in {@code coin} (silver if a purse can't hold it), as far as a purse holds. */
	public static PurseContents contentsOf(Currency coin, long milli) {
		Currency c = coin.money() ? coin : Currency.SILVER;
		Currencies.Pay pay = Currencies.pay(c, milli);
		return EMPTY.with(pay.coin(), (int)Math.min(Integer.MAX_VALUE, pay.coins())).with(Currency.BIT, (int)Math.min(Integer.MAX_VALUE, pay.bits()));
	}

	/** How many coins of {@code coin}. */
	public int count(Currency coin) {
		return this.held.getOrDefault(coin, 0);
	}

	/** Coins in all. */
	public int coins() {
		int n = 0;
		for (int v : this.held.values()) {
			n += v;
		}
		return n;
	}

	public int room() {
		return Math.max(0, PURSE_MOST - coins());
	}

	/** Worth at the standard rate, in milli. */
	public long value() {
		long v = 0L;
		for (Map.Entry<Currency, Integer> e : this.held.entrySet()) {
			v += (long)e.getValue() * e.getKey().base;
		}
		return v;
	}

	/** 0 (empty) to 1 (full), by coin count. */
	public float fullness() {
		return Math.min(1.0F, (float)coins() / PURSE_MOST);
	}

	public boolean isEmpty() {
		return this.held.isEmpty();
	}

	/** {@code delta} more (or fewer, if negative) of {@code coin}; never below none, nor over the purse's room. */
	public PurseContents with(Currency coin, int delta) {
		if (!coin.money()) {
			return this;
		}
		int n = Math.max(-count(coin), Math.min(delta, room()));
		if (n == 0) {
			return this;
		}
		EnumMap<Currency, Integer> next = new EnumMap<>(Currency.class);
		next.putAll(this.held);
		next.put(coin, count(coin) + n);
		return new PurseContents(next);
	}

	/** What it holds as shown, biggest coin first ("3 Gold Dinar, 12 Copper Bit"), or "No coin". */
	public Component list() {
		List<Currency> coins = new ArrayList<>(this.held.keySet());
		coins.sort((a, b) -> Integer.compare(b.base, a.base));
		if (coins.isEmpty()) {
			return Component.translatable("gui.jaymoskingdoms.story.purse.none");
		}
		MutableComponent out = Component.empty();
		for (int i = 0; i < coins.size(); i++) {
			out.append(i == 0 ? Component.empty() : Component.literal(", ")).append(Currencies.amount(coins.get(i), count(coins.get(i))));
		}
		return out;
	}
}
