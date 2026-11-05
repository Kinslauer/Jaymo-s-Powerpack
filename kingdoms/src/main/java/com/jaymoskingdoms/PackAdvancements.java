package com.jaymoskingdoms;

import com.jaymoskingdoms.remnants.AetherGiant;
import com.jaymoskingdoms.remnants.Monoliths;
import com.jaymoskingdoms.sailing.Ship;
import com.jaymoskingdoms.whaling.GreatWhale;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.579) Andy (seven-mods plan, patch 3): our own advancement line, one "Jaymo's Powerpack" tab (Powers' data,
 * {@code jaymospowers:pack/*}). Kingdoms awards its part of it: the life story told, a job's first rank, a patron sworn,
 * a monolith seen, each kind of Giant felled, a ship taken out, a great whale brought down. The rest of the line is
 * awarded by Powers and Kitchen; reaching the Aether is a plain dimension trigger. Without Powers loaded the
 * advancements don't exist and this does nothing.
 */
public final class PackAdvancements {
	/** How often (ticks) each player is looked at. */
	static final int EVERY = 100;
	/** How near a monolith counts as finding it; how near a Giant's fall counts as being at it. */
	static final double MONOLITH_RANGE = 48.0, GIANT_RANGE = 96.0;
	static final String NAMESPACE = "jaymospowers";

	private PackAdvancements() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % EVERY == 0) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					look(server, player);
				}
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity.level() instanceof ServerLevel level)) {
				return;
			}
			if (entity instanceof GreatWhale && source.getEntity() instanceof ServerPlayer killer) {
				award(killer, "whale", "done");
			}
			if (entity instanceof AetherGiant giant) {
				String element = giant.element().name().toLowerCase(Locale.ROOT);
				for (ServerPlayer p : level.players()) {
					if (p.distanceToSqr(giant) <= GIANT_RANGE * GIANT_RANGE || p == source.getEntity()) {
						felled(p, element);
					}
				}
			}
		});
	}

	/** {@code player} was at a Giant's fall. */
	private static void felled(ServerPlayer player, String element) {
		award(player, "giant", "done");
		award(player, "giant_all", element);
		AdvancementHolder all = get(player.level().getServer(), "giant_all");
		if (all != null) {
			int kinds = 0;
			for (String ignored : player.getAdvancements().getOrStartProgress(all).getCompletedCriteria()) {
				kinds++;
			}
			if (kinds >= 2) {
				award(player, "giant_two", "done");
			}
		}
	}

	private static void look(MinecraftServer server, ServerPlayer player) {
		List<String> story = LifeStory.of(player);
		if (story != null && !story.isEmpty() && !LifeStory.roadOwed(story)) {
			award(player, "story", "done");
		}
		for (Careers.Job job : Careers.Job.values()) {
			if (Careers.heldRank(player, job) >= Careers.JOURNEYMAN) {
				award(player, "first_rank", "done");
				break;
			}
		}
		if (Patrons.of(player) != null) {
			award(player, "sworn", "done");
		}
		ServerLevel overworld = Factions.level(server);
		if (player.level() == overworld) {
			BlockPos foot = Monoliths.nearestFoot(overworld, player.blockPosition());
			if (foot != null && Math.abs(foot.getX() - player.getX()) <= MONOLITH_RANGE && Math.abs(foot.getZ() - player.getZ()) <= MONOLITH_RANGE) {
				award(player, "monolith", "done");
			}
		}
		if (player.getRootVehicle() instanceof Ship) {
			award(player, "cast_off", "done");
		}
	}

	static @Nullable AdvancementHolder get(MinecraftServer server, String name) {
		return server.getAdvancements().get(Identifier.fromNamespaceAndPath(NAMESPACE, "pack/" + name));
	}

	/** Gives {@code player} the {@code criterion} of {@code pack/<name>} (nothing if it's done, or doesn't exist). */
	public static void award(ServerPlayer player, String name, String criterion) {
		AdvancementHolder holder = get(player.level().getServer(), name);
		if (holder == null) {
			return;
		}
		AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
		if (!progress.isDone()) {
			player.getAdvancements().award(holder, criterion);
		}
	}
}
