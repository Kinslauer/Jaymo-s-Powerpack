package com.jaymospowers.tinkering.smeltery;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jaymospowers.JaymosPowers;
import com.jaymospowers.tinkering.TinkerToolItem;
import com.jaymospowers.tinkering.ToolPartItem;
import com.jaymospowers.tinkering.WeaponPartItem;
import com.jaymospowers.tinkering.WeaponParts;
import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.block.WeatheringCopperCollection;
import org.jspecify.annotations.Nullable;

/**
 * (0.13) Andy: every metal item in the pack melts back into the metals it was made of. On server start and after every
 * data pack reload this works out, on top of {@link Melting}'s own table:
 * <ul>
 * <li>data files {@code data/<namespace>/tinkering_melting/*.json}, for other mods' uncraftable metal (no code
 * dependency): {@code {"items": {"mod:item": {"iron": 360, "gold": 90}}}} in mB (an ingot is 90);</li>
 * <li>forged weapons (Armory's, Balkon's): the metal of the head they're put together from;</li>
 * <li>anything crafted, smithed or stonecut from metal: the metal of its ingredients over the count it makes, worked
 * through chains (iron, then minecart, then hopper minecart) until nothing changes.</li>
 * </ul>
 * Against dupes: an item made several ways melts by the cheapest; amounts round down; ingots, nuggets, blocks, raw
 * metal and ores never derive (their table entries stand); an ingredient counts only if everything it accepts melts
 * (else the cheapest it accepts is nothing), and never if it's handed back (a frying pan). The result goes to every
 * player so the smeltery screen and JEI agree with the server.
 */
public final class MeltingRecipes {
	/** Log each metal-sounding item that still doesn't melt when a server starts (a dev aid for Andy). */
	static final boolean LOG_GAPS = true;
	/** Words in an item's id that make it sound metal, for the gap log. */
	static final List<String> METAL_WORDS = List.of("iron", "gold", "golden", "copper", "silver", "steel", "chain",
		"chainmail", "netherite", "bronze");
	/** Passes at most through the recipes (chains are rarely more than five deep). */
	static final int MAX_PASSES = 32;
	/** Where data files live, under data/<namespace>/. */
	static final String DATA_FOLDER = "tinkering_melting";

	/** Item tags of the base metal forms, which never derive. */
	private static final List<TagKey<Item>> BASE_FORMS = List.of(tag("ingots"), tag("nuggets"), tag("storage_blocks"),
		tag("raw_materials"), tag("raw_blocks"), tag("ores"));
	/** Vanilla copper sets whose aged and waxed kinds melt like the fresh one. */
	private static final List<WeatheringCopperCollection<Item>> COPPER_SETS = List.of(Items.CHISELED_COPPER,
		Items.CUT_COPPER, Items.CUT_COPPER_STAIRS, Items.CUT_COPPER_SLAB, Items.COPPER_BARS, Items.COPPER_CHAIN,
		Items.LIGHTNING_ROD, Items.COPPER_DOOR, Items.COPPER_TRAPDOOR, Items.COPPER_LANTERN, Items.COPPER_GRATE,
		Items.COPPER_BULB, Items.COPPER_CHEST);
	private static final Map<Molten, Integer> NONE = Map.of();

	private MeltingRecipes() {
	}

