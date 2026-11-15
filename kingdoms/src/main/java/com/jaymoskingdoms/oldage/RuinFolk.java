package com.jaymoskingdoms.oldage;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.McaCompat;
import com.jaymoskingdoms.Professions;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (medieval picks, cv-trade) The old age's descendants: a few still live hidden in each city's swept huts
 * ({@link OldRuins}), speaking the old tongue ({@code Languages}' {@code OLD}), MCA villagers (plain ones without MCA)
 * who keep the ruin's stall ({@link Professions#RUIN_KEEPER}). They come to the huts the first time a player walks
 * into the city. Their stall is in emeralds, the old age's money (they are keepers, {@code Currencies.keeper}), stocked
 * each day: their people's relic and arms and carved stone, a map to the nearest city of another people, and the food,
 * iron and wool they can't make for themselves.
 */
public final class RuinFolk {
	// ---- tuning ----
	/** How often the cities round the players are looked at (ticks), and how far round a player the stalls are stocked. */
	static final int EVERY = 100;
	static final double STOCK_RANGE = 48.0;
	/** How far they keep from their hut (blocks). */
	static final int HOME = 14;
	/**
	 * Their prices (emeralds), how many of each before the stall runs out (restocked each day), the price rise. A relic
	 * and arms cost more than a rich town's changer pays for them ({@code Currencies.RELIC_WORTH}, {@code ARMS_WORTH}, at
	 * {@code EMERALD_BUY_RICH}) and its emeralds cost back ({@code EMERALD_SELL}), even at a Hero of the Village V's
	 * discount, and their price doesn't fall with a good name ({@link #COLLECTED_MULT}), so going round never makes money.
	 */
	static final int RELIC_PRICE = 16, WEAPON_PRICE = 12, SHIELD_PRICE = 12, MAP_PRICE = 7, STONE_PRICE = 1, STONE_COUNT = 8;
	static final int USES = 6, XP = 2;
	static final float PRICE_MULT = 0.05F, COLLECTED_MULT = 0.0F;
	/** What they pay an emerald for. */
	static final int BREAD = 6, IRON = 4, WOOL = 10;
	/** How far a map looks for another city (chunks). */
	static final int MAP_SEARCH = 100;

	/** On a descendant: their people's id. Saved. */
	static final AttachmentType<String> FOLK = AttachmentRegistry.create(JaymosKingdoms.id("ruin_folk"), b -> b.persistent(Codec.STRING));
	/** On a level: the cities whose descendants have come (their start chunks). Saved. */
	static final AttachmentType<List<Long>> PEOPLED = AttachmentRegistry.create(JaymosKingdoms.id("ruins_peopled"), b -> b.persistent(Codec.LONG.listOf()));
	/** On a descendant: the day their stall was last stocked. Saved. */
	static final AttachmentType<Long> STOCKED = AttachmentRegistry.create(JaymosKingdoms.id("ruin_folk_stocked"), b -> b.persistent(Codec.LONG));
	/** On a level: the one map each city's stall sells to each other city ("city chunk>target"), by map id. Saved. */
	static final AttachmentType<Map<String, Integer>> MAPS = AttachmentRegistry.create(JaymosKingdoms.id("ruin_maps"),
		b -> b.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)));

	/** The nearest other city each city's map leads to (its start chunk → place). */
	private static final Map<Long, BlockPos> MAPPED = new HashMap<>();

	private RuinFolk() {
	}

	static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.getGameTime() % EVERY == 0L && !level.players().isEmpty()) {
				look(level);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			MAPPED.clear();
		});
	}

	/** {@code e} is one of the old age's descendants. */
	public static boolean is(@Nullable Entity e) {
		return e != null && e.hasAttached(FOLK);
	}

	private static void look(ServerLevel level) {
		long day = level.getGameTime() / 24000L;
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}
			StructureStart start = level.structureManager().getStructureWithPieceAt(player.blockPosition(), OldRuins.RUINS);
			if (start.isValid()) {
				long key = start.getChunkPos().pack();
				List<Long> peopled = level.getAttachedOrElse(PEOPLED, List.<Long>of());
				if (!peopled.contains(key)) {
					List<Long> next = new ArrayList<>(peopled);
					next.add(key);
					level.setAttached(PEOPLED, next);
					come(level, start);
				}
			}
			for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(player.blockPosition()).inflate(STOCK_RANGE), RuinFolk::is)) {
				if (v.getAttachedOrElse(STOCKED, -1L) != day) {
					stock(level, v, day);
				}
			}
		}
	}

	/** The city's descendants come home to its huts. */
	private static void come(ServerLevel level, StructureStart start) {
		for (StructurePiece piece : start.getPieces()) {
			if (!(piece instanceof OldRuins.Piece hut) || !hut.hut()) {
				continue;
			}
			Vec3 at = Vec3.atBottomCenterOf(hut.at().above());
			Villager v = McaCompat.newVillager(level, at);
			if (v == null) {
				continue;
			}
			v.snapTo(at.x, at.y, at.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
			v.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), EntitySpawnReason.EVENT, null);
			v.setAttached(FOLK, hut.people().id);
			v.setVillagerData(v.getVillagerData().withProfession(BuiltInRegistries.VILLAGER_PROFESSION.wrapAsHolder(Professions.RUIN_KEEPER)));
			// (a little experience, so the game doesn't take the stall off them for want of a job site)
			v.setVillagerXp(Math.max(1, v.getVillagerXp()));
			v.setPersistenceRequired();
			v.setHomeTo(hut.at(), HOME);
			stock(level, v, level.getGameTime() / 24000L);
			level.addFreshEntity(v);
		}
	}

	static People people(Entity e) {
		return People.byName(e.getAttachedOrElse(FOLK, People.SUNSTEP.id));
	}

	private static void stock(ServerLevel level, Villager v, long day) {
		v.setAttached(STOCKED, day);
		v.setOffers(wares(level, v));
	}

	/** A descendant's stall, in emeralds. */
	static MerchantOffers wares(ServerLevel level, Villager v) {
		People p = people(v);
		MerchantOffers offers = new MerchantOffers();
		sell(offers, RELIC_PRICE, new ItemStack(OldAge.relic(p)), COLLECTED_MULT);
		sell(offers, WEAPON_PRICE, com.jaymoskingdoms.Spoils.powers(p.weapon), COLLECTED_MULT);
		if (!p.weapon2.equals(p.weapon)) {
			sell(offers, WEAPON_PRICE, com.jaymoskingdoms.Spoils.powers(p.weapon2), COLLECTED_MULT);
		}
		if (!p.shield.isEmpty()) {
			sell(offers, SHIELD_PRICE, com.jaymoskingdoms.Spoils.powers(p.shield), COLLECTED_MULT);
		}
		if (!p.shield2.isEmpty()) {
			sell(offers, SHIELD_PRICE, com.jaymoskingdoms.Spoils.powers(p.shield2), COLLECTED_MULT);
		}
		sell(offers, STONE_PRICE, new ItemStack(p.trim().getBlock().asItem(), STONE_COUNT), PRICE_MULT);
		sell(offers, MAP_PRICE, map(level, v, p), PRICE_MULT);
		buy(offers, Items.BREAD, BREAD);
		buy(offers, Items.IRON_INGOT, IRON);
		buy(offers, Items.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE), WOOL);
		return offers;
	}

	private static void sell(MerchantOffers offers, int emeralds, ItemStack stack, float mult) {
		if (!stack.isEmpty() && stack.getItem() != Items.AIR) {
			offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, emeralds), stack, USES, XP, mult));
		}
	}

	private static void buy(MerchantOffers offers, Item item, int count) {
		offers.add(new MerchantOffer(new ItemCost(item, count), new ItemStack(Items.EMERALD), USES * 2, XP, PRICE_MULT));
	}

	/**
	 * A map to the nearest city of another people than {@code p}'s, or empty if there's none near enough: the same map
	 * (one saved map) each time for each city and its target.
	 */
	private static ItemStack map(ServerLevel level, Villager v, People p) {
		StructureStart here = level.structureManager().getStructureWithPieceAt(v.blockPosition(), OldRuins.RUINS);
		long key = here.isValid() ? here.getChunkPos().pack() : v.blockPosition().asLong();
		BlockPos found = MAPPED.get(key);
		if (found == null && !MAPPED.containsKey(key)) {
			List<Holder<Structure>> others = new ArrayList<>();
			for (People o : People.values()) {
				if (o != p) {
					level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
						.get(ResourceKey.create(Registries.STRUCTURE, JaymosKingdoms.id(o.id + "_ruins"))).ifPresent(others::add);
				}
			}
			found = others.isEmpty() ? null : level.findNearestMapStructure(HolderSet.direct(others), v.blockPosition(), MAP_SEARCH, false);
			MAPPED.put(key, found);
		}
		if (found == null) {
			return ItemStack.EMPTY;
		}
		String pair = key + ">" + found.asLong();
		Map<String, Integer> maps = level.getAttachedOrElse(MAPS, Map.<String, Integer>of());
		Integer id = maps.get(pair);
		ItemStack map;
		if (id != null && level.getMapData(new MapId(id)) != null) {
			map = new ItemStack(Items.FILLED_MAP);
			map.set(DataComponents.MAP_ID, new MapId(id));
		} else {
			map = MapItem.create(level, found.getX(), found.getZ(), (byte) 2, true, true);
			MapItem.renderBiomePreviewMap(level, map);
			MapId made = map.get(DataComponents.MAP_ID);
			if (made != null) {
				Map<String, Integer> next = new HashMap<>(maps);
				next.put(pair, made.id());
				level.setAttached(MAPS, next);
			}
		}
		MapItemSavedData.addTargetDecoration(map, found, "+", MapDecorationTypes.RED_X);
		map.set(DataComponents.ITEM_NAME, Component.translatable("filled_map.jaymoskingdoms.old_ruins"));
		return map;
	}
}
