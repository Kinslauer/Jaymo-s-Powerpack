package com.jaymoskingdoms.blossomland;

import com.jaymoskingdoms.JaymosKingdoms;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;

/**
 * What the blossom folk wear and what it is made of (docs/BlossomLands-Plan.md, patch 6; ideas from Andy's Sengoku
 * Jidai picks, the items and art ours).
 * <ul>
 * <li>The <b>straw hat</b> (a wide cone) and the <b>basket hat</b> (a deep woven basket over the whole head): worn on the
 * head and drawn there from their own models, as a carved pumpkin is. The <b>straw cape</b>: worn on the chest.</li>
 * <li><b>Ramie</b>, the fibre crop. It is a perennial, as the real plant is: a grown stand is cut by hand (use it) for
 * its fibre and comes again from the root, {@link #CUT_BACK} grown. Every cut takes from the root, and one in
 * {@link #SPENT_ONE_IN} leaves it spent, starting over from a shoot. Breaking a grown stand gives seed. Three fibres
 * twist into two strings.</li>
 * </ul>
 * Models, textures, recipes, loot and names come from {@code kingdoms/tools/gen_blossom_blocks.py}.
 */
public final class BlossomDress {
	/** The age a cut stand comes again at, and how often the cut leaves the root spent (back to a shoot). */
	static final int CUT_BACK = 3, SPENT_ONE_IN = 5;
	/** Fibre from one cut: at least, and up to this many more. */
	static final int CUT_MIN = 2, CUT_MORE = 2;

	public static Block RAMIE;
	public static Item STRAW_HAT, BASKET_HAT, STRAW_CAPE, RAMIE_SEEDS, RAMIE_FIBRE;

	private BlossomDress() {
	}

	static void register() {
		STRAW_HAT = item("straw_hat", new Item.Properties().stacksTo(1).component(DataComponents.EQUIPPABLE,
			Equippable.builder(EquipmentSlot.HEAD).setEquipSound(SoundEvents.ARMOR_EQUIP_LEATHER).build()));
		BASKET_HAT = item("basket_hat", new Item.Properties().stacksTo(1).component(DataComponents.EQUIPPABLE,
			Equippable.builder(EquipmentSlot.HEAD).setEquipSound(SoundEvents.ARMOR_EQUIP_LEATHER).build()));
		STRAW_CAPE = item("straw_cape", new Item.Properties().stacksTo(1).component(DataComponents.EQUIPPABLE,
			Equippable.builder(EquipmentSlot.CHEST).setEquipSound(SoundEvents.ARMOR_EQUIP_LEATHER)
				.setAsset(ResourceKey.create(EquipmentAssets.ROOT_ID, JaymosKingdoms.id("straw_cape"))).build()));
		ResourceKey<Block> ramie = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id("ramie"));
		RAMIE = Registry.register(BuiltInRegistries.BLOCK, ramie, new Ramie(BlockBehaviour.Properties.of().setId(ramie).mapColor(MapColor.PLANT)
			.noCollision().randomTicks().instabreak().sound(SoundType.CROP).pushReaction(PushReaction.POPPED)));
		ResourceKey<Item> seeds = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("ramie_seeds"));
		RAMIE_SEEDS = Registry.register(BuiltInRegistries.ITEM, seeds, new BlockItem(RAMIE, new Item.Properties().setId(seeds).useItemDescriptionPrefix()));
		BlossomBlocks.ITEMS.add(RAMIE_SEEDS);
		RAMIE_FIBRE = item("ramie_fibre", new Item.Properties());
	}

	private static Item item(String name, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		Item item = Registry.register(BuiltInRegistries.ITEM, key, new Item(properties.setId(key)));
		BlossomBlocks.ITEMS.add(item);
		return item;
	}

	/** Ramie: a crop that is cut and comes again. */
	static final class Ramie extends CropBlock {
		Ramie(BlockBehaviour.Properties properties) {
			super(properties);
		}

		@Override
		protected ItemLike getBaseSeedId() {
			return RAMIE_SEEDS;
		}

		@Override
		protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
			if (!this.isMaxAge(state)) {
				return InteractionResult.PASS;
			}
			if (!level.isClientSide()) {
				Block.popResource(level, pos, new ItemStack(RAMIE_FIBRE, CUT_MIN + level.getRandom().nextInt(CUT_MORE + 1)));
				boolean spent = level.getRandom().nextInt(SPENT_ONE_IN) == 0;
				level.setBlock(pos, this.getStateForAge(spent ? 0 : CUT_BACK), Block.UPDATE_CLIENTS);
				level.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
			}
			return InteractionResult.SUCCESS;
		}
	}
}
