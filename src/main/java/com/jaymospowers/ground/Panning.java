package com.jaymospowers.ground;

import com.jaymospowers.JaymosPowers;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * Andy's TFC Hardrock picks, rock and ore: the tools that go with few, big veins. The prospector's pick
 * ({@link ProspectorPickItem}, forged), support beams against cave-ins ({@link SupportBeamBlock}, {@link CaveIns}), and the
 * pan and sluice ({@link PanItem}, {@link SluiceBlock}), an early copper and gold route for the smeltery.
 */
public final class Panning {
	/** What a panful turns up (before a sluice's boost): gold, copper and iron nuggets, and flint. Iron is scarce on purpose. */
	static final float GOLD = 0.06F, COPPER = 0.14F, IRON = 0.02F, FLINT = 0.12F;
	static final int PICK_DURABILITY = 180, PAN_DURABILITY = 120;

	public static Item PROSPECTOR_PICK, PAN;
	public static Block SUPPORT_BEAM, SLUICE;

	private Panning() {
	}

	public static void init() {
		PROSPECTOR_PICK = item("prospector_pick", p -> new ProspectorPickItem(p.durability(PICK_DURABILITY)));
		PAN = item("pan", p -> new PanItem(p.durability(PAN_DURABILITY)));
		SUPPORT_BEAM = block("support_beam", BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F).sound(SoundType.WOOD).ignitedByLava()
			.noOcclusion(), SupportBeamBlock::new);
		SLUICE = block("sluice", BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5F).sound(SoundType.WOOD).ignitedByLava()
			.noOcclusion(), SluiceBlock::new);
		CaveIns.init();
		VeinRumours.init();
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			output.accept(PROSPECTOR_PICK);
			output.accept(PAN);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			output.accept(SUPPORT_BEAM);
			output.accept(SLUICE);
		});
	}

	/** Gravel or sand: what a pan or a sluice washes. */
	static boolean pannable(BlockState state) {
		return state.is(Blocks.GRAVEL) || state.is(BlockTags.SAND);
	}

	/** One panful's find (empty most times); {@code boost} scales every chance. */
	static ItemStack wash(RandomSource random, float boost) {
		float roll = random.nextFloat();
		if ((roll -= GOLD * boost) < 0) {
			return new ItemStack(Items.GOLD_NUGGET);
		}
		if ((roll -= COPPER * boost) < 0) {
			return new ItemStack(Items.COPPER_NUGGET, 1 + random.nextInt(2));
		}
		if ((roll -= IRON * boost) < 0) {
			return new ItemStack(Items.IRON_NUGGET);
		}
		if ((roll -= FLINT * boost) < 0) {
			return new ItemStack(Items.FLINT);
		}
		return ItemStack.EMPTY;
	}

	private static Item item(String name, Function<Item.Properties, Item> make) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, make.apply(new Item.Properties().setId(key)));
	}

	private static Block block(String name, BlockBehaviour.Properties properties, Function<BlockBehaviour.Properties, Block> make) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, make.apply(properties.setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id(name));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}
}
