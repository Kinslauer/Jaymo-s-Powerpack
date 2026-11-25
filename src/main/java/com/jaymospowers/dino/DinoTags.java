package com.jaymospowers.dino;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

public final class DinoTags {
	public static final TagKey<Item> CARNIVORE_FOOD = TagKey.create(Registries.ITEM, Dino.id("carnivore_food"));
	public static final TagKey<EntityType<?>> DINOSAURS = TagKey.create(Registries.ENTITY_TYPE, Dino.id("dinosaurs"));
	public static final TagKey<Item> HERBIVORE_FOOD = TagKey.create(Registries.ITEM, Dino.id("herbivore_food"));

	private DinoTags() {
	}
}
