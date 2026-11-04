package com.jaymospowers.alchemy.brewing;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.alchemy.Alchemy;
import com.jaymospowers.alchemy.Essence;
import com.jaymospowers.alchemy.EssenceList;
import com.jaymospowers.alchemy.Research;
import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Unit;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * (0.20.13) Brewing quality: the Mortar and Pestle (grinding), graded brews ({@link BrewGrade}) and the Brewer's
 * Alembic. Stirring lives in {@code CauldronStirring}. The plan is docs/Brewing-Quality-Plan.md. Our own version of an
 * idea from Immersive Brewing; none of its code, art or data.
 */
public final class Brewing {
	/** The grade on a bottled brew ({@link BrewGrade#id}). */
	public static DataComponentType<String> BREW_GRADE;
	/** On a brew that has been through the Brewer's Alembic. */
	public static DataComponentType<Unit> BREW_DISTILLED;
	/** On a ground ingredient: 1 Coarse, 2 Fine, 3 Dust. */
	public static DataComponentType<Integer> GROUND;
	public static Block MORTAR, BREWERS_ALEMBIC;
	public static BlockEntityType<MortarEntity> MORTAR_ENTITY;
	public static BlockEntityType<BrewersAlembicEntity> BREWERS_ALEMBIC_ENTITY;
	/** Research that lets you work the Brewer's Alembic. */
	public static final String DISTILLING = "distilling";
	/** What the mortar takes (data; packs can add to it). */
	public static final TagKey<Item> GRINDABLE = TagKey.create(Registries.ITEM, JaymosPowers.id("mortar_grindable"));

	private Brewing() {
	}

	public static void init() {
		BREW_GRADE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("brew_grade"),
			DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());
		BREW_DISTILLED = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("brew_distilled"),
			DataComponentType.<Unit>builder().persistent(Unit.CODEC).networkSynchronized(Unit.STREAM_CODEC).build());
		GROUND = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("ground"),
			DataComponentType.<Integer>builder().persistent(Codec.intRange(1, 3)).networkSynchronized(ByteBufCodecs.VAR_INT).build());

		MORTAR = block("mortar", MortarBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.5F, 6.0F).sound(SoundType.STONE).noOcclusion());
		BREWERS_ALEMBIC = block("brewers_alembic", BrewersAlembicBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(2.0F).sound(SoundType.COPPER).noOcclusion());
		MORTAR_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosPowers.id("mortar"),
			new BlockEntityType<>(MortarEntity::new, Set.of(MORTAR)));
		BREWERS_ALEMBIC_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosPowers.id("brewers_alembic"),
			new BlockEntityType<>(BrewersAlembicEntity::new, Set.of(BREWERS_ALEMBIC)));

		Research.register(new Research.Entry(DISTILLING, Research.Category.CRUCIBLE, () -> BREWERS_ALEMBIC.asItem(),
			EssenceList.of(Essence.WATER, 10, Essence.FIRE, 8).plus(Essence.MERCURY, 6), List.of("alembic"), 0, 0, 2));
		BrewingNet.register();
	}

	/** Registers a block and its item (named with the block prefix, in the Alchemy tab). */
	private static Block block(String id, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(id));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
		Alchemy.item(id, p -> new BlockItem(block, p.useBlockDescriptionPrefix()));
		return block;
	}
}
