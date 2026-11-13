package com.jaymoskitchen.butchery;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** A set of frame bones, named for the animal they were ("Bones of a Cow", "Cleaned Bones of a Cow"): {@link Skeletons}. */
public class BonesItem extends Item {
	public BonesItem(Properties properties) {
		super(properties);
	}

	@Override
	public Component getName(ItemStack stack) {
		Identifier animal = Skeletons.of(stack);
		if (animal == null) {
			return super.getName(stack);
		}
		Component name = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceKey.create(Registries.ENTITY_TYPE, animal))
			.map(EntityType::getDescription).orElse(Component.literal(animal.getPath()));
		return Component.translatable(this.getDescriptionId() + ".of", name);
	}
}