	/** One way of making an item: what it makes, how many, and what each ingredient slot accepts. */
	private record Made(Item result, int count, List<List<Item>> ingredients) {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Sync.TYPE, Sync.CODEC);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			rebuild(server, server.getResourceManager());
			if (LOG_GAPS) {
				logGaps();
			}
		});
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
			if (success) {
				rebuild(server, resources);
				for (ServerPlayer player : PlayerLookup.all(server)) {
					send(player);
				}
			}
		});
		ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) -> {
			if (joined) {
				send(player);
			}
		});
	}

	private static void send(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, Sync.TYPE)) {
			ServerPlayNetworking.send(player, new Sync(Melting.extra()));
		}
	}

	/** Works the extras out again from the data files, the forged weapons and the recipes. */
	static void rebuild(MinecraftServer server, ResourceManager resources) {
		Map<Item, Map<Molten, Integer>> fixed = new IdentityHashMap<>();
		readFiles(resources, fixed);
		forgedWeapons(fixed);
		Map<Item, Map<Molten, Integer>> builtIn = new IdentityHashMap<>();
		List<Made> made = new ArrayList<>();
		for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
			try {
				Made one = made(holder.value());
				if (one != null && derivable(one.result(), fixed, builtIn)) {
					made.add(one);
				}
			} catch (RuntimeException e) {
				JaymosPowers.LOGGER.debug("Melting: skipped recipe {}", holder.id().identifier(), e);
			}
		}
		// aged copper (no recipe) melts like the fresh kind it aged from
		for (WeatheringCopperCollection<Item> set : COPPER_SETS) {
			Item fresh = set.weathering().unaffected();
			for (Item item : set.asList()) {
				if (item != fresh && derivable(item, fixed, builtIn)) {
					made.add(new Made(item, 1, List.of(List.of(fresh))));
				}
			}
		}
		Map<Item, Map<Molten, Integer>> derived = derive(made, fixed, builtIn);

		Map<Item, Melting.Result> extra = new IdentityHashMap<>();
		derived.forEach((item, metals) -> put(extra, item, metals));
		fixed.forEach((item, metals) -> put(extra, item, metals));
		Melting.setExtra(extra);
		JaymosPowers.LOGGER.info("Melting: {} items from data files and forged weapons, {} from recipes", fixed.size(), derived.size());
	}

	private static void put(Map<Item, Melting.Result> extra, Item item, Map<Molten, Integer> metals) {
		Melting.Result result = Melting.Result.of(metals);
		if (result != null) {
			extra.put(item, result);
		}
	}

	// --- data files and weapons ---

	private static void readFiles(ResourceManager resources, Map<Item, Map<Molten, Integer>> fixed) {
		Map<Identifier, Resource> files = resources.listResources(DATA_FOLDER, id -> id.getPath().endsWith(".json"));
		for (Map.Entry<Identifier, Resource> file : files.entrySet()) {
			try (Reader reader = file.getValue().openAsReader()) {
				JsonObject items = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("items");
				for (Map.Entry<String, JsonElement> entry : items.entrySet()) {
					Identifier id = Identifier.tryParse(entry.getKey());
					Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
					if (item == null) {
						// its mod isn't loaded
						continue;
					}
					Map<Molten, Integer> metals = new EnumMap<>(Molten.class);
					for (Map.Entry<String, JsonElement> liquid : entry.getValue().getAsJsonObject().entrySet()) {
						Molten molten = Molten.byId(liquid.getKey());
						if (molten == null) {
							JaymosPowers.LOGGER.warn("Melting: unknown liquid '{}' for {} in {}", liquid.getKey(), id, file.getKey());
						} else {
							metals.put(molten, liquid.getValue().getAsInt());
						}
					}
					fixed.put(item, metals);
				}
			} catch (Exception e) {
				JaymosPowers.LOGGER.warn("Melting: couldn't read {}", file.getKey(), e);
			}
		}
	}

	/** Armory's and Balkon's weapons are forged from a head (their recipes are off), so they melt into its metal. */
	private static void forgedWeapons(Map<Item, Map<Molten, Integer>> fixed) {
		for (WeaponParts.Kind kind : WeaponParts.KINDS.values()) {
			for (WeaponParts.Metal metal : WeaponParts.Metal.values()) {
				Item weapon = metal.molten == null ? null : kind.in(metal);
				if (weapon != null && !fixed.containsKey(weapon) && Melting.builtIn(new ItemStack(weapon)) == null) {
					fixed.put(weapon, Map.of(metal.molten, kind.mb() / kind.count()));
				}
			}
		}
	}

	// --- recipes ---

	/** A crafting, smithing or stonecutting recipe as ingredients and a result, or null if it isn't one we read. */
	private static @Nullable Made made(Recipe<?> recipe) {
		Object type = recipe.getType();
		if (type != RecipeType.CRAFTING && type != RecipeType.SMITHING && type != RecipeType.STONECUTTING || recipe.isSpecial()) {
			return null;
		}
		PlacementInfo placement = recipe.placementInfo();
		if (placement.isImpossibleToPlace()) {
			return null;
		}
		Item result = null;
		int count = 0;
		for (RecipeDisplay display : recipe.display()) {
			SlotDisplay shown = display.result();
			if (shown instanceof SlotDisplay.ItemStackSlotDisplay stack) {
				result = stack.stack().item().value();
				count = stack.stack().count();
			} else if (shown instanceof SlotDisplay.ItemSlotDisplay item) {
				result = item.item().value();
				count = 1;
			}
			if (result != null) {
				break;
			}
		}
		if (result == null || result == Items.AIR || count <= 0) {
			return null;
		}
		List<List<Item>> ingredients = new ArrayList<>();
		for (Ingredient ingredient : placement.ingredients()) {
			List<Item> accepts = ingredient.items().map(Holder::value).toList();
			if (accepts.contains(result)) {
				// dyeing or re-making the same item: it doesn't say what the item is made of
				return null;
			}
			ingredients.add(accepts);
		}
		return new Made(result, count, ingredients);
	}

	/** Whether this item may get its melting from recipes (it doesn't already melt, and isn't a base metal form). */
	private static boolean derivable(Item item, Map<Item, Map<Molten, Integer>> fixed, Map<Item, Map<Molten, Integer>> builtIn) {
		if (fixed.containsKey(item) || builtIn(item, builtIn) != null || item instanceof ToolPartItem
			|| item instanceof WeaponPartItem || item instanceof TinkerToolItem) {
			return false;
		}
		String path = BuiltInRegistries.ITEM.getKey(item).getPath();
		if (path.endsWith("_ingot") || path.endsWith("_nugget") || path.startsWith("raw_")) {
			return false;
		}
		for (TagKey<Item> tag : BASE_FORMS) {
			if (item.builtInRegistryHolder().is(tag)) {
				return false;
			}
		}
		return true;
	}

	/** The metal an item melts into by the built-in table (cached; null if none, metal only). */
	private static @Nullable Map<Molten, Integer> builtIn(Item item, Map<Item, Map<Molten, Integer>> cache) {
		Map<Molten, Integer> metals = cache.get(item);
		if (metals == null) {
			metals = new EnumMap<>(Molten.class);
			if (!(item instanceof ToolPartItem) && !(item instanceof WeaponPartItem)) {
				Melting.Result result = Melting.builtIn(new ItemStack(item));
				if (result != null) {
					for (Melting.Input part : result.parts()) {
						if (part.molten().metal()) {
							metals.merge(part.molten(), part.amount(), Integer::sum);
						}
					}
				}
			}
			cache.put(item, metals);
		}
		return metals.isEmpty() ? null : metals;
	}

	/** Goes through the recipes until nothing changes; each item takes its cheapest recipe. */
	private static Map<Item, Map<Molten, Integer>> derive(List<Made> made, Map<Item, Map<Molten, Integer>> fixed,
		Map<Item, Map<Molten, Integer>> builtIn) {
		Map<Item, Map<Molten, Integer>> derived = new IdentityHashMap<>();
		for (int pass = 0; pass < MAX_PASSES; pass++) {
			Map<Item, Map<Molten, Integer>> next = new IdentityHashMap<>();
			Map<Item, Integer> totals = new IdentityHashMap<>();
			for (Made one : made) {
				Map<Molten, Integer> sum = new EnumMap<>(Molten.class);
				for (List<Item> accepts : one.ingredients()) {
					cheapest(accepts, fixed, builtIn, derived).forEach((molten, amount) -> sum.merge(molten, amount, Integer::sum));
				}
				Map<Molten, Integer> each = new EnumMap<>(Molten.class);
				int total = 0;
				for (Map.Entry<Molten, Integer> entry : sum.entrySet()) {
					int amount = entry.getValue() / one.count();
					if (amount > 0) {
						each.put(entry.getKey(), amount);
						total += amount;
					}
				}
				Integer best = totals.get(one.result());
				if (best == null || total < best) {
					totals.put(one.result(), total);
					next.put(one.result(), each);
				}
			}
			next.values().removeIf(Map::isEmpty);
			if (next.equals(derived)) {
				break;
			}
			derived = next;
		}
		return derived;
	}

	/** The least metal one ingredient slot can be filled with (nothing if it accepts anything that doesn't melt). */
	private static Map<Molten, Integer> cheapest(List<Item> accepts, Map<Item, Map<Molten, Integer>> fixed,
		Map<Item, Map<Molten, Integer>> builtIn, Map<Item, Map<Molten, Integer>> derived) {
		Map<Molten, Integer> best = null;
		int bestTotal = Integer.MAX_VALUE;
		for (Item item : accepts) {
			if (item.getCraftingRemainder(new ItemStack(item)) != null) {
				// handed back after crafting (a bucket, a frying pan): its metal isn't in the result
				return NONE;
			}
			Map<Molten, Integer> metals = fixed.get(item);
			if (metals == null) {
				metals = builtIn(item, builtIn);
			}
			if (metals == null) {
				metals = derived.getOrDefault(item, NONE);
			}
			int total = 0;
			for (Map.Entry<Molten, Integer> entry : metals.entrySet()) {
				total += entry.getKey().metal() ? entry.getValue() : 0;
			}
			if (total == 0) {
				return NONE;
			}
			if (total < bestTotal) {
				best = metals;
				bestTotal = total;
			}
		}
		if (best == null) {
			return NONE;
		}
		Map<Molten, Integer> metal = new EnumMap<>(Molten.class);
		best.forEach((molten, amount) -> {
			if (molten.metal()) {
				metal.put(molten, amount);
			}
		});
		return metal;
	}

	// --- the gap log ---

	/** One line for each metal-sounding item that still melts into nothing. */
	private static void logGaps() {
		int gaps = 0;
		for (Item item : BuiltInRegistries.ITEM) {
			Identifier id = BuiltInRegistries.ITEM.getKey(item);
			if (item instanceof ToolPartItem || item instanceof WeaponPartItem || item instanceof TinkerToolItem
				|| METAL_WORDS.stream().noneMatch(id.getPath()::contains) || Melting.of(new ItemStack(item)) != null) {
				continue;
			}
			JaymosPowers.LOGGER.info("Melting gap: {} doesn't melt", id);
			gaps++;
		}
		JaymosPowers.LOGGER.info("Melting: {} metal-sounding items don't melt (LOG_GAPS in MeltingRecipes)", gaps);
	}

	private static TagKey<Item> tag(String path) {
		return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", path));
	}

	/** Server → client: the extras, so the smeltery's slots and JEI know what melts. */
	public record Sync(Map<Item, Melting.Result> items) implements CustomPacketPayload {
		public static final Type<Sync> TYPE = new Type<>(JaymosPowers.id("melting"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = CustomPacketPayload.codec(Sync::write, Sync::read);

		private void write(RegistryFriendlyByteBuf buf) {
			buf.writeVarInt(items.size());
			items.forEach((item, result) -> {
				buf.writeIdentifier(BuiltInRegistries.ITEM.getKey(item));
				buf.writeVarInt(result.parts().size());
				for (Melting.Input part : result.parts()) {
					buf.writeUtf(part.molten().id);
					buf.writeVarInt(part.amount());
				}
			});
		}

		private static Sync read(RegistryFriendlyByteBuf buf) {
			Map<Item, Melting.Result> items = new IdentityHashMap<>();
			int size = buf.readVarInt();
			for (int i = 0; i < size; i++) {
				Item item = BuiltInRegistries.ITEM.getOptional(buf.readIdentifier()).orElse(null);
				Map<Molten, Integer> metals = new EnumMap<>(Molten.class);
				int parts = buf.readVarInt();
				for (int j = 0; j < parts; j++) {
					Molten molten = Molten.byId(buf.readUtf());
					int amount = buf.readVarInt();
					if (molten != null) {
						metals.put(molten, amount);
					}
				}
				Melting.Result result = Melting.Result.of(metals);
				if (item != null && result != null) {
					items.put(item, result);
				}
			}
			return new Sync(items);
		}

		@Override
		public Type<Sync> type() {
			return TYPE;
		}
	}
}
