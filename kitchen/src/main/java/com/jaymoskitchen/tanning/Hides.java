package com.jaymoskitchen.tanning;

import com.jaymoskitchen.JaymosKitchen;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.MapColor;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.27) Andy: "Leather needs tanning", after SevTech Ages' (our own code and art):
 * <ol>
 * <li>animals drop a hide, never leather (their loot tables, and butchering on a block);</li>
 * <li>a hide with a work blade ({@link #WORK_BLADES}: the primitive knife and other knives) cuts into raw hide; a
 * rabbit hide gives one;</li>
 * <li>two raw hides, salt and water (a waterskin with a sip in it, or a water bucket) make a salted hide;</li>
 * <li>hung on a drying rack it dries into a dried hide;</li>
 * <li>two dried hides, resin and water make wet tanned hides;</li>
 * <li>hung on the rack again they dry into leather.</li>
 * </ol>
 * Resin is bark ground on the hand grindstone; a work blade on the side of a log peels its bark (an axe's stripping
 * still sometimes does too, {@link Tanning}). Salt: Powers' salt crystals, lying in the water where rocks lie on land.
 * Leather can still be soaked in a bark cauldron into tanned leather for straps ({@link Tanning}). Crafting recipes that
 * keep the blade and the waterskin are {@link BladeRecipe}s.
 */
public final class Hides {
	/** How long a load takes to dry on a rack (4 minutes), while it isn't rained on. */
	public static final int DRY_TICKS = 4800;
	/** How many of one thing a rack holds. */
	public static final int RACK_HOLDS = 4;
	/** Bark a work blade peels off a log. */
	static final int BARK_PER_PEEL = 1;

	/** Knives that cut hides and peel bark ({@code jaymoskitchen:work_blades}). */
	public static final TagKey<Item> WORK_BLADES = TagKey.create(Registries.ITEM, JaymosKitchen.id("work_blades"));

	public static Item HIDE;
	public static Item RAW_HIDE;
	public static Item SALTED_HIDE;
	public static Item DRIED_HIDE;
	public static Item WET_TANNED_HIDE;
	public static Item SALT;
	public static Item RESIN;
	public static DryingRackBlock DRYING_RACK;
	public static BlockEntityType<DryingRackEntity> DRYING_RACK_ENTITY;

	private Hides() {
	}

	public static void register() {
		HIDE = item("hide", new Item.Properties());
		RAW_HIDE = item("raw_hide", new Item.Properties());
		SALTED_HIDE = item("salted_hide", new Item.Properties());
		DRIED_HIDE = item("dried_hide", new Item.Properties());
		WET_TANNED_HIDE = item("wet_tanned_hide", new Item.Properties());
		SALT = item("salt", new Item.Properties());
		RESIN = item("resin", new Item.Properties());

		ResourceKey<Block> rackKey = ResourceKey.create(Registries.BLOCK, JaymosKitchen.id("drying_rack"));
		DRYING_RACK = Registry.register(BuiltInRegistries.BLOCK, rackKey, new DryingRackBlock(BlockBehaviour.Properties.of()
			.mapColor(MapColor.WOOD).strength(1.0F).sound(SoundType.WOOD).noOcclusion().ignitedByLava().setId(rackKey)));
		ResourceKey<Item> rackItem = ResourceKey.create(Registries.ITEM, JaymosKitchen.id("drying_rack"));
		Registry.register(BuiltInRegistries.ITEM, rackItem, new BlockItem(DRYING_RACK, new Item.Properties().setId(rackItem).useBlockDescriptionPrefix()));
		DRYING_RACK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosKitchen.id("drying_rack"),
			new BlockEntityType<>(DryingRackEntity::new, java.util.Set.of(DRYING_RACK)));

		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, JaymosKitchen.id("blade_crafting"), BladeRecipe.SERIALIZER);

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			for (Item item : List.of(HIDE, RAW_HIDE, SALTED_HIDE, DRIED_HIDE, WET_TANNED_HIDE, SALT, RESIN)) {
				output.accept(new ItemStack(item));
			}
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.accept(new ItemStack(DRYING_RACK)));

		// animals give hides, not leather
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			boolean entity = table.unwrapKey().map(key -> key.identifier().getPath().startsWith("entities/")).orElse(false);
			if (entity) {
				drops.replaceAll(Hides::hideFor);
			}
		});

		// a work blade on the side of a log peels its bark
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			ItemStack held = player.getItemInHand(hand);
			if (player.isSpectator() || !held.is(WORK_BLADES) || hit.getDirection().getAxis().isVertical()) {
				return InteractionResult.PASS;
			}
			BlockPos pos = hit.getBlockPos();
			BlockState state = level.getBlockState(pos);
			Block stripped = state.is(BlockTags.LOGS) ? stripped(state) : null;
			if (stripped == null) {
				return InteractionResult.PASS;
			}
			if (level instanceof ServerLevel server) {
				server.setBlock(pos, stripped.withPropertiesOf(state), Block.UPDATE_ALL_IMMEDIATE);
				server.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
				Block.popResourceFromFace(server, pos, hit.getDirection(), new ItemStack(Tanning.BARK, BARK_PER_PEEL));
				server.playSound(null, pos, SoundEvents.AXE_STRIP.value(), SoundSource.BLOCKS, 0.8F, 1.3F);
				held.hurtAndBreak(1, player, hand);
			}
			return InteractionResult.SUCCESS;
		});
	}

	private static Item item(String id, Item.Properties properties) {
		return item(id, Item::new, properties);
	}

	private static Item item(String id, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	/** Leather from an animal becomes that many hides; anything else stays as it is. */
	public static ItemStack hideFor(ItemStack drop) {
		return drop.is(Items.LEATHER) ? new ItemStack(HIDE, drop.getCount()) : drop;
	}

	/** What a stack hung on a drying rack dries into, or null if it doesn't go on one. */
	public static @Nullable ItemStack dried(ItemStack stack) {
		if (stack.is(SALTED_HIDE)) {
			return new ItemStack(DRIED_HIDE);
		}
		if (stack.is(WET_TANNED_HIDE)) {
			return new ItemStack(Items.LEATHER);
		}
		return null;
	}

	/** The stripped block of a log by name ({@code oak_log} to {@code stripped_oak_log}), or null if it's stripped already. */
	private static @Nullable Block stripped(BlockState log) {
		Identifier id = BuiltInRegistries.BLOCK.getKey(log.getBlock());
		if (id.getPath().startsWith("stripped_")) {
			return null;
		}
		return BuiltInRegistries.BLOCK.getOptional(Identifier.fromNamespaceAndPath(id.getNamespace(), "stripped_" + id.getPath())).orElse(null);
	}
}
