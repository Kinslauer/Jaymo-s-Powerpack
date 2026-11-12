package com.jaymoskingdoms.hunger;

import com.jaymoskingdoms.JaymosKingdoms;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;

/**
 * The hunger in the cold: the Gaunt ({@link Gaunt}, a monster of legend of the snowy woods), its heart ({@link #HEART}, an
 * alchemy ingredient in Jaymo's Powers: {@code data/jaymospowers/alchemy/essences/hunger_picks.json}) and the curse of the
 * hungry the heart and the Gaunt's bite bring ({@link HungerCurse}). The Gaunt spawns through {@code Encounters} (its row in
 * the table and the biome tag {@code has_gaunt}), is a great foe for {@code BossIntros} (the tag {@code boss_intro}) and
 * for the journal, and is drawn by {@code GauntModel}. Registered from {@code Monsters.register}.
 */
public final class Hunger {
	public static EntityType<Gaunt> GAUNT;
	/** The Gaunt's heart: eaten, it brings the curse of the hungry (to one already cursed, a meal). */
	public static Item HEART;

	private Hunger() {
	}

	public static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("gaunt"));
		GAUNT = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<Gaunt>of(Gaunt::new, MobCategory.MONSTER)
			.sized(0.9F, 2.9F).eyeHeight(2.6F).clientTrackingRange(10).build(key));
		FabricDefaultAttributeRegistry.register(GAUNT, Gaunt.createAttributes());
		ResourceKey<Item> heart = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("gaunts_heart"));
		HEART = Registry.register(BuiltInRegistries.ITEM, heart, new Item(new Item.Properties().setId(heart).rarity(Rarity.RARE)
			.food(new FoodProperties.Builder().nutrition(4).saturationModifier(0.3F).alwaysEdible().build())));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> output.insertAfter(Items.ROTTEN_FLESH, HEART));
		HungerCurse.register();
	}

	/** Used by {@code Encounters}: the Gaunt is a thing of the trees as well as of the open, so the canopy doesn't keep it from spawning. */
	public static boolean gaunt(EntityType<?> type) {
		return type == GAUNT;
	}
}
