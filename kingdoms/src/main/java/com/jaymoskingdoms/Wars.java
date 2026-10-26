package com.jaymoskingdoms;

import com.jaymoskingdoms.WarData.Side;
import com.jaymoskingdoms.WarData.War;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

/**
 * War between realms (kingdoms and fiefdoms). A realm declares war (its NPC king on his own, out of rivalry with a
 * weaker neighbour, or a member lord with {@code /kingdom war declare}); after a day's warm-up, announced to everyone,
 * the relation becomes {@link Relation#WAR}. Each side is a <b>principal</b> realm and every fiefdom beneath it: vassals
 * follow their overlord into its wars, and an overlord defends its vassals (a war declared by or on a fiefdom is its
 * overlord's). While it lasts, construction stops on both sides ({@link Economy#atPeace}), their fighting folk attack
 * each other (and the other side's sworn players) on sight, {@link WarParties} march on the enemy's land, and banners
 * planted in enemy land and held claim it ({@link Banners}).
 *
 * <p>The war score counts enemy folk killed, enemy leaders killed, chunks taken and goods carried off from the enemy's
 * storage. Peace comes at a score threshold ({@link #WIN_SCORE}), after {@link #MAX_WAR_TICKS}, or when one side's lord
 * offers it and the other accepts (an NPC king answers on his own), on terms set by the lead: white peace, tribute (real
 * goods from storage and emeralds from the treasury), land ceded, or vassalage. Killing the enemy principal's ruler, or
 * holding a banner at its seat, ends the war at once in the victor's favour (vassalage). Players who fight in a war gain
 * reputation with the side they help; the allies of each side take note. All of it is saved ({@link WarData}).
 */
public final class Wars {
	/** Declared war starts this long after (ticks: a day). */
	static final long WARMUP_TICKS = 24000L;
	/** How often wars are looked at (ticks). */
	static final int CHECK_TICKS = 100;
	/** NPC kings: how often one may declare (ticks), the chance a day that one with a weaker rival near declares, how near (blocks). */
	static final int DECLARE_CHECK_TICKS = 1200;
	static final float DECLARE_CHANCE_PER_DAY = 0.3F;
	static final double DECLARE_RANGE = 1000.0;
	/** An NPC king only declares on a rival whose army is this much smaller than his own (his army ≥ theirs × this). */
	static final double ARMY_EDGE = 1.25;
	/** A leader counts as this many soldiers in an army. */
	static final int LEADER_ARMY_WORTH = 4;
	/** Rivalry: kingdoms this near each other may become rivals, with this chance a day per pair. */
	static final double RIVALRY_RANGE = 800.0;
	static final float RIVALRY_CHANCE = 0.15F;
	/** A war ends this long after it starts (ticks: five days), on the terms its score gives. */
	static final long MAX_WAR_TICKS = 24000L * 5;
	/** After a peace, the same two can't go to war again for this long (ticks: three days). */
	static final long TRUCE_TICKS = 24000L * 3;
	/** The war score: per enemy folk killed, per enemy leader (a lord or a king) killed, per chunk taken, and per this many items plundered. */
	static final int SCORE_KILL = 2, SCORE_LEADER = 40, SCORE_CHUNK = 10, LOOT_PER_SCORE = 16;
	/** Holding the seat of a fiefdom on the other side counts as this many chunks. */
	static final int SEAT_CHUNKS_WORTH = 3;
	/** A side with this score that leads by {@link #WIN_LEAD} wins the war. */
	static final int WIN_SCORE = 120, WIN_LEAD = 50;
	/** The terms, by the winner's lead: tribute from this lead, land ceded from this, vassalage from this. */
	static final int TRIBUTE_LEAD = 15, CEDE_LEAD = 45, VASSAL_LEAD = 90;
	/** Tribute: this share of the loser's treasury (at least {@code TRIBUTE_MIN_EMERALDS} if it has them), and this many loads of goods of up to a stack each. */
	static final double TRIBUTE_SHARE = 0.3;
	static final int TRIBUTE_MIN_EMERALDS = 10, TRIBUTE_LOADS = 6, TRIBUTE_LOAD_MAX = 64;
	/** When the goods can't be moved (storage not loaded), they're paid in this many emeralds per load instead (as far as the treasury goes). */
	static final int TRIBUTE_EMERALDS_PER_LOAD = 4;
	/** Land ceded: this many chunks (nearest the winner first), never within this many chunks of the loser's seat. */
	static final int CEDE_CHUNKS = 4, CEDE_SEAT_KEEP = 2;
	/** A peace offer stands this long (ticks); an NPC king answers it after this long. */
	static final long OFFER_TICKS = 2400L, NPC_ANSWER_TICKS = 200L;
	/** An NPC king accepts a white peace once the war is this old (ticks), or any peace when he's behind by this much. */
	static final long WHITE_PEACE_MIN_TICKS = 24000L * 2;
	static final int NPC_YIELD_LEAD = 30;
	/** Unseen battles: every this many ticks, a war nobody is watching (no player this near either seat) is fought out in a skirmish of this many blows. */
	static final long SKIRMISH_TICKS = 6000L;
	static final int SKIRMISH_BLOWS = 4;
	static final double OBSERVE_RANGE = 128.0;
	/** Reputation for players who fight: with every realm of the side they help, per enemy folk and per enemy leader they kill. */
	static final int REP_WAR_KILL = 6, REP_WAR_LEADER = 50;
	/** For taking a chunk: the one who planted the banner, and the side's sworn players who held it with them; the land's owner. */
	static final int REP_WAR_CHUNK = 15, REP_WAR_HOLDER = 5, REP_WAR_CHUNK_LOST = -10;
	/** For tearing down an enemy banner in your side's land. */
	static final int REP_WAR_RETAKE = 8;
	/** For plunder: a point per this many items, at most this many a time. */
	static final int REP_PLUNDER_ITEMS = 16, REP_PLUNDER_MAX = 8;
	/** The allies of the side helped (and of the side hurt) react this much, each time. */
	static final int REP_ALLY_REACT = 2;
	/** Fighting folk attack sworn players of a realm at war with theirs on sight. */
	static final boolean HUNT_ENEMY_PLAYERS = true;
	/** The longest overlord chain followed. */
	static final int MAX_CHAIN = 16;

