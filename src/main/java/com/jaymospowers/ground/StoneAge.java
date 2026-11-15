package com.jaymospowers.ground;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.xenoform.XenoFinds;
import com.jaymospowers.tinkering.kiln.Kiln;
import com.jaymospowers.tinkering.smeltery.Melting;
import com.jaymospowers.tinkering.smeltery.Molten;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

/**
 * Andy's GT New Horizons picks, patch 1 (docs/GTNH-Plan.md): the Stone Age recipes. Most of it is data (written by
 * tools/gen_stone_age.py): gravel drops no flint, the first crafting table and chest want flint, doors want fittings,
 * fences want rails, paper is pressed from pulp, torches yield by fuel, clay balls and sand don't smelt. Here:
 * <ul>
 * <li>the sieve: a plant mesh used on gravel sifts it away, and {@link #SIEVE_FLINT} of the time leaves a flint;</li>
 * <li>what the brewing mortar also grinds ({@link #groundInto}): flint, clay, ingots, sugar cane, sticks;</li>
 * <li>the dusts the smeltery melts ({@link Melting#dusts}, faster than the ingot), glass dust into molten glass;</li>
 * <li>the brick form and clay bucket, both fired or kept in the grid ({@link GridToolRecipe}, {@link ClayBucketItem}).</li>
 * </ul>
 */
public final class StoneAge {
	/** A sift of gravel through a plant mesh leaves a flint this often. */
	public static final float SIEVE_FLINT = 0.3F;
	/** One sift in this many wears the plant mesh through. */
	public static final int MESH_WEARS = 8;
	/** Uses of a brick form before it splits. */
	public static final int BRICK_FORM_USES = 32;
	/** A clay bucket is filled and emptied this many times before it cracks. */
	public static final int CLAY_BUCKET_USES = 20;

	/** What a grid tool recipe hands back ({@link GridToolRecipe}): knives, the press's stone slabs, the brick form. */
	public static final TagKey<Item> GRID_TOOLS = TagKey.create(Registries.ITEM, JaymosPowers.id("grid_tools"));

	public static Item FLINT_DUST, CLAY_DUST, BRONZE_DUST, GLASS_DUST, SAWDUST, CANE_CHAD, FENCE_RAIL, BRICK_FORM, UNFIRED_BRICK,
		UNFIRED_CLAY_BUCKET, CLAY_BUCKET, CLAY_WATER_BUCKET, CLAY_MILK_BUCKET;

	/** What the mortar grinds an item into (one for one). */
	private static final Map<Item, Item> GRINDS = new IdentityHashMap<>();

	private StoneAge() {
	}

	/** After {@link FirstTools#init}, {@link XenoFinds} (its metal dusts) and {@link Kiln#register}. */
	public static void init() {
		FLINT_DUST = item("flint_dust", Item::new, Function.identity());
		CLAY_DUST = item("clay_dust", Item::new, Function.identity());
		// copper and tin dust ground together (3:1, in the grid); it melts into bronze once bronze is in the smeltery
		BRONZE_DUST = item("bronze_dust", Item::new, Function.identity());
		GLASS_DUST = item("glass_dust", Item::new, Function.identity());
		SAWDUST = item("sawdust", Item::new, Function.identity());
		CANE_CHAD = item("cane_chad", Item::new, Function.identity());
		FENCE_RAIL = item("fence_rail", Item::new, Function.identity());
		BRICK_FORM = item("brick_form", Item::new, p -> p.durability(BRICK_FORM_USES));
		UNFIRED_BRICK = item("unfired_brick", Item::new, Function.identity());
		UNFIRED_CLAY_BUCKET = item("unfired_clay_bucket", Item::new, p -> p.stacksTo(1));
		CLAY_BUCKET = item("clay_bucket", p -> new ClayBucketItem(Fluids.EMPTY, p), p -> p.durability(CLAY_BUCKET_USES));
		CLAY_WATER_BUCKET = item("clay_water_bucket", p -> new ClayBucketItem(Fluids.WATER, p), p -> p.durability(CLAY_BUCKET_USES));
		CLAY_MILK_BUCKET = item("clay_milk_bucket", ClayMilkBucketItem::new,
			p -> p.durability(CLAY_BUCKET_USES).component(net.minecraft.core.component.DataComponents.CONSUMABLE, Consumables.MILK_BUCKET));
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, JaymosPowers.id("tool_crafting"), GridToolRecipe.SERIALIZER);

