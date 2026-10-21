package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * (0.5.19) A boss's bar (a king, a lord, a chief, a warlord, a quest's champion) shows only to the players who've hit it: by hand, with an
 * arrow or javelin, or with a power. It stays while they're within {@link #RANGE}; once they leave, it's gone until
 * they hit the boss again. Its death or unloading clears it.
 */
public final class BossBars {
	/** How near a player who's hit the boss must stay to keep its bar. */
	static final double RANGE = 48.0;

	/** Each boss's players who've hit it (and haven't left since). */
	private static final Map<UUID, Set<UUID>> HITTERS = new HashMap<>();

	private BossBars() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, blocked) -> hit(entity, source));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> HITTERS.remove(entity.getUUID()));
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> HITTERS.remove(entity.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> HITTERS.clear());
	}

	private static void hit(LivingEntity entity, DamageSource source) {
		// the player behind the hit: their own blow, their arrow or javelin, or a power that names them
		if (source.getEntity() instanceof ServerPlayer player && boss(entity)) {
			HITTERS.computeIfAbsent(entity.getUUID(), id -> new HashSet<>()).add(player.getUUID());
		}
	}

	static boolean boss(Entity entity) {
		// (0.7.137) a wild dragon too; (0.7.138) a villager crowned too; (0.7.142) the Nether forts' lords
		return entity instanceof Folk folk ? folk.leader() : Rulers.is(entity) || Illagers.warlord(entity) || QuestFoes.champion(entity)
			|| entity instanceof com.jaymoskingdoms.dragons.WindDragon dragon && !dragon.isTamed()
			// (0.7.142) the Nether forts' warlords and warbosses
			|| com.jaymoskingdoms.nether.NetherForts.warlord(entity)
			|| entity instanceof com.jaymoskingdoms.nether.Hornkin hornkin && hornkin.kind() == com.jaymoskingdoms.nether.Hornkin.Kind.WARLORD;
	}

	/** The boss's bar goes to the players who've hit it and are still near; anyone else loses it. */
	public static void show(ServerLevel level, LivingEntity boss, ServerBossEvent bar) {
		Set<UUID> hitters = HITTERS.get(boss.getUUID());
		double r2 = RANGE * RANGE;
		if (hitters != null) {
			hitters.removeIf(id -> {
				ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
				// gone, elsewhere, or out of range: they'll have to hit it again
				return player == null || player.level() != level || !player.isAlive() || player.distanceToSqr(boss) > r2;
			});
			if (hitters.isEmpty()) {
				HITTERS.remove(boss.getUUID());
				hitters = null;
			}
		}
		for (ServerPlayer player : List.copyOf(bar.getPlayers())) {
			if (hitters == null || !hitters.contains(player.getUUID()) || player.isSpectator()) {
				bar.removePlayer(player);
			}
		}
		if (hitters != null) {
			for (UUID id : hitters) {
				ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
				if (player != null && !player.isSpectator()) {
					bar.addPlayer(player);
				}
			}
		}
	}
}
