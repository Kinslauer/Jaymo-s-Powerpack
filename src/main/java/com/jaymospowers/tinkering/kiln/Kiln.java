package com.jaymospowers.tinkering.kiln;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.zfighter.pod.PodPart;
import com.jaymospowers.power.zfighter.pod.PodParts;
import com.jaymospowers.tinkering.Tinkering;
import com.jaymospowers.tinkering.forge.BellowsBlock;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import org.jspecify.annotations.Nullable;

/**
 * The brick kiln, after Better With Mods (Copyright (c) 2016-2018 BeetoGuy, primetoxinz, BordListian, MIT). There's no
 * kiln block: any space walled on {@link #WALLS} or more of its sides and top by bricks (block tag
 * {@code jaymospowers:kiln_walls}) over a fire is a kiln. Right-click into it with something to fire and it's set
 * down there as a load ({@link KilnLoadBlock}, up to {@link #LOAD_MAX}; sneak to put in all you hold); it fires in
 * {@link #STEPS} steps, each {@link #BASE_DELAY} + {@link #HEAT_DELAY} x (steps - heat) / steps ticks, the heat being
 * everything burning in the 3x3 under it. A recipe needs at least its heat in the fire right below. Right-click the load
 * to take it back out.
 */
public final class Kiln {
	/** Brick sides (of the four sides and the top) a kiln needs. */
	public static final int WALLS = 3;
	/** Firing steps, and each step's delay: base, plus this much more the cooler the kiln (never under the least). */
	public static final int STEPS = 8, BASE_DELAY = 20, HEAT_DELAY = 60, MIN_DELAY = 20;
	/** Most items in one load. */
	public static final int LOAD_MAX = 8;
	/** Heat a crucible steel blend needs (a lit forge hearth, soul fire, lava or magma). */
	public static final int STEEL_HEAT = 2;
	/** (0.18.14) Heat a copper block needs to be fired into a pod's thruster bell: the best fire there is, with bellows. */
	public static final int BELL_HEAT = STEEL_HEAT + BellowsBlock.HEAT_BONUS;
	/** Chance a log gives a second charcoal. */
	public static final float KILN_CHARCOAL_BONUS = 0.25F;
	/** (Furnaces) Raw ore and ores (this item tag) take this many times as long in a plain furnace (SlowOreSmeltMixin). */
	public static final int FURNACE_ORE_SLOWDOWN = 4;
	public static final TagKey<Item> SLOW_SMELT = TagKey.create(Registries.ITEM, JaymosPowers.id("slow_smelt"));
	/** Bricks a kiln is built of. */
	public static final TagKey<Block> KILN_WALLS = TagKey.create(Registries.BLOCK, JaymosPowers.id("kiln_walls"));

	public static Block KILN_LOAD, UNFIRED_POT;
	public static BlockEntityType<KilnLoadEntity> KILN_LOAD_ENTITY;

	/** One firing: what goes in, the heat it needs, what comes out, and the chance of one more. */
	public record Recipe(Predicate<ItemStack> input, int heat, Item output, float bonus) {
		/** The fired stack for {@code count} of the input. */
		public ItemStack fire(int count, RandomSource random) {
			int out = count;
			for (int i = 0; i < count; i++) {
				if (bonus > 0 && random.nextFloat() < bonus) {
					out++;
				}
			}
			return new ItemStack(output, out);
		}

		/** How many of the input one load holds (so the fired stack still fits in one stack). */
		public int capacity() {
			return Math.max(1, Math.min(LOAD_MAX, new ItemStack(output).getMaxStackSize() / (bonus > 0 ? 2 : 1)));
		}
	}

	private static List<Recipe> recipes = List.of();

	private Kiln() {
	}