		GRINDS.put(Items.FLINT, FLINT_DUST);
		GRINDS.put(Items.CLAY_BALL, CLAY_DUST);
		GRINDS.put(Items.IRON_INGOT, XenoFinds.IRON_DUST);
		GRINDS.put(Items.COPPER_INGOT, XenoFinds.COPPER_DUST);
		GRINDS.put(Items.GOLD_INGOT, XenoFinds.GOLD_DUST);
		GRINDS.put(Items.SUGAR_CANE, CANE_CHAD);
		GRINDS.put(Items.STICK, SAWDUST);

		Melting.dusts(Molten.IRON, Molten.INGOT, XenoFinds.IRON_DUST);
		Melting.dusts(Molten.COPPER, Molten.INGOT, XenoFinds.COPPER_DUST);
		Melting.dusts(Molten.GOLD, Molten.INGOT, XenoFinds.GOLD_DUST);
		// a glass dust is a block of glass's worth
		Melting.dusts(Molten.GLASS, Melting.GLASS_BLOCK_MB, GLASS_DUST);
		Melting.glass();

		Kiln.add(new Kiln.Recipe(s -> s.is(UNFIRED_BRICK), 1, Items.BRICK, 0));
		Kiln.add(new Kiln.Recipe(s -> s.is(UNFIRED_CLAY_BUCKET), 1, CLAY_BUCKET, 0));

		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			ItemStack mesh = player.getItemInHand(hand);
			BlockPos pos = hit.getBlockPos();
			if (player.isSpectator() || !mesh.is(FirstTools.PLANT_MESH) || !level.getBlockState(pos).is(Blocks.GRAVEL)) {
				return InteractionResult.PASS;
			}
			if (level instanceof ServerLevel server) {
				sift(server, player, mesh, pos);
			}
			return InteractionResult.SUCCESS;
		});

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			for (Item item : new Item[] {FLINT_DUST, CLAY_DUST, BRONZE_DUST, GLASS_DUST, SAWDUST, CANE_CHAD, FENCE_RAIL, UNFIRED_BRICK,
				UNFIRED_CLAY_BUCKET}) {
				output.accept(item);
			}
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			output.accept(BRICK_FORM);
			output.accept(CLAY_BUCKET);
			output.accept(CLAY_WATER_BUCKET);
			output.accept(CLAY_MILK_BUCKET);
		});
	}

	/** What the mortar grinds {@code stack} into, or null (then it's a brewing ingredient or nothing it takes). */
	public static @Nullable Item groundInto(ItemStack stack) {
		return stack.isEmpty() ? null : GRINDS.get(stack.getItem());
	}

	/** Every grind, for JEI. */
	public static Map<Item, Item> grinds() {
		return GRINDS;
	}

	/** Gravel shaken through the mesh: the fines fall away, and sometimes a flint is left in it. */
	private static void sift(ServerLevel level, net.minecraft.world.entity.player.Player player, ItemStack mesh, BlockPos pos) {
		BlockState gravel = level.getBlockState(pos);
		level.removeBlock(pos, false);
		level.gameEvent(player, GameEvent.BLOCK_DESTROY, pos);
		level.playSound(null, pos, SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, 0.8F, 1.3F + level.getRandom().nextFloat() * 0.2F);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, gravel), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
			16, 0.3, 0.3, 0.3, 0.05);
		if (level.getRandom().nextFloat() < SIEVE_FLINT) {
			player.getInventory().placeItemBackInInventory(new ItemStack(Items.FLINT), Prediction.SERVER_ONLY);
		}
		if (!player.hasInfiniteMaterials() && level.getRandom().nextInt(MESH_WEARS) == 0) {
			mesh.shrink(1);
			level.playSound(null, pos, SoundEvents.WOOL_BREAK, SoundSource.PLAYERS, 0.6F, 1.2F);
		}
	}

	private static Item item(String id, Function<Item.Properties, Item> make, Function<Item.Properties, Item.Properties> props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, make.apply(props.apply(new Item.Properties().setId(key))));
	}
}
