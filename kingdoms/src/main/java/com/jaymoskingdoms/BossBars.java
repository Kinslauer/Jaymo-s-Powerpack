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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

/**
 * (0.7.518) a patron's champion (it keeps its own bar, but counts as a boss everywhere else).
 * (0.5.19) A boss's bar (a king, a lord, a chief, a warlord, a quest's champion, (0.7.145) an Aether Giant) shows only to the players who've hit it: by hand, with an
 * arrow or javelin, or with a power, or that it has turned on (its target). It stays while they're within {@link #RANGE}; once they leave, it's gone until
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
		// (0.7.524) a death to one of these is a boss's, to Powers' death by cause
		PowersCompat.tellBosses(BossBars::boss);
		PowersCompat.tellGuilty(Outlaws::is);
	}

	private static void hit(LivingEntity entity, DamageSource source) {
		// the player behind the hit: their own blow, their arrow or javelin, or a power that names them
		// (GTNH patch 6) and a monster with marks enough for a bar (MarkedMonsters), which is no boss anywhere else
		if (source.getEntity() instanceof ServerPlayer player && (boss(entity) || MarkedMonsters.barred(entity))) {
			HITTERS.computeIfAbsent(entity.getUUID(), id -> new HashSet<>()).add(player.getUUID());
		}
	}

	public static boolean boss(Entity entity) {
		// (0.7.137) a wild dragon too; (0.7.138) a villager crowned too; (0.7.142) the Nether forts' lords
		return entity instanceof Folk folk ? folk.leader() : Rulers.is(entity) || Illagers.warlord(entity) || QuestFoes.champion(entity)
			|| entity instanceof com.jaymoskingdoms.dragons.WindDragon dragon && !dragon.isTamed()
			// (0.7.142) the Nether forts' warlords and warbosses
			|| com.jaymoskingdoms.nether.NetherForts.warlord(entity)
			// the fortress knight
			|| com.jaymoskingdoms.nether.FortressKnight.is(entity)
			|| entity instanceof com.jaymoskingdoms.nether.Hornkin hornkin && hornkin.kind() == com.jaymoskingdoms.nether.Hornkin.Kind.WARLORD
			// (0.7.145) the Aether Giants
			|| com.jaymoskingdoms.remnants.Monoliths.giant(entity)
			// (0.7.518) a patron's champion
			|| entity instanceof PatronChampion champion && !champion.decoy()
			// (0.7.610) the grizzly mini-boss
			|| entity instanceof com.jaymoskingdoms.wild.Grizzly
			// the great necromancer
			|| entity instanceof GreatNecromancer
			// (medieval picks) an old age's ruler, once woken
			|| entity instanceof com.jaymoskingdoms.oldage.OldRuler ruler && ruler.awake()
			// the blossom lands' great night things
			|| entity instanceof com.jaymoskingdoms.blossomland.SnowWidow || entity instanceof com.jaymoskingdoms.blossomland.Unburied
			// the brood queen, once woken
			|| entity instanceof com.jaymoskingdoms.wild.nest.BroodQueen queen && !queen.sleeping()
			// a horde's grave-captain
			|| ZombieHordes.captain(entity)
			// (Awakening pick) the Cordon's Arbiter in its Court of Judgment
			|| entity instanceof CordonFolk cordon && cordon.rank() == Cordon.Rank.ARBITER;
	}

	/** Whether {@code player} has struck {@code boss} (or met it) and not left it since: they are in its fight. */
	public static boolean hasHit(LivingEntity boss, ServerPlayer player) {
		Set<UUID> hitters = HITTERS.get(boss.getUUID());
		return hitters != null && hitters.contains(player.getUUID());
	}

	/** {@code player} has met {@code boss} face to face (its entrance played, {@code BossIntros}): its bar shows to them as if they had struck it. */
	public static void met(LivingEntity boss, ServerPlayer player) {
		if (boss(boss)) {
			HITTERS.computeIfAbsent(boss.getUUID(), id -> new HashSet<>()).add(player.getUUID());
		}
	}

	/** {@code boss} (one with a bar) is after {@code player}: its bar shows to them as if they had struck it. */
	public static void hunting(LivingEntity boss, ServerPlayer player) {
		if (!player.isSpectator() && player.isAlive()) {
			HITTERS.computeIfAbsent(boss.getUUID(), id -> new HashSet<>()).add(player.getUUID());
		}
	}

	/** Who {@code boss} is after: its own target, or (a brain-driven mob's) its attack target. */
	private static LivingEntity target(LivingEntity boss) {
		if (boss instanceof Mob mob && mob.getTarget() != null) {
			return mob.getTarget();
		}
		return boss.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET) ? boss.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) : null;
	}

	/** The boss's bar goes to the players who've hit it or that it is after, while they're near; anyone else loses it. */
	public static void show(ServerLevel level, LivingEntity boss, ServerBossEvent bar) {
		show(level, boss, bar, p -> true);
	}

	/** As {@link #show(ServerLevel, LivingEntity, ServerBossEvent)}, only to those of them {@code to} lets see this bar. */
	public static void show(ServerLevel level, LivingEntity boss, ServerBossEvent bar, java.util.function.Predicate<ServerPlayer> to) {
		double r2 = RANGE * RANGE;
		// a boss that has turned on a player shows them its bar too, as if they had struck it
		if (target(boss) instanceof ServerPlayer hunted && hunted.level() == level && hunted.isAlive() && !hunted.isSpectator()
			&& hunted.distanceToSqr(boss) <= r2) {
			HITTERS.computeIfAbsent(boss.getUUID(), id -> new HashSet<>()).add(hunted.getUUID());
		}
		Set<UUID> hitters = HITTERS.get(boss.getUUID());
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
			if (hitters == null || !hitters.contains(player.getUUID()) || player.isSpectator() || !to.test(player)) {
				bar.removePlayer(player);
			}
		}
		if (hitters != null) {
			for (UUID id : hitters) {
				ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
				if (player != null && !player.isSpectator() && to.test(player)) {
					bar.addPlayer(player);
				}
			}
		}
	}
}
