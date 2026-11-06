package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

/**
 * Andy (2026-11-06): "all items that are inaccessible to your world tier should be hidden from JEI and otherwise. you
 * should really have to discover everything." What a player can't reach yet at their world tier ({@link WorldTiers}) or
 * {@link Knowledge} level is kept off their JEI list (and every JEI page that makes or uses it) and out of their recipe
 * book, until their tier and knowledge catch up or until they've had one in their own inventory (then it's discovered,
 * for good). Creative and spectating players see everything.
 * <p>
 * Where each item sits, worked out on the server when it starts and on every {@code /reload}:
 * <ul>
 * <li>Seeds: the item tags {@code jaymoskingdoms:tier_hidden/<tier>} (which take in {@link TierGate}'s own tags too),
 * {@link TierGate#tierOf}, whole mods by namespace ({@link #NAMESPACE_TIER}), and for knowledge the tags
 * {@code jaymoskingdoms:knowledge_hidden/<level>} and every block {@link Knowledge#needed} (anvils, the smithing table,
 * the enchanting table...). The tag {@code tier_hidden/never} and spawn eggs are never reached in survival: hidden until
 * held.</li>
 * <li>Then through the recipes (every recipe type the server knows, modded ones too): an item made only from hidden
 * things, or only at a hidden station (netherite gear at the smithing table), is as hidden as the easiest way to make
 * it. An item can only be raised by this, never lowered below its seed.</li>
 * </ul>
 * The server sends each player their list (a {@link Hidden} packet) when they join, when it changes (a tier opened, a
 * boss's knowledge, a find) and after a reload; the client's JEI plugin and recipe book read it. Trades are still
 * {@link TierGate}'s.
 */
public final class TierHiding {
	// ---- tuning ----
	/** How often each player's inventory is looked through for finds and their tier for changes (ticks). */
	static final int SCAN_TICKS = 20;
	/** Whole mods sitting at a tier (namespace to tier ordinal): the Aether's islands are a Borderlands journey. */
	static final Map<String, Integer> NAMESPACE_TIER = Map.of("aether", 1, "aether_ii", 1, "deep_aether", 1);
	/** The tier given to what survival never reaches (spawn eggs, operator blocks): above Legend, so only finding one shows it. */
	static final int NEVER = WorldTiers.Tier.values().length;
	/** How many times the recipe pass goes round at most (each round lets a hidden thing reach one more step of crafting). */
	static final int MAX_PASSES = 64;

	private static final String[] TIER_TAGS = {null, "borderlands", "wildlands", "dread", "legend"};
	private static final int UNREACHED = Integer.MAX_VALUE;

	/** Items a player has had in their inventory (only the ones that were ever hidden are kept). Saved, kept through death. */
	static final AttachmentType<List<Identifier>> SEEN = AttachmentRegistry.create(JaymosKingdoms.id("tier_seen"),
		builder -> builder.persistent(Identifier.CODEC.listOf()).copyOnDeath());

	/** Server to client: every item kept from this player's sight (replaces the last list). */
	public record Hidden(List<Identifier> items) implements CustomPacketPayload {
		public static final Type<Hidden> TYPE = new Type<>(JaymosKingdoms.id("tier_hidden"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Hidden> CODEC = CustomPacketPayload.codec(
			(p, buf) -> Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, p.items),
			buf -> new Hidden(Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf)));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Per item, the tier it waits for and the knowledge it waits for (only items that wait for either are in these). */
	private static Map<Item, Integer> tiers = Map.of();
	private static Map<Item, Integer> knowledge = Map.of();
	/** What each online player was last sent, so only changes go out. */
	private static final Map<UUID, Set<Item>> SENT = new HashMap<>();

