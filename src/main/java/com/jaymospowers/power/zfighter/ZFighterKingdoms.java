package com.jaymospowers.power.zfighter;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import org.jspecify.annotations.Nullable;

/**
 * Stage 6 reads Jaymo's Kingdoms (when it's loaded) by reflection only, like {@code WindStaff}: its settlements (kind,
 * name, seat, wealth), their people, a player's reputation with one, whether he walks the Rogue's path, and how long an
 * elder has been grown. Without Kingdoms every answer is empty and the masters, tournaments and raids don't happen.
 */
final class ZFighterKingdoms {
	/** A settlement: its id, kind (KINGDOM, FIEFDOM, VILLAGE, BARBARIAN), name, seat and treasury, and Kingdoms' own record. */
	record Settlement(int id, String type, String name, BlockPos seat, long treasury, Object handle) {
		boolean realm() {
			return "KINGDOM".equals(type) || "FIEFDOM".equals(type);
		}
	}

	static final long DAY = 24000L;

	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("jaymoskingdoms");
	private static Method ALL, PEOPLE, REPUTATION, SKILLS_OF, ALIGNMENT, GROWN, WORKER_FACTION;
	private static Method F_ID, F_TYPE, F_NAME, F_SEAT, F_FALLEN, F_TREASURY;
	private static Class<?> AGING, WORKER;
	private static Method TRAINED;
	/** (0.15.60) Kingdoms' WorldTiers.of(Player): the world tier a player is in. */
	private static Method TIER_OF;
	private static Method STAT_VALUE;
	private static Object[] STATS;
	private static int STAT_MAX;
	private static final long UNKNOWN = Long.MIN_VALUE, CHILD = Long.MIN_VALUE + 1;

