package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.490) Currencies, Andy (2026-11-03): "Diversify the currencies, it can change based on the prominent trading hub's
 * currency or other factors." Emeralds stay the one coin every trader knows; each culture also has a coin of its own,
 * the greenskins keep their bent ingots and pirates take anything gold. {@link #base} is what one coin is worth at the
 * standard rate, in thousandths of an emerald (the unit every settlement's treasury is still kept in, so old saves
 * stay valid); the rate a money-changer really gives moves with the realms behind each coin ({@link Currencies#rate}).
 * (0.7.498) Coins replace emeralds as money, Andy (2026-11-04): "Combine: culture coins replace emeralds". The emerald stays the
 * unit of account and the keepers' coin, but is otherwise a gem sold to a money-changer; the {@link #BIT} is the one small
 * coin every town shares, in place of emerald change. Saved by id ({@link #CODEC}); only add at the end.
 */
public enum Currency implements StringRepresentable {
	/** The common coin: every trader takes it, and the standard everything else is measured against. */
	EMERALD(1000),
	/** The Common realms' (plains) silver penny. */
	SILVER(500),
	/** The North's (taiga and snow) chopped hacksilver, weighed more than counted. (0.7.490) Four to the emerald, so prices land on whole coins. */
	HACKSILVER(250),
	/** The South's (savanna, desert, badlands) gold dinar. */
	DINAR(2000),
	/** The Wildwood's (jungle and swamp) strings of shells. */
	SHELLS(250),
	/** The Blossom land's copper mon, with the square hole. (0.7.490) Five to the emerald, so prices land on whole coins. */
	MON(200),
	/** The greenskins' bent copper ({@link BentIngots}); two to the emerald, as {@link TribePacts} has always paid. */
	BENT(500),
	/** The pirates' coin: gold in any shape. */
	GOLD(1800),
	/** (0.7.498) The copper bit: the small coin every town takes at par, the change for any price (a tenth of an emerald). */
	BIT(100);

	/** (0.7.498) Saved and sent by {@link #id}. */
	public static final Codec<Currency> CODEC = StringRepresentable.fromEnum(Currency::values);

	/** One coin's worth at the standard rate, in thousandths of an emerald. */
	public final int base;

	Currency(int base) {
		this.base = base;
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	/** Its name's lang key (the coin, in the plural: "silver", "dinars"). */
	public String key() {
		return "currency.jaymoskingdoms." + this.id();
	}

	@Override
	public String getSerializedName() {
		return this.id();
	}

	/** The coin itself. */
	public Item item() {
		return switch (this) {
			case EMERALD -> Items.EMERALD;
			case SILVER -> Currencies.REALM_SILVER;
			case HACKSILVER -> Currencies.HACKSILVER;
			case DINAR -> Currencies.GOLD_DINAR;
			case SHELLS -> Currencies.SHELL_STRING;
			case MON -> Currencies.MON_COIN;
			case BENT -> BentIngots.BENT_COPPER;
			case GOLD -> Items.GOLD_INGOT;
			case BIT -> Currencies.bit();
		};
	}

	/** True for the coins a human settlement can use (everything but the greenskins' and the pirates'); the bit and the emerald too. */
	public boolean human() {
		return this != BENT && this != GOLD;
	}

	/** (0.7.498) A realm's coin: human, and neither the emerald (a gem now) nor the bit (every town's small change). */
	public boolean coin() {
		return this.human() && this != EMERALD && this != BIT;
	}

	/** (0.7.498) What a purse holds and {@code Money.charge} takes: human, and not the emerald (a gem). The bit counts. */
	public boolean money() {
		return this.human() && this != EMERALD;
	}

	/** The currency whose coin is {@code item}, or null. */
	public static @Nullable Currency byItem(Item item) {
		// (0.7.498) the bit's item is looked up when first asked for: nothing is a coin before it is registered
		if (item == Items.AIR) {
			return null;
		}
		for (Currency c : values()) {
			if (c.item() == item) {
				return c;
			}
		}
		return null;
	}
}
