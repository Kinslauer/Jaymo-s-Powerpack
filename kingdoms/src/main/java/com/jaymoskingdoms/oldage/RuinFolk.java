package com.jaymoskingdoms.oldage;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.McaCompat;
import com.jaymoskingdoms.Professions;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
	/** Their prices (emeralds), how many of each before the stall runs out (restocked each day), the price rise. */
	static final int RELIC_PRICE = 6, WEAPON_PRICE = 10, SHIELD_PRICE = 8, MAP_PRICE = 7, STONE_PRICE = 1, STONE_COUNT = 8;
	static final int USES = 6, XP = 2;
	static final float PRICE_MULT = 0.05F;
	/** What they pay an emerald for. */
	static final int BREAD = 6, IRON = 4, WOOL = 10;
	/** How far a map looks for another city (chunks). */
	static final int MAP_SEARCH = 100;

	/** On a descendant: their people's id. Saved. */
	static final AttachmentType<String> FOLK = AttachmentRegistry.create(JaymosKingdoms.id("ruin_folk"), b -> b.persistent(Codec.STRING));
	/** On a level: the cities whose descendants have come (their start chunks). Saved. */
	static final AttachmentType<List<Long>> PEOPLED = AttachmentRegistry.create(JaymosKingdoms.id("ruins_peopled"), b -> b.persistent(Codec.LONG.listOf()));

	/** The day each stall was last stocked; the nearest other city each city's map leads to (its start chunk → place). */
	private static final Map<UUID, Long> STOCKED = new HashMap<>();
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
			STOCKED.clear();
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
				if (STOCKED.getOrDefault(v.getUUID(), -1L) != day) {
					STOCKED.put(v.getUUID(), day);
					v.setOffers(wares(level, v));
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
			v.setOffers(wares(level, v));
			level.addFreshEntity(v);
		}
	}

	static People people(Entity e) {
		return People.byName(e.getAttachedOrElse(FOLK, People.SUNSTEP.id));
	}

	/** A descendant's stall, in emeralds. */
	static MerchantOffers wares(ServerLevel level, Villager v) {
		People p = people(v);
		MerchantOffers offers = new MerchantOffers();
		sell(offers, RELIC_PRICE, new ItemStack(OldAge.relic(p)));
		sell(offers, WEAPON_PRICE, com.jaymoskingdoms.Spoils.powers(p.weapon));
		if (!p.weapon2.equals(p.weapon)) {
			sell(offers, WEAPON_PRICE, com.jaymoskingdoms.Spoils.powers(p.weapon2));
		}
		if (!p.shield.isEmpty()) {
			sell(offers, SHIELD_PRICE, com.jaymoskingdoms.Spoils.powers(p.shield));
		}
		if (!p.shield2.isEmpty()) {
			sell(offers, SHIELD_PRICE, com.jaymoskingdoms.Spoils.powers(p.shield2));
		}
		sell(offers, STONE_PRICE, new ItemStack(p.trim().getBlock().asItem(), STONE_COUNT));
		sell(offers, MAP_PRICE, map(level, v, p));
		buy(offers, Items.BREAD, BREAD);
		buy(offers, Items.IRON_INGOT, IRON);
		buy(offers, Items.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE), WOOL);
		return offers;
	}

	private static void sell(MerchantOffers offers, int emeralds, ItemStack stack) {
		if (!stack.isEmpty() && stack.getItem() != Items.AIR) {
			offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, emeralds), stack, USES, XP, PRICE_MULT));
		}
	}

	private static void buy(MerchantOffers offers, Item item, int count) {
		offers.add(new MerchantOffer(new ItemCost(item, count), new ItemStack(Items.EMERALD), USES * 2, XP, PRICE_MULT));
	}

	/** A map to the nearest city of another people than {@code p}'s, or empty if there's none near enough. */
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
		ItemStack map = MapItem.create(level, found.getX(), found.getZ(), (byte) 2, true, true);
		MapItem.renderBiomePreviewMap(level, map);
		MapItemSavedData.addTargetDecoration(map, found, "+", MapDecorationTypes.RED_X);
		map.set(DataComponents.ITEM_NAME, Component.translatable("filled_map.jaymoskingdoms.old_ruins"));
		return map;
	}
}
