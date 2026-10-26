package com.jaymospowers.alchemy.essence;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jaymospowers.JaymosPowers;
import com.jaymospowers.alchemy.Alchemy;
import com.jaymospowers.alchemy.Essence;
import com.jaymospowers.alchemy.EssenceList;
import io.netty.buffer.ByteBuf;
import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
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
import org.jspecify.annotations.Nullable;

/**
 * What every item and creature is made of. Three layers, the later never overriding the earlier:
 * <ol>
 * <li>The hand table, data files {@code data/<namespace>/alchemy/essences/*.json}:
 * {@code {"items": {"minecraft:iron_ingot": {"metal": 8, "earth": 2}, "#minecraft:logs": {...}}, "entities": {...}}}.
 * An id beats a tag; a data pack can add or replace entries, so other mods' things can be given essences without
 * code.</li>
 * <li>Everything crafted, smelted, smithed or stonecut out of other things: the essences of the ingredients, times
 * {@link #DERIVE_SHARE}, over how many it makes (a little fire more when it was cooked), at least 1 of something.
 * A slot that accepts several things counts the cheapest; an item made several ways takes the cheapest way; chains
 * (log, planks, sticks) are followed until nothing changes, so a loop can't run away. Our own mod items and the
 * Kitchen's and Kingdoms' come out of this.</li>
 * <li>Creatures with no entry take the first matching entity tag in the table, and any other living thing
 * {@link #LIVING}.</li>
 * </ol>
 * Worked out on server start and after every {@code /reload}, and sent to every player, so the lens, the tooltips and
 * the Codex agree with the server. (Modelled on {@code MeltingRecipes}.)
 */
public final class Essences {
	/** How much of the ingredients' essence a crafted thing keeps (the rest is lost in the making). */
	public static final double DERIVE_SHARE = 0.75;
	/** Passes at most through the recipes (chains are rarely more than six deep). */
	static final int MAX_PASSES = 32;
	/** Where the data files live, under data/<namespace>/. */
	static final String FOLDER = "alchemy/essences";
	/** A cooked thing (furnace, smoker, campfire, blast furnace) carries this much fire on top. */
	static final EssenceList COOKED = EssenceList.of(Essence.FIRE, 1);
	/** A living creature nothing else describes (modded folk, say). */
	public static final EssenceList LIVING = EssenceList.of(Essence.LIFE, 3, Essence.BEAST, 1);

	private static volatile Map<Item, EssenceList> items = Map.of();
	private static volatile Map<EntityType<?>, EssenceList> entities = Map.of();

