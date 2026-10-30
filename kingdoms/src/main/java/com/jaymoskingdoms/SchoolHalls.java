package com.jaymoskingdoms;

import com.jaymoskingdoms.forms.Schools;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.384) A realm's school master and where they stand (Weapon Forms, patch 4). Every standing kingdom and fiefdom keeps
 * one master ({@link Schools#ofRealm}: the school is fixed by the realm's id), an MCA villager held at the post by
 * {@link Schools#step}. They stand at the middle of the realm's <b>training hall</b> ({@code training_hall}, a building the
 * realm raises as it grows: {@link JobHalls#trainingHall}); a kingdom that has not raised one yet has its master at its
 * <b>barracks</b> instead (a fiefdom waits for its hall). When the hall is finished, a barracks master is moved into it. The
 * master is kept up now and then, once a player's near enough to have the place loaded ({@link #EVERY}): none found by the
 * spot (by the realm's tag) means a new one, more than one means the extras are let go. Without MCA there are no masters.
 */
final class SchoolHalls {
	/** How often the realms are looked over (ticks), and how far (blocks) from a spot a master is looked for. */
	static final int EVERY = 200;
	static final double SEARCH = 24.0;
	/** A master this far from their spot is moved back (blocks). */
	static final double STRAY = 6.0;

	private SchoolHalls() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(SchoolHalls::tick);
	}

	private static void tick(MinecraftServer server) {
		if (!McaCompat.LOADED || server.getTickCount() % EVERY != 0) {
			return;
		}
		ServerLevel level = server.overworld();
		if (level.players().isEmpty()) {
			return;
		}
		for (Faction f : Factions.all(server)) {
			if (f.type().isRealm() && !f.fallen()) {
				keep(level, f);
			}
		}
	}

	/** The places a realm's master may stand, best first: its finished training hall, then (a kingdom) its barracks. */
	private static List<BlockPos> places(ServerLevel level, Faction f) {
		List<BlockPos> halls = new ArrayList<>(), barracks = new ArrayList<>();
		for (Works.Built b : Works.of(level.getServer(), f.id()).built()) {
			if (b.ruined() != 0L) {
				continue;
			}
			BlockPos mid = new BlockPos((b.min().getX() + b.max().getX()) / 2, b.min().getY() + 1, (b.min().getZ() + b.max().getZ()) / 2);
			if (b.entry().equals("training_hall")) {
				halls.add(mid);
			} else if (b.entry().equals("barracks") && f.type() == FactionType.KINGDOM) {
				barracks.add(mid);
			}
		}
		halls.addAll(barracks);
		return halls;
	}

	private static void keep(ServerLevel level, Faction f) {
		List<BlockPos> places = places(level, f);
		if (places.isEmpty()) {
			return;
		}
		BlockPos spot = places.get(0);
		// (only once the place is loaded and ticking, or a master unseen would be made twice)
		if (!level.isPositionEntityTicking(spot)) {
			return;
		}
		String tag = Schools.REALM_TAG_PREFIX + f.id();
		List<PathfinderMob> found = new ArrayList<>();
		Set<UUID> seen = new HashSet<>();
		for (BlockPos place : places) {
			if (!level.isPositionEntityTicking(place)) {
				continue;
			}
			for (PathfinderMob m : level.getEntitiesOfClass(PathfinderMob.class, new AABB(place).inflate(SEARCH),
				e -> e.isAlive() && e.entityTags().contains(tag))) {
				if (seen.add(m.getUUID())) {
					found.add(m);
				}
			}
		}
		if (!found.isEmpty()) {
			PathfinderMob master = found.get(0);
			for (int i = 1; i < found.size(); i++) {
				found.get(i).discard();
			}
			Vec3 stand = Vec3.atBottomCenterOf(Construction.standable(level, spot, 2));
			if (master.distanceToSqr(stand) > STRAY * STRAY) {
				// the hall is finished, or was lost and raised again: the master moves in
				master.setHomeTo(BlockPos.containing(stand), 1);
				master.teleportTo(stand.x, stand.y, stand.z);
			}
			return;
		}
		Schools.School school = Schools.ofRealm(f.id());
		BlockPos at = Construction.standable(level, spot, 2);
		McaBridge.master(level, Vec3.atBottomCenterOf(at), level.getRandom().nextFloat() * 360.0F,
			List.of(Schools.MASTER_TAG, Schools.SCHOOL_TAG_PREFIX + school.key(), tag));
	}
}
