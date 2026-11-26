package com.jaymoskingdoms;

import com.jaymoskingdoms.sailing.Crew;
import com.jaymoskingdoms.sailing.Fleet;
import com.jaymoskingdoms.sailing.Ship;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The pillagers' long ships put to sea (Andy, 2026-11-26: "they should sail and fish off the sides for sustenance like all
 * other boat crews now should"). Once the camp has moved in aboard one of Moog's long ships ({@link IllagerSites}), she
 * casts off ({@link Fleet#castOff}) as a sea rover of that camp: her warlord at her helm, a gunner to her rail guns, the
 * rest of her pillagers about her deck, each taken aboard where he stood. From then on {@link Corsairs} sails her as it
 * does any pirate: she cruises the waters she lay in, runs down ships and fires her broadsides (the real powder and shot
 * in her barrels), her pillagers' crossbows speak from her rail, and her idle hands fish over her side for her stores
 * ({@link Provisions}). The camp is aboard her now, so it's no longer kept up where she lay: no new warlord is seated in
 * the empty water, no hands are made up there, and those she loses are lost.
 */
final class PillagerShips {
	/** Her crew's flag. */
	static final String FLAG = "pillager";
	/** The camps whose long ships have put to sea; kept on the Overworld. */
	static final AttachmentType<List<Integer>> SAILED = AttachmentRegistry.create(JaymosKingdoms.id("pillager_ships_sailed"),
		builder -> builder.persistent(Codec.INT.listOf()));

	private PillagerShips() {
	}

	static void register() {
		// (class loaded at init, for its attachment)
	}

	/** Whether {@code camp}'s long ship has put to sea (its people are aboard her, not where she lay). */
	static boolean atSea(MinecraftServer server, int camp) {
		return Factions.level(server).getAttachedOrElse(SAILED, List.of()).contains(camp);
	}

	/**
	 * {@code camp}, settled aboard the long ship in {@code box} (started in chunk {@code ref}), puts to sea in her. Her ship, or
	 * null if she can't (no helm, not afloat): then she lies at anchor where she is, her guns served from there ({@code DeckGuns}).
	 */
	static @Nullable Ship putToSea(ServerLevel level, Faction camp, long ref, BoundingBox box) {
		MinecraftServer server = level.getServer();
		BlockPos helm = SeaSites.helmOf(level, ref, SeaSites.LONG_SHIP, box);
		if (helm == null) {
			return null;
		}
		List<Mob> people = level.getEntitiesOfClass(Mob.class, AABB.of(box).inflate(1.0), m -> {
			Illagers.Tag tag = Illagers.tag(m);
			return m.isAlive() && tag != null && tag.camp() == camp.id();
		});
		Crew crew = new Crew(Crew.Side.PIRATE, camp.id(), camp.name(), helm);
		crew.flag = FLAG;
		Ship ship = Fleet.castOff(level, helm, crew);
		if (ship == null) {
			JaymosKingdoms.LOGGER.info("{}'s long ship at {} couldn't put to sea; she stays at anchor", camp.name(), helm.toShortString());
			return null;
		}
		Faction now = Factions.get(server, camp.id());
		UUID leader = now == null ? null : now.leader().orElse(null);
		Fleet.Stations stations = Fleet.stations(ship);
		List<Vec3> deck = new ArrayList<>(stations.deck());
		List<Map.Entry<Long, Vec3>> guns = new ArrayList<>(stations.guns().entrySet());
		Set<Vec3> taken = new HashSet<>();
		int gun = 0, aboard = 0;
		for (Mob m : people) {
			Vec3 seat;
			if (m.getUUID().equals(leader) && stations.helm() != null && crew.captain == null) {
				seat = stations.helm();
				crew.captain = m.getUUID();
			} else if (gun < guns.size() && m instanceof net.minecraft.world.entity.monster.illager.Pillager) {
				// (a crossbowman to each gun: he serves it)
				Map.Entry<Long, Vec3> g = guns.get(gun++);
				seat = g.getValue();
				crew.gunners.put(g.getKey(), m.getUUID());
			} else {
				seat = nearestFree(deck, taken, ship.toLocal(m.position()));
			}
			if (seat == null) {
				continue;
			}
			taken.add(seat);
			if (ship.takeAboard(m, seat)) {
				aboard++;
			}
		}
		// guns with nobody to them: the nearest gunner takes two
		if (!crew.gunners.isEmpty()) {
			List<UUID> gunners = new ArrayList<>(crew.gunners.values());
			for (int i = gun, k = 0; i < guns.size(); i++, k++) {
				crew.gunners.put(guns.get(i).getKey(), gunners.get(k % gunners.size()));
			}
		}
		List<Integer> sailed = new ArrayList<>(Factions.level(server).getAttachedOrElse(SAILED, List.of()));
		if (!sailed.contains(camp.id())) {
			sailed.add(camp.id());
			Factions.level(server).setAttached(SAILED, List.copyOf(sailed));
		}
		JaymosKingdoms.LOGGER.info("{} put to sea in their long ship from {} ({} aboard)", camp.name(), helm.toShortString(), aboard);
		return ship;
	}

	/** The free deck place nearest {@code at} (in her frame), or null. */
	private static @Nullable Vec3 nearestFree(List<Vec3> deck, Set<Vec3> taken, Vec3 at) {
		Vec3 best = null;
		double bestD = Double.MAX_VALUE;
		for (Vec3 p : deck) {
			double d = p.distanceToSqr(at);
			if (!taken.contains(p) && d < bestD) {
				best = p;
				bestD = d;
			}
		}
		return best;
	}
}
