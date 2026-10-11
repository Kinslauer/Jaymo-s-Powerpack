package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Villages as factions: a bell that isn't a capital becomes a {@link FactionType#VILLAGE} when a player first comes
 * near (see {@link KingdomEvents}). Its leader is an elder (one of its own villagers, named for it; not a boss), its
 * people are the vanilla villagers round its bell, and its iron golems are its guards (they turn on anyone the village
 * counts as hostile). It gets a shed for its storage, a small builder crew in work clothes, and a little land.
 */
public final class Villages {
	/** A village's people and golems are those this close to its bell. */
	static final double VILLAGE_RADIUS = 48.0;
	/** Land claimed round a village's bell (chunks out each way; only chunks nobody holds). */
	static final int CLAIM_RADIUS = 2;
	/** How often golems look for the village's enemies, and how far (ticks, blocks). */
	static final int GOLEM_CHECK_TICKS = 40;
	static final double GOLEM_RANGE = 24.0;
	/** Reputation for killing a village's elder. */
	static final int REP_KILL_ELDER = -150;

	private Villages() {
	}

	/** A new village at {@code bell}: named, its land claimed, its elder chosen, its shed and crew. */
	public static Faction found(ServerLevel level, BlockPos bell) {
		MinecraftServer server = level.getServer();
		var random = level.getRandom();
		DyeColor color = DyeColor.values()[random.nextInt(DyeColor.values().length)];
		Faction village = Faction.create(Factions.nextId(server), FactionType.VILLAGE, Names.village(random), color, bell,
			KingdomEvents.claim(server, bell, CLAIM_RADIUS));
		Factions.put(server, village);
		chooseElder(level, village);
		Storehouse.build(level, Factions.get(server, village.id()));
		JaymosKingdoms.LOGGER.info("The village of {} at {}", village.name(), bell);
		return Factions.get(server, village.id());
	}

	/** Its elder: the oldest-standing grown villager nearest the bell, named for the village. Returns him (or null if none is near). */
	static @Nullable Villager chooseElder(ServerLevel level, Faction village) {
		List<Villager> villagers = new ArrayList<>(level.getEntitiesOfClass(Villager.class, new AABB(village.seat()).inflate(VILLAGE_RADIUS),
			v -> v.isAlive() && !v.isBaby()));
		if (villagers.isEmpty()) {
			return null;
		}
		BlockPos bell = village.seat();
		villagers.sort(Comparator.comparingDouble(v -> v.distanceToSqr(bell.getX() + 0.5, bell.getY(), bell.getZ() + 0.5)));
		Villager elder = villagers.getFirst();
		if (!McaCompat.keepsOwnName(elder)) {
			elder.setCustomName(Component.translatable("entity.jaymoskingdoms.elder_of", village.name()));
		}
		elder.setPersistenceRequired();
		Factions.update(level.getServer(), village.id(), f -> f.withLeader(Optional.of(elder.getUUID())));
		return elder;
	}

	/** Villages without an elder choose one (when their bell is loaded); golems turn on the village's enemies. */
	static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % GOLEM_CHECK_TICKS != 0) {
			return;
		}
		for (Faction village : Factions.all(server, FactionType.VILLAGE)) {
			if (village.fallen() || !level.isPositionEntityTicking(village.seat())) {
				continue;
			}
			if (village.leader().isEmpty() && now % (GOLEM_CHECK_TICKS * 30) == 0) {
				chooseElder(level, village);
			}
			for (IronGolem golem : guards(level, village)) {
				if (golem.getTarget() != null) {
					continue;
				}
				ServerPlayer enemy = null;
				for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, golem.getBoundingBox().inflate(GOLEM_RANGE))) {
					if (!player.isCreative() && !player.isSpectator() && Reputation.get(player, village.id()) <= Reputation.HOSTILE) {
						enemy = player;
						break;
					}
				}
				if (enemy != null) {
					golem.setTarget(enemy);
				}
			}
		}
	}

	/** A village's guards: its iron golems. */
	public static List<IronGolem> guards(ServerLevel level, Faction village) {
		return level.getEntitiesOfClass(IronGolem.class, new AABB(village.seat()).inflate(VILLAGE_RADIUS), IronGolem::isAlive);
	}

	/** Its people: the villagers round its bell (loaded ones). */
	public static List<Villager> people(ServerLevel level, Faction village) {
		return level.getEntitiesOfClass(Villager.class, new AABB(village.seat()).inflate(VILLAGE_RADIUS), Villager::isAlive);
	}

	/** A villager died: if it was a village's elder, the village mourns and will choose another; his killer is resented. */
	static void villagerDied(ServerLevel level, Villager villager, @Nullable ServerPlayer killer) {
		for (Faction village : Factions.all(level.getServer(), FactionType.VILLAGE)) {
			if (village.leader().isPresent() && village.leader().get().equals(villager.getUUID())) {
				Factions.update(level.getServer(), village.id(), f -> f.withLeader(Optional.empty()));
				if (killer != null) {
					Reputation.change(killer, village, REP_KILL_ELDER);
				}
				for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, villager.getBoundingBox().inflate(VILLAGE_RADIUS))) {
					player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.elder_died", village.name()).withStyle(ChatFormatting.GRAY));
				}
				return;
			}
		}
	}
}