	public static void register() {
		KILN_LOAD = block("kiln_load", KilnLoadBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.CLAY).instabreak()
			.noCollision().noOcclusion().noLootTable().sound(SoundType.GRAVEL).pushReaction(PushReaction.POPPED), false);
		UNFIRED_POT = block("unfired_pot", UnfiredPotBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.CLAY).strength(0.6F)
			.sound(SoundType.GRAVEL).noOcclusion().pushReaction(PushReaction.POPPED), true);
		KILN_LOAD_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosPowers.id("kiln_load"),
			new BlockEntityType<>(KilnLoadEntity::new, Set.of(KILN_LOAD)));
		recipes = List.of(
			new Recipe(s -> s.is(Items.CLAY_BALL), 1, Items.BRICK, 0),
			new Recipe(s -> s.is(Items.CLAY), 1, Items.TERRACOTTA, 0),
			new Recipe(s -> s.is(UNFIRED_POT.asItem()), 1, Items.DECORATED_POT, 0),
			new Recipe(s -> s.is(ItemTags.LOGS_THAT_BURN), 1, Items.CHARCOAL, KILN_CHARCOAL_BONUS),
			new Recipe(s -> s.is(Items.NETHERRACK), 2, Items.NETHER_BRICK, 0),
			new Recipe(s -> s.is(Tinkering.GROUT.asItem()), 2, Tinkering.SEARED_BRICK, 0),
			new Recipe(s -> s.is(Tinkering.STEEL_BLEND), STEEL_HEAT, Tinkering.STEEL_INGOT, 0),
			// (0.18.14) a copper block fired as hot as the kiln gets (a soul fire or hearth with the bellows on it) is a pod's thruster bell
			new Recipe(s -> s.is(Items.COPPER_BLOCK.weathering().unaffected()) || s.is(Items.COPPER_BLOCK.waxed().unaffected()), BELL_HEAT,
				PodParts.item(PodPart.BELL), 0));
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (player.isSpectator() || stack.isEmpty() || recipe(stack) == null || level.getBlockState(hit.getBlockPos()).is(KILN_LOAD)) {
				return InteractionResult.PASS;
			}
			BlockPos space = hit.getBlockPos().relative(hit.getDirection());
			if (!level.isEmptyBlock(space) || !walled(level, space)) {
				return InteractionResult.PASS;
			}
			if (!level.isClientSide()) {
				level.setBlock(space, KILN_LOAD.defaultBlockState(), Block.UPDATE_ALL);
				if (level.getBlockEntity(space) instanceof KilnLoadEntity load) {
					load.insert(stack, player.isShiftKeyDown(), player.hasInfiniteMaterials());
				}
				level.playSound(null, space, SoundEvents.DECORATED_POT_INSERT, SoundSource.BLOCKS, 0.8F, 0.9F);
			}
			return InteractionResult.SUCCESS;
		});
		CreativeModeTabEvents.modifyOutputEvent(tab()).register(output -> output.accept(UNFIRED_POT));
	}

	/** (0.18.14) Every firing, for JEI. */
	public static List<Recipe> recipes() {
		return recipes;
	}

	/** What firing {@code stack} makes, or null if the kiln can't fire it. */
	public static @Nullable Recipe recipe(ItemStack stack) {
		for (Recipe recipe : recipes) {
			if (recipe.input().test(stack)) {
				return recipe;
			}
		}
		return null;
	}

	/** Walled by bricks and over something that burns (lit or not): a load can be set here. */
	public static boolean walled(Level level, BlockPos space) {
		if (!Heat.source(level.getBlockState(space.below()))) {
			return false;
		}
		int walls = 0;
		for (Direction side : Direction.values()) {
			if (side != Direction.DOWN && level.getBlockState(space.relative(side)).is(KILN_WALLS)) {
				walls++;
			}
		}
		return walls >= WALLS;
	}

	/** The heat right under the kiln (what a recipe needs), counting bellows blowing into the kiln itself. */
	public static int heat(Level level, BlockPos space) {
		int heat = Heat.at(level, space.below());
		return heat > 0 && BellowsBlock.blowing(level, space) ? heat + BellowsBlock.HEAT_BONUS : heat;
	}

	/** Ticks one firing step takes: everything burning in the 3x3 under it adds up. */
	public static int stepDelay(Level level, BlockPos space) {
		int sum = BellowsBlock.blowing(level, space) ? BellowsBlock.HEAT_BONUS : 0;
		BlockPos below = space.below();
		for (BlockPos pos : BlockPos.betweenClosed(below.offset(-1, 0, -1), below.offset(1, 0, 1))) {
			sum += Heat.at(level, pos);
		}
		return Math.max(MIN_DELAY, BASE_DELAY + HEAT_DELAY * (STEPS - sum) / STEPS);
	}

	private static Block block(String id, java.util.function.Function<BlockBehaviour.Properties, Block> factory,
		BlockBehaviour.Properties properties, boolean withItem) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(id));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
		if (withItem) {
			ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
			Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()
				.component(DataComponents.LORE, new ItemLore(List.of(
					Component.translatable("tooltip.jaymospowers.tinkering." + id).withStyle(ChatFormatting.GRAY))))));
		}
		return block;
	}

	/** Shared by the forge's blocks: a block and its item, with a one-line grey tooltip. */
	public static Block blockWithTooltip(String id, java.util.function.Function<BlockBehaviour.Properties, Block> factory,
		BlockBehaviour.Properties properties) {
		return block(id, factory, properties, true);
	}

	/** Shared by the forge: the Tinkering tab's key. */
	public static ResourceKey<CreativeModeTab> tab() {
		return ResourceKey.create(Registries.CREATIVE_MODE_TAB, JaymosPowers.id("tinkering"));
	}
}
