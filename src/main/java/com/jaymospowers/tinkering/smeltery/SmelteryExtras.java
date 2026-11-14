package com.jaymospowers.tinkering.smeltery;

import com.jaymospowers.JaymosPowers;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * The smeltery's smaller kin, after Tinkers' Construct 3: the seared tank (holds one metal), the melter (a one-block
 * smeltery over a fire) and the channel (carries a faucet's metal along to several casts).
 */
public final class SmelteryExtras {
	public static Block TANK, MELTER, CHANNEL;
	public static BlockEntityType<SearedTankEntity> TANK_ENTITY;
	public static BlockEntityType<MelterEntity> MELTER_ENTITY;
	public static BlockEntityType<ChannelEntity> CHANNEL_ENTITY;

	private SmelteryExtras() {
	}

	public static void register() {
		TANK = block("seared_tank", SearedTankBlock::new, seared().noOcclusion());
		MELTER = block("seared_melter", MelterBlock::new, seared().noOcclusion().lightLevel(state -> state.getValue(MelterBlock.LIT) ? 10 : 0));
		CHANNEL = block("seared_channel", ChannelBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_GRAY).strength(1.5F)
			.sound(SoundType.METAL).noOcclusion());
		TANK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosPowers.id("seared_tank"),
			new BlockEntityType<>(SearedTankEntity::new, Set.of(TANK)));
		MELTER_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosPowers.id("seared_melter"),
			new BlockEntityType<>(MelterEntity::new, Set.of(MELTER)));
		CHANNEL_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosPowers.id("seared_channel"),
			new BlockEntityType<>(ChannelEntity::new, Set.of(CHANNEL)));
	}

	/** For the creative tab. */
	public static List<Block> blocks() {
		return List.of(MELTER, TANK, CHANNEL);
	}

	private static BlockBehaviour.Properties seared() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_GRAY).strength(3.0F, 9.0F).sound(SoundType.METAL)
			.requiresCorrectToolForDrops();
	}

	private static Block block(String id, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(id));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}
}
