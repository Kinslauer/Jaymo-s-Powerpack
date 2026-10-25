package com.jaymoskitchen.brewing;

import com.jaymoskitchen.JaymosKitchen;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import org.jspecify.annotations.Nullable;

/**
 * The brewing drop: a cask that ferments ale, cider, mead, berry wine and grape wine, a still that distils them into strong
 * spirits (aqua vitae), and the drunkenness all of it brings ({@link Drunk}). Everything is in-world, right-click.
 */
public final class Brewing {
	public static CaskBlock CASK;
	public static StillBlock STILL;
	public static BlockEntityType<CaskEntity> CASK_ENTITY;
	public static BlockEntityType<StillEntity> STILL_ENTITY;
	/** The fermented drinks, by brew. */
	public static final Map<Brew, Item> DRINKS = new EnumMap<>(Brew.class);
	/** Distilled from any three fermented drinks: Strength for a while, and a great deal of drunkenness. */
	public static Item STRONG_SPIRITS;

	private Brewing() {
	}

	public static void register() {
		List<Item> blockItems = new ArrayList<>();

		CASK = block("cask", CaskBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS).strength(2.5F).sound(SoundType.WOOD)
			.noOcclusion().ignitedByLava());
		blockItems.add(blockItem("cask", CASK));
		STILL = block("still", StillBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_ORANGE).instrument(NoteBlockInstrument.BASEDRUM).strength(3.0F).sound(SoundType.METAL)
			.noOcclusion().requiresCorrectToolForDrops());
		blockItems.add(blockItem("still", STILL));

		Set<Block> casks = new HashSet<>();
		casks.add(CASK);
		CASK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosKitchen.id("cask"),
			new BlockEntityType<>(CaskEntity::new, casks));
		Set<Block> stills = new HashSet<>();
		stills.add(STILL);
		STILL_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosKitchen.id("still"),
			new BlockEntityType<>(StillEntity::new, stills));

		for (Brew brew : Brew.values()) {
			DRINKS.put(brew, makeDrink(brew.id, brew.nutrition, brew.saturation));
		}
		STRONG_SPIRITS = register("strong_spirits", new Item.Properties()
			.stacksTo(16)
			.usingConvertsTo(Items.GLASS_BOTTLE)
			.component(DataComponents.CONSUMABLE, Consumables.DEFAULT_DRINK)
			.component(DataComponents.LORE, new ItemLore(List.of(
				Component.translatable("tooltip.jaymoskitchen.aqua_vitae").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)))));

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			for (Item item : blockItems) {
				output.accept(new ItemStack(item));
			}
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> {
			for (Map.Entry<Brew, Item> entry : DRINKS.entrySet()) {
				if (entry.getKey().baseItem() != Items.AIR) {
					output.accept(new ItemStack(entry.getValue()));
				}
			}
			output.accept(new ItemStack(STRONG_SPIRITS));
		});

		Drunk.register();
	}

	/** The brew a fermented drink is, or null (it isn't one, or it's the spirits). */
	public static @Nullable Brew brewOf(ItemStack stack) {
		for (Map.Entry<Brew, Item> entry : DRINKS.entrySet()) {
			if (stack.is(entry.getValue())) {
				return entry.getKey();
			}
		}
		return null;
	}

	/** The brew a cask makes from this ingredient, or null. */
	public static @Nullable Brew baseOf(ItemStack stack) {
		for (Brew brew : Brew.values()) {
			Item base = brew.baseItem();
			if (base != Items.AIR && stack.is(base)) {
				return brew;
			}
		}
		return stack.is(Items.GLOW_BERRIES) ? Brew.BERRY_WINE : null;
	}

	public static Item drink(Brew brew) {
		return DRINKS.get(brew);
	}

	/** Drunkenness points a drink gives. */
	public static int drunkOf(ItemStack stack) {
		Brew brew = brewOf(stack);
		if (brew != null) {
			return brew.drunk;
		}
		return stack.is(STRONG_SPIRITS) ? Drunk.SPIRITS_DRUNK : 0;
	}

	/** A fermented drink: food that goes through Stomach as a drink, and leaves its glass bottle. */
	private static Item makeDrink(String id, int nutrition, float saturation) {
		FoodProperties food = new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build();
		Item.Properties properties = new Item.Properties();
		properties.food(food, Consumables.DEFAULT_DRINK);
		properties.usingConvertsTo(Items.GLASS_BOTTLE).stacksTo(16);
		return register(id, properties);
	}

	private static Item register(String id, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(properties.setId(key)));
	}

	private static <B extends Block> B block(String name, Function<BlockBehaviour.Properties, B> factory,
		BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKitchen.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	private static Item blockItem(String name, Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, new Item.Properties().useBlockDescriptionPrefix().setId(key)));
	}
}