	/** The terms of a peace. */
	enum Terms {
		WHITE, TRIBUTE, CEDE, VASSAL;

		String key() {
			return "message.jaymoskingdoms.war.terms." + this.name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	private Wars() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Wars::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register(Wars::died);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> WarParties.loaded(entity));
		ServerLifecycleEvents.SERVER_STOPPING.register(Banners::restoreDefenders);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			WarParties.clear();
			Banners.clear();
		});
		Banners.register();
		// (0.7.224) creeds, tension, great wars
		GreatWars.register();
	}

	// ---- sides ---------------------------------------------------------------------------------------------------

	/** The realm at the head of {@code faction}'s side: its overlord's overlord, as far as it goes. */
	static Faction top(MinecraftServer server, Faction faction) {
		Faction f = faction;
		for (int i = 0; i < MAX_CHAIN && f.overlord() >= 0; i++) {
			Faction up = Factions.get(server, f.overlord());
			if (up == null || up.fallen() || !up.type().isRealm()) {
				break;
			}
			f = up;
		}
		return f;
	}

	/** {@code faction} is {@code principal}, or a vassal beneath it (a standing realm). */
	static boolean onSide(MinecraftServer server, int faction, int principal) {
		Faction f = Factions.get(server, faction);
		for (int i = 0; i < MAX_CHAIN && f != null; i++) {
			if (f.fallen() || !f.type().isRealm()) {
				return false;
			}
			if (f.id() == principal) {
				return true;
			}
			if (f.overlord() < 0) {
				// (0.7.224) a free realm pledged to its side in a great war
				return GreatWars.pledgedTo(server, f.id()) == principal;
			}
			f = Factions.get(server, f.overlord());
		}
		return false;
	}

	/** (0.7.224) The principal {@code f} fights under: its top, or the one its top is pledged to in a great war. */
	static Faction warTop(MinecraftServer server, Faction f) {
		Faction top = top(server, f);
		int pledged = GreatWars.pledgedTo(server, top.id());
		Faction p = pledged < 0 ? null : Factions.get(server, pledged);
		return p != null && !p.fallen() ? p : top;
	}

	/** The standing realms on {@code principal}'s side (it first). */
	static List<Faction> members(MinecraftServer server, int principal) {
		List<Faction> out = new ArrayList<>();
		Faction head = Factions.get(server, principal);
		if (head != null && !head.fallen()) {
			out.add(head);
		}
		for (Faction f : Factions.all(server)) {
			if (f.id() != principal && f.type().isRealm() && !f.fallen() && onSide(server, f.id(), principal)) {
				out.add(f);
			}
		}
		return out;
	}

	/** The principal of the side {@code faction} fights on in {@code war}, or -1. */
	static int sideOf(MinecraftServer server, War war, int faction) {
		if (onSide(server, faction, war.attacker().principal())) {
			return war.attacker().principal();
		}
		return onSide(server, faction, war.defender().principal()) ? war.defender().principal() : -1;
	}

	static int otherSide(War war, int principal) {
		return war.attacker().principal() == principal ? war.defender().principal() : war.attacker().principal();
	}

	/** The war (declared or under way) between the sides of principals {@code a} and {@code b}. */
	static @Nullable War between(MinecraftServer server, int a, int b) {
		for (War war : WarData.wars(server)) {
			if (war.involves(a) && war.involves(b) && a != b) {
				return war;
			}
		}
		return null;
	}

	/** The war under way in which factions {@code a} and {@code b} are on opposite sides. */
	static @Nullable War activeBetween(MinecraftServer server, int a, int b) {
		for (War war : WarData.wars(server)) {
			if (!war.active()) {
				continue;
			}
			int sa = sideOf(server, war, a), sb = sideOf(server, war, b);
			if (sa >= 0 && sb >= 0 && sa != sb) {
				return war;
			}
		}
		return null;
	}

	/** The wars {@code faction} is in (declared or under way). */
	static List<War> warsOf(MinecraftServer server, int faction) {
		return WarData.wars(server).stream().filter(w -> sideOf(server, w, faction) >= 0).toList();
	}

	/** {@code principal}'s army: the guards and archers of every realm on its side, and their leaders. */
	public static int army(MinecraftServer server, int principal) {
		int army = 0;
		for (Faction f : members(server, principal)) {
			army += f.holdings().count(Folk.GUARD) + f.holdings().count(Folk.ARCHER) + (f.leader().isPresent() ? LEADER_ARMY_WORTH : 0);
		}
		// (0.7.224) and the sellswords in its war chests
		return army + GreatWars.mercs(server, principal);
	}

	/** A member of lord rank of any realm on {@code principal}'s side. */
	static boolean hasLords(MinecraftServer server, int principal) {
		for (Faction f : members(server, principal)) {
			for (Faction.Member m : f.members().values()) {
				if (Rank.forStanding(m.standing()) == Rank.LORD) {
					return true;
				}
			}
		}
		return false;
	}

	static boolean isLord(Faction f, UUID player) {
		Faction.Member m = f.member(player);
		return m != null && Rank.forStanding(m.standing()) == Rank.LORD;
	}

	static String name(MinecraftServer server, int id) {
		Faction f = Factions.get(server, id);
		return f == null ? "?" : f.name();
	}

	// ---- queries for other systems -------------------------------------------------------------------------------

	/** Factions {@code a} and {@code b} are at war (a war under way: their relation is WAR). Barbarians aren't counted. */
	public static boolean enemies(MinecraftServer server, int a, int b) {
		if (a == b) {
			return false;
		}
		Faction fa = Factions.get(server, a), fb = Factions.get(server, b);
		return fa != null && fb != null && fa.type().isRealm() && fb.type().isRealm() && Relations.between(fa, fb) == Relation.WAR;
	}

	/** {@code player} is sworn to a realm at war with {@code faction}. */
	public static boolean enemy(ServerPlayer player, Faction faction) {
		MinecraftServer server = player.level().getServer();
		Faction theirs = Factions.of(server, player.getUUID());
		return theirs != null && enemies(server, theirs.id(), faction.id());
	}

	/** War-time goals (from {@link Folk#registerGoals}): fighting folk attack the folk of realms at war with theirs, and those realms' sworn players. */
	static void addGoals(Folk folk, GoalSelector targets) {
		targets.addGoal(2, new NearestAttackableTargetGoal<>(folk, Folk.class, true,
			(target, level) -> folk.combatant() && target instanceof Folk other && enemies(level.getServer(), folk.factionId(), other.factionId())));
		if (HUNT_ENEMY_PLAYERS) {
			targets.addGoal(2, new NearestAttackableTargetGoal<>(folk, Player.class, true,
				(target, level) -> folk.combatant() && target instanceof ServerPlayer player && !player.isCreative() && !player.isSpectator()
					&& enemySworn(level.getServer(), player, folk.factionId())));
		}
	}

	private static boolean enemySworn(MinecraftServer server, ServerPlayer player, int faction) {
		Faction theirs = Factions.of(server, player.getUUID());
		return theirs != null && enemies(server, theirs.id(), faction);
	}

	// ---- declaring -----------------------------------------------------------------------------------------------

	/**
	 * {@code from} declares war on {@code on} (each stands for its whole side). Returns null when declared, or the key
	 * of the reason it can't be.
	 */
	static @Nullable String declare(MinecraftServer server, Faction from, Faction on, boolean skipWarmup) {
		if (from.fallen() || on.fallen() || !from.type().isRealm() || !on.type().isRealm()) {
			return "message.jaymoskingdoms.war.not_realm";
		}
		Faction a = top(server, from), d = top(server, on);
		if (a.id() == d.id()) {
			return "message.jaymoskingdoms.war.same_side";
		}
		if (between(server, a.id(), d.id()) != null) {
			return "message.jaymoskingdoms.war.already";
		}
		long now = Factions.level(server).getGameTime();
		if (WarData.truce(server, a.id(), d.id(), now)) {
			return "message.jaymoskingdoms.war.truce";
		}
		if (a.relation(d.id()) == Relation.ALLIED) {
			return "message.jaymoskingdoms.war.allied";
		}
		// (0.7.224) a realm pledged to another's great war fights only that one
		if (GreatWars.pledgedTo(server, a.id()) >= 0 || GreatWars.pledgedTo(server, d.id()) >= 0) {
			return "message.jaymoskingdoms.war.pledged";
		}
		int id = WarData.book(server).nextId();
		War war = new War(id, Side.of(a.id()), Side.of(d.id()), now, skipWarmup ? now : now + WARMUP_TICKS, false, -1, 0L);
		WarData.update(server, b -> {
			List<War> wars = new ArrayList<>(b.wars());
			wars.add(war);
			return b.withWars(wars).withNextId(id + 1);
		});
		// a rivalry at least, from now on
		if (a.relation(d.id()) == Relation.NEUTRAL) {
			Relations.set(server, a.id(), d.id(), Relation.RIVAL);
		}
		KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.war.declared", a.name(), d.name(),
			(int)(WARMUP_TICKS / 1200L)).withStyle(ChatFormatting.RED));
		if (from.id() != a.id() || on.id() != d.id()) {
			KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.war.sides", from.name(), a.name(), on.name(), d.name())
				.withStyle(ChatFormatting.GOLD));
		}
		JaymosKingdoms.LOGGER.info("{} declared war on {} (war {})", a.name(), d.name(), id);
		if (skipWarmup) {
			begin(server, war);
		}
		return null;
	}

	/** The warm-up is over: the war is on. */
	private static void begin(MinecraftServer server, War war) {
		WarData.updateWar(server, war.id(), w -> w.withActive(true));
		syncRelations(server);
		KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.war.begun", name(server, war.attacker().principal()),
			name(server, war.defender().principal())).withStyle(ChatFormatting.DARK_RED));
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.0F, 0.7F);
		}
		// (0.7.224) between two rich realms, a great war
		GreatWars.begun(server, war);
	}

	/** (0.7.224) The score that wins {@code war}, and how long it may last (ticks): more for a great war. */
	static int winScore(MinecraftServer server, War war) {
		return GreatWars.great(server, war) ? (int)Math.round(WIN_SCORE * GreatWars.SCORE_TIMES) : WIN_SCORE;
	}

	static long maxTicks(MinecraftServer server, War war) {
		return GreatWars.great(server, war) ? MAX_WAR_TICKS * GreatWars.LENGTH_TIMES : MAX_WAR_TICKS;
	}

	/**
	 * Keeps the stored relations in step with the wars: WAR between every pair of realms on opposite sides of a war
	 * under way; a stored war that no war accounts for (an operator's, from {@code /kingdom relation}) becomes a war
	 * under way at once; pairs no longer at war are set neutral.
	 */
	static void syncRelations(MinecraftServer server) {
		List<Faction> realms = Factions.all(server).stream().filter(f -> f.type().isRealm() && !f.fallen()).toList();
		// adopt stored wars nobody declared
		for (int i = 0; i < realms.size(); i++) {
			for (int j = i + 1; j < realms.size(); j++) {
				Faction a = realms.get(i), b = realms.get(j);
				if (a.relation(b.id()) != Relation.WAR) {
					continue;
				}
				Faction pa = top(server, a), pb = top(server, b);
				if (pa.id() != pb.id() && between(server, pa.id(), pb.id()) == null) {
					long now = Factions.level(server).getGameTime();
					int id = WarData.book(server).nextId();
					War war = new War(id, Side.of(pa.id()), Side.of(pb.id()), now, now, true, -1, 0L);
					WarData.update(server, book -> {
						List<War> wars = new ArrayList<>(book.wars());
						wars.add(war);
						return book.withWars(wars).withNextId(id + 1);
					});
					KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.war.begun", pa.name(), pb.name())
						.withStyle(ChatFormatting.DARK_RED));
				}
			}
		}
		Set<String> desired = new HashSet<>();
		for (War war : WarData.wars(server)) {
			if (!war.active()) {
				continue;
			}
			for (Faction a : members(server, war.attacker().principal())) {
				for (Faction d : members(server, war.defender().principal())) {
					if (a.id() != d.id()) {
						desired.add(WarData.pair(a.id(), d.id()));
					}
				}
			}
		}
		for (int i = 0; i < realms.size(); i++) {
			for (int j = i + 1; j < realms.size(); j++) {
				Faction a = realms.get(i), b = realms.get(j);
				boolean want = desired.contains(WarData.pair(a.id(), b.id()));
				Relation stored = a.relation(b.id());
				if (want && stored != Relation.WAR) {
					Relations.set(server, a.id(), b.id(), Relation.WAR);
				} else if (!want && stored == Relation.WAR) {
					Relations.set(server, a.id(), b.id(), Relation.NEUTRAL);
				}
			}
		}
	}

	// ---- the clock -----------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		Banners.tick(server, level, now);
		WarParties.tick(server, level, now);
		if (now % CHECK_TICKS == 0) {
			check(server, level, now);
		}
		if (now % DECLARE_CHECK_TICKS == 0) {
			stirRivalries(server, level);
			considerWar(server, level);
		}
	}

	private static void check(MinecraftServer server, ServerLevel level, long now) {
		boolean begun = false;
		for (War war : List.copyOf(WarData.wars(server))) {
			Faction a = Factions.get(server, war.attacker().principal());
			Faction d = Factions.get(server, war.defender().principal());
			if (a == null || d == null || a.fallen() || d.fallen()) {
				Faction standing = a != null && !a.fallen() ? a : d != null && !d.fallen() ? d : null;
				end(server, war, Component.translatable("message.jaymoskingdoms.war.ended_fallen", standing == null ? "?" : standing.name(),
					a == null || a.fallen() ? name(server, war.attacker().principal()) : name(server, war.defender().principal())));
				continue;
			}
			if (top(server, a).id() == top(server, d).id() || onSide(server, a.id(), d.id()) || onSide(server, d.id(), a.id())) {
				end(server, war, Component.translatable("message.jaymoskingdoms.war.ended_joined", a.name(), d.name()));
				continue;
			}
			if (!war.active()) {
				if (now >= war.startsAt()) {
					begin(server, war);
					begun = true;
				}
				answerOffer(server, war, now);
				continue;
			}
			if (answerOffer(server, war, now)) {
				continue;
			}
			War current = WarData.war(server, war.id());
			if (current == null) {
				continue;
			}
			Side sa = current.attacker(), sd = current.defender();
			int lead = Math.abs(sa.score() - sd.score());
			Side ahead = sa.score() >= sd.score() ? sa : sd;
			if (ahead.score() >= winScore(server, current) && lead >= WIN_LEAD) {
				conclude(server, current, ahead.principal(), false, "message.jaymoskingdoms.war.reason.victory");
				continue;
			}
			if (now - current.startsAt() >= maxTicks(server, current)) {
				conclude(server, current, ahead.principal(), false, "message.jaymoskingdoms.war.reason.exhausted");
				continue;
			}
			skirmish(server, level, current, now);
		}
		if (!begun && now % (CHECK_TICKS * 4) == 0) {
			syncRelations(server);
		}
	}

	/** A war nobody's watching is fought out of sight: a few blows, each landing for one side or the other by the size of their armies. */
	private static void skirmish(MinecraftServer server, ServerLevel level, War war, long now) {
		if (now % SKIRMISH_TICKS >= CHECK_TICKS || now - war.startsAt() < SKIRMISH_TICKS || WarParties.fighting(war.id())) {
			return;
		}
		Faction a = Factions.get(server, war.attacker().principal()), d = Factions.get(server, war.defender().principal());
		if (a == null || d == null || Raids.playerNear(level, a.seat(), OBSERVE_RANGE) || Raids.playerNear(level, d.seat(), OBSERVE_RANGE)) {
			return;
		}
		int armyA = Math.max(1, army(server, a.id())), armyD = Math.max(1, army(server, d.id()));
		RandomSource random = level.getRandom();
		int killsA = 0, killsD = 0;
		int blows = GreatWars.great(server, war) ? SKIRMISH_BLOWS * GreatWars.BLOWS_TIMES : SKIRMISH_BLOWS;
		for (int i = 0; i < blows; i++) {
			if (random.nextInt(armyA + armyD) < armyA) {
				killsA++;
			} else {
				killsD++;
			}
		}
		int ka = killsA, kd = killsD;
		WarData.updateWar(server, war.id(), w -> w.withSide(w.attacker().add(ka, 0, 0, 0)).withSide(w.defender().add(kd, 0, 0, 0)));
	}

	// ---- NPC kings -------------------------------------------------------------------------------------------------

	/** Once a day: neighbouring kingdoms with nothing between them may become rivals. */
	private static void stirRivalries(MinecraftServer server, ServerLevel level) {
		long day = Production.day(server);
		if (WarData.book(server).lastRivalryDay() == day) {
			return;
		}
		WarData.update(server, b -> b.withLastRivalryDay(day));
		long now = level.getGameTime();
		List<Faction> heads = heads(server);
		for (int i = 0; i < heads.size(); i++) {
			for (int j = i + 1; j < heads.size(); j++) {
				Faction a = heads.get(i), b = heads.get(j);
				if (a.relation(b.id()) != Relation.NEUTRAL || Raids.horizontal(a.seat(), b.seat()) > RIVALRY_RANGE * RIVALRY_RANGE
					|| WarData.truce(server, a.id(), b.id(), now) || level.getRandom().nextFloat() >= RIVALRY_CHANCE) {
					continue;
				}
				Relations.set(server, a.id(), b.id(), Relation.RIVAL);
				KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.war.rivals", a.name(), b.name()).withStyle(ChatFormatting.GOLD));
			}
		}
	}

	/** Standing kingdoms at the head of their own side (no overlord), with a ruler on the throne. */
	static List<Faction> heads(MinecraftServer server) {
		return Factions.all(server).stream().filter(f -> f.type() == FactionType.KINGDOM && !f.fallen() && f.overlord() < 0 && f.leader().isPresent())
			.toList();
	}

	/** Now and then a king at peace looks at his rivals: a weaker one near enough, and he declares. */
	static void considerWar(MinecraftServer server, ServerLevel level) {
		float chance = DECLARE_CHANCE_PER_DAY * DECLARE_CHECK_TICKS / 24000.0F;
		long now = level.getGameTime();
		for (Faction king : heads(server)) {
			// (0.7.224) the more money, the more war
			if (!warsOf(server, king.id()).isEmpty() || level.getRandom().nextFloat() >= chance * GreatWars.appetite(server, king)) {
				continue;
			}
			declareOnRival(server, king, now);
		}
	}

	/** {@code king} declares on his weakest rival in reach, if he's strong enough. Returns the rival, or null. */
	static @Nullable Faction declareOnRival(MinecraftServer server, Faction king, long now) {
		// (0.7.224) with the sellswords his treasury could buy
		int mine = army(server, king.id()) + GreatWars.hireable(king);
		Faction best = null;
		int bestArmy = Integer.MAX_VALUE;
		for (Faction other : heads(server)) {
			if (other.id() == king.id() || king.relation(other.id()) != Relation.RIVAL || !warsOf(server, other.id()).isEmpty()
				|| Raids.horizontal(king.seat(), other.seat()) > DECLARE_RANGE * DECLARE_RANGE || WarData.truce(server, king.id(), other.id(), now)) {
				continue;
			}
			int theirs = army(server, other.id());
			if (mine >= theirs * ARMY_EDGE && theirs < bestArmy) {
				best = other;
				bestArmy = theirs;
			}
		}
		if (best != null && declare(server, king, best, false) == null) {
			return best;
		}
		return null;
	}

	// ---- the score ---------------------------------------------------------------------------------------------------

	/** Adds to {@code principal}'s side of {@code war}. */
	static void credit(MinecraftServer server, int war, int principal, int kills, int leaders, int chunks, int loot) {
		WarData.updateWar(server, war, w -> {
			Side side = w.side(principal);
			return side == null ? w : w.withSide(side.add(kills, leaders, chunks, loot));
		});
	}

	/** The side (principal) that {@code source}'s killer fights for in {@code war}, against {@code victimSide}; -1 if none. */
	static int killerSide(MinecraftServer server, War war, int victimSide, DamageSource source) {
		if (source.getEntity() instanceof Folk folk) {
			return sideOf(server, war, folk.factionId());
		}
		if (source.getEntity() instanceof ServerPlayer player) {
			Faction theirs = Factions.of(server, player.getUUID());
			int side = theirs == null ? -1 : sideOf(server, war, theirs.id());
			if (side >= 0) {
				return side;
			}
			// one who isn't sworn to either side takes the other side by fighting
			return otherSide(war, victimSide);
		}
		return -1;
	}

	/** A folk died: if its realm is at war and its killer is on the other side, the score; a player who did it is thanked by the side they helped. */
	private static void died(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof Folk folk) || !(folk.level() instanceof ServerLevel level)) {
			return;
		}
		WarParties.soldierDied(folk);
		MinecraftServer server = level.getServer();
		for (War war : WarData.wars(server)) {
			if (!war.active()) {
				continue;
			}
			int victimSide = sideOf(server, war, folk.factionId());
			if (victimSide < 0) {
				continue;
			}
			int side = killerSide(server, war, victimSide, source);
			if (side < 0 || side == victimSide) {
				continue;
			}
			boolean leader = folk.leader();
			credit(server, war.id(), side, leader ? 0 : 1, leader ? 1 : 0, 0, 0);
			if (source.getEntity() instanceof Folk killer) {
				Treasuries.plunder(server, killer, folk);
			}
			if (source.getEntity() instanceof ServerPlayer player) {
				reward(server, player, side, victimSide, leader ? REP_WAR_LEADER : REP_WAR_KILL, 0);
			}
			return;
		}
	}

	/**
	 * {@code player} helped {@code helped}'s side against {@code hurt}'s: reputation {@code amount} with every realm on
	 * the side helped, {@code hurtAmount} with every realm of the other; their allies react a little.
	 */
	static void reward(MinecraftServer server, ServerPlayer player, int helped, int hurt, int amount, int hurtAmount) {
		List<Faction> helpedSide = members(server, helped), hurtSide = members(server, hurt);
		for (Faction f : helpedSide) {
			Reputation.change(player, f, amount);
		}
		if (hurtAmount != 0) {
			for (Faction f : hurtSide) {
				Reputation.change(player, f, hurtAmount);
			}
		}
		Faction h = Factions.get(server, helped), x = Factions.get(server, hurt);
		if (h == null || x == null) {
			return;
		}
		for (Faction other : Factions.all(server)) {
			if (other.fallen() || other.type() == FactionType.BARBARIAN || helpedSide.stream().anyMatch(f -> f.id() == other.id())
				|| hurtSide.stream().anyMatch(f -> f.id() == other.id())) {
				continue;
			}
			if (Relations.between(other, h) == Relation.ALLIED) {
				Reputation.change(player, other, REP_ALLY_REACT);
			} else if (Relations.between(other, x) == Relation.ALLIED) {
				Reputation.change(player, other, -REP_ALLY_REACT);
			}
		}
	}

	/**
	 * {@code player} carried {@code items} off from {@code faction}'s storage (from {@link Theft}). If they're sworn to a
	 * realm at war with it, it's plunder, not theft: the war score, a little reputation with their side. Returns whether
	 * it was plunder.
	 */
	static boolean plunder(ServerPlayer player, ServerLevel level, Faction faction, int items) {
		MinecraftServer server = level.getServer();
		Faction theirs = Factions.of(server, player.getUUID());
		War war = theirs == null ? null : activeBetween(server, theirs.id(), faction.id());
		if (war == null) {
			return false;
		}
		int side = sideOf(server, war, theirs.id());
		credit(server, war.id(), side, 0, 0, 0, items);
		int points = Math.min(REP_PLUNDER_MAX, items / REP_PLUNDER_ITEMS);
		if (points > 0) {
			reward(server, player, side, otherSide(war, side), points, 0);
		}
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.war.plunder", items, faction.name()).withStyle(ChatFormatting.GOLD));
		return true;
	}

	// ---- a ruler falls ---------------------------------------------------------------------------------------------

	/**
	 * The ruler of {@code faction} was killed (from {@link KingdomEvents#folkDied}). If it heads a side in a war and its
	 * killer fought for the other, the war ends at once: the realm becomes the victor's fiefdom, and a lord succeeds.
	 * Returns whether the war decided the realm's fate (so the usual fates don't apply).
	 */
	static boolean rulerFell(ServerLevel level, Faction faction, DamageSource source) {
		MinecraftServer server = level.getServer();
		for (War war : WarData.wars(server)) {
			if (!war.active() || !war.involves(faction.id())) {
				continue;
			}
			int side = killerSide(server, war, faction.id(), source);
			if (side < 0 || side == faction.id()) {
				continue;
			}
			War current = WarData.war(server, war.id());
			conclude(server, current == null ? war : current, side, true, "message.jaymoskingdoms.war.reason.ruler");
			Faction after = Factions.get(server, faction.id());
			if (after != null && !after.fallen() && after.successionAt() < 0) {
				Factions.update(server, faction.id(), f -> f.withSuccessionAt(level.getGameTime() + KingdomEvents.SUCCESSION_TICKS));
			}
			return true;
		}
		return false;
	}

	/** A banner was held at {@code target}'s seat by {@code taker}'s side: a principal's seat ends the war; a vassal's goes over to the taker. */
	static void seatTaken(MinecraftServer server, War war, int takerSide, Faction target) {
		if (war.involves(target.id())) {
			War current = WarData.war(server, war.id());
			conclude(server, current == null ? war : current, takerSide, true, "message.jaymoskingdoms.war.reason.seat");
			return;
		}
		credit(server, war.id(), takerSide, 0, 0, SEAT_CHUNKS_WORTH, 0);
		if (target.overlord() >= 0) {
			Relations.set(server, target.id(), target.overlord(), Relation.NEUTRAL);
		}
		Relations.vassalize(server, target.id(), takerSide);
		syncRelations(server);
		KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.war.seat_changes", target.name(), name(server, takerSide))
			.withStyle(ChatFormatting.GOLD));
	}

	// ---- peace -------------------------------------------------------------------------------------------------------

	/**
	 * The terms {@code winner}, leading by {@code lead}, imposes in {@code war} ({@code decisive}: a seat or a ruler taken);
	 * (0.7.224) an attacker by its creed's war goal ({@link GreatWars#goal}).
	 */
	static Terms terms(MinecraftServer server, War war, int winner, int lead, boolean decisive) {
		GreatWars.Goal goal = GreatWars.goal(server, war, winner);
		if (decisive || lead >= goal.vassal()) {
			return Terms.VASSAL;
		}
		return lead >= goal.cede() ? Terms.CEDE : lead >= goal.tribute() ? Terms.TRIBUTE : Terms.WHITE;
	}

	/** The side ahead in {@code war} (the attacker on a tie). */
	static Side ahead(War war) {
		return war.attacker().score() >= war.defender().score() ? war.attacker() : war.defender();
	}

	/** What peace now would bring, in words. */
	static Component preview(MinecraftServer server, War war) {
		Side win = ahead(war), lose = war.other(win.principal());
		Terms t = terms(server, war, win.principal(), win.score() - lose.score(), false);
		return Component.translatable(t.key() + ".preview", name(server, win.principal()), name(server, lose.principal()));
	}

	/** A peace offer standing: answered by an NPC king if the other side has no lord, or expired. Returns whether the war ended. */
	private static boolean answerOffer(MinecraftServer server, War war, long now) {
		if (war.offerFrom() < 0) {
			return false;
		}
		int from = war.offerFrom(), to = otherSide(war, from);
		if (now - war.offerAt() > OFFER_TICKS) {
			WarData.updateWar(server, war.id(), w -> w.withOffer(-1, 0L));
			KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.war.peace.unanswered", name(server, to), name(server, from))
				.withStyle(ChatFormatting.GRAY));
			return false;
		}
		if (hasLords(server, to) || now - war.offerAt() < NPC_ANSWER_TICKS) {
			return false;
		}
		Side mine = war.side(to), theirs = war.side(from);
		int margin = mine.score() - theirs.score();
		boolean accept = margin >= TRIBUTE_LEAD || margin <= -NPC_YIELD_LEAD
			|| (Math.abs(margin) < TRIBUTE_LEAD && (!war.active() || now - war.startsAt() >= WHITE_PEACE_MIN_TICKS));
		if (accept) {
			KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.war.peace.npc_accepts", name(server, to))
				.withStyle(ChatFormatting.GREEN));
			concludeByScore(server, war, "message.jaymoskingdoms.war.reason.agreed");
			return true;
		}
		WarData.updateWar(server, war.id(), w -> w.withOffer(-1, 0L));
		KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.war.peace.npc_refuses", name(server, to), name(server, from))
			.withStyle(ChatFormatting.RED));
		return false;
	}

	/** Peace on the terms the score gives. */
	static void concludeByScore(MinecraftServer server, War war, String reason) {
		War current = WarData.war(server, war.id());
		War w = current == null ? war : current;
		conclude(server, w, ahead(w).principal(), false, reason);
	}

	/** {@code by}'s side offers peace in {@code war} (or accepts the other side's offer). */
	static void offer(MinecraftServer server, War war, int by) {
		int to = otherSide(war, by);
		if (war.offerFrom() == to) {
			KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.war.peace.accepted", name(server, by), name(server, to))
				.withStyle(ChatFormatting.GREEN));
			concludeByScore(server, war, "message.jaymoskingdoms.war.reason.agreed");
			return;
		}
		long now = Factions.level(server).getGameTime();
		WarData.updateWar(server, war.id(), w -> w.withOffer(by, now));
		KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.war.peace.offered", name(server, by), name(server, to),
			preview(server, war), (int)(OFFER_TICKS / 1200L)).withStyle(ChatFormatting.YELLOW));
		String command = "/kingdom war peace accept " + name(server, by);
		for (Faction f : members(server, to)) {
			for (UUID id : f.members().keySet()) {
				ServerPlayer lord = server.getPlayerList().getPlayer(id);
				if (lord != null && isLord(f, id)) {
					lord.sendSystemMessage(Component.translatable("message.jaymoskingdoms.war.peace.click").withStyle(style -> style
						.withColor(ChatFormatting.GREEN).withUnderlined(true).withClickEvent(new ClickEvent.RunCommand(command))
						.withHoverEvent(new HoverEvent.ShowText(Component.literal(command)))));
				}
			}
		}
	}

	/** Ends {@code war} with nothing changing hands (a side fell, or both ended up on one side, or an operator ended it). */
	static void end(MinecraftServer server, War war, Component why) {
		WarData.updateWar(server, war.id(), w -> null);
		Banners.dropWar(server, war.id());
		GreatWars.ended(server, war, -1, null);
		syncRelations(server);
		KingdomEvents.announce(server, why.copy().withStyle(ChatFormatting.GOLD));
	}

	/**
	 * Peace: {@code winner}'s side (a principal) imposes the terms its lead (or a {@code decisive} blow) gives, the
	 * relations go back to rivalry, and a truce holds for a while.
	 */
	static void conclude(MinecraftServer server, War war, int winner, boolean decisive, String reason) {
		ServerLevel level = Factions.level(server);
		Side win = war.side(winner), lose = war.other(winner);
		if (win == null || lose == null) {
			return;
		}
		WarData.updateWar(server, war.id(), w -> null);
		Banners.dropWar(server, war.id());
		int lead = win.score() - lose.score();
		Terms t = terms(server, war, winner, lead, decisive);
		GreatWars.Goal goal = GreatWars.goal(server, war, winner);
		boolean great = GreatWars.great(server, war);
		Faction w = Factions.get(server, win.principal()), l = Factions.get(server, lose.principal());
		List<Component> said = new ArrayList<>();
		if (w != null && l != null && !l.fallen()) {
			if (t != Terms.WHITE) {
				said.add(tribute(level, w, l, goal.share() + (great ? 0.1 : 0.0)));
			}
			if (t == Terms.CEDE) {
				said.add(cede(server, w, l, great ? goal.chunks() * 3 / 2 : goal.chunks()));
			}
			if (t == Terms.VASSAL) {
				if (l.overlord() >= 0) {
					Relations.set(server, l.id(), l.overlord(), Relation.NEUTRAL);
				}
				Relations.vassalize(server, l.id(), w.id());
				said.add(Component.translatable(Terms.VASSAL.key(), l.name(), w.name()));
			}
		}
		if (said.isEmpty()) {
			said.add(Component.translatable(Terms.WHITE.key()));
		}
		syncRelations(server);
		if (t != Terms.VASSAL && w != null && l != null) {
			Relations.set(server, w.id(), l.id(), Relation.RIVAL);
		}
		WarData.setTruce(server, win.principal(), lose.principal(), level.getGameTime() + TRUCE_TICKS * goal.truce());
		KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.war.peace", name(server, win.principal()),
			name(server, lose.principal()), Component.translatable(reason), win.score(), lose.score()).withStyle(ChatFormatting.GOLD));
		for (Component line : said) {
			KingdomEvents.announce(server, Component.literal("  ").append(line).withStyle(ChatFormatting.YELLOW));
		}
		// (0.7.224) allies' spoils and reparations, conversions, pledges and wagers settled
		GreatWars.ended(server, war, winner, t);
		syncRelations(server);
		JaymosKingdoms.LOGGER.info("War {} ended: {} over {} ({} to {}), {}", war.id(), name(server, win.principal()), name(server, lose.principal()),
			win.score(), lose.score(), t);
	}

	/** {@code loser} pays {@code winner}: a share of its treasury, and real goods from its storage (in coin when they can't be moved). */
	private static Component tribute(ServerLevel level, Faction winner, Faction loser, double share) {
		MinecraftServer server = level.getServer();
		long treasury = loser.treasury();
		long emeralds = Math.min(treasury, Math.max(TRIBUTE_MIN_EMERALDS, Math.round(treasury * share)));
		int goods = 0;
		if (Storage.loaded(level, loser) && Storage.loaded(level, winner)) {
			RandomSource random = level.getRandom();
			for (int i = 0; i < TRIBUTE_LOADS; i++) {
				List<ItemStack> contents = Storage.contents(level, loser);
				if (contents.isEmpty()) {
					break;
				}
				List<ItemStack> valuables = contents.stream()
					.filter(s -> s.is(Items.EMERALD) || s.is(Items.GOLD_INGOT) || s.is(Items.IRON_INGOT) || s.is(Items.DIAMOND)).toList();
				ItemStack pick = !valuables.isEmpty() && random.nextBoolean() ? valuables.get(random.nextInt(valuables.size()))
					: contents.get(random.nextInt(contents.size()));
				ItemStack taken = Storage.take(level, loser, s -> ItemStack.isSameItemSameComponents(s, pick), TRIBUTE_LOAD_MAX);
				if (taken.isEmpty()) {
					continue;
				}
				int count = taken.getCount();
				ItemStack left = Storage.put(level, winner, taken);
				goods += count - left.getCount();
				if (!left.isEmpty()) {
					Storage.put(level, loser, left);
				}
			}
		} else {
			emeralds = Math.min(treasury, emeralds + (long)TRIBUTE_LOADS * TRIBUTE_EMERALDS_PER_LOAD);
		}
		long paid = emeralds;
		Factions.update(server, loser.id(), f -> f.withTreasury(Math.max(0L, f.treasury() - paid)));
		Factions.update(server, winner.id(), f -> f.withTreasury(f.treasury() + paid));
		return Component.translatable(Terms.TRIBUTE.key(), loser.name(), winner.name(), paid, goods);
	}

	/** {@code loser} cedes land to {@code winner}: the chunks nearest the winner's seat, never those round its own seat. */
	private static Component cede(MinecraftServer server, Faction winner, Faction loser, int chunks) {
		ChunkPos seat = ChunkPos.containing(loser.seat());
		ChunkPos toward = ChunkPos.containing(winner.seat());
		List<Long> ceded = loser.claims().stream()
			.filter(c -> ChunkPos.unpack(c).getChessboardDistance(seat) > CEDE_SEAT_KEEP)
			.sorted(Comparator.comparingInt(c -> ChunkPos.unpack(c).distanceSquared(toward)))
			.limit(chunks).toList();
		if (!ceded.isEmpty()) {
			Factions.update(server, loser.id(), f -> {
				List<Long> claims = new ArrayList<>(f.claims());
				claims.removeAll(ceded);
				return f.withClaims(claims);
			});
			Factions.update(server, winner.id(), f -> {
				List<Long> claims = new ArrayList<>(f.claims());
				claims.addAll(ceded);
				return f.withClaims(claims);
			});
		}
		return Component.translatable(Terms.CEDE.key(), loser.name(), ceded.size(), winner.name());
	}

	/** The players sworn to a realm on {@code principal}'s side who are online. */
	static List<ServerPlayer> sworn(MinecraftServer server, int principal) {
		List<ServerPlayer> out = new ArrayList<>();
		for (Faction f : members(server, principal)) {
			for (UUID id : f.members().keySet()) {
				ServerPlayer p = server.getPlayerList().getPlayer(id);
				if (p != null) {
					out.add(p);
				}
			}
		}
		return out;
	}
}
