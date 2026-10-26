package com.jaymospowers.client.alchemy;

import com.jaymospowers.alchemy.Essence;
import com.jaymospowers.alchemy.Phials;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Item tint source {@code jaymospowers:essence}: the colour of the essence a stack holds (its
 * {@code jaymospowers:essences} component), for the liquid layer of the essence phial. Registered in
 * {@link CodexClient#init()}; {@code default} is used for a stack holding nothing.
 */
public record EssenceTint(int defaultColor) implements ItemTintSource {
	public static final MapCodec<EssenceTint> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		Codec.INT.fieldOf("default").forGetter(EssenceTint::defaultColor)).apply(i, EssenceTint::new));

	@Override
	public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
		Essence main = Phials.contents(stack).main();
		return main == null ? defaultColor : 0xFF000000 | main.colour;
	}

	@Override
	public MapCodec<EssenceTint> type() {
		return MAP_CODEC;
	}
}
