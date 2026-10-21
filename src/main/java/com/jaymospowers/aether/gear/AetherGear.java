package com.jaymospowers.aether.gear;

import com.jaymospowers.JaymosPowers;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * The Aether's gear (docs/aether.md, "Aether II gear"): tools and hammers of five metals, zanite shears, six armour sets with
 * set bonuses, the four aercloud gliders, enchanted fruit and the altar that repairs and enchants them. All item ids are the
 * ones tools/gen_aether_gear.py paints and writes recipes for.
 */
public final class AetherGear {
	// ---- tuning ----
	/** Enchanted fruit: hunger, saturation, and the regeneration (seconds, level - 1) each gives. */
	public static final int ENCHANTED_FOOD = 6, ENCHANTED_REGEN_SECONDS = 6, ENCHANTED_REGEN_LEVEL = 1;
	public static final float ENCHANTED_SATURATION = 1.0F;
	/** The altar: hardness and blast resistance. */
	public static final float ALTAR_HARDNESS = 3.0F, ALTAR_BLAST = 6.0F;

	/** What each tier makes, by kind. */
	public static final Map<GearTier, Item> SHORTSWORD = new EnumMap<>(GearTier.class), HAMMER = new EnumMap<>(GearTier.class),
		PICKAXE = new EnumMap<>(GearTier.class), AXE = new EnumMap<>(GearTier.class), SHOVEL = new EnumMap<>(GearTier.class),
		HOE = new EnumMap<>(GearTier.class);
	public static Item ARKENIUM_PLATE, ZANITE_SHEARS;
	public static Item ENCHANTED_BLUE_BERRY, ENCHANTED_ORANGE, ENCHANTED_WYNDBERRY;
	public static final Map<AercloudGliderItem.Kind, Item> GLIDERS = new EnumMap<>(AercloudGliderItem.Kind.class);
	public static Block ALTAR;
	public static BlockEntityType<AltarBlockEntity> ALTAR_ENTITY;
	public static MenuType<AltarMenu> ALTAR_MENU;
	/** Every item of the gear, for the creative tab and the altar's repair table. */
	private static final List<Item> ALL = new ArrayList<>();

	private AetherGear() {
	}

	public static void init() {
		ARKENIUM_PLATE = item("arkenium_plate", Item::new, p -> p);
		for (GearTier tier : GearTier.values()) {
			tools(tier);
		}
		ZANITE_SHEARS = item("zanite_shears", ShearsItem::new, p -> p.durability(GearTier.SHEARS_DURABILITY)
			.component(DataComponents.TOOL, ShearsItem.createToolProperties()).enchantable(GearTier.ZANITE.material.enchantmentValue())
			.repairable(GearTier.ZANITE.materials));
		for (ArmorSet set : ArmorSet.values()) {
			for (int i = 0; i < 4; i++) {
				int slot = i;
				set.pieces[i] = item(set.id + "_" + ArmorSet.NAMES[i], Item::new, p -> p.humanoidArmor(set.material, ArmorSet.TYPES[slot]));
			}
		}
		for (AercloudGliderItem.Kind kind : AercloudGliderItem.Kind.values()) {
			GLIDERS.put(kind, item(kind.id + "_aercloud_glider", p -> new AercloudGliderItem(p, kind), p -> p.durability(kind.durability)));
		}
		ENCHANTED_BLUE_BERRY = enchanted("enchanted_blue_berry");
		ENCHANTED_ORANGE = enchanted("enchanted_orange");
		ENCHANTED_WYNDBERRY = enchanted("enchanted_wyndberry");

		registerAltar();
		AltarRecipes.init();
		ArmorAbilities.init();
		GravititeTools.init();
		AercloudGliderItem.init();

		ResourceKey<CreativeModeTab> tab = ResourceKey.create(Registries.CREATIVE_MODE_TAB, JaymosPowers.id("aether"));
		CreativeModeTabEvents.modifyOutputEvent(tab).register(output -> {
			output.accept(ALTAR);
			for (Item item : ALL) {
				output.accept(item);
			}
		});
	}

	/** Every item of the gear, in creative-tab order. */
	public static List<Item> all() {
		return ALL;
	}

	private static void tools(GearTier tier) {
		SHORTSWORD.put(tier, item(tier.id + "_shortsword", Item::new, p -> p.sword(tier.material, GearTier.SWORD_DAMAGE, GearTier.SWORD_SPEED)));
		HAMMER.put(tier, item(tier.id + "_hammer", Item::new, p -> p.sword(tier.material, GearTier.HAMMER_DAMAGE, GearTier.HAMMER_SPEED)));
		// gravitite's tools lift the block they are used on
		Function<Item.Properties, Item> factory = tier == GearTier.GRAVITITE ? GravititeToolItem::new : Item::new;
		PICKAXE.put(tier, item(tier.id + "_pickaxe", factory, p -> p.pickaxe(tier.material, GearTier.PICKAXE_DAMAGE, GearTier.PICKAXE_SPEED)));
		AXE.put(tier, item(tier.id + "_axe", factory, p -> p.axe(tier.material, GearTier.AXE_DAMAGE, GearTier.AXE_SPEED)));
		SHOVEL.put(tier, item(tier.id + "_shovel", factory, p -> p.shovel(tier.material, GearTier.SHOVEL_DAMAGE, GearTier.SHOVEL_SPEED)));
		HOE.put(tier, item(tier.id + "_hoe", factory, p -> p.hoe(tier.material, -tier.material.attackDamageBonus(), tier.hoeSpeed)));
	}

	private static Item enchanted(String id) {
		return item(id, Item::new, p -> p.rarity(Rarity.RARE).food(
			new FoodProperties.Builder().nutrition(ENCHANTED_FOOD).saturationModifier(ENCHANTED_SATURATION).build(),
			Consumables.defaultFood().onConsume(new ApplyStatusEffectsConsumeEffect(
				new MobEffectInstance(MobEffects.REGENERATION, ENCHANTED_REGEN_SECONDS * 20, ENCHANTED_REGEN_LEVEL))).build()));
	}

	private static void registerAltar() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("altar"));
		ALTAR = Registry.register(BuiltInRegistries.BLOCK, key, new AltarBlock(BlockBehaviour.Properties.of().setId(key).mapColor(MapColor.QUARTZ)
			.strength(ALTAR_HARDNESS, ALTAR_BLAST).sound(SoundType.STONE).requiresCorrectToolForDrops().noOcclusion()));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id("altar"));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(ALTAR, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		ALTAR_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosPowers.id("altar"),
			new BlockEntityType<>(AltarBlockEntity::new, Set.of(ALTAR)));
		ALTAR_MENU = Registry.register(BuiltInRegistries.MENU, JaymosPowers.id("altar"), new MenuType<>(AltarMenu::new, FeatureFlags.VANILLA_SET));
	}

	private static Item item(String id, Function<Item.Properties, Item> factory, Function<Item.Properties, Item.Properties> customize) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		Item item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(customize.apply(new Item.Properties().setId(key))));
		ALL.add(item);
		return item;
	}
}
