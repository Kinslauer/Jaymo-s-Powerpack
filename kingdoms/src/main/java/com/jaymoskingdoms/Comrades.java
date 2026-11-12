package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.illager.SpellcasterIllager;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TridentItem;
import org.jspecify.annotations.Nullable;

/**
 * The shared fighting brain (docs/Pawn-Plan.md, patch 1), after Dragon's Dogma's pawns (a commercial game: ideas only,
 * our own names, code and lines). Andy (2026-11-12): "It should be a system that you experience on both sides, friend
 * and foe." Every humanoid in a fight runs it the same way: a hired sellsword, a realm's guard, a camp's raider, an orc,
 * an illager.
 * <p>
 * <b>Leanings.</b> Each fighter carries a main and a second {@link Leaning} ({@link #LEANINGS}, saved), drawn once from
 * its temperament ({@link Temperaments}) and its part. Every {@link #THINK} ticks a fighter looks over the foes already
 * in its fight ({@link #candidates}: whoever its side is attacking, and whoever is attacking its side, never someone new,
 * never a downed body) and goes for the one its leanings like best, if that one is clearly better than the one it has
 * ({@link #SWITCH_MARGIN}). When it switches it says so ({@link Callouts}).
 * <p>
 * <b>Sides</b> ({@link #side}): a player and the villagers following them or under their orders; a camp's or realm's
 * own by faction; a camp's illagers by camp; the settlement folk together; any other kind of mob with its own kind.
 * <p>
 * Who is left alone: anyone tending the downed ({@link Downed#helping}, {@link FieldMedics}), a guard making an arrest,
 * a companion told to wait ({@link QuickOrders}) or to stand ({@link Followers#STAND}).
 */
public final class Comrades {
	/** How a fighter picks among its foes. Main counts fully, the second half. */
	public enum Leaning {
		/** Takes on the biggest. */
		BIGGEST,
		/** Hunts bowmen and casters. */
		SHOOTERS,
		/** Clears the weak and the hurt. */
		WEAK,
		/** Keeps by the one they follow (a companion's player, a band's leader). */
		GUARD,
		/** Minds the fallen: tends them mid-fight ({@link FieldMedics}), goes for whoever is at a hurt friend. */
		MEDIC,
		/** First in: the foe nobody else is on yet. */
		FIRST,
		/** After the spoils: the best-armed foe. */
		SPOILS,
		/** Backs the others up: the foe most of its side is on. */
		BACKUP;

		public String key() {
			return this.name().toLowerCase(Locale.ROOT);
		}
	}

	public record Leanings(Leaning main, Leaning second) {
		boolean has(Leaning l) {
			return this.main == l || this.second == l;
		}
	}

	/** Looked over this often (ticks): who is fighting near the players. */
	static final int EVERY = 10;
	/** Each fighter rethinks its foe this often (ticks, spread over fighters). */
	static final int THINK = 30;
	/** Fighters are looked for this far round each player (blocks); foes this far round each fighter. */
	static final double SCAN = 48.0, FIGHT_RANGE = 16.0;
	/** Closer than this a foe counts even unseen (blocks). */
	static final double FEEL = 6.0;
	/** A new foe must score this much better than the one they're on. */
	static final double SWITCH_MARGIN = 15.0;
	/** Staying on the foe they have is worth this much. */
	static final double STICK = 10.0;
	/** Every block of distance costs this much. */
	static final double PER_BLOCK = 1.5;
	/** Someone kneeling over a downed friend is worth this much more to every foe ("get the one tending them"). */
	static final double TENDER_BONUS = 20.0;
	/** A companion told to come fights only foes this near the player (blocks), and drops one farther than the second. */
	static final double CLOSE = 6.0, CLOSE_DROP = 8.0;
	/** A fighter this badly hurt (share of its health) says so, once a fight. */
	static final float HURT_SHARE = 0.35F;
	/** A friend is warned when a foe this near (blocks) comes at them from behind. */
	static final double BEHIND_NEAR = 4.0;
	/** Quiet this long (ticks) after a fight, and it's won. */
	static final int WON_AFTER = 80;
	/** Leaning weights when drawn: every leaning, the temperament's own, and the part's. */
	static final int BASE_WEIGHT = 10, TEMPERAMENT_WEIGHT = 30, PART_WEIGHT = 20;

	/** On every fighter once it has fought: "main,second". Saved, so later patches can shift it. */
	public static final AttachmentType<String> LEANINGS = AttachmentRegistry.create(JaymosKingdoms.id("comrade_leanings"),
		builder -> builder.persistent(Codec.STRING));

