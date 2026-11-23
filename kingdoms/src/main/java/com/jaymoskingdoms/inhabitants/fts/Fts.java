package com.jaymoskingdoms.inhabitants.fts;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.beasts.Beasts;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;

/**
 * The mobs and things ported from From the Shadows Reborn (Sonmok14 and L_Ender, AGPL v3; the code here is our own, the art
 * is theirs: licenses/from-the-shadows/). Registered from {@link com.jaymoskingdoms.inhabitants.Inhabitants#register}.
 * The froglin, its vomit and its legs.
 */
public final class Fts {
	public static EntityType<Froglin> FROGLIN;
	public static EntityType<FrogVomit> FROG_VOMIT;
	public static Item FROGLIN_LEG, COOKED_FROGLIN_LEG;

	/** The raw leg: poor food that may turn the stomach (the original's own numbers). */
	static final int RAW_NUTRITION = 2, RAW_HUNGER_TICKS = 500;
	static final float RAW_HUNGER_CHANCE = 0.5F;
	/** The cooked leg. */
	static final int COOKED_NUTRITION = 6;
	static final float COOKED_SATURATION = 0.4F;

	private Fts() {
	}

	public static void register() {
		FROGLIN = Beasts.type("froglin", EntityType.Builder.of(Froglin::new, MobCategory.MONSTER).sized(0.6F, 1.95F).clientTrackingRange(9),
			Froglin.createAttributes());
		ResourceKey<EntityType<?>> vomit = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("froglin_vomit"));
		FROG_VOMIT = Registry.register(BuiltInRegistries.ENTITY_TYPE, vomit, EntityType.Builder.<FrogVomit>of(FrogVomit::new, MobCategory.MISC)
			.noSummon().sized(0.5F, 0.5F).clientTrackingRange(6).updateInterval(10).build(vomit));
		FROGLIN_LEG = item("froglin_leg", p -> new Item(p.food(new FoodProperties.Builder().nutrition(RAW_NUTRITION).build(),
			Consumables.defaultFood().onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.HUNGER, RAW_HUNGER_TICKS, 0),
				RAW_HUNGER_CHANCE)).build())));
		COOKED_FROGLIN_LEG = item("cooked_froglin_leg", p -> new Item(p.food(new FoodProperties.Builder().nutrition(COOKED_NUTRITION)
			.saturationModifier(COOKED_SATURATION).build())));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> {
			output.insertAfter(Items.CHICKEN, FROGLIN_LEG);
			output.insertAfter(Items.COOKED_CHICKEN, COOKED_FROGLIN_LEG);
		});
	}

	private static Item item(String name, Function<Item.Properties, Item> make) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, make.apply(new Item.Properties().setId(key)));
	}
}
