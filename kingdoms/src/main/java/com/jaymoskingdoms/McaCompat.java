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
	 * (0.7.87) A player's MCA look as it's saved ({@code McaPlayerSizeMixin}): (0.7.290) its Size and Width are kept to
	 * their heritage's body ({@link BodySizes}).
	 */
	public static void capSaved(ServerPlayer player, net.minecraft.nbt.CompoundTag data) {
		McaBridge.keepBody(data, Heritage.of(player));
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

	/** (0.7.187) A new grown MCA guard at {@code at}, in the world and in no one's service; null without MCA. */
	public static @Nullable Villager spawnGuard(net.minecraft.server.level.ServerLevel level, net.minecraft.world.phys.Vec3 at) {
		return LOADED ? McaBridge.spawnGuard(level, at) : null;
	}

	/** (0.7.351) A new MCA zombie villager at {@code at}, in the world ({@code nether.FortressHaunts}); null without MCA or if it failed. */
	public static net.minecraft.world.entity.monster.zombie.@Nullable ZombieVillager spawnZombie(ServerLevel level, net.minecraft.world.phys.Vec3 at) {
		return LOADED ? McaBridge.spawnZombie(level, at) : null;
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

	/** (0.7.184) {@code villager} is {@code player}'s own child in MCA's family tree ({@link Lineage}); never without MCA. */
	static boolean childOf(Villager villager, ServerPlayer player) {
		return LOADED && McaBridge.childOf(villager, player);
	}

	/** (0.7.221) {@code villager} has a player for a parent, partner or child in MCA's family tree, online or not. */
	static boolean playerFamily(Villager villager) {
		return LOADED && McaBridge.playerFamily(villager);
	}

	/** (0.7.221) {@code villager} leaves their MCA village and home ({@link Emigrants}): MCA finds them another where they settle. */
	static void leaveVillage(Villager villager) {
		if (LOADED) {
			McaBridge.leaveVillage(villager);
		}
	}

	/** (0.7.184) An MCA village: its middle and its name. */
	record Town(BlockPos center, String name) {
	}

	/** (0.7.184) Every MCA village in {@code level} ({@link Elders}); none without MCA. */
	static List<Town> towns(ServerLevel level) {
		return LOADED ? McaBridge.towns(level) : List.of();
	}

	/** (0.7.184) An MCA letter to player {@code to} (online or not); nothing without MCA. */
	static void letter(ServerLevel level, UUID to, net.minecraft.network.chat.Component text) {
		if (LOADED) {
			McaBridge.letter(level, to, text);
		}
	}

	/** (0.7.201) The first letter in {@code player}'s MCA mail, taken out of it (the {@link Post} brings it), or empty. */
	static net.minecraft.world.item.ItemStack takeMail(ServerPlayer player) {
		return LOADED ? McaBridge.takeMail(player) : net.minecraft.world.item.ItemStack.EMPTY;
	}

	/** (0.7.184) {@code villager} is a teen or grown, old enough to live on as ({@link Lineage}). */
	static boolean teenOrOlder(Villager villager) {
		return LOADED && McaBridge.teenOrOlder(villager);
	}

	/** (0.7.184) Puts {@code stack} in an MCA villager's pack; what doesn't fit (all of it, for anyone else) comes back. */
	static net.minecraft.world.item.ItemStack stow(PathfinderMob villager, net.minecraft.world.item.ItemStack stack) {
		return LOADED ? McaBridge.stow(villager, stack) : stack;
	}

	/** (0.7.184) {@link McaBridge#takeOver}: the villager left in {@code player}'s old body, or null. */
	static @Nullable PathfinderMob takeOver(ServerPlayer player, Villager child, long elderSince) {
		return LOADED ? McaBridge.takeOver(player, child, elderSince) : null;
	}

	/** (0.7.19) {@link #kin}: no kin, kin (family), partners (married, engaged or promised). */
	static final int STRANGERS = 0, KIN = 1, PARTNER = 2;

	/** (0.7.19) How {@code a} and {@code b} are related (MCA's family tree); {@link #STRANGERS} without MCA. */
	static int kin(Villager a, Villager b) {
		return LOADED ? McaBridge.kin(a, b) : STRANGERS;
	}

	/** (0.7.132) How {@code v} was related to someone now dead ({@code dead}, by UUID): {@link #STRANGERS} without MCA. */
	static int kinOf(Villager v, java.util.UUID dead) {
		return LOADED ? McaBridge.kinOf(v, dead) : STRANGERS;
	}

	/** (0.7.211) An MCA villager's personality id ("relaxed", "crabby"...); "" without MCA or for a plain villager. */
	static String personality(Villager v) {
		return LOADED ? McaBridge.personality(v) : "";
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

	/** (0.7.220) An MCA villager follows {@code player} (MCA's own Follow, as a hired guard does). False without MCA. */
	static boolean follow(Villager v, ServerPlayer player) {
		return LOADED && McaBridge.follow(v, player);
	}

	/**
	 * (0.7.414) {@code n} poor villagers hired to {@code player} from the first minute (MCA's mercenaries, following, in our
	 * {@code outfits}, with {@code health} and {@code damage}, for {@code contract} ticks): the Nobodies road. How many were made;
	 * none without MCA.
	 */
	static int hireNobodies(ServerLevel level, ServerPlayer player, BlockPos around, int n, List<String> outfits, double health, double damage, int contract) {
		return LOADED ? McaBridge.hireNobodies(level, player, around, n, outfits, health, damage, contract) : 0;
	}

	/** (0.7.418) {@code e} is an MCA villager whose hired contract is still running; false without MCA. */
	static boolean contractRunning(net.minecraft.world.entity.Entity e) {
		return LOADED && McaBridge.contractRunning(e);
	}

	/** (0.5.39) {@code player}'s hearts with {@code villager} (0 without MCA, or for a plain villager). */
	static int heartsOf(Villager villager, ServerPlayer player) {
		return LOADED ? McaBridge.heartsOf(villager, player) : 0;
	}

	/** Tells MCA about the workers' professions (they're kept: not traded with, not made guards, not lost). */
	static void register() {
		if (LOADED) {
			McaBridge.register();
			ClothingLibrary.register();
			Wardrobe.register();
			Uniforms.register();
			// (0.7.413) hired villagers learn from fights; drifters for hire
			Learners.register();
			Drifters.register();
		}
	}

	/** (0.7.218) {@code e}'s mother and father (an MCA villager's, those it knows of); none without MCA. */
	static java.util.List<java.util.UUID> parents(net.minecraft.world.entity.Entity e) {
		return LOADED ? McaBridge.parents(e) : java.util.List.of();
	}

	/** (0.7.171) {@code e} is a woman (an MCA villager's gender); false without MCA. */
	static boolean female(LivingEntity e) {
		return LOADED && McaBridge.female(e);
	}

	/**
	 * (0.7.171) {@code mob} (a ruler just crowned, or one who's stepped down) is dressed again from MCA's clothing
	 * library ({@link ClothingLibrary}). True if they now wear royal clothes from it.
	 */
	static boolean redress(PathfinderMob mob) {
		return LOADED && McaBridge.redress(mob);
	}

	/** One of {@code f}'s MCA villagers made its {@code role} (or a new one who moves in, if none can be spared); null without MCA. */
	static @Nullable Worker enlist(ServerLevel level, Faction f, int role, BlockPos at, BlockPos home, int radius) {
		return LOADED ? McaBridge.enlist(level, f, role, at, home, radius) : null;
	}

	/**
	 * (0.7.138) One of {@code f}'s villagers made its ruler ({@code role}: a king, a lord): the late ruler's family first
	 * ({@code house}: who they were), else anyone grown, else someone who moves in. Null without MCA.
	 */
	static @Nullable Worker enlistRuler(ServerLevel level, Faction f, int role, java.util.@Nullable UUID house) {
		return LOADED ? McaBridge.enlistRuler(level, f, role, house) : null;
	}

	/** (0.7.138) A posted folk guard of a realm, with MCA loaded: an MCA guard of the same blood takes the post; true if so. */
	static boolean becomeGuard(ServerLevel level, Folk folk) {
		return LOADED && McaBridge.convertGuard(level, folk);
	}

	/** (0.5.53) {@code f}'s court wizard: {@code chosen}, else the eldest of its spare villagers; null if it has no elder (or without MCA). */
	static @Nullable Worker enlistWizard(ServerLevel level, Faction f, @Nullable PathfinderMob chosen) {
		return LOADED ? McaBridge.enlistWizard(level, f, chosen) : null;
	}

	/** (0.7.291) {@code f}'s new court or camp caster ({@link Casters}): {@code chosen}, else a villager; null without MCA. */
	static @Nullable Worker enlistCaster(ServerLevel level, Faction f, int role, @Nullable PathfinderMob chosen) {
		return LOADED ? McaBridge.enlistCaster(level, f, role, chosen) : null;
	}

	/** (0.7.291) With MCA, a court's necromancer or a camp's caster at peace becomes one of MCA's villagers. True if so (the folk is gone). */
	static boolean becomeCaster(ServerLevel level, Folk folk) {
		return LOADED && Casters.mayConvert(level, folk) && McaBridge.convertCaster(level, folk);
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

	/** (0.7.250) {@code mob} is one of MCA's guards (never without MCA). */
	static boolean isGuard(PathfinderMob mob) {
		return LOADED && McaBridge.isGuard(mob);
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
	/** (0.7.148) With MCA, a camp's raider (or a cove's deckhand) at home becomes one of MCA's villagers ({@link Barbarians}). True if so (the folk is gone). */
	static boolean becomeRaider(ServerLevel level, Folk folk) {
		return LOADED && Barbarians.mayBecomeVillager(level, folk) && McaBridge.convertRaider(level, folk);
	}

	/**
	 * (0.7.294) {@code e} leaves the world for good (a caravan's man gone home, a coachman whose trip is over): an MCA
	 * villager is taken out of MCA's family tree too, so the comings and goings don't fill it.
	 */
	static void vanish(net.minecraft.world.entity.Entity e) {
		if (LOADED) {
			McaBridge.forgetVillager(e);
		}
		e.discard();
	}

	/** (0.7.299) A visitor for the day ({@link Visitors}) at {@code at}: MCA's villager with MCA, else the game's; already in the world, or null. */
	static @Nullable Villager visitor(ServerLevel level, net.minecraft.world.phys.Vec3 at, net.minecraft.world.entity.npc.villager.@Nullable VillagerProfession job, boolean child) {
		if (LOADED) {
			return McaBridge.visitor(level, at, job, child);
		}
		Villager v = net.minecraft.world.entity.EntityTypes.VILLAGER.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
		if (v == null) {
			return null;
		}
		v.snapTo(at.x, at.y, at.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
		if (job != null) {
			v.setVillagerData(v.getVillagerData().withProfession(net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.wrapAsHolder(job)));
		}
		if (child) {
			v.setAge(-24000);
		}
		return level.addFreshEntity(v) ? v : null;
	}

	/** (0.7.294) With MCA, a guard of {@code f}'s for a caravan ({@link Caravans}) at {@code at} on {@code task}, or null. */
	static @Nullable Worker escort(ServerLevel level, Faction f, net.minecraft.core.BlockPos at, float yaw, String task, int radius) {
		return LOADED ? McaBridge.escort(level, f, at, yaw, task, radius) : null;
	}

	/** (0.7.295) With MCA, a stranger of no settlement in {@code role} (a looter), kept to {@code radius} of {@code home}; or null. */
	static net.minecraft.world.entity.@Nullable PathfinderMob stranger(ServerLevel level, net.minecraft.core.BlockPos at, float yaw, int role,
		net.minecraft.core.BlockPos home, int radius) {
		if (!LOADED) {
			return null;
		}
		return McaBridge.stranger(level, at, yaw, role, home, radius);
	}

	/** (0.7.295) With MCA, an assassin ({@link Assassins}): a villager stranger at {@code at}, on his way to {@code mark}; or null. */
	static net.minecraft.world.entity.@Nullable PathfinderMob assassin(ServerLevel level, net.minecraft.core.BlockPos at, float yaw, net.minecraft.core.BlockPos mark) {
		return LOADED ? McaBridge.stranger(level, at, yaw, Assassins.ASSASSIN, mark, 64) : null;
	}

	/**
	 * (0.7.296) With MCA, {@code f}'s harbour shipwright ({@link Navies}) as an MCA villager by its pier at {@code shore}:
	 * in {@code folk}'s place (his blood and spot; the folk is left for the caller), or new to the realm; or null.
	 */
	static net.minecraft.world.entity.@Nullable PathfinderMob shipwright(ServerLevel level, Faction f, @Nullable Folk folk, net.minecraft.core.BlockPos shore) {
		return LOADED ? McaBridge.shipwright(level, f, folk, shore) : null;
	}

	/** (0.7.294) With MCA, one of {@code f}'s villagers to drive its horses ({@link Drivers}), or null. */
	static @Nullable Worker driver(ServerLevel level, Faction f, net.minecraft.core.BlockPos at, float yaw, String task, int radius) {
		return LOADED ? McaBridge.driver(level, f, at, yaw, task, radius) : null;
	}

	/**
	 * (0.7.295) With MCA, a raider just mustered ({@link Raids}) as an MCA villager in his place (a barbarian, a pirate,
	 * an orc of the war band), or null (he stays folk).
	 */
	static net.minecraft.world.entity.@Nullable PathfinderMob raider(ServerLevel level, Folk folk) {
		if (!LOADED) {
			return null;
		}
		return Greenskins.brood(folk.role()) != null ? McaBridge.greenskinOf(level, folk) : McaBridge.raiderOf(level, folk);
	}

	/** (0.7.292) With MCA, a goblin village's or an orc camp's own at home becomes one of MCA's villagers ({@link Greenskins}). True if so (the folk is gone). */
	static boolean becomeGreenskin(ServerLevel level, Folk folk) {
		return LOADED && Greenskins.mayConvert(level, folk) && McaBridge.convertGreenskin(level, folk);
	}

	/**
	 * (0.7.242) With MCA, a ship's crewman as one of its villagers: in {@code folk}'s place (set up for its role), in
	 * the world, with its blood, gear and side; (0.7.259) a fisherman (a sailor) in no armour, their weapons
	 * stowed till a fight. Null without MCA (the folk sails).
	 */
	static @Nullable PathfinderMob crewman(ServerLevel level, Folk folk) {
		return LOADED ? McaBridge.crewman(level, folk) : null;
	}

	/**
	 * (0.7.272) Andy: "there shouldnt be any female sailors". With MCA, a man in {@code her} place (one of a ship's crew
	 * from before): her blood, gear, role and side. Null without MCA or if one couldn't be made. She's left for the caller.
	 */
	static @Nullable PathfinderMob recast(ServerLevel level, PathfinderMob her) {
		return LOADED ? McaBridge.recast(level, her) : null;
	}

	/** (0.7.272) {@code mob} (one of MCA's villagers) in their set outfit if they're one of our named characters ({@code Uniforms}). */
	static void uniform(PathfinderMob mob) {
		if (LOADED) {
			McaBridge.uniform(mob);
		}
	}

	/** (0.7.246) {@code villager} (one of MCA's) has seen {@code player} before: no stranger's greeting from them. */
	static void met(net.minecraft.world.entity.LivingEntity villager, net.minecraft.server.level.ServerPlayer player) {
		if (LOADED) {
			McaBridge.met(villager, player);
		}
	}

	/**
	 * (0.7.246, from MCA's dialogue, so MCA is loaded) Its menu opening on {@code villager}: a shipmate's or a story's
	 * opening instead of MCA's own; true if so.
	 */
	public static boolean storyOpening(net.minecraft.world.entity.LivingEntity villager, net.minecraft.server.level.ServerPlayer player) {
		return LOADED && McaBridge.storyOpening(villager, player);
	}

	/** (0.7.380, from MCA's dialogue) Its menu opening on {@code villager}: what just passed between you, instead of MCA's own; true if so. */
	public static boolean dealingOpening(net.minecraft.world.entity.LivingEntity villager, net.minecraft.server.level.ServerPlayer player) {
		return LOADED && McaBridge.dealingOpening(villager, player);
	}

	/** (0.7.397, from MCA's dialogue) Its menu opening on an outlaw ({@link Outlaws}): a curt word, never a stranger's welcome; true if so. */
	public static boolean outlawOpening(net.minecraft.world.entity.LivingEntity villager, net.minecraft.server.level.ServerPlayer player) {
		return LOADED && McaBridge.outlawOpening(villager, player);
	}

	/** (0.7.424) The opening of one held down for questioning ({@link McaBridge#questioningOpening}). */
	public static boolean questioningOpening(net.minecraft.world.entity.LivingEntity villager, net.minecraft.server.level.ServerPlayer player) {
		return LOADED && McaBridge.questioningOpening(villager, player);
	}

	/** (0.7.405) A Z Fighter character's own opening, never MCA's stranger's welcome ({@link McaBridge#zfighterOpening}). */
	public static boolean zfighterOpening(net.minecraft.world.entity.LivingEntity villager, net.minecraft.server.level.ServerPlayer player) {
		return LOADED && McaBridge.zfighterOpening(villager, player);
	}

	/** (0.7.246) A crewman (one of MCA's villagers) with no armour packed, and a sailor by trade. */
	static void toSea(PathfinderMob mob) {
		if (LOADED) {
			McaBridge.toSea(mob);
		}
	}

	/** (0.7.148) A fallen camp's MCA villagers scatter (gone, as its folk are). */
	static void scatterRaiders(ServerLevel level, int camp, AABB box) {
		if (LOADED) {
			// ((0.7.292) its casters too)
			for (Worker w : McaBridge.workers(level, box, w -> (Barbarians.villagerRole(w.role()) || Casters.isOffice(w.role())) && w.factionId() == camp)) {
				level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, w.mob().getX(), w.mob().getY() + 1.0, w.mob().getZ(), 10, 0.3, 0.5, 0.3, 0.02);
				w.mob().discard();
			}
		}
	}

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
		if (com.jaymoskingdoms.nether.AvatarCamps.avatar(guard)) {
			// (0.7.187) a Flame Avatar of the Nether: Hornkin, piglins, and strangers at its camp
			return com.jaymoskingdoms.nether.AvatarCamps.prey(guard, target);
		}
		Boolean trial = Trials.foe(guard, target);
		if (trial != null) {
			// (0.7.429) a trial under way: the pit's champion goes for his challenger alone, the rest of the camp leaves them be
			return trial;
		}
		if (Tribes.of(guard) != null && (Pedlars.party(target) || Tribes.spares(guard, target))) {
			// (0.7.425) no greenskin sets on a pedlar's party; a tribe under truce with a player, or a pedlar's party, spares them
			return false;
		}
		if (guard instanceof Worker w && Barbarians.villagerRole(w.role()) && guard.level().getServer() != null) {
			// (0.7.148) a camp's MCA villager: what its raiders (or pirates) go for
			return Barbarians.villagerPrey(w.role(), w.factionId(), target);
		}
		if (Assassins.revealed(target)) {
			// (0.7.295) an assassin with his cloak off
			return guard instanceof Worker g && (g.role() == Folk.GUARD || g.role() == Folk.ARCHER);
		}
		if (!(guard instanceof Worker w) || w.role() != Folk.GUARD && w.role() != Folk.ARCHER || guard.level().getServer() == null) {
			return false;
		}
		MinecraftServer server = guard.level().getServer();
		int faction = w.factionId();
		if (target instanceof ServerPlayer player) {
			// (0.7.220) a Master Courier passes freely
			return !player.isCreative() && !player.isSpectator() && Reputation.get(player, faction) <= Reputation.HOSTILE && !Careers.passes(player, faction);
		}
		if (target instanceof Folk folk) {
			return folk.factionId() != faction && (folk.style() == FactionType.BARBARIAN || Relations.between(server, faction, folk.factionId()) == Relation.WAR);
		}
		if (target instanceof Worker other && (other.role() == Folk.GUARD || other.role() == Folk.ARCHER) && other.factionId() >= 0 && other.factionId() != faction
			&& Relations.between(server, faction, other.factionId()) == Relation.WAR) {
			// (0.7.295) a realm's at war: its guards, archers and soldiers, villagers now as its own are
			return true;
		}
		// (0.7.148) a barbarian camp's MCA villager
		return Barbarians.isCampVillager(target);
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