	/** Who was fighting, when last (game time), for the end of a fight. Server memory. */
	private static final Map<UUID, Mob> FOUGHT = new HashMap<>();
	private static final Map<UUID, Long> FOUGHT_AT = new HashMap<>();
	/** Who has said they're badly hurt this fight. */
	private static final Set<UUID> HURT_SAID = new java.util.HashSet<>();

	/** This tick's fighters on a level, and what each side is going for (rebuilt every {@link #EVERY}). */
	private static final Map<ServerLevel, List<Mob>> FIGHTERS = new IdentityHashMap<>();
	private static final Map<ServerLevel, Map<String, Map<LivingEntity, Integer>>> SIDE_TARGETS = new IdentityHashMap<>();

	private Comrades() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("comrade attachment: {}", LEANINGS);
		ServerTickEvents.END_SERVER_TICK.register(Comrades::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			FOUGHT.clear();
			FOUGHT_AT.clear();
			HURT_SAID.clear();
			FIGHTERS.clear();
			SIDE_TARGETS.clear();
		});
	}

	// ---- who is who ---------------------------------------------------------------------------------------------------

	/** Whoever {@code m} is set on just now (its brain's attack target, else its goal target), or null. */
	public static @Nullable LivingEntity target(Mob m) {
		LivingEntity t = null;
		if (m.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
			t = m.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
		}
		if (t == null) {
			t = m.getTarget();
		}
		return t != null && t.isAlive() ? t : null;
	}

	/** {@code m} goes for {@code foe}. */
	static void aim(Mob m, LivingEntity foe) {
		if (m instanceof Villager || m instanceof AbstractPiglin) {
			m.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, foe);
		} else {
			m.setTarget(foe);
		}
	}

	/** {@code m} lets go of whoever it was on. */
	static void stand(Mob m) {
		m.setTarget(null);
		if (m.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
			m.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
		}
	}

	/** A humanoid in a fight: on its feet, thinking, set on someone. */
	static boolean fighter(Mob m) {
		return m.isAlive() && !m.isBaby() && !m.isNoAi() && Temperaments.humanoid(m) && !Downed.isDowned(m) && target(m) != null;
	}

	/** {@code e}'s side: a key two allies share. */
	public static String side(LivingEntity e) {
		if (e instanceof Player p) {
			return "p:" + p.getUUID();
		}
		String band = Loans.bandSide(e);
		if (band != null) {
			return band;
		}
		if (e instanceof Villager v) {
			UUID following = McaCompat.following(v);
			if (following != null) {
				return "p:" + following;
			}
			Followers.Order order = v.getAttached(Followers.ORDER);
			if (order != null) {
				return "p:" + order.by();
			}
		}
		if (e instanceof Worker w && w.factionId() >= 0) {
			return "f:" + w.factionId();
		}
		Illagers.Tag tag = Illagers.tag(e);
		if (tag != null) {
			return "f:" + tag.camp();
		}
		if (e instanceof Villager || Downed.isGuard(e)) {
			return "town";
		}
		if (e instanceof AbstractIllager) {
			return "illager";
		}
		return "t:" + BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
	}

	/** The player whose side {@code side} is, or null. */
	static @Nullable ServerPlayer playerOf(ServerLevel level, String side) {
		if (!side.startsWith("p:")) {
			return null;
		}
		try {
			return level.getPlayerByUUID(UUID.fromString(side.substring(2))) instanceof ServerPlayer p ? p : null;
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** Bowmen, crossbowmen, spear throwers and casters. */
	static boolean ranged(LivingEntity e) {
		if (e instanceof Folk folk) {
			return folk.ranged();
		}
		if (e instanceof SpellcasterIllager || e instanceof Witch) {
			return true;
		}
		return shoots(e.getItemInHand(InteractionHand.MAIN_HAND)) || shoots(e.getItemInHand(InteractionHand.OFF_HAND));
	}

	private static boolean shoots(ItemStack stack) {
		return stack.getItem() instanceof ProjectileWeaponItem || stack.getItem() instanceof TridentItem;
	}

	/** A player who'd shoot and has nothing left to shoot. */
	static boolean outOfArrows(Player p) {
		if (p.isCreative()) {
			return false;
		}
		ItemStack held = p.getMainHandItem();
		return held.getItem() instanceof ProjectileWeaponItem && p.getProjectile(held).isEmpty();
	}

	// ---- leanings ------------------------------------------------------------------------------------------------------

	/** {@code m}'s leanings, drawn now (and kept) if it has none yet. */
	public static Leanings leanings(Mob m) {
		String kept = m.getAttached(LEANINGS);
		if (kept != null) {
			String[] parts = kept.split(",");
			Leaning a = byKey(parts[0]), b = parts.length > 1 ? byKey(parts[1]) : null;
			if (a != null && b != null) {
				return new Leanings(a, b);
			}
		}
		Leanings drawn = draw(m);
		m.setAttached(LEANINGS, drawn.main().key() + "," + drawn.second().key());
		return drawn;
	}

	/** {@link #leanings}, with a band's turn after its losses ({@link LeaningDrift#bandLeans}) as the second. */
	static Leanings fighting(Mob m, String side) {
		Leanings l = leanings(m);
		Leaning men = TableTalk.menLeaning(m, side);
		if (men != null && men != l.main()) {
			// a sworn captain told his men how to lean (docs/Pawn-Plan.md row D4)
			return new Leanings(l.main(), men);
		}
		Leaning band = side.startsWith("p:") ? null : LeaningDrift.bandLeans(m, side);
		return band == null || band == l.main() ? l : new Leanings(l.main(), band);
	}

	private static @Nullable Leaning byKey(String key) {
		for (Leaning l : Leaning.values()) {
			if (l.key().equals(key)) {
				return l;
			}
		}
		return null;
	}

	/** Two different leanings, the temperament's own and the part's likelier. Seeded by the body, so it never flickers. */
	private static Leanings draw(Mob m) {
		int[] weights = new int[Leaning.values().length];
		java.util.Arrays.fill(weights, BASE_WEIGHT);
		Temperaments.Temperament t = Temperaments.of(m);
		if (t != null) {
			weights[byTemperament(t).ordinal()] += TEMPERAMENT_WEIGHT;
		}
		if (Downed.isGuard(m)) {
			weights[Leaning.GUARD.ordinal()] += PART_WEIGHT;
		}
		if (Orcs.isOrc(m)) {
			weights[Leaning.BIGGEST.ordinal()] += PART_WEIGHT;
		}
		if (Raids.isRaider(m) || Barbarians.isCampVillager(m) || Illagers.tag(m) != null) {
			weights[Leaning.SPOILS.ordinal()] += PART_WEIGHT / 2;
			weights[Leaning.FIRST.ordinal()] += PART_WEIGHT / 2;
		}
		if (side(m).startsWith("p:")) {
			weights[Leaning.GUARD.ordinal()] += PART_WEIGHT / 2;
			weights[Leaning.MEDIC.ordinal()] += PART_WEIGHT / 2;
		}
		Random random = new Random(m.getUUID().getLeastSignificantBits() ^ 0x5EED_C0DEL);
		Leaning main = pick(random, weights);
		weights[main.ordinal()] = 0;
		return new Leanings(main, pick(random, weights));
	}

	private static Leaning pick(Random random, int[] weights) {
		int total = 0;
		for (int w : weights) {
			total += w;
		}
		int roll = random.nextInt(Math.max(1, total));
		for (int i = 0; i < weights.length; i++) {
			roll -= weights[i];
			if (roll < 0) {
				return Leaning.values()[i];
			}
		}
		return Leaning.BIGGEST;
	}

	/** Which leaning each temperament draws more often. */
	static Leaning byTemperament(Temperaments.Temperament t) {
		return switch (t) {
			case KINDHEARTED, DEVOUT -> Leaning.MEDIC;
			case CHEERFUL, TIMID -> Leaning.BACKUP;
			case DUTIFUL -> Leaning.GUARD;
			case BRAVE, PROUD -> Leaning.BIGGEST;
			case GREEDY -> Leaning.SPOILS;
			case SUSPICIOUS -> Leaning.SHOOTERS;
			case HOTHEADED -> Leaning.FIRST;
			case SPITEFUL, GLOOMY -> Leaning.WEAK;
		};
	}

	// ---- every few ticks -------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		if (now % EVERY != 0) {
			return;
		}
		FIGHTERS.clear();
		SIDE_TARGETS.clear();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator()) {
				continue;
			}
			ServerLevel level = player.level();
			List<Mob> list = FIGHTERS.computeIfAbsent(level, l -> new ArrayList<>());
			Set<Mob> seen = new LinkedHashSet<>(list);
			for (Mob m : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(SCAN), Comrades::fighter)) {
				if (seen.add(m)) {
					list.add(m);
				}
			}
		}
		long time = server.overworld().getGameTime();
		for (Map.Entry<ServerLevel, List<Mob>> e : FIGHTERS.entrySet()) {
			ServerLevel level = e.getKey();
			Map<String, Map<LivingEntity, Integer>> targets = new HashMap<>();
			for (Mob m : e.getValue()) {
				LivingEntity t = target(m);
				if (t != null) {
					targets.computeIfAbsent(side(m), s -> new IdentityHashMap<>()).merge(t, 1, Integer::sum);
				}
				FOUGHT.put(m.getUUID(), m);
				FOUGHT_AT.put(m.getUUID(), time);
			}
			SIDE_TARGETS.put(level, targets);
			for (Mob m : e.getValue()) {
				if ((m.getId() + now) % THINK < EVERY) {
					think(level, m, false);
				}
			}
			BandLeaders.tick(level, e.getValue(), now);
		}
		ended(time);
	}

	/** Fights that have gone quiet: the ones still standing say it's won. */
	private static void ended(long time) {
		for (var it = FOUGHT_AT.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Long> e = it.next();
			if (time - e.getValue() < WON_AFTER) {
				continue;
			}
			Mob m = FOUGHT.remove(e.getKey());
			it.remove();
			HURT_SAID.remove(e.getKey());
			if (m != null && m.isAlive() && !m.isRemoved() && !Downed.isDowned(m) && target(m) == null) {
				Callouts.say(m, "won", null, false);
				Gestures.won(m);
				BandTallies.ended(m);
			}
		}
	}

	/** Every foe in {@code m}'s fight: attacking its side, or attacked by it; on their feet; not its own side. */
	static List<LivingEntity> candidates(ServerLevel level, Mob m, String mine) {
		Map<LivingEntity, Integer> ours = SIDE_TARGETS.getOrDefault(level, Map.of()).getOrDefault(mine, Map.of());
		LivingEntity current = target(m);
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, m.getBoundingBox().inflate(FIGHT_RANGE),
			e -> e != m && e.isAlive() && !(e instanceof ArmorStand))) {
			if (Downed.isDowned(e) || e instanceof Player p && (p.isCreative() || p.isSpectator())) {
				continue;
			}
			String theirs = side(e);
			if (theirs.equals(mine)) {
				continue;
			}
			boolean inFight = e == current || ours.containsKey(e);
			if (!inFight && e instanceof Mob em) {
				LivingEntity their = target(em);
				inFight = their != null && side(their).equals(mine);
			}
			if (inFight && (m.distanceToSqr(e) < FEEL * FEEL || m.hasLineOfSight(e))) {
				out.add(e);
			}
		}
		return out;
	}

	/**
	 * {@code m} looks over its foes and takes the one its leanings like best ({@code force}: no margin, a fresh pick on
	 * an order). False if it's left as it was.
	 */
	static boolean think(ServerLevel level, Mob m, boolean force) {
		if (busy(m)) {
			return false;
		}
		String mine = side(m);
		QuickOrders.Stance stance = QuickOrders.stance(m);
		ServerPlayer leader = playerOf(level, mine);
		if (stance != null && stance.kind() == QuickOrders.WAIT && !justHurt(m)) {
			stand(m);
			return false;
		}
		boolean close = stance != null && stance.kind() == QuickOrders.COME && leader != null;
		LivingEntity current = target(m);
		if (close && current != null && current.distanceToSqr(leader) > CLOSE_DROP * CLOSE_DROP) {
			stand(m);
			current = null;
		}
		Leanings leanings = fighting(m, mine);
		LivingEntity protectee = leader != null ? leader : BandLeaders.leaderOf(level, m, mine);
		Map<LivingEntity, Integer> claims = SIDE_TARGETS.getOrDefault(level, Map.of()).getOrDefault(mine, Map.of());
		LivingEntity best = null;
		double bestScore = -Double.MAX_VALUE, currentScore = -Double.MAX_VALUE;
		for (LivingEntity e : candidates(level, m, mine)) {
			if (close && e.distanceToSqr(leader) > CLOSE * CLOSE) {
				continue;
			}
			int claimed = claims.getOrDefault(e, 0) - (e == current ? 1 : 0);
			double score = score(leanings.main(), e, protectee, claimed, mine) + 0.5 * score(leanings.second(), e, protectee, claimed, mine)
				- PER_BLOCK * Math.sqrt(m.distanceToSqr(e)) + (tending(e) ? TENDER_BONUS : 0.0) + FoeLore.knownScore(m, e);
			if (e == current) {
				score += STICK;
				currentScore = score;
			}
			if (score > bestScore) {
				bestScore = score;
				best = e;
			}
		}
		if (best == null || best == current || !force && current != null && bestScore < currentScore + SWITCH_MARGIN) {
			calls(level, m, mine, current);
			return false;
		}
		aim(m, best);
		// it points before it goes (docs/Pawn-Plan.md row C2)
		Gestures.point(m, best);
		if (!FoeLore.taken(m, best)) {
			if (Callouts.say(m, tending(best) ? "foe.medic" : "take." + leanings.main().key(), best, false, best.getDisplayName())) {
				// a foe tongue-speaker understood that (row G2)
				Specialties.overheard(level, m, best);
			}
		}
		return true;
	}

	/** How much a leaning likes {@code e}. */
	private static double score(Leaning l, LivingEntity e, @Nullable LivingEntity protectee, int claimed, String mine) {
		float health = e.getHealth(), max = Math.max(1.0F, e.getMaxHealth());
		return switch (l) {
			case BIGGEST -> Math.min(60.0, max + e.getBbHeight() * 5.0);
			case SHOOTERS -> ranged(e) ? 40.0 : 0.0;
			case WEAK -> (1.0 - health / max) * 25.0 + Math.max(0.0, 30.0 - health);
			case GUARD -> protectee == null ? Math.max(0.0, 30.0 - health)
				: (e instanceof Mob em && target(em) == protectee ? 45.0 : 0.0) - 2.0 * Math.sqrt(e.distanceToSqr(protectee));
			case MEDIC -> e instanceof Mob em && target(em) instanceof LivingEntity friend && side(friend).equals(mine)
				&& friend.getHealth() < friend.getMaxHealth() * 0.5F ? 30.0 : 0.0;
			case FIRST -> claimed == 0 ? 30.0 : -10.0 * claimed;
			case SPOILS -> e.getArmorValue() * 2.0 + (e.getMainHandItem().isEmpty() ? 0.0 : 10.0) + (e.getMainHandItem().isEnchanted() ? 10.0 : 0.0);
			case BACKUP -> 12.0 * claimed;
		};
	}

	/** {@code e} is on one knee over someone downed. */
	static boolean tending(LivingEntity e) {
		return FieldMedics.busy(e) || Downed.helping(e);
	}

	/** Left to what they're doing. */
	static boolean busy(Mob m) {
		if (FieldMedics.busy(m) || Downed.helping(m) || Arrests.busy(m) || Grapples.busy(m) || Grapples.held(m) || Scavengers.busy(m) || Loans.working(m)) {
			return true;
		}
		Followers.Order order = m instanceof Villager v ? v.getAttached(Followers.ORDER) : null;
		return order != null && order.kind() == Followers.STAND;
	}

	/** Hit in the last two seconds: a companion told to wait still hits back. */
	static boolean justHurt(Mob m) {
		return m.getLastHurtByMob() != null && m.tickCount - m.getLastHurtByMobTimestamp() < 40;
	}

	/** The lines a fighter says about its fight without changing foe: hurt, a friend's back, a bow with nothing to shoot. */
	private static void calls(ServerLevel level, Mob m, String mine, @Nullable LivingEntity current) {
		if (m.getHealth() < m.getMaxHealth() * HURT_SHARE && HURT_SAID.add(m.getUUID())) {
			Callouts.say(m, "hurt", null, false);
			return;
		}
		if (current instanceof Player p && outOfArrows(p)) {
			Callouts.say(m, "foe.arrows", p, false, p.getDisplayName());
			return;
		}
		for (LivingEntity friend : level.getEntitiesOfClass(LivingEntity.class, m.getBoundingBox().inflate(10.0),
			f -> f != m && f.isAlive() && !Downed.isDowned(f) && side(f).equals(mine))) {
			for (Mob foe : level.getEntitiesOfClass(Mob.class, friend.getBoundingBox().inflate(BEHIND_NEAR), f -> target(f) == friend)) {
				var look = friend.getViewVector(1.0F);
				var toFoe = foe.position().subtract(friend.position()).normalize();
				if (look.x * toFoe.x + look.z * toFoe.z < -0.3 && m.hasLineOfSight(foe)) {
					Callouts.say(m, "behind", friend, false, friend.getDisplayName());
					return;
				}
			}
		}
	}

	/** Fighters near a level's players this tick (for the other comrade classes). */
	static List<Mob> fighters(ServerLevel level) {
		return FIGHTERS.getOrDefault(level, List.of());
	}

	/** Who this side is fighting just now, and how many are on each. */
	static Map<LivingEntity, Integer> targetsOf(ServerLevel level, String side) {
		return SIDE_TARGETS.getOrDefault(level, Map.of()).getOrDefault(side, Map.of());
	}

	/** {@code e} has been in a fight lately. */
	static boolean fought(Entity e) {
		return FOUGHT_AT.containsKey(e.getUUID());
	}
}
