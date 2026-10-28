package com.jaymospowers.tinkering.smithing;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.tinkering.forge.Forge;
import com.jaymospowers.tinkering.kiln.Kiln;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * (Smithing) Overgeared's smithing worked into our forging (Overgeared, Copyright (c) phuccom000, MIT; rewritten for
 * 26.3 Fabric): ingots heated on the Forge Hearth ({@link HotMetal}), carried with tongs, hammered out on the Smithing
 * Anvil in a timing game ({@link Forging}) into heads with a {@link Grade} the finished tool keeps ({@link Grades}).
 * docs/Smithing.md has the player-facing rules.
 */
public final class Smithing {
	/** A hammering in progress, carried on the forging blank so it survives a trip back to the hearth. */
	public record Work(String option, int hits, float score, int perfects, long seed) {
		public static final Codec<Work> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("option").forGetter(Work::option),
			Codec.INT.fieldOf("hits").forGetter(Work::hits),
			Codec.FLOAT.fieldOf("score").forGetter(Work::score),
			Codec.INT.fieldOf("perfects").forGetter(Work::perfects),
			Codec.LONG.fieldOf("seed").forGetter(Work::seed)).apply(i, Work::new));

		public Work hit(int result) {
			return new Work(option, hits + 1, score + Forging.score(result), perfects + (result == Forging.PERFECT ? 1 : 0), seed);
		}
	}

	/** How well a head (and the tool made from it) was forged. */
	public static DataComponentType<String> GRADE;
	/** The game time the stack was last in the fire (hot until {@link HotMetal#COOL_TICKS} after). */
	public static DataComponentType<Long> HEATED_AT;
	/** A Hot Ingot's metal (a {@link com.jaymospowers.tinkering.smeltery.Molten} id). */
	public static DataComponentType<String> HOT_METAL;
	/** A forging blank's hammering so far. */
	public static DataComponentType<Work> FORGE_WORK;

	public static Item HOT_INGOT, FORGING_BLANK, WOODEN_TONGS, IRON_TONGS, STEEL_TONGS, COPPER_SMITHING_HAMMER, STEEL_SMITHING_HAMMER;
	public static Block SMITHING_ANVIL;
	public static BlockEntityType<SmithingAnvilEntity> SMITHING_ANVIL_ENTITY;
	public static BlockEntityType<HearthEntity> HEARTH_ENTITY;

	/** Durability of each pair of tongs (Overgeared's) and each hammer. */
	public static final int WOODEN_TONGS_USES = 120, IRON_TONGS_USES = 512, STEEL_TONGS_USES = 1024;
	public static final int COPPER_HAMMER_USES = 250, STEEL_HAMMER_USES = 1000;

	private Smithing() {
	}

	public static void register() {
		GRADE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("forging_grade"),
			DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());
		HEATED_AT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("heated_at"),
			DataComponentType.<Long>builder().persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG).build());
		HOT_METAL = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("hot_metal"),
			DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());
		FORGE_WORK = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("forge_work"),
			DataComponentType.<Work>builder().persistent(Work.CODEC).networkSynchronized(ByteBufCodecs.fromCodec(Work.CODEC)).build());

		HOT_INGOT = item("hot_ingot", HotIngotItem::new, Function.identity());
		FORGING_BLANK = item("forging_blank", ForgingBlankItem::new, p -> p.stacksTo(1));
		WOODEN_TONGS = item("wooden_tongs", TongsItem::new, p -> p.durability(WOODEN_TONGS_USES));
		IRON_TONGS = item("iron_tongs", TongsItem::new, p -> p.durability(IRON_TONGS_USES));
		STEEL_TONGS = item("steel_tongs", TongsItem::new, p -> p.durability(STEEL_TONGS_USES));
		COPPER_SMITHING_HAMMER = item("copper_smithing_hammer", SmithingHammerItem::new, p -> p.durability(COPPER_HAMMER_USES));
		STEEL_SMITHING_HAMMER = item("steel_smithing_hammer", SmithingHammerItem::new, p -> p.durability(STEEL_HAMMER_USES));

		SMITHING_ANVIL = Kiln.blockWithTooltip("smithing_anvil", SmithingAnvilBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.METAL).requiresCorrectToolForDrops().strength(5.0F, 1200.0F).sound(SoundType.ANVIL)
			.pushReaction(PushReaction.IMMOVEABLE));
		SMITHING_ANVIL_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosPowers.id("smithing_anvil"),
			new BlockEntityType<>(SmithingAnvilEntity::new, Set.of(SMITHING_ANVIL, StoneAnvil.create())));
		HEARTH_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosPowers.id("forge_hearth"),
			new BlockEntityType<>(HearthEntity::new, Set.of(Forge.FORGE_HEARTH)));

		SmithingNet.register();
		Grades.register();
		SmithingWider.register();
		Finishing.register();
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				HotMetal.tick(player);
			}
		});
		CreativeModeTabEvents.modifyOutputEvent(Kiln.tab()).register(output -> {
			output.accept(SMITHING_ANVIL);
			output.accept(WOODEN_TONGS);
			output.accept(IRON_TONGS);
			output.accept(STEEL_TONGS);
			output.accept(COPPER_SMITHING_HAMMER);
			output.accept(STEEL_SMITHING_HAMMER);
		});
	}

	static Item item(String id, Function<Item.Properties, Item> factory, Function<Item.Properties, Item.Properties> props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.apply(new Item.Properties().setId(key))));
	}
}
