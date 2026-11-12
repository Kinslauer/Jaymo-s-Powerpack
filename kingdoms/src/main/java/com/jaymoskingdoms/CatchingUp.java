package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Captains catch up (docs/Pawn-Plan.md row B4, foe side; after the pawns who come back level with you in Dragon's
 * Dogma, a commercial game: ideas only). A captain (or a Bane) who has sat out the fights that raised his fellows doesn't
 * stay where he was: once a day, a standing captain {@link #BEHIND} or more ranks under the best of his tribe has a
 * {@link #CHANCE} to climb one rank (with a new strength, as any climb brings), training with his betters. His body
 * grows with it if it's about; one with no body yet comes in at his new rank.
 */
public final class CatchingUp {
	/** Ranks behind the best of his tribe before a captain catches up; the chance a day he climbs one. */
	static final int BEHIND = 2;
	static final float CHANCE = 0.5F;
	/** Looked at once a day (ticks). */
	static final int EVERY = 24000;

	private CatchingUp() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % EVERY == EVERY / 2) {
				day(server);
			}
		});
	}

	private static void day(MinecraftServer server) {
		Map<String, Integer> best = new HashMap<>();
		List<Captains.Captain> standing = new ArrayList<>();
		for (Captains.Captain c : Captains.all(server).values()) {
			if (!c.fallen()) {
				standing.add(c);
				best.merge(c.tribe(), c.rank(), Math::max);
			}
		}
		ServerLevel level = server.overworld();
		for (Captains.Captain c : standing) {
			if (c.rank() > best.get(c.tribe()) - BEHIND || c.rank() >= Captains.rankMax(server, c) || level.getRandom().nextFloat() >= CHANCE) {
				continue;
			}
			if (c.body().isEmpty()) {
				// (no body about: he comes in at his new rank)
				Captains.put(server, c.climbed(c.rank() + 1, c.traits(), c.trophy(), c.slain()));
				continue;
			}
			LivingEntity body = find(server, c.body().get());
			if (body != null && body.level() instanceof ServerLevel there) {
				Captains.promote(there, c, body);
			}
			// (a body out of the loaded world waits for another day)
		}
	}

	private static LivingEntity find(MinecraftServer server, UUID id) {
		for (ServerLevel l : server.getAllLevels()) {
			Entity e = l.getEntity(id);
			if (e instanceof LivingEntity living && living.isAlive()) {
				return living;
			}
		}
		return null;
	}
}