	private Essences() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(Sync.TYPE, Sync.CODEC);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> rebuild(server, server.getResourceManager()));
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

	/** The essences in one of this item (not times the stack's count); empty for things with none (air, unknowns).
	 * A phial with essence in it holds that essence as well as its glass. */
	public static EssenceList of(ItemStack stack) {
		if (stack.isEmpty()) {
			return EssenceList.EMPTY;
		}
		EssenceList base = items.getOrDefault(stack.getItem(), EssenceList.EMPTY);
		EssenceList held = stack.get(Alchemy.ESSENCES);
		return held == null || held.isEmpty() ? base : base.plus(held);
	}

	/** The essences of an item type, with nothing on it. */
	public static EssenceList of(Item item) {
		return items.getOrDefault(item, EssenceList.EMPTY);
	}

	/** The essences a creature is made of (by its type); an item on the ground is the item. */
	public static EssenceList of(Entity entity) {
		if (entity instanceof ItemEntity dropped) {
			return of(dropped.getItem());
		}
		EssenceList list = entities.get(entity.getType());
		if (list != null) {
			return list;
		}
		return entity instanceof LivingEntity ? LIVING : EssenceList.EMPTY;
	}

	// --- sync ---

	private static void send(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, Sync.TYPE)) {
			ServerPlayNetworking.send(player, new Sync(items, entities));
		}
	}

	/** Server → client: the whole table, so tooltips and the lens read what the server does. */
	public record Sync(Map<Item, EssenceList> items, Map<EntityType<?>, EssenceList> entities) implements CustomPacketPayload {
		public static final Type<Sync> TYPE = new Type<>(JaymosPowers.id("essences"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = CustomPacketPayload.codec(Sync::write, Sync::read);

		private void write(RegistryFriendlyByteBuf buf) {
			buf.writeVarInt(items.size());
			items.forEach((item, list) -> {
				buf.writeIdentifier(BuiltInRegistries.ITEM.getKey(item));
				EssenceList.STREAM_CODEC.encode(buf, list);
			});
			buf.writeVarInt(entities.size());
			entities.forEach((type, list) -> {
				buf.writeIdentifier(BuiltInRegistries.ENTITY_TYPE.getKey(type));
				EssenceList.STREAM_CODEC.encode(buf, list);
			});
		}

		private static Sync read(RegistryFriendlyByteBuf buf) {
			Map<Item, EssenceList> items = new IdentityHashMap<>();
			for (int i = buf.readVarInt(); i > 0; i--) {
				Identifier id = buf.readIdentifier();
				EssenceList list = EssenceList.STREAM_CODEC.decode((ByteBuf) buf);
				BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> items.put(item, list));
			}
			Map<EntityType<?>, EssenceList> entities = new IdentityHashMap<>();
			for (int i = buf.readVarInt(); i > 0; i--) {
				Identifier id = buf.readIdentifier();
				EssenceList list = EssenceList.STREAM_CODEC.decode((ByteBuf) buf);
				BuiltInRegistries.ENTITY_TYPE.getOptional(id).ifPresent(type -> entities.put(type, list));
			}
			return new Sync(items, entities);
		}

		@Override
		public Type<Sync> type() {
			return TYPE;
		}
	}

	/** Client side: takes the server's table. */
	public static void accept(Sync sync) {
		items = sync.items();
		entities = sync.entities();
	}

	// --- building the table ---

	/** One way of making an item: what it makes, how many, what each slot accepts, and what the making adds. */
	private record Made(Item result, int count, List<List<Item>> ingredients, EssenceList bonus) {
	}

	/** Reads the data files, then derives the rest from the recipes. */
	static void rebuild(MinecraftServer server, ResourceManager resources) {
		Map<Item, EssenceList> byTag = new IdentityHashMap<>();
		Map<Item, EssenceList> fixed = new IdentityHashMap<>();
		Map<EntityType<?>, EssenceList> creaturesByTag = new IdentityHashMap<>();
		Map<EntityType<?>, EssenceList> creatures = new IdentityHashMap<>();
		readFiles(resources, byTag, fixed, creaturesByTag, creatures);
		byTag.forEach(fixed::putIfAbsent);
		creaturesByTag.forEach(creatures::putIfAbsent);

		List<Made> made = new ArrayList<>();
		for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
			try {
				Made one = made(holder.value());
				if (one != null && !fixed.containsKey(one.result())) {
					made.add(one);
				}
			} catch (RuntimeException e) {
				JaymosPowers.LOGGER.debug("Essences: skipped recipe {}", holder.id().identifier(), e);
			}
		}
		Map<Item, EssenceList> derived = derive(made, fixed);

		Map<Item, EssenceList> all = new IdentityHashMap<>(derived);
		all.putAll(fixed);
		all.values().removeIf(EssenceList::isEmpty);
		items = all;
		entities = creatures;
		JaymosPowers.LOGGER.info("Essences: {} items from data files, {} from recipes, {} creatures", fixed.size(), derived.size(), creatures.size());
	}

	private static void readFiles(ResourceManager resources, Map<Item, EssenceList> byTag, Map<Item, EssenceList> fixed,
		Map<EntityType<?>, EssenceList> creaturesByTag, Map<EntityType<?>, EssenceList> creatures) {
		// sorted, so the first tag that matches a thing is the same every time
		Map<Identifier, Resource> files = new TreeMap<>(resources.listResources(FOLDER, id -> id.getPath().endsWith(".json")));
		for (Map.Entry<Identifier, Resource> file : files.entrySet()) {
			try (Reader reader = file.getValue().openAsReader()) {
				JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
				if (root.has("items")) {
					for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("items").entrySet()) {
						EssenceList list = parse(entry.getValue(), file.getKey(), entry.getKey());
						if (list == null) {
							continue;
						}
						String key = entry.getKey();
						if (key.startsWith("#")) {
							Identifier tag = Identifier.tryParse(key.substring(1));
							if (tag == null) {
								continue;
							}
							TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tag);
							for (Item item : BuiltInRegistries.ITEM) {
								if (item.builtInRegistryHolder().is(tagKey)) {
									byTag.putIfAbsent(item, list);
								}
							}
						} else {
							Identifier id = Identifier.tryParse(key);
							// absent: its mod isn't loaded
							BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> fixed.put(item, list));
						}
					}
				}
				if (root.has("entities")) {
					for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("entities").entrySet()) {
						EssenceList list = parse(entry.getValue(), file.getKey(), entry.getKey());
						if (list == null) {
							continue;
						}
						String key = entry.getKey();
						if (key.startsWith("#")) {
							Identifier tag = Identifier.tryParse(key.substring(1));
							if (tag == null) {
								continue;
							}
							TagKey<EntityType<?>> tagKey = TagKey.create(Registries.ENTITY_TYPE, tag);
							for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
								if (type.builtInRegistryHolder().is(tagKey)) {
									creaturesByTag.putIfAbsent(type, list);
								}
							}
						} else {
							Identifier id = Identifier.tryParse(key);
							BuiltInRegistries.ENTITY_TYPE.getOptional(id).ifPresent(type -> creatures.put(type, list));
						}
					}
				}
			} catch (Exception e) {
				JaymosPowers.LOGGER.warn("Essences: couldn't read {}", file.getKey(), e);
			}
		}
	}

	private static @Nullable EssenceList parse(JsonElement json, Identifier file, String key) {
		var result = EssenceList.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json);
		if (result.error().isPresent()) {
			JaymosPowers.LOGGER.warn("Essences: bad entry '{}' in {}: {}", key, file, result.error().get().message());
			return null;
		}
		return result.result().orElse(null);
	}

	/** A crafting, smelting, smithing or stonecutting recipe as ingredients and a result, or null if it isn't one we read. */
	private static @Nullable Made made(Recipe<?> recipe) {
		Object type = recipe.getType();
		boolean cooked = type == RecipeType.SMELTING || type == RecipeType.BLASTING || type == RecipeType.SMOKING
			|| type == RecipeType.CAMPFIRE_COOKING;
		if (!cooked && type != RecipeType.CRAFTING && type != RecipeType.SMITHING && type != RecipeType.STONECUTTING || recipe.isSpecial()) {
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
		return new Made(result, count, ingredients, cooked ? COOKED : EssenceList.EMPTY);
	}

	/** Goes through the recipes until nothing changes; each item takes its cheapest recipe. */
	private static Map<Item, EssenceList> derive(List<Made> made, Map<Item, EssenceList> fixed) {
		Map<Item, EssenceList> derived = new IdentityHashMap<>();
		for (int pass = 0; pass < MAX_PASSES; pass++) {
			Map<Item, EssenceList> next = new IdentityHashMap<>();
			for (Made one : made) {
				EssenceList each = made(one, fixed, derived);
				if (each.isEmpty()) {
					continue;
				}
				EssenceList best = next.get(one.result());
				if (best == null || each.total() < best.total()) {
					next.put(one.result(), each);
				}
			}
			if (next.equals(derived)) {
				break;
			}
			derived = next;
		}
		return derived;
	}

	/** What one recipe makes of its ingredients' essences, each of the result. */
	private static EssenceList made(Made one, Map<Item, EssenceList> fixed, Map<Item, EssenceList> derived) {
		EnumMap<Essence, Double> sum = new EnumMap<>(Essence.class);
		for (List<Item> accepts : one.ingredients()) {
			cheapest(accepts, fixed, derived).amounts().forEach((e, n) -> sum.merge(e, (double) n, Double::sum));
		}
		if (sum.isEmpty()) {
			return EssenceList.EMPTY;
		}
		EnumMap<Essence, Integer> out = new EnumMap<>(Essence.class);
		Essence biggest = null;
		double most = 0;
		for (Map.Entry<Essence, Double> en : sum.entrySet()) {
			double each = en.getValue() * DERIVE_SHARE / one.count();
			if (each > most) {
				most = each;
				biggest = en.getKey();
			}
			int rounded = (int) Math.round(each);
			if (rounded > 0) {
				out.put(en.getKey(), rounded);
			}
		}
		if (out.isEmpty() && biggest != null) {
			// never nothing: the biggest part of it survives
			out.put(biggest, 1);
		}
		return new EssenceList(out).plus(one.bonus());
	}

	/** The least essence one ingredient slot can be filled with (nothing if what it accepts has none, or is handed back). */
	private static EssenceList cheapest(List<Item> accepts, Map<Item, EssenceList> fixed, Map<Item, EssenceList> derived) {
		EssenceList best = EssenceList.EMPTY;
		for (Item item : accepts) {
			if (item.getCraftingRemainder() != null) {
				// handed back after crafting (a bucket, a bottle): it isn't in the result
				return EssenceList.EMPTY;
			}
			EssenceList list = fixed.get(item);
			if (list == null) {
				list = derived.getOrDefault(item, EssenceList.EMPTY);
			}
			if (!list.isEmpty() && (best.isEmpty() || list.total() < best.total())) {
				best = list;
			}
		}
		return best;
	}
}
