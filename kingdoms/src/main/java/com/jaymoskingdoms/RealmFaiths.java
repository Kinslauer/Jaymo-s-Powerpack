package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.513) What each realm holds (docs/Patrons-B-Plan.md section 7). Andy: The Truth "usually is the faith among humans".
 * A Faith-creed realm holds The Truth; any other holds the one of the seven most of its folk do (counted once a day), or,
 * when they hold none of the seven between them, its blood's patron or the one its creed and land make its own
 * ({@link #reasoned}). Realms of rival patrons grow tense each day, and a war between them is a holy
 * war ({@link GreatWars}). Saved on the Overworld.
 */
public final class RealmFaiths {
	static final int CENSUS_EVERY = 1200, CENSUS_MIN = 6, HOLY_NAMED = 2, HOLY_TRUTH = 1;
	static final double CENSUS_RANGE = 64.0, CENSUS_HEIGHT = 24.0;
	static final float STATE_SHARE = 0.5F, HOLY_GAIN = 2.0F, HOLY_FAITH_CREED = 1.5F, KINDRED_FAITH_EASE = 1.0F;
	static final double HOLY_RALLY = 3.0, HOLY_RALLY_RIVAL = 1.5;

	/** Each realm's counted state patron (realm id to patron key), the wars that are holy, and the day each realm was last counted. */
	record Holdings(Map<String, String> patrons, List<Integer> holy, Map<String, Long> counted) {
		static final Holdings EMPTY = new Holdings(Map.of(), List.of(), Map.of());
		static final Codec<Holdings> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("patrons", Map.of()).forGetter(Holdings::patrons),
			Codec.INT.listOf().optionalFieldOf("holy", List.of()).forGetter(Holdings::holy),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("counted", Map.of()).forGetter(Holdings::counted)).apply(i, Holdings::new));
	}

	static final AttachmentType<Holdings> HOLDINGS = AttachmentRegistry.create(JaymosKingdoms.id("realm_faiths"), b -> b.persistent(Holdings.CODEC));

	private RealmFaiths() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(RealmFaiths::tick);
	}

	private static Holdings holdings(MinecraftServer server) {
		return Factions.level(server).getAttachedOrElse(HOLDINGS, Holdings.EMPTY);
	}

	private static void set(MinecraftServer server, Holdings h) {
		Factions.level(server).setAttached(HOLDINGS, h);
	}

	// ---- the state patron --------------------------------------------------------------------------------------------------

	/** {@code f}'s state patron: The Truth for a Faith creed, else the counted one, else its blood's. Null for a barbarian camp (or none). */
	public static @Nullable Patron patron(MinecraftServer server, @Nullable Faction f) {
		if (f == null || f.type() == FactionType.BARBARIAN) {
			return null;
		}
		if (GreatWars.creed(server, f) == GreatWars.Creed.FAITH) {
			return Patron.TRUTH;
		}
		Patron stored = Patron.byKey(holdings(server).patrons().get(Integer.toString(f.id())));
		// (Andy, 2026-11-26: "Realms should have their own ... patrons") only a Faith realm holds The Truth as a realm; a count
		// kept from before, when most realms did, gives way to the realm's own
		return stored != null && stored != Patron.TRUTH ? stored : fallback(server, f);
	}

	/**
	 * (Andy, 2026-11-26) The patron a realm of men keeps when its own folk don't hold one of the seven between them, reasoned
	 * from what it is: its creed and its land each name the patrons that fit (a crown the Sun, a market the Anvil-Father, the
	 * Deep Mother or the Hearthkeeper, the long northern nights the Lantern Warden, the forests the Huntsman), and the realm
	 * keeps one of them, the same one every time. Never the Ash Whisperer: the crown's men hang her followers.
	 */
	private static Patron reasoned(MinecraftServer server, Faction f) {
		List<Patron> fit = new ArrayList<>(List.of(switch (GreatWars.creed(server, f)) {
			case CROWN -> new Patron[] {Patron.CROWNED_SUN, Patron.CROWNED_SUN, Patron.HEARTHKEEPER};
			case CHIVALRY -> new Patron[] {Patron.CROWNED_SUN, Patron.HEARTHKEEPER, Patron.LANTERN_WARDEN};
			case CONQUEST -> new Patron[] {Patron.CROWNED_SUN, Patron.ANVIL_FATHER, Patron.GREEN_HUNTSMAN};
			case COIN -> new Patron[] {Patron.ANVIL_FATHER, Patron.DEEP_MOTHER, Patron.HEARTHKEEPER};
			case BLOOD -> new Patron[] {Patron.GREEN_HUNTSMAN, Patron.ANVIL_FATHER, Patron.LANTERN_WARDEN};
			case FAITH -> new Patron[] {Patron.TRUTH};
		}));
		fit.addAll(List.of(switch (Climate.at(Factions.level(server), f.seat())) {
			case PLAINS, CHERRY -> new Patron[] {Patron.HEARTHKEEPER};
			case TAIGA -> new Patron[] {Patron.GREEN_HUNTSMAN, Patron.LANTERN_WARDEN};
			case SNOWY, SWAMP -> new Patron[] {Patron.LANTERN_WARDEN};
			case SAVANNA -> new Patron[] {Patron.GREEN_HUNTSMAN, Patron.CROWNED_SUN};
			case DESERT -> new Patron[] {Patron.CROWNED_SUN};
			case BADLANDS -> new Patron[] {Patron.ANVIL_FATHER};
			case JUNGLE -> new Patron[] {Patron.GREEN_HUNTSMAN};
		}));
		return fit.get(Math.floorMod(f.id() * 31 + f.seat().hashCode(), fit.size()));
	}

	/** A haven of one blood takes that blood's patron; an orc-ruled mixed haven the Anvil-Father; another mixed one one of four; any other realm its {@link #reasoned} one. */
	private static Patron fallback(MinecraftServer server, Faction f) {
		String haven = Havens.of(server, f);
		if (haven.isEmpty()) {
			return reasoned(server, f);
		}
		if (Havens.MIXED.equals(haven)) {
			if (Havens.orcRuled(server, f)) {
				return Patron.ANVIL_FATHER;
			}
			Patron[] four = {Patron.DEEP_MOTHER, Patron.ANVIL_FATHER, Patron.CROWNED_SUN, Patron.GREEN_HUNTSMAN};
			return four[Math.floorMod(f.id() * 31 + f.seat().hashCode(), four.length)];
		}
		return Faiths.lean(Heritage.Kind.byKey(haven));
	}

	// ---- the daily count ---------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CENSUS_EVERY != 0) {
			return;
		}
		long day = Production.day(server);
		Holdings now = holdings(server);
		ServerLevel level = Factions.level(server);
		Map<String, String> patrons = new HashMap<>(now.patrons());
		Map<String, Long> counted = new HashMap<>(now.counted());
		boolean changed = false;
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || f.type() == FactionType.BARBARIAN || !level.isPositionEntityTicking(f.seat())
				|| counted.getOrDefault(Integer.toString(f.id()), -1L) == day) {
				continue;
			}
			Map<Patron, Integer> counts = new EnumMap<>(Patron.class);
			int total = 0;
			AABB box = new AABB(f.seat()).inflate(CENSUS_RANGE, CENSUS_HEIGHT, CENSUS_RANGE);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> x.isAlive() && Faiths.holds(x) && !Accusations.priest(x)
				&& (!(x instanceof Folk folk) || folk.factionId() == f.id()))) {
				Patron p = Faiths.of(e);
				if (p != null) {
					counts.merge(p, 1, Integer::sum);
					total++;
				}
			}
			if (total < CENSUS_MIN) {
				continue;
			}
			Patron best = null;
			for (Map.Entry<Patron, Integer> entry : counts.entrySet()) {
				if (best == null || entry.getValue() > counts.get(best)) {
					best = entry.getKey();
				}
			}
			// (a realm whose folk mostly hold The Truth, outside a Faith realm, keeps its own patron: the Truth is a realm's only by its creed)
			Patron state = best != null && best != Patron.TRUTH && counts.get(best) >= STATE_SHARE * total ? best : fallback(server, f);
			patrons.put(Integer.toString(f.id()), state.key);
			counted.put(Integer.toString(f.id()), day);
			changed = true;
		}
		if (changed) {
			set(server, new Holdings(Map.copyOf(patrons), now.holy(), Map.copyOf(counted)));
		}
	}

	// ---- holy wars -----------------------------------------------------------------------------------------------------------------

	/** Points of holy friction between two realms: a named rival pair, The Truth against one of the seven, 0 otherwise, and -1 for the same patron. */
	static int holy(MinecraftServer server, Faction a, Faction b) {
		Patron pa = patron(server, a), pb = patron(server, b);
		if (pa == null || pb == null) {
			return 0;
		}
		if (pa == pb) {
			return -1;
		}
		if (pa.rival == pb || pb.rival == pa) {
			return HOLY_NAMED;
		}
		return pa.rivals(pb) ? HOLY_TRUTH : 0;
	}

	/** A day's holy tension between two realms (not times wealth: faith is not about money). */
	static float holyTension(MinecraftServer server, Faction a, Faction b) {
		int holy = holy(server, a, b);
		if (holy < 0) {
			return -KINDRED_FAITH_EASE;
		}
		if (holy == 0) {
			return 0.0F;
		}
		boolean faith = GreatWars.creed(server, a) == GreatWars.Creed.FAITH || GreatWars.creed(server, b) == GreatWars.Creed.FAITH;
		return holy * HOLY_GAIN * (faith ? HOLY_FAITH_CREED : 1.0F);
	}

	/** How much more {@code h} leans to {@code friend} against {@code foe} for their faith. */
	static double rally(MinecraftServer server, Faction h, Faction friend, Faction foe) {
		Patron ph = patron(server, h), pf = patron(server, friend), po = patron(server, foe);
		if (ph == null || pf == null || po == null) {
			return 0.0;
		}
		if (ph == pf && pf.rivals(po)) {
			return HOLY_RALLY;
		}
		return ph.rivals(po) ? HOLY_RALLY_RIVAL : 0.0;
	}

	static void markHoly(MinecraftServer server, int war) {
		Holdings h = holdings(server);
		if (!h.holy().contains(war)) {
			List<Integer> holy = new ArrayList<>(h.holy());
			holy.add(war);
			set(server, new Holdings(h.patrons(), List.copyOf(holy), h.counted()));
		}
	}

	static void unmarkHoly(MinecraftServer server, int war) {
		Holdings h = holdings(server);
		if (h.holy().contains(war)) {
			List<Integer> holy = new ArrayList<>(h.holy());
			holy.remove(Integer.valueOf(war));
			set(server, new Holdings(h.patrons(), List.copyOf(holy), h.counted()));
		}
	}

	/** Drops the holy mark of wars that are gone. */
	static void forgetEnded(MinecraftServer server, Set<Integer> live) {
		Holdings h = holdings(server);
		if (h.holy().stream().anyMatch(id -> !live.contains(id))) {
			set(server, new Holdings(h.patrons(), h.holy().stream().filter(live::contains).toList(), h.counted()));
		}
	}

	/** For part (c): {@code war} is a holy war (champions fight in it). */
	public static boolean holyWar(MinecraftServer server, int war) {
		return holdings(server).holy().contains(war);
	}
}
