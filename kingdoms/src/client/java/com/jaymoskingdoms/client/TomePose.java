package com.jaymoskingdoms.client;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The item model property {@code jaymoskingdoms:tome_pose} the spell tomes' item definitions dispatch on (see
 * {@code tools/blockbench/elemental_tomes.py}): which frame of the book to draw, worked out on the client by
 * {@link TomeClient#pose}. Registered in {@link TomeClient#register}.
 */
record TomePose() implements RangeSelectItemModelProperty {
	static final MapCodec<TomePose> CODEC = MapCodec.unit(new TomePose());

	@Override
	public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
		return TomeClient.pose(owner);
	}

	@Override
	public MapCodec<TomePose> type() {
		return CODEC;
	}
}
