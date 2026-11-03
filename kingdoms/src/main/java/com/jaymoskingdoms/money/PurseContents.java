package com.jaymoskingdoms.money;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

/**
 * (0.7.487) What a coin purse holds: loose coins by kind ({@code jaymoskingdoms:coins}). At most {@value #PURSE_MOST} in
 * all. Whenever it changes, {@link #set} also writes how full the purse is into {@code custom_model_data} (float 0),
 * which {@code items/coin_purse.json} reads to draw a flat or a bulging purse (and Powers draws the same on the hip).
 */
public record PurseContents(int copper, int silver, int gold) {
	/** Coins a purse holds (ten stacks of 64). */
	public static final int PURSE_MOST = 640;
	public static final PurseContents EMPTY = new PurseContents(0, 0, 0);

	public static final Codec<PurseContents> CODEC = RecordCodecBuilder.<PurseContents>create(i -> i.group(
			ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("copper", 0).forGetter(PurseContents::copper),
			ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("silver", 0).forGetter(PurseContents::silver),
			ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("gold", 0).forGetter(PurseContents::gold))
		.apply(i, PurseContents::new))
		.validate(c -> c.coins() <= PURSE_MOST ? DataResult.success(c) : DataResult.error(() -> "A purse holds at most " + PURSE_MOST + " coins"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PurseContents> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, PurseContents::copper, ByteBufCodecs.VAR_INT, PurseContents::silver, ByteBufCodecs.VAR_INT, PurseContents::gold,
		PurseContents::new);

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

	/** A purse holding {@code cp} in the fewest coins (what fits; the rest is dropped, so keep it under {@value #PURSE_MOST} coins). */
	public static ItemStack filled(long cp) {
		return filled(EMPTY.plus(cp).contents());
	}

	/** How many coins of {@code coin}. */
	public int count(Coin coin) {
		return switch (coin) {
			case COPPER -> this.copper;
			case SILVER -> this.silver;
			case GOLD -> this.gold;
		};
	}

	public int coins() {
		return this.copper + this.silver + this.gold;
	}

	public int room() {
		return Math.max(0, PURSE_MOST - coins());
	}

	/** Worth in copper pieces. */
	public long value() {
		return this.copper + (long)this.silver * Coin.SILVER.value() + (long)this.gold * Coin.GOLD.value();
	}

	/** 0 (empty) to 1 (full), by coin count. */
	public float fullness() {
		return Math.min(1.0F, (float)coins() / PURSE_MOST);
	}

	public boolean isEmpty() {
		return coins() == 0;
	}

	/** {@code delta} more (or fewer, if negative) of {@code coin}; never below none, nor over the purse's room. */
	public PurseContents with(Coin coin, int delta) {
		int n = Math.max(-count(coin), Math.min(delta, room()));
		return switch (coin) {
			case COPPER -> new PurseContents(this.copper + n, this.silver, this.gold);
			case SILVER -> new PurseContents(this.copper, this.silver + n, this.gold);
			case GOLD -> new PurseContents(this.copper, this.silver, this.gold + n);
		};
	}

	/** What {@link #plus} made: the new contents, and the copper pieces' worth that didn't fit. */
	public record Added(PurseContents contents, long left) {
	}

	/** {@code cp} more, as the fewest coins that fit (gold first); {@code left} is what didn't. */
	public Added plus(long cp) {
		PurseContents now = this;
		for (int i = Coin.values().length - 1; i >= 0 && cp > 0; i--) {
			Coin c = Coin.values()[i];
			int n = (int)Math.min(cp / c.value(), now.room());
			if (n > 0) {
				now = now.with(c, n);
				cp -= (long)n * c.value();
			}
		}
		return new Added(now, cp);
	}
}
