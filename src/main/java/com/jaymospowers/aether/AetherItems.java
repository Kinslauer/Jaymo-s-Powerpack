package com.jaymospowers.aether;

import com.jaymospowers.JaymosPowers;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.level.block.Block;

/** The Aether's items, and its creative tab ("The Aether"). */
public final class AetherItems {
	// ---- tuning ----
	/** Food: blue berry 2 hunger, white apple 4 (and it clears bad effects, whatever you've eaten). */
	public static final int BLUE_BERRY_FOOD = 2, WHITE_APPLE_FOOD = 4;
	public static final float BLUE_BERRY_SATURATION = 0.4F, WHITE_APPLE_SATURATION = 0.6F;

	public static Item AMBROSIUM_SHARD, ZANITE_GEMSTONE, GOLDEN_AMBER, BLUE_BERRY, WHITE_APPLE;
	/** Aether II's ore items (track N1): arkenium, glint (and its coin: one gemstone is {@link #COINS_PER_GEMSTONE} coins), corrobonite, scatterglass. */
	public static Item INERT_ARKENIUM, GLINT_GEMSTONE, GLINT_COIN, CORROBONITE_CRYSTAL, SCATTERGLASS_SHARD;
	public static final int COINS_PER_GEMSTONE = 4;

	private AetherItems() {
	}

	static void init() {
		AMBROSIUM_SHARD = item("ambrosium_shard", AmbrosiumShardItem::new, new Item.Properties());
		ZANITE_GEMSTONE = item("zanite_gemstone", Item::new, new Item.Properties());
		GOLDEN_AMBER = item("golden_amber", Item::new, new Item.Properties());
		BLUE_BERRY = item("blue_berry", Item::new, new Item.Properties()
			.food(new FoodProperties.Builder().nutrition(BLUE_BERRY_FOOD).saturationModifier(BLUE_BERRY_SATURATION).build(),
				Consumables.defaultFood().build()));
		WHITE_APPLE = item("white_apple", WhiteAppleItem::new, new Item.Properties().rarity(Rarity.UNCOMMON)
			.food(new FoodProperties.Builder().nutrition(WHITE_APPLE_FOOD).saturationModifier(WHITE_APPLE_SATURATION).alwaysEdible().build(),
				Consumables.defaultFood().build()));

		INERT_ARKENIUM = item("inert_arkenium", Item::new, new Item.Properties());
		GLINT_GEMSTONE = item("glint_gemstone", Item::new, new Item.Properties());
		GLINT_COIN = item("glint_coin", Item::new, new Item.Properties());
		CORROBONITE_CRYSTAL = item("corrobonite_crystal", Item::new, new Item.Properties());
		SCATTERGLASS_SHARD = item("scatterglass_shard", Item::new, new Item.Properties());

		ResourceKey<CreativeModeTab> tab = ResourceKey.create(Registries.CREATIVE_MODE_TAB, JaymosPowers.id("aether"));
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, tab, FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.jaymospowers.aether"))
			.icon(() -> new ItemStack(AetherBlocks.AETHER_GRASS_BLOCK))
			.build());
		CreativeModeTabEvents.modifyOutputEvent(tab).register(output -> {
			for (Block block : List.of(AetherBlocks.AETHER_GRASS_BLOCK, AetherBlocks.ENCHANTED_AETHER_GRASS_BLOCK, AetherBlocks.AETHER_DIRT,
				AetherBlocks.QUICKSOIL, AetherBlocks.QUICKSOIL_GLASS, AetherBlocks.HOLYSTONE, AetherBlocks.MOSSY_HOLYSTONE, AetherBlocks.HOLYSTONE_BRICKS,
				AetherBlocks.ICESTONE, AetherBlocks.AMBROSIUM_ORE, AetherBlocks.ZANITE_ORE, AetherBlocks.GRAVITITE_ORE, AetherBlocks.ENCHANTED_GRAVITITE,
				AetherBlocks.AMBROSIUM_BLOCK, AetherBlocks.ZANITE_BLOCK, AetherBlocks.COLD_AERCLOUD, AetherBlocks.BLUE_AERCLOUD, AetherBlocks.GOLDEN_AERCLOUD,
				AetherBlocks.SKYROOT_LOG, AetherBlocks.SKYROOT_WOOD, AetherBlocks.STRIPPED_SKYROOT_LOG, AetherBlocks.STRIPPED_SKYROOT_WOOD,
				AetherBlocks.SKYROOT_PLANKS, AetherBlocks.SKYROOT_LEAVES, AetherBlocks.SKYROOT_SAPLING,
				AetherBlocks.GOLDEN_OAK_LOG, AetherBlocks.GOLDEN_OAK_WOOD, AetherBlocks.GOLDEN_OAK_LEAVES, AetherBlocks.GOLDEN_OAK_SAPLING,
				AetherBlocks.CRYSTAL_LEAVES, AetherBlocks.CRYSTAL_FRUIT_LEAVES, AetherBlocks.BERRY_BUSH, AetherBlocks.BERRY_BUSH_STEM,
				AetherBlocks.WHITE_FLOWER, AetherBlocks.PURPLE_FLOWER, AetherBlocks.AMBROSIUM_TORCH)) {
				output.accept(block);
			}
			for (Item item : List.of(AMBROSIUM_SHARD, ZANITE_GEMSTONE, GOLDEN_AMBER, BLUE_BERRY, WHITE_APPLE,
				INERT_ARKENIUM, GLINT_GEMSTONE, GLINT_COIN, CORROBONITE_CRYSTAL, SCATTERGLASS_SHARD)) {
				output.accept(item);
			}
			// Aether II's stone, soil, ice, ore, cloud and building blocks (track N1)
			for (Block block : AetherBlocks.added()) {
				output.accept(block);
			}
		});
	}

	private static Item item(String id, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}
}
