package com.jaymoskingdoms.inhabitants.fts;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.beasts.Beasts;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
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
	public static EntityType<PlagueBearer> PLAGUE_BEARER;
	public static EntityType<PlagueKnife> PLAGUE_KNIFE;
	public static Item SUSPICIOUS_CLOTH, PLAGUE_DOCTOR_MASK;
	public static SoundEvent PLAGUE_BEARER_IDLE, PLAGUE_BEARER_HURT, PLAGUE_BEARER_DEATH, PLAGUE_BEARER_PREATTACK, PLAGUE_BEARER_ATTACK;

	/** The plague doctor's mask: its armour points, its durability (the original's 8 times a helmet's 11), its enchantability. */
	static final int MASK_ARMOR = 3, MASK_DURABILITY = 88, MASK_ENCHANTABILITY = 9;
	static final TagKey<Biome> HAS_PLAGUE_BEARER = TagKey.create(Registries.BIOME, JaymosKingdoms.id("has_plague_bearer"));

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

		PLAGUE_BEARER = Beasts.type("plague_bearer", EntityType.Builder.of(PlagueBearer::new, MobCategory.MONSTER).sized(0.6F, 1.95F).clientTrackingRange(9),
			PlagueBearer.createAttributes());
		ResourceKey<EntityType<?>> knife = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("plague_knife"));
		PLAGUE_KNIFE = Registry.register(BuiltInRegistries.ENTITY_TYPE, knife, EntityType.Builder.<PlagueKnife>of(PlagueKnife::new, MobCategory.MISC)
			.noSummon().sized(0.4F, 0.4F).clientTrackingRange(6).updateInterval(10).build(knife));
		SUSPICIOUS_CLOTH = item("suspicious_cloth", Item::new);
		Identifier maskId = JaymosKingdoms.id("plague_doctor_mask");
		PLAGUE_DOCTOR_MASK = item("plague_doctor_mask", p -> new PlagueDoctorMask(p.durability(MASK_DURABILITY).enchantable(MASK_ENCHANTABILITY)
			.attributes(ItemAttributeModifiers.builder().add(Attributes.ARMOR,
				new AttributeModifier(maskId, MASK_ARMOR, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.HEAD).build())
			.component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD).setEquipSound(SoundEvents.ARMOR_EQUIP_LEATHER)
				.setAsset(ResourceKey.create(EquipmentAssets.ROOT_ID, maskId)).build())
			.repairable(SUSPICIOUS_CLOTH)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> output.insertAfter(Items.LEATHER_BOOTS, PLAGUE_DOCTOR_MASK));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> output.accept(SUSPICIOUS_CLOTH));
		PLAGUE_BEARER_IDLE = sound("plague_bearer.idle");
		PLAGUE_BEARER_HURT = sound("plague_bearer.hurt");
		PLAGUE_BEARER_DEATH = sound("plague_bearer.death");
		PLAGUE_BEARER_PREATTACK = sound("plague_bearer.preattack");
		PLAGUE_BEARER_ATTACK = sound("plague_bearer.attack");
		ServerTickEvents.END_SERVER_TICK.register(PlagueBearer::tickClouds);
		// an exile, now and then, in the open; a camp's own are put there by CampBeasts
		SpawnPlacements.register(PLAGUE_BEARER, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, PlagueBearer::checkSpawn);
		BiomeModifications.addSpawn(BiomeSelectors.tag(HAS_PLAGUE_BEARER), MobCategory.MONSTER, PLAGUE_BEARER, PlagueBearer.SPAWN_WEIGHT, 1, 1);
	}

	private static SoundEvent sound(String name) {
		Identifier id = JaymosKingdoms.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	private static Item item(String name, Function<Item.Properties, Item> make) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, make.apply(new Item.Properties().setId(key)));
	}
}
