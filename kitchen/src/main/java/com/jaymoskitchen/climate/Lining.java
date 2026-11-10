package com.jaymoskitchen.climate;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * A lining sewn into a piece of armour or clothing at a loom (Andy's Hardrock picks, after Cold Sweat's insulation,
 * GPL-3.0, ideas only): wool keeps the cold out, rabbit hide keeps the heat off, leather does a little of both. A lined
 * piece counts in {@link BodyHeat} as that much of a warm or cool piece.
 */
public enum Lining {
	WOOL("wool", 1.0F, 0.0F),
	RABBIT_HIDE("rabbit_hide", 0.0F, 1.0F),
	LEATHER("leather", 0.5F, 0.5F);

	public static final Codec<Lining> CODEC = Codec.STRING.xmap(Lining::byId, lining -> lining.id);
	public static final StreamCodec<ByteBuf, Lining> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(Lining::byId, lining -> lining.id);

	public final String id;
	/** How much of a warm piece ({@link BodyHeat#WARM_PIECE}) and of a cool piece it counts as. */
	public final float warm, cool;

	Lining(String id, float warm, float cool) {
		this.id = id;
		this.warm = warm;
		this.cool = cool;
	}

	public static Lining byId(String id) {
		for (Lining lining : values()) {
			if (lining.id.equals(id)) {
				return lining;
			}
		}
		return WOOL;
	}

	/** The lining {@code stack} sews in, or null. */
	public static @Nullable Lining of(ItemStack stack) {
		if (stack.is(ItemTags.WOOL)) {
			return WOOL;
		}
		if (stack.is(Items.RABBIT_HIDE)) {
			return RABBIT_HIDE;
		}
		if (stack.is(Items.LEATHER)) {
			return LEATHER;
		}
		return null;
	}
}
