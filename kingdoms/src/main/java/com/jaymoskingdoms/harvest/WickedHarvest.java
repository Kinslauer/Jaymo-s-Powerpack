package com.jaymoskingdoms.harvest;

import com.jaymoskingdoms.JaymosKingdoms;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * The wicked harvest (Seasonal: The Wicked Harvest by Lazurium, MIT; see {@code licenses/seasonal}), our own take, placed in
 * the lore as "The harvest's last fire" in Lore.md. Its parts:
 * <ul>
 * <li>The witch's cauldron ({@link WitchCauldronBlock}): over soul fire, at night, two rotten pumpkins (Kitchen) and the
 * soul of one of the dead (a full soul jar, {@code SoulJars}) are boiled down to wicked resin.</li>
 * <li>The ruined barn ({@code tools/small_designs/wicked_barn.py}) with its altar ({@link HarvestAltarBlock}), which takes
 * two wicked resin and answers only in late autumn, at night, once the harvest is in (Kitchen's calendar).</li>
 * <li>The Harvest Lich ({@link HarvestLich}) on its {@link LichSteed}, with the flying {@link WickedPumpkin}s that shoot
 * {@link WickedOrb}s and the rotten pumpkins it lobs ({@link ThrownRottenPumpkin}). Beaten, it drops Kitchen's Spark of the
 * Harvest, which wakes a scarecrow.</li>
 * </ul>
 */
public final class WickedHarvest {
	/** The lich's colours: its pumpkin-fire, and the pale green of the souls it keeps. */
	public static final int FIRE = 0xFFA640, SOUL = 0x7CFFB0;

	public static EntityType<HarvestLich> LICH;
	public static EntityType<LichSteed> STEED;
	public static EntityType<WickedPumpkin> PUMPKIN;
	public static EntityType<WickedOrb> ORB;
	public static EntityType<ThrownRottenPumpkin> THROWN_PUMPKIN;
	public static Block WITCH_CAULDRON, HARVEST_ALTAR;
	public static Item WITCH_CAULDRON_ITEM, HARVEST_ALTAR_ITEM, WICKED_RESIN;

	private WickedHarvest() {
	}

	public static void register() {
		LICH = type("harvest_lich", EntityType.Builder.of(HarvestLich::new, MobCategory.MONSTER).sized(0.9F, 2.9F).eyeHeight(2.6F).fireImmune()
			.clientTrackingRange(10));
		STEED = type("lich_steed", EntityType.Builder.of(LichSteed::new, MobCategory.MONSTER).sized(1.4F, 1.6F).clientTrackingRange(10));
		PUMPKIN = type("wicked_pumpkin", EntityType.Builder.of(WickedPumpkin::new, MobCategory.MONSTER).sized(0.9F, 0.9F).fireImmune().clientTrackingRange(8));
		ORB = type("wicked_orb", EntityType.Builder.<WickedOrb>of(WickedOrb::new, MobCategory.MISC).sized(0.3F, 0.3F).clientTrackingRange(4).updateInterval(2));
		THROWN_PUMPKIN = type("thrown_rotten_pumpkin", EntityType.Builder.<ThrownRottenPumpkin>of(ThrownRottenPumpkin::new, MobCategory.MISC)
			.sized(0.7F, 0.7F).clientTrackingRange(6).updateInterval(2));
		FabricDefaultAttributeRegistry.register(LICH, HarvestLich.createAttributes());
		FabricDefaultAttributeRegistry.register(STEED, LichSteed.createAttributes());
		FabricDefaultAttributeRegistry.register(PUMPKIN, WickedPumpkin.createAttributes());

		WITCH_CAULDRON = block("witch_cauldron", WitchCauldronBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK)
			.strength(2.0F).sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops());
		HARVEST_ALTAR = block("harvest_altar", HarvestAltarBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK)
			.strength(50.0F, 1200.0F).sound(SoundType.POLISHED_DEEPSLATE).requiresCorrectToolForDrops());
		WITCH_CAULDRON_ITEM = item("witch_cauldron", p -> new BlockItem(WITCH_CAULDRON, p), Rarity.COMMON);
		HARVEST_ALTAR_ITEM = item("harvest_altar", p -> new BlockItem(HARVEST_ALTAR, p), Rarity.RARE);
		WICKED_RESIN = item("wicked_resin", Item::new, Rarity.UNCOMMON);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			output.accept(WITCH_CAULDRON_ITEM);
			output.accept(HARVEST_ALTAR_ITEM);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> output.accept(WICKED_RESIN));
	}

	private static <T extends Entity> EntityType<T> type(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	private static Item item(String name, Function<Item.Properties, Item> make, Rarity rarity) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, make.apply(new Item.Properties().setId(key).rarity(rarity)));
	}

	private static <B extends Block> B block(String name, Function<BlockBehaviour.Properties, B> make, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, make.apply(properties.setId(key)));
	}
}