	private TierHiding() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("tier hiding attachment: {}", SEEN);
		PayloadTypeRegistry.clientboundPlay().register(Hidden.TYPE, Hidden.CODEC);
		ServerLifecycleEvents.SERVER_STARTED.register(TierHiding::rebuild);
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
			if (success) {
				rebuild(server);
				SENT.clear();
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SENT.clear();
			tiers = Map.of();
			knowledge = Map.of();
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			SENT.remove(handler.player.getUUID());
			update(handler.player);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SENT.remove(handler.player.getUUID()));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % SCAN_TICKS == 0) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					update(player);
				}
			}
		});
	}

	/** Works out where every item sits (see the class notes). */
	static void rebuild(MinecraftServer server) {
		long start = System.nanoTime();
		Map<Item, Integer> tierSeed = new IdentityHashMap<>(), knowSeed = new IdentityHashMap<>();
		List<TagKey<Item>> tierTags = new ArrayList<>();
		for (int t = 1; t < TIER_TAGS.length; t++) {
			tierTags.add(TagKey.create(Registries.ITEM, JaymosKingdoms.id("tier_hidden/" + TIER_TAGS[t])));
		}
		TagKey<Item> never = TagKey.create(Registries.ITEM, JaymosKingdoms.id("tier_hidden/never"));
		List<TagKey<Item>> knowTags = new ArrayList<>();
		for (int k = 1; k <= Knowledge.MAX; k++) {
			knowTags.add(TagKey.create(Registries.ITEM, JaymosKingdoms.id("knowledge_hidden/" + k)));
		}
		for (Item item : BuiltInRegistries.ITEM) {
			ItemStack stack = new ItemStack(item);
			if (stack.isEmpty()) {
				continue;
			}
			int tier = TierGate.tierOf(stack);
			for (int t = 0; t < tierTags.size(); t++) {
				if (stack.is(tierTags.get(t))) {
					tier = Math.max(tier, t + 1);
				}
			}
			tier = Math.max(tier, NAMESPACE_TIER.getOrDefault(BuiltInRegistries.ITEM.getKey(item).getNamespace(), 0));
			if (stack.is(never) || item instanceof SpawnEggItem) {
				tier = NEVER;
			}
			int know = item instanceof BlockItem block ? Knowledge.needed(block.getBlock().defaultBlockState()) : 0;
			for (int k = 0; k < knowTags.size(); k++) {
				if (stack.is(knowTags.get(k))) {
					know = Math.max(know, k + 1);
				}
			}
			if (tier > 0) {
				tierSeed.put(item, tier);
			}
			if (know > 0) {
				knowSeed.put(item, know);
			}
		}
		List<Made> recipes = recipes(server);
		tiers = through(recipes, tierSeed);
		knowledge = through(recipes, knowSeed);
		JaymosKingdoms.LOGGER.info("Tier hiding: {} items wait for a tier, {} for knowledge ({} recipes, {} ms)", tiers.size(),
			knowledge.size(), recipes.size(), (System.nanoTime() - start) / 1_000_000);
	}

	/** One way of making things: what comes out, the slots it needs (each any one of its items), and where it's made. */
	private record Made(List<Item> results, List<List<Item>> slots) {
	}

	private static List<Made> recipes(MinecraftServer server) {
		ContextMap context = SlotDisplayContext.fromLevel(server.overworld());
		List<Made> out = new ArrayList<>();
		for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
			try {
				Recipe<?> recipe = holder.value();
				var placement = recipe.placementInfo();
				if (placement.isImpossibleToPlace()) {
					continue;
				}
				List<List<Item>> slots = new ArrayList<>();
				for (Ingredient ingredient : placement.ingredients()) {
					slots.add(ingredient.items().map(Holder::value).toList());
				}
				Set<Item> results = new HashSet<>();
				for (RecipeDisplay display : recipe.display()) {
					for (ItemStack stack : display.result().resolveForStacks(context)) {
						results.add(stack.getItem());
					}
					List<Item> station = display.craftingStation().resolveForStacks(context).stream().map(ItemStack::getItem).distinct().toList();
					if (!station.isEmpty()) {
						slots.add(station);
					}
				}
				// an item made from itself (a repair, a dye, an armour trim) says nothing about how it's first had
				for (List<Item> slot : slots) {
					slot.forEach(results::remove);
				}
				if (!results.isEmpty()) {
					out.add(new Made(List.copyOf(results), slots));
				}
			} catch (RuntimeException e) {
				JaymosKingdoms.LOGGER.debug("Tier hiding skipped recipe {}: {}", holder.id(), e.toString());
			}
		}
		return out;
	}

	/**
	 * Carries {@code seed} through {@code recipes}: an item that comes out of any recipe waits for the easiest of them (the
	 * hardest slot of it, each slot as easy as its easiest item), and never for less than its own seed. Worked from above,
	 * so a loop of recipes (ingot to block to ingot) doesn't hold anything down; anything no recipe reaches keeps its seed.
	 */
	private static Map<Item, Integer> through(List<Made> recipes, Map<Item, Integer> seed) {
		Map<Item, Integer> value = new IdentityHashMap<>();
		for (Made made : recipes) {
			for (Item result : made.results) {
				value.put(result, UNREACHED);
			}
		}
		for (int pass = 0; pass < MAX_PASSES; pass++) {
			Map<Item, Integer> best = new IdentityHashMap<>();
			for (Made made : recipes) {
				int need = 0;
				for (List<Item> slot : made.slots) {
					int easiest = slot.isEmpty() ? 0 : UNREACHED;
					for (Item item : slot) {
						easiest = Math.min(easiest, value.getOrDefault(item, seed.getOrDefault(item, 0)));
						if (easiest == 0) {
							break;
						}
					}
					need = Math.max(need, easiest);
				}
				for (Item result : made.results) {
					best.merge(result, need, Math::min);
				}
			}
			boolean changed = false;
			for (var e : best.entrySet()) {
				int v = e.getValue() == UNREACHED ? UNREACHED : Math.max(e.getValue(), seed.getOrDefault(e.getKey(), 0));
				if (!value.get(e.getKey()).equals(v)) {
					value.put(e.getKey(), v);
					changed = true;
				}
			}
			if (!changed) {
				break;
			}
		}
		Map<Item, Integer> out = new IdentityHashMap<>();
		seed.forEach((item, v) -> {
			if (v > 0) {
				out.put(item, v);
			}
		});
		value.forEach((item, v) -> {
			int final_ = v == UNREACHED ? seed.getOrDefault(item, 0) : v;
			if (final_ > 0) {
				out.put(item, final_);
			} else {
				out.remove(item);
			}
		});
		return Map.copyOf(out);
	}

	/** The tier {@code item} waits for (0: none). */
	public static int tierOf(Item item) {
		return tiers.getOrDefault(item, 0);
	}

	/** The knowledge level {@code item} waits for (0: none). */
	public static int knowledgeOf(Item item) {
		return knowledge.getOrDefault(item, 0);
	}

	/** Whether {@code player} doesn't get to see {@code item} yet. */
	public static boolean hidden(ServerPlayer player, Item item, int open, int know, Set<Identifier> seen) {
		return (tierOf(item) > open || knowledgeOf(item) > know) && !seen.contains(BuiltInRegistries.ITEM.getKey(item));
	}

	/** Notes what {@code player} has found, and sends their list if it changed. */
	static void update(ServerPlayer player) {
		if (tiers.isEmpty() && knowledge.isEmpty()) {
			return;
		}
		Set<Identifier> seen = new HashSet<>(player.getAttachedOrElse(SEEN, List.of()));
		int before = seen.size();
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty() && (tierOf(stack.getItem()) > 0 || knowledgeOf(stack.getItem()) > 0)) {
				seen.add(BuiltInRegistries.ITEM.getKey(stack.getItem()));
			}
		}
		if (seen.size() != before) {
			player.setAttached(SEEN, List.copyOf(seen));
		}
		Set<Item> hide = new HashSet<>();
		if (!player.isCreative() && !player.isSpectator()) {
			int open = WorldTiers.progress(player).open();
			int know = Knowledge.level(player);
			for (Item item : tiers.keySet()) {
				if (hidden(player, item, open, know, seen)) {
					hide.add(item);
				}
			}
			for (Item item : knowledge.keySet()) {
				if (hidden(player, item, open, know, seen)) {
					hide.add(item);
				}
			}
		}
		if (hide.equals(SENT.get(player.getUUID()))) {
			return;
		}
		// a client without Kingdoms can't take it (and is tried again on the next scan)
		if (ServerPlayNetworking.canSend(player, Hidden.TYPE)) {
			SENT.put(player.getUUID(), hide);
			ServerPlayNetworking.send(player, new Hidden(hide.stream().map(BuiltInRegistries.ITEM::getKey).toList()));
		}
	}
}
