package com.jaymoskitchen.client.delight;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.delight.SkilletItem;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** (From Farmer's Delight, MIT.) The item model test {@code jaymoskitchen:skillet/is_cooking}: whether a pan has food in it. */
public class SkilletCookingProperty implements ConditionalItemModelProperty {
	public static final Identifier ID = JaymosKitchen.id("skillet/is_cooking");
	public static final MapCodec<SkilletCookingProperty> MAP_CODEC = MapCodec.unit(new SkilletCookingProperty());

	@Override
	public boolean get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed, ItemDisplayContext context) {
		return stack.get(SkilletItem.INGREDIENT) != null;
	}

	@Override
	public MapCodec<SkilletCookingProperty> type() {
		return MAP_CODEC;
	}
}
