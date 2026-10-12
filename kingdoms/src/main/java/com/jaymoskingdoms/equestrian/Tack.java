package com.jaymoskingdoms.equestrian;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * (0.5.40) The tack a horse wears beyond vanilla's saddle and armour slots: its bridle and its blanket (put on in the
 * tack screen, {@link TackMenu}). Saved, and synced so every client draws them.
 */
public record Tack(ItemStack bridle, ItemStack blanket) {
	public static final Codec<Tack> CODEC = RecordCodecBuilder.create(i -> i.group(
			ItemStack.OPTIONAL_CODEC.optionalFieldOf("bridle", ItemStack.EMPTY).forGetter(Tack::bridle),
			ItemStack.OPTIONAL_CODEC.optionalFieldOf("blanket", ItemStack.EMPTY).forGetter(Tack::blanket))
		.apply(i, Tack::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, Tack> STREAM_CODEC = StreamCodec.composite(
		ItemStack.OPTIONAL_STREAM_CODEC, Tack::bridle,
		ItemStack.OPTIONAL_STREAM_CODEC, Tack::blanket,
		Tack::new);

	public static final Tack NONE = new Tack(ItemStack.EMPTY, ItemStack.EMPTY);

	public ItemStack get(int slot) {
		return slot == 0 ? this.bridle : this.blanket;
	}

	public Tack with(int slot, ItemStack stack) {
		return slot == 0 ? new Tack(stack, this.blanket) : new Tack(this.bridle, stack);
	}

	public boolean empty() {
		return this.bridle.isEmpty() && this.blanket.isEmpty();
	}
}