	static {
		if (LOADED) {
			try {
				Class<?> factions = Class.forName("com.jaymoskingdoms.Factions");
				Class<?> faction = Class.forName("com.jaymoskingdoms.Faction");
				ALL = factions.getMethod("all", MinecraftServer.class);
				PEOPLE = Class.forName("com.jaymoskingdoms.Villages").getMethod("people", ServerLevel.class, faction);
				REPUTATION = Class.forName("com.jaymoskingdoms.Reputation").getMethod("get", ServerPlayer.class, int.class);
				Class<?> skills = Class.forName("com.jaymoskingdoms.Skills");
				SKILLS_OF = skills.getMethod("of", net.minecraft.world.entity.player.Player.class);
				ALIGNMENT = Class.forName("com.jaymoskingdoms.Renown").getMethod("alignment", Class.forName("com.jaymoskingdoms.Skills$Progress"));
				AGING = Class.forName("com.jaymoskingdoms.Aging");
				GROWN = AGING.getMethod("grownSince");
				WORKER = Class.forName("com.jaymoskingdoms.Worker");
				WORKER_FACTION = WORKER.getMethod("factionId");
				F_ID = faction.getMethod("id");
				F_TYPE = faction.getMethod("type");
				F_NAME = faction.getMethod("name");
				F_SEAT = faction.getMethod("seat");
				F_FALLEN = faction.getMethod("fallen");
				F_TREASURY = faction.getMethod("treasury");
			} catch (ReflectiveOperationException | LinkageError e) {
				ALL = null;
			}
			try {
				Class<?> stats = Class.forName("com.jaymoskingdoms.Stats");
				Class<?> stat = Class.forName("com.jaymoskingdoms.Stats$Stat");
				STAT_VALUE = stats.getMethod("value", net.minecraft.world.entity.player.Player.class, stat);
				STATS = stat.getEnumConstants();
				STAT_MAX = stats.getField("MAX").getInt(null);
			} catch (ReflectiveOperationException | LinkageError e) {
				STAT_VALUE = null;
			}
			try {
				TIER_OF = Class.forName("com.jaymoskingdoms.WorldTiers").getMethod("of", net.minecraft.world.entity.player.Player.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				TIER_OF = null;
			}
			try {
				TRAINED = Class.forName("com.jaymoskingdoms.ClassPerks").getMethod("trainedWith", ServerPlayer.class, net.minecraft.world.item.ItemStack.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				TRAINED = null;
			}
		}
	}

	/** Whether his Kingdoms class is trained with {@code weapon} (never without Kingdoms, or without a class). */
	static boolean trainedWith(ServerPlayer player, net.minecraft.world.item.ItemStack weapon) {
		if (TRAINED == null) {
			return false;
		}
		try {
			return (boolean)TRAINED.invoke(null, player, weapon);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	/** How far his Kingdoms attributes are maxed, 0-1 (all six at their most: 1), or -1 without Kingdoms. */
	static double attributes(ServerPlayer player) {
		if (STAT_VALUE == null || STATS == null || STATS.length == 0 || STAT_MAX <= 0) {
			return -1.0;
		}
		try {
			int total = 0;
			for (Object stat : STATS) {
				total += (int)STAT_VALUE.invoke(null, player, stat);
			}
			return total / (double)(STATS.length * STAT_MAX);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return -1.0;
		}
	}

	/** (0.15.60) His Kingdoms world tier, from 0 (Hearth, the first), or -1 if it can't be read. */
	static int worldTier(net.minecraft.world.entity.player.Player player) {
		if (TIER_OF == null) {
			return -1;
		}
		try {
			return ((Enum<?>)TIER_OF.invoke(null, player)).ordinal();
		} catch (ReflectiveOperationException | RuntimeException e) {
			return -1;
		}
	}

	private ZFighterKingdoms() {
	}

	static boolean present() {
		return ALL != null;
	}

	/** Every standing settlement (none without Kingdoms). */
	static List<Settlement> settlements(MinecraftServer server) {
		List<Settlement> out = new ArrayList<>();
		if (ALL == null) {
			return out;
		}
		try {
			for (Object f : (List<?>)ALL.invoke(null, server)) {
				if ((boolean)F_FALLEN.invoke(f)) {
					continue;
				}
				out.add(new Settlement((int)F_ID.invoke(f), ((Enum<?>)F_TYPE.invoke(f)).name(), (String)F_NAME.invoke(f), (BlockPos)F_SEAT.invoke(f),
					(long)F_TREASURY.invoke(f), f));
			}
		} catch (ReflectiveOperationException | ClassCastException e) {
			return List.of();
		}
		return out;
	}

	/** Its villagers round the seat (loaded ones). */
	@SuppressWarnings("unchecked")
	static List<Villager> people(ServerLevel level, Settlement settlement) {
		if (PEOPLE == null) {
			return List.of();
		}
		try {
			return (List<Villager>)PEOPLE.invoke(null, level, settlement.handle());
		} catch (ReflectiveOperationException | ClassCastException e) {
			return List.of();
		}
	}

	/** -1000 to 1000; 0 without Kingdoms. */
	static int reputation(ServerPlayer player, int settlement) {
		if (REPUTATION == null) {
			return 0;
		}
		try {
			return (int)REPUTATION.invoke(null, player, settlement);
		} catch (ReflectiveOperationException | ClassCastException e) {
			return 0;
		}
	}

	/** Whether he walks the Rogue's path (his skill tree leans to Stealth). */
	static boolean rogue(ServerPlayer player) {
		if (ALIGNMENT == null) {
			return false;
		}
		try {
			Object pillar = ALIGNMENT.invoke(null, SKILLS_OF.invoke(null, player));
			return pillar instanceof Enum<?> e && "STEALTH".equals(e.name());
		} catch (ReflectiveOperationException e) {
			return false;
		}
	}

	/** Whole days since he came of age, or -1 (a child, not yet seen, or not one of Kingdoms' ageing folk). */
	static long grownDays(ServerLevel level, Entity entity) {
		if (AGING == null || !AGING.isInstance(entity)) {
			return -1L;
		}
		try {
			long since = (long)GROWN.invoke(entity);
			return since == UNKNOWN || since == CHILD ? -1L : (level.getGameTime() - since) / DAY;
		} catch (ReflectiveOperationException e) {
			return -1L;
		}
	}

	/** Which settlement a Kingdoms worker (or its Folk) serves, or -1. */
	static int factionOf(@Nullable Entity entity) {
		if (WORKER == null || !WORKER.isInstance(entity)) {
			return -1;
		}
		try {
			return (int)WORKER_FACTION.invoke(entity);
		} catch (ReflectiveOperationException e) {
			return -1;
		}
	}
}
