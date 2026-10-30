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
	/** (0.15.61) Kingdoms' WorldTiers.of(Player): the world tier a player is in. */
	private static Method TIER_OF;
	private static Method STAT_VALUE;
	/** (0.17.60) Kingdoms' Reputation.change(ServerPlayer, Faction, int): shifts a player's reputation with a settlement. */
	private static Method CHANGE_REPUTATION;
	/** (0.17.61) Kingdoms' Parties.members(ServerPlayer): a player's party, himself included. */
	private static Method PARTY_MEMBERS;
	/** (0.17.61) Kingdoms' Quests.open(ServerPlayer, LivingEntity): a villager's town errands. */
	private static Method OPEN_ERRANDS;
	/** (0.17.61) Kingdoms' Monoliths.nearestFoot(ServerLevel, BlockPos): the nearest known monolith, or null. */
	private static Method NEAREST_MONOLITH;
	/** (0.18.52) Kingdoms' WorldTiers.deed(ServerPlayer, String): a rung's exit deed is done ("pod_flight", "maw_salvage"). */
	private static Method DEED;
	/** (0.18.54) Kingdoms' WorldTiers.open(Player, Tier) and the Tier constants: whether a tier is open to a player (their deeds). */
	private static Method TIER_OPEN;
	private static Object[] TIERS;
	/** (0.18.54) Kingdoms' LandingYard: padsNear(level, pos, range), nameNear(level, pos, range), touched(level, pos). */
	private static Method YARD_PADS, YARD_NAME, YARD_TOUCHED;
	/** (0.18.54) Kingdoms' Careers.flown(pilot, passenger, level, pos) and Careers.firstFind(player, world, kind). */
	private static Method FLOWN, FIRST_FIND;
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
				CHANGE_REPUTATION = Class.forName("com.jaymoskingdoms.Reputation").getMethod("change", ServerPlayer.class,
					Class.forName("com.jaymoskingdoms.Faction"), int.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				CHANGE_REPUTATION = null;
			}
			try {
				PARTY_MEMBERS = Class.forName("com.jaymoskingdoms.Parties").getMethod("members", ServerPlayer.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				PARTY_MEMBERS = null;
			}
			try {
				OPEN_ERRANDS = Class.forName("com.jaymoskingdoms.Quests").getMethod("open", ServerPlayer.class, net.minecraft.world.entity.LivingEntity.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				OPEN_ERRANDS = null;
			}
			try {
				NEAREST_MONOLITH = Class.forName("com.jaymoskingdoms.remnants.Monoliths").getMethod("nearestFoot", ServerLevel.class, BlockPos.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				NEAREST_MONOLITH = null;
			}
			try {
				TIER_OF = Class.forName("com.jaymoskingdoms.WorldTiers").getMethod("of", net.minecraft.world.entity.player.Player.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				TIER_OF = null;
			}
			try {
				DEED = Class.forName("com.jaymoskingdoms.WorldTiers").getMethod("deed", ServerPlayer.class, String.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				DEED = null;
			}
			try {
				Class<?> tier = Class.forName("com.jaymoskingdoms.WorldTiers$Tier");
				TIER_OPEN = Class.forName("com.jaymoskingdoms.WorldTiers").getMethod("open", net.minecraft.world.entity.player.Player.class, tier);
				TIERS = tier.getEnumConstants();
			} catch (ReflectiveOperationException | LinkageError e) {
				TIER_OPEN = null;
			}
			try {
				Class<?> yard = Class.forName("com.jaymoskingdoms.LandingYard");
				YARD_PADS = yard.getMethod("padsNear", ServerLevel.class, BlockPos.class, double.class);
				YARD_NAME = yard.getMethod("nameNear", ServerLevel.class, BlockPos.class, double.class);
				YARD_TOUCHED = yard.getMethod("touched", ServerLevel.class, BlockPos.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				YARD_PADS = null;
			}
			try {
				Class<?> careers = Class.forName("com.jaymoskingdoms.Careers");
				FLOWN = careers.getMethod("flown", ServerPlayer.class, ServerPlayer.class, ServerLevel.class, BlockPos.class);
				FIRST_FIND = careers.getMethod("firstFind", ServerPlayer.class, String.class, String.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				FLOWN = null;
			}
			try {
				TRAINED = Class.forName("com.jaymoskingdoms.ClassPerks").getMethod("trainedWith", ServerPlayer.class, net.minecraft.world.item.ItemStack.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				TRAINED = null;
			}
		}
	}

	/**
	 * (0.17.61, Andy's NOEA picks) {@code player}'s party, himself included (Kingdoms' {@code Parties.members}); just him
	 * without Kingdoms, without parties, or if the call fails.
	 */
	static List<ServerPlayer> partyMembers(ServerPlayer player) {
		if (PARTY_MEMBERS != null) {
			try {
				List<ServerPlayer> members = new ArrayList<>();
				for (Object member : (List<?>)PARTY_MEMBERS.invoke(null, player)) {
					if (member instanceof ServerPlayer other && !members.contains(other)) {
						members.add(other);
					}
				}
				if (!members.contains(player)) {
					members.add(0, player);
				}
				return members;
			} catch (ReflectiveOperationException | RuntimeException e) {
				PARTY_MEMBERS = null;
			}
		}
		return List.of(player);
	}

	/** (0.17.61) Opens {@code giver}'s town errands on {@code player}'s screen (Kingdoms' own work screen); false if it can't be. */
	static boolean openErrands(ServerPlayer player, net.minecraft.world.entity.LivingEntity giver) {
		if (OPEN_ERRANDS == null) {
			return false;
		}
		try {
			OPEN_ERRANDS.invoke(null, player, giver);
			return true;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	/** (0.17.61) The foot of the nearest monolith Kingdoms knows of, or null (none known, or no Kingdoms). */
	static @Nullable BlockPos nearestMonolith(ServerLevel level, BlockPos from) {
		if (NEAREST_MONOLITH == null) {
			return null;
		}
		try {
			return (BlockPos)NEAREST_MONOLITH.invoke(null, level, from);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	/**
	 * (0.18.52, Andy's SevTech picks: "One Deed Per Rung") Tells Kingdoms {@code player} did a deed that opens a world tier
	 * ("pod_flight": a pod trip flown and landed home; "maw_salvage": a part set taken from a Maw wreck). Nothing without Kingdoms.
	 */
	static void deed(ServerPlayer player, String deed) {
		if (DEED == null) {
			return;
		}
		try {
			DEED.invoke(null, player, deed);
		} catch (ReflectiveOperationException | RuntimeException e) {
			DEED = null;
		}
	}

	/**
	 * (0.18.54, Andy's SevTech picks: "Ranks Follow Rungs") The highest world tier open to {@code player} through his deeds, from 0
	 * (Hearth), or -1 without Kingdoms (the Patrol then goes by power alone).
	 */
	static int openTier(net.minecraft.world.entity.player.Player player) {
		if (TIER_OPEN == null || TIERS == null) {
			return -1;
		}
		try {
			int top = 0;
			for (int i = 0; i < TIERS.length; i++) {
				if ((boolean)TIER_OPEN.invoke(null, player, TIERS[i])) {
					top = i;
				}
			}
			return top;
		} catch (ReflectiveOperationException | RuntimeException e) {
			TIER_OPEN = null;
			return -1;
		}
	}

	/**
	 * (0.18.54) A Landing Yard within {@code range} of {@code from}: its beacon post first, then the block to stand a pod on for
	 * each pad. Empty without Kingdoms or a yard.
	 */
	@SuppressWarnings("unchecked")
	static List<BlockPos> yardPads(ServerLevel level, BlockPos from, double range) {
		if (YARD_PADS == null) {
			return List.of();
		}
		try {
			return (List<BlockPos>)YARD_PADS.invoke(null, level, from, range);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return List.of();
		}
	}

	/** (0.18.54) The name of the town whose yard is within {@code range} of {@code from}, or "". */
	static String yardName(ServerLevel level, BlockPos from, double range) {
		if (YARD_NAME == null) {
			return "";
		}
		try {
			return (String)YARD_NAME.invoke(null, level, from, range);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return "";
		}
	}

	/** (0.18.54) A patrol pod stood down at {@code at}: the yard's town is named in the next Tidings. */
	static void yardTouched(ServerLevel level, BlockPos at) {
		if (YARD_TOUCHED == null) {
			return;
		}
		try {
			YARD_TOUCHED.invoke(null, level, at);
		} catch (ReflectiveOperationException | RuntimeException e) {
			YARD_TOUCHED = null;
		}
	}

	/** (0.18.54) {@code pilot} flew a trip and came down at {@code at} (with {@code passenger} in the second seat, or null): a Pilot's work. */
	static void flown(ServerPlayer pilot, @Nullable ServerPlayer passenger, ServerLevel level, BlockPos at) {
		if (FLOWN == null) {
			return;
		}
		try {
			FLOWN.invoke(null, pilot, passenger, level, at);
		} catch (ReflectiveOperationException | RuntimeException e) {
			FLOWN = null;
		}
	}

	/** (0.18.54) A first find ({@link Prospecting#found}): a Prospector's work. */
	static void firstFind(ServerPlayer player, String world, String kind) {
		if (FIRST_FIND == null) {
			return;
		}
		try {
			FIRST_FIND.invoke(null, player, world, kind);
		} catch (ReflectiveOperationException | RuntimeException e) {
			FIRST_FIND = null;
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

	/** (0.15.61) His Kingdoms world tier, from 0 (Hearth, the first), or -1 if it can't be read. */
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

	/** (0.17.60) Shifts {@code player}'s standing with {@code settlement} by {@code by} (negative: lost). False without Kingdoms or if it can't be done. */
	static boolean changeReputation(ServerPlayer player, Settlement settlement, int by) {
		if (CHANGE_REPUTATION == null) {
			return false;
		}
		try {
			CHANGE_REPUTATION.invoke(null, player, settlement.handle(), by);
			return true;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
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
