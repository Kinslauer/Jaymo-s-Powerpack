package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.villager.Villager;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Minecraft Comes Alive (mod id {@value #MOD}, GPL-3.0; nothing of it is copied here) turns villagers into people you
 * befriend, marry and raise children with. When it's loaded, its villages and the factions are one world:
 * <ul>
 * <li>A settlement's people liking you warms the faction to you: a share of your hearts with the MCA village round its
 * seat is added to your reputation with it ({@link #heartsReputation}).</li>
 * <li>Your place in a faction is your place in its village: a soldier counts as an MCA merchant, a knight as a noble, a
 * lord as a mayor; and a faction that's hostile to you makes you an outlaw there ({@link #rankSay}, used by
 * {@code McaTasksMixin}). Monarch stays MCA's own crown to earn.</li>
 * <li>A village's elder keeps their own name (an MCA villager's name is who they are; renaming one renames them in
 * the family tree too).</li>
 * <li>(0.5.21) A realm's workers (builders, farmers, miners, woodcutters) are its MCA villagers, promoted where they
 * stand ({@link #enlist}) or through MCA's Professions menu ({@link #promote}); its old {@link Folk} workers become
 * villagers ({@link #becomeVillager}). See {@link Workers}.</li>
 * <li>(0.5.53) A kingdom's court wizard is one of its elders ({@link CourtWizards}).</li>
 * </ul>
 * Everything that names an MCA class is in {@link McaBridge}, which is only touched while MCA is {@link #LOADED}.
 */
public final class McaCompat {
	/** MCA's mod id. */
	public static final String MOD = "mca";
	public static final boolean LOADED = FabricLoader.getInstance().isModLoaded(MOD);

	/** Reputation per heart of village reputation in MCA (the hearts every villager there has for you, summed). */
	static final float REPUTATION_PER_HEART = 0.2F;
	/** The most (and least) reputation a village's hearts can add. */
	static final int HEARTS_REPUTATION_MOST = 300, HEARTS_REPUTATION_LEAST = -200;
	/** How long a player's hearts-reputation with a faction is remembered before it's looked up again (ticks). */
	static final int CACHE_TICKS = 100;

	/** {@link #rankSay}: no say (MCA's own rank stands), or make them an outlaw. Otherwise the MCA rank's ordinal. */
	public static final int NO_SAY = -1, OUTLAW = 0;
	/** MCA's rank ordinals: peasant, merchant, noble, mayor (MCA's {@code Rank}: outlaw, peasant, ... monarch). */
	static final int MCA_PEASANT = 1, MCA_MERCHANT = 2, MCA_NOBLE = 3, MCA_MAYOR = 4;

	/** Per player: faction id → {when it was looked up, the reputation it adds}. */
	private static final Map<UUID, Map<Integer, long[]>> HEARTS = new ConcurrentHashMap<>();

	private McaCompat() {
	}

	/**
	 * (0.7.87) A player's MCA look as it's saved ({@code McaPlayerSizeMixin}): its Size is held under the top of the
	 * slider unless they're Verdant ({@link Heritage#MCA_SIZE_CAP}).
	 */
	public static void capSaved(ServerPlayer player, net.minecraft.nbt.CompoundTag data) {
		if (Heritage.sizeCapped(player)) {
			McaBridge.capSize(data, Heritage.MCA_SIZE_CAP);
		}
	}

	/** The reputation {@code player}'s hearts with the people round {@code factionId}'s seat add (0 without MCA). */
	static int heartsReputation(ServerPlayer player, int factionId) {
		if (!LOADED) {
			return 0;
		}
		MinecraftServer server = player.level().getServer();
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		Map<Integer, long[]> mine = HEARTS.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
		synchronized (mine) {
			long[] cached = mine.get(factionId);
			if (cached != null && now - cached[0] < CACHE_TICKS && now >= cached[0]) {
				return (int)cached[1];
			}
			int bonus = 0;
			Faction faction = Factions.get(server, factionId);
			if (faction != null && faction.type() != FactionType.BARBARIAN) {
				Integer hearts = McaBridge.villageHearts(level, faction.seat(), player);
				if (hearts != null) {
					bonus = Mth.clamp(Math.round(hearts * REPUTATION_PER_HEART), HEARTS_REPUTATION_LEAST, HEARTS_REPUTATION_MOST);
				}
			}
			mine.put(factionId, new long[] {now, bonus});
			return bonus;
		}
	}

	/**
	 * What the factions say about {@code player}'s MCA rank in the village centred at {@code center}: {@link #OUTLAW} if
	 * the faction there (or the realm whose land it's on) is hostile to them; else the MCA rank their standing earns
	 * (the better of the two factions'); else {@link #NO_SAY}.
	 */
	public static int rankSay(ServerPlayer player, BlockPos center) {
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return NO_SAY;
		}
		ServerLevel level = Factions.level(server);
		Faction near = Factions.around(server, level, center, Villages.VILLAGE_RADIUS);
		Faction land = Factions.at(server, level, center);
		int say = NO_SAY;
		for (Faction f : new Faction[] {near, land}) {
			if (f == null || f.fallen() || f.type() == FactionType.BARBARIAN) {
				continue;
			}
			if (Reputation.get(player, f.id()) <= Reputation.HOSTILE) {
				return OUTLAW;
			}
			Faction.Member member = f.member(player.getUUID());
			if (member != null) {
				say = Math.max(say, mcaRank(Rank.forStanding(member.standing())));
			}
		}
		return say;
	}

	private static int mcaRank(Rank rank) {
		return switch (rank) {
			case PEASANT -> MCA_PEASANT;
			case SOLDIER -> MCA_MERCHANT;
			case KNIGHT -> MCA_NOBLE;
			case LORD -> MCA_MAYOR;
		};
	}

	/** Whether {@code villager} keeps their own name on becoming an elder (MCA's villagers do). */
	static boolean keepsOwnName(Villager villager) {
		return LOADED && McaBridge.isMca(villager);
	}

	/** (0.5.29) An MCA villager (their own menu has the Quests button). */
	static boolean isMca(net.minecraft.world.entity.Entity entity) {
		return LOADED && entity instanceof Villager v && McaBridge.isMca(v);
	}

	/** (0.5.50) An MCA villager's mood, -15 (depressed, crying) to +15 (overjoyed); 0 for a plain one. */
	static int mood(Villager villager) {
		return LOADED ? McaBridge.mood(villager) : 0;
	}

	/** (0.5.50) An MCA villager running scared. */
	static boolean panicking(Villager villager) {
		return LOADED && McaBridge.panicking(villager);
	}

	/** (0.5.29) {@code villager} (if MCA's) warms to {@code player} by {@code hearts}: a quest done for them, a letter brought. */
	static void hearts(LivingEntity villager, ServerPlayer player, int hearts) {
		// (0.7.62) not for a masked stranger: they don't know whom they're warming to
		if (LOADED && villager instanceof Villager v && !Identities.hiddenFrom(player, villager)) {
			McaBridge.hearts(v, player, hearts);
		}
	}

	/** (0.7.61) A new grown villager at {@code at} (MCA's, with MCA), not yet added to the world ({@link Captives}). */
	static @Nullable Villager newVillager(ServerLevel level, net.minecraft.world.phys.Vec3 at) {
		if (LOADED) {
			return McaBridge.newVillager(level, at);
		}
		return net.minecraft.world.entity.EntityTypes.VILLAGER.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
	}

	/** (0.6.9) {@link McaBridge#families}; 0 without MCA. */
	static int families(ServerLevel level, AABB box, int grownPerYoung, int leastGrown, float chance, int soonUnits, boolean takeIn) {
		return LOADED ? McaBridge.families(level, box, grownPerYoung, leastGrown, chance, soonUnits, takeIn) : 0;
	}

	/** (0.6.9) Where {@code villager} lives (their bed): MCA's home, else the vanilla villager's. */
	static java.util.Optional<net.minecraft.core.GlobalPos> home(Villager villager) {
		if (LOADED) {
			java.util.Optional<net.minecraft.core.GlobalPos> home = McaBridge.home(villager);
			if (home.isPresent()) {
				return home;
			}
		}
		net.minecraft.world.entity.ai.Brain<?> brain = villager.getBrain();
		return brain.checkMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.HOME, net.minecraft.world.entity.ai.memory.MemoryStatus.VALUE_PRESENT)
			? brain.getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.HOME) : java.util.Optional.empty();
	}

	/** (0.7.20) {@code villager} is {@code player}'s family in MCA (spouse, child, parent): they always understand each other. */
	static boolean family(Villager villager, ServerPlayer player) {
		return LOADED && McaBridge.family(villager, player);
	}

	/** (0.7.19) {@link #kin}: no kin, kin (family), partners (married, engaged or promised). */
	static final int STRANGERS = 0, KIN = 1, PARTNER = 2;

	/** (0.7.19) How {@code a} and {@code b} are related (MCA's family tree); {@link #STRANGERS} without MCA. */
	static int kin(Villager a, Villager b) {
		return LOADED ? McaBridge.kin(a, b) : STRANGERS;
	}

	/** (0.7.98) How {@code v} was related to someone now dead ({@code dead}, by UUID): {@link #STRANGERS} without MCA. */
	static int kinOf(Villager v, java.util.UUID dead) {
		return LOADED ? McaBridge.kinOf(v, dead) : STRANGERS;
	}

	/** (0.7.19) An MCA villager's temper, by their personality: 1 warm (friendly, upbeat, peaceful), -1 sour (greedy, gloomy), -2 crabby; else 0. */
	static int temper(Villager v) {
		return LOADED ? McaBridge.temper(v) : 0;
	}

	/** (0.7.19) {@code a} and {@code b} might court: grown, unattached, not kin, a man and a woman (MCA only). */
	static boolean courtable(Villager a, Villager b) {
		return LOADED && McaBridge.courtable(a, b);
	}

	/** (0.7.19) An MCA villager's mood goes up (or down) by {@code by}. */
	static void cheer(Villager v, int by) {
		if (LOADED) {
			McaBridge.cheer(v, by);
		}
	}

	/** (0.7.19) {@code a} and {@code b} marry (MCA); true if they did. */
	static boolean marry(Villager a, Villager b) {
		return LOADED && courtable(a, b) && McaBridge.marry(a, b);
	}

	/** (0.7.21) The player an MCA villager was asked to follow (their menu's Follow), while they're following; else null. */
	static java.util.@Nullable UUID following(Villager v) {
		return LOADED ? McaBridge.following(v) : null;
	}

	/** (0.7.21) An MCA villager following someone goes back to going about their day. */
	static void stopFollowing(Villager v) {
		if (LOADED) {
			McaBridge.stopFollowing(v);
		}
	}

	/** (0.5.39) {@code player}'s hearts with {@code villager} (0 without MCA, or for a plain villager). */
	static int heartsOf(Villager villager, ServerPlayer player) {
		return LOADED ? McaBridge.heartsOf(villager, player) : 0;
	}

	/** Tells MCA about the workers' professions (they're kept: not traded with, not made guards, not lost). */
	static void register() {
		if (LOADED) {
			McaBridge.register();
		}
	}

	/** One of {@code f}'s MCA villagers made its {@code role} (or a new one who moves in, if none can be spared); null without MCA. */
	static @Nullable Worker enlist(ServerLevel level, Faction f, int role, BlockPos at, BlockPos home, int radius) {
		return LOADED ? McaBridge.enlist(level, f, role, at, home, radius) : null;
	}

	/** (0.5.53) {@code f}'s court wizard: {@code chosen}, else the eldest of its spare villagers; null if it has no elder (or without MCA). */
	static @Nullable Worker enlistWizard(ServerLevel level, Faction f, @Nullable PathfinderMob chosen) {
		return LOADED ? McaBridge.enlistWizard(level, f, chosen) : null;
	}

	/** (0.5.53) The grown MCA villager nearest {@code player} (a few blocks), or null. */
	static @Nullable PathfinderMob nearestGrown(ServerLevel level, ServerPlayer player) {
		return LOADED ? McaBridge.nearestGrown(level, player) : null;
	}

	/** The MCA villagers in {@code box} who are workers and pass {@code test}. */
	static List<Worker> workers(ServerLevel level, AABB box, Predicate<Worker> test) {
		return LOADED ? McaBridge.workers(level, box, test) : List.of();
	}

	/** Squares a villager's job with its profession (taken on, or let go); true if it's a worker now. */
	static boolean reconcile(ServerLevel level, Worker w) {
		return LOADED && McaBridge.reconcile(level, w);
	}

	/** Someone's talking to it (MCA's menu is open on it). */
	static boolean busy(PathfinderMob mob) {
		return LOADED && McaBridge.busy(mob);
	}

	/** (0.5.54) An elder's hair goes grey ({@code frail}: white), unless someone has dyed it. */
	static void greyHair(PathfinderMob mob, boolean frail) {
		if (LOADED) {
			McaBridge.greyHair(mob, frail);
		}
	}

	/** A worker {@link Folk} of a realm, with MCA loaded: a villager takes its place (same job, same work); true if so. */
	static boolean becomeVillager(ServerLevel level, Folk folk) {
		return LOADED && folk.style() != FactionType.BARBARIAN && McaBridge.convert(level, folk);
	}

	/**
	 * MCA's Professions menu: {@code player} made {@code villager} a {@code job}. True if it's one of the workers' jobs
	 * ({@link Professions#JOBS}), whether or not they could take it on (they're told why not).
	 */
	public static boolean promote(ServerPlayer player, Villager villager, String job) {
		int role = Professions.role(job);
		if (!LOADED || role < 0 || !(villager.level() instanceof ServerLevel level)) {
			return false;
		}
		McaBridge.promote(level, villager, role, player);
		return true;
	}

	/**
	 * (0.5.21) {@code target} is a foe to {@code guard}, an MCA guard in a realm's service: a player the realm counts
	 * hostile, a barbarian, or the folk of a realm it's at war with. (Monsters MCA's guards fight already.)
	 */
	public static boolean foe(LivingEntity guard, LivingEntity target) {
		if (!(guard instanceof Worker w) || w.role() != Folk.GUARD || guard.level().getServer() == null) {
			return false;
		}
		MinecraftServer server = guard.level().getServer();
		int faction = w.factionId();
		if (target instanceof ServerPlayer player) {
			return !player.isCreative() && !player.isSpectator() && Reputation.get(player, faction) <= Reputation.HOSTILE;
		}
		if (target instanceof Folk folk) {
			return folk.factionId() != faction && (folk.style() == FactionType.BARBARIAN || Relations.between(server, faction, folk.factionId()) == Relation.WAR);
		}
		return false;
	}

	/** A player left: forget what was looked up for them. */
	static void forget(@Nullable ServerPlayer player) {
		if (player != null) {
			HEARTS.remove(player.getUUID());
		}
	}

	/**
	 * (0.7.45) Whether MCA's destiny screen is still owed to {@code player}: MCA opens it to everyone whose look isn't
	 * set yet (its {@code launchIntoDestiny}), and keeps it open until they finish.
	 */
	public static boolean destinyPending(ServerPlayer player) {
		return LOADED && McaBridge.destinyPending(player);
	}
}
