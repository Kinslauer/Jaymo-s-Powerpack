package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jspecify.annotations.Nullable;

/**
 * Village mods patch 7 (Andy's picks tt_ship, tt_tideborn, tt_sea): joshieman's Tidal Towns (all rights reserved, packs
 * welcome; shipped unchanged in the pack) floats driftwood raft villages out on the deep ocean. Nothing of it is copied
 * or changed: its structure is only known here by its id.
 * <ul>
 * <li>Its bell founds a village like any other mod's ({@link Seats#village}), but its set keeps its own spacing out of
 * the shared village pool ({@link SharedPools}), so the rafts stay as rare as their maker set them.</li>
 * <li>The town is a Tideborn haven always ({@link Havens#tidal}): its people standing are Tideborn and those born there
 * are too.</li>
 * <li>It is part of the sea's life: a mooring off the raft ({@link #moor}) where realms' merchantmen call to trade
 * ({@link Navies#destination}), its own fishing smack puts out ({@link Fishers}), its handcarts go as skiffs
 * ({@link TradeRoutes}), and pirate coves raid it from the sea as they do every coastal settlement ({@link Raids}).</li>
 * </ul>
 * Inert while the mod is absent.
 */
public final class TidalTowns {
	/** Tidal Towns' village structure. */
	static final String ID = "joshie:village_ocean";
	/** Its mod id. */
	static final String MOD = "mr_tidal_towns";
	/** The key prefix of a raft town in {@link Havens#HAVENS}. */
	static final String KEY = "tidal:";
	/** How far round the town's bell open water is looked for, for its mooring (blocks). */
	static final int MOORING_REACH = 120;

	private TidalTowns() {
	}

	static boolean loaded() {
		return FabricLoader.getInstance().isModLoaded(MOD);
	}

	/** {@code holder} is the raft village. */
	static boolean is(Holder<Structure> holder) {
		return holder.unwrapKey().map(k -> k.identifier().toString().equals(ID)).orElse(false);
	}

	/** {@code f} was founded at a raft town's bell. */
	static boolean is(MinecraftServer server, @Nullable Faction f) {
		return f != null && !f.fallen() && Factions.level(server).getAttachedOrElse(Havens.HAVENS, Map.of()).containsKey(KEY + f.id());
	}

	/** {@code f} was just founded at a raft town's bell: marked as one, made a Tideborn haven, and moored. */
	static void found(ServerLevel level, Faction f) {
		MinecraftServer server = level.getServer();
		ServerLevel store = Factions.level(server);
		Map<String, String> next = new HashMap<>(store.getAttachedOrElse(Havens.HAVENS, Map.of()));
		next.put(KEY + f.id(), "1");
		store.setAttached(Havens.HAVENS, Map.copyOf(next));
		Havens.tidal(level, f);
		moor(level, f);
	}

	/**
	 * {@code f}'s mooring: the nearest open sea a ship can lie in off the raft, kept as its harbour ({@link Navies.Harbour},
	 * the raft's bell its shore and pier), so ships can be bound for it and put out from it. A town has no warships: the
	 * navy's own rounds go by realms only. Null while no open water round it is loaded.
	 */
	static Navies.@Nullable Harbour moor(ServerLevel level, Faction f) {
		MinecraftServer server = level.getServer();
		Navies.Harbour h = Navies.harbour(server, f.id());
		if (h != null) {
			return h;
		}
		BlockPos water = PirateCoves.sea(level, f.seat(), MOORING_REACH);
		if (water == null) {
			return null;
		}
		h = new Navies.Harbour(f.seat(), f.seat(), water, Optional.empty(), Optional.empty(), -1L, Optional.empty(), -1L, Optional.empty(), List.of());
		Navies.put(server, f.id(), h);
		JaymosKingdoms.LOGGER.info("{}, a raft town, moors its boats at {}", f.name(), water);
		return h;
	}
}
