package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.money.PurseContents;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.568) The item model property {@code jaymoskingdoms:purse_coins} that {@code items/coin_purse.json} dispatches on: how
 * many coins the purse holds, read straight from its {@code jaymoskingdoms:coins} component. (It used to read the purse's
 * fullness out of 640 from custom_model_data, so a purse of a few dozen coins never looked any fuller.) Powers draws the
 * purse on the hip with the same model, so it bulges there too.
 */
record PurseCoins() implements RangeSelectItemModelProperty {
	static final MapCodec<PurseCoins> CODEC = MapCodec.unit(new PurseCoins());

	static void register() {
		RangeSelectItemModelProperties.ID_MAPPER.put(JaymosKingdoms.id("purse_coins"), CODEC);
	}

	@Override
	public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
		return PurseContents.of(stack).coins();
	}

	@Override
	public MapCodec<PurseCoins> type() {
		return CODEC;
	}
}
