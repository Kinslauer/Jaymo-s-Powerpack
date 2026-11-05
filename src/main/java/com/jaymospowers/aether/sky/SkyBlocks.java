package com.jaymospowers.aether.sky;

import com.jaymospowers.JaymosPowers;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/** (0.20.96) The Aether sky's blocks (seven-mods plan, patch 10): updraft vents and their air, upfalls, a watched vault's heart. */
public final class SkyBlocks {
	public static Block UPDRAFT_VENT, UPDRAFT, UPFALL, VAULT_HEART;
	public static BlockEntityType<VaultHeartEntity> VAULT_HEART_ENTITY;

	private SkyBlocks() {
	}

	static void init() {
		UPDRAFT_VENT = block("updraft_vent", UpdraftVentBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(1.5F, 6.0F)
			.requiresCorrectToolForDrops(), true);
		UPDRAFT = block("updraft", UpdraftBlock::new, BlockBehaviour.Properties.of().replaceable().noCollision().noLootTable().noOcclusion()
			.pushReaction(PushReaction.POPPED), false);
		UPFALL = block("upfall", UpfallBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WATER).replaceable().noCollision().noLootTable().noOcclusion()
			.strength(100.0F).sound(SoundType.EMPTY).pushReaction(PushReaction.POPPED), false);
		VAULT_HEART = block("vault_heart", VaultHeartBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(-1.0F, 3600000.0F).noLootTable(), false);
		VAULT_HEART_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosPowers.id("vault_heart"),
			new BlockEntityType<>(VaultHeartEntity::new, Set.of(VAULT_HEART)));
		ResourceKey<CreativeModeTab> tab = ResourceKey.create(Registries.CREATIVE_MODE_TAB, JaymosPowers.id("aether"));
		CreativeModeTabEvents.modifyOutputEvent(tab).register(output -> output.accept(UPDRAFT_VENT));
	}

	private static Block block(String id, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties, boolean item) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(id));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
		if (item) {
			ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
			Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		}
		return block;
	}
}
