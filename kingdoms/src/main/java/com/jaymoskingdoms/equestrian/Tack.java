package com.jaymoskingdoms.equestrian;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

/**
 * (0.5.44) The tack a horse wears beyond vanilla's saddle and armour slots: its bridle and its blanket (put on in the
 * tack screen, {@link TackMenu}). Saved, and synced so every client draws them. (0.7.305) And a banner, flown from a
 * pole behind the saddle; the blanket's place can take a carpet instead, a saddle cloth for show (no warmer for it).
 * (0.7.322) And a caparison: cloth cover over barding, in realm colour with banner patterns.
 */
public record Tack(ItemStack bridle, ItemStack blanket, ItemStack banner, ItemStack caparison) {
	public static final int SLOTS = 4;

	public static final Codec<Tack> CODEC = RecordCodecBuilder.create(i -> i.group(
			ItemStack.OPTIONAL_CODEC.optionalFieldOf("bridle", ItemStack.EMPTY).forGetter(Tack::bridle),
			ItemStack.OPTIONAL_CODEC.optionalFieldOf("blanket", ItemStack.EMPTY).forGetter(Tack::blanket),
			ItemStack.OPTIONAL_CODEC.optionalFieldOf("banner", ItemStack.EMPTY).forGetter(Tack::banner),
			ItemStack.OPTIONAL_CODEC.optionalFieldOf("caparison", ItemStack.EMPTY).forGetter(Tack::caparison))
		.apply(i, Tack::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, Tack> STREAM_CODEC = StreamCodec.composite(
		ItemStack.OPTIONAL_STREAM_CODEC, Tack::bridle,
		ItemStack.OPTIONAL_STREAM_CODEC, Tack::blanket,
		ItemStack.OPTIONAL_STREAM_CODEC, Tack::banner,
		ItemStack.OPTIONAL_STREAM_CODEC, Tack::caparison,
		Tack::new);

	public static final Tack NONE = new Tack(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);

	public Tack(ItemStack bridle, ItemStack blanket) {
		this(bridle, blanket, ItemStack.EMPTY, ItemStack.EMPTY);
	}

	public Tack(ItemStack bridle, ItemStack blanket, ItemStack banner) {
		this(bridle, blanket, banner, ItemStack.EMPTY);
	}

	public ItemStack get(int slot) {
		return switch (slot) {
			case 0 -> this.bridle;
			case 1 -> this.blanket;
			case 2 -> this.banner;
			case 3 -> this.caparison;
			default -> ItemStack.EMPTY;
		};
	}

	public Tack with(int slot, ItemStack stack) {
		return switch (slot) {
			case 0 -> new Tack(stack, this.blanket, this.banner, this.caparison);
			case 1 -> new Tack(this.bridle, stack, this.banner, this.caparison);
			case 2 -> new Tack(this.bridle, this.blanket, stack, this.caparison);
			case 3 -> new Tack(this.bridle, this.blanket, this.banner, stack);
			default -> this;
		};
	}

	public boolean empty() {
		return this.bridle.isEmpty() && this.blanket.isEmpty() && this.banner.isEmpty() && this.caparison.isEmpty();
	}

	/** It wears a real blanket (warm, keeps the rain off), not just a carpet. */
	public boolean warm() {
		return this.blanket.is(Tackroom.HORSE_BLANKET);
	}

	/** Whether {@code stack} can go in the blanket's place: a blanket, or a carpet. */
	public static boolean cloth(ItemStack stack) {
		return stack.is(Tackroom.HORSE_BLANKET) || stack.is(net.minecraft.tags.ItemTags.WOOL_CARPETS) || stack.is(Items.MOSS_CARPET);
	}

	/** Whether {@code stack} can go in the caparison place: a caparison item. */
	public static boolean caparison(ItemStack stack) {
		return stack.is(Tackroom.CAPARISON);
	}
}
