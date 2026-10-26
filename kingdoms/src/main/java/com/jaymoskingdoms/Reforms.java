package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.219) Andy (2026-10-26), on whether rulers should answer their people leaving ({@link Emigrants}): "please". A
 * realm whose households keep leaving ({@link #left}) builds up {@link #PRESSURE}; past {@link #REFORM_AT} its ruler
 * answers, by temper ({@link #REFORM_CHANCE}): a noble or a reveler nearly always reforms, a warlord as often as not, a
 * purist and a paranoid king seldom, a tyrant hardly ever. Otherwise he doubles down, and his people go on leaving
 * until the realm falls to ruin ({@link Emigrants#surveyed}) or he thinks better of it next time.
 * <p>Each reform is a step ({@link Grievance#level}, up to {@link #MOST}), and each step:
 * <ul>
 * <li>cuts his taxes by {@link #TAX_CUT} ({@link #taxes}, from {@link Tempers#taxes});</li>
 * <li>eases what drives people off ({@link #ease}): a tyrant's yoke, a purist's scorn and plain unhappiness count for
 * {@link #EASE} less in {@link Emigrants#decide}; a purist's markup on outsiders comes down too ({@link #markup});</li>
 * <li>has his guards ask fewer tolls and search fewer packs ({@link #guards});</li>
 * <li>cheers the townsfolk there and then (MCA mood), and the news goes round (Tidings) and into their gossip;</li>
 * <li>draws settlers: a reformed realm is likelier somewhere others go when they leave home ({@link Emigrants}).</li>
 * </ul>
 * (Andy: "More".) When the ridiculed are most of those who left, the reform comes with an edict of tolerance
 * ({@link #tolerated}): his townsfolk mind a blood 25% less a step ({@link Prejudice#minds}). A ruler who digs in has
 * teeth: a tyrant's, a paranoid king's or a warlord's guards stop {@link #CAUGHT_CHANCE} of households at the gate
 * ({@link #caught}), and a purist drives out those not of his blood ({@link Emigrants#EXPEL}).
 * With no one leaving for {@link #RELAPSE_TICKS}, a ruler slips back a step: the old ways creep in again.
 */
public final class Reforms {
	/** One household leaving adds this; it ebbs by this much a day. */
	static final float PRESSURE = 1.0F, EBB_A_DAY = 0.35F;
	/** A ruler answers once it's this high, at most this often (ticks: two days). */
	static final float REFORM_AT = 2.0F;
	static final long ANSWER_GAP = 48000L;
	/** The chance he reforms, by temper (TYRANT, NOBLE, WARLORD, PARANOID, REVELER, PURIST); else he doubles down. */
	static final float[] REFORM_CHANCE = {0.2F, 0.95F, 0.5F, 0.3F, 0.85F, 0.35F};
	/** The most steps; a step's tax cut and easing (shares). */
	static final int MOST = 3;
	static final float TAX_CUT = 0.2F, EASE = 0.25F;
	/** An edict of tolerance (when the ridiculed are most of those leaving): townsfolk there mind a blood this much less a step. */
	static final float TOLERANCE_EASE = 0.25F;
	/**
	 * Digging in: a tyrant's, a paranoid king's or a warlord's guards stop a household at the gate this often
	 * ({@link #caught}); a purist's drives out those not of his blood ({@link Emigrants#decide}).
	 */
	static final float CAUGHT_CHANCE = 0.4F;
	/** A step's guards: this much less keen on tolls and searches (weight). */
	static final int GUARD_EASE = 1;
	/** A reform cheers townsfolk about the seat this much (MCA mood), this far. */
	static final int CHEER = 4;
	static final double CHEER_RANGE = 96.0;
	/** No one leaving this long (ticks: ten days), and he slips back a step. */
	static final long RELAPSE_TICKS = 240000L;
	/** How often the realms are seen to (ticks). */
	static final int TICK = 1200;
	/** Gossip about the reforms in his realm, this often; its lines. */
	static final float GOSSIP_CHANCE = 0.2F;
	static final int GOSSIP_LINES = 4, STUBBORN_GOSSIP_LINES = 3, TOLERANCE_GOSSIP_LINES = 3;

	/**
	 * A realm's grievance: how much leaving weighs on it, when the last left, its reforms, when he last answered, whether he
	 * dug in; why they've left since he last answered (households by reason); his edicts of tolerance.
	 */
	record Grievance(float pressure, long lastLeft, int level, long answered, boolean stubborn, Map<String, Integer> why, int tolerance) {
		static final Codec<Grievance> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.FLOAT.fieldOf("pressure").forGetter(Grievance::pressure),
			Codec.LONG.fieldOf("last_left").forGetter(Grievance::lastLeft),
			Codec.INT.fieldOf("level").forGetter(Grievance::level),
			Codec.LONG.fieldOf("answered").forGetter(Grievance::answered),
			Codec.BOOL.fieldOf("stubborn").forGetter(Grievance::stubborn),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("why", Map.of()).forGetter(Grievance::why),
			Codec.INT.optionalFieldOf("tolerance", 0).forGetter(Grievance::tolerance)).apply(i, Grievance::new));
	}

	/** On the Overworld: each realm's grievance, by id. */
	static final AttachmentType<Map<String, Grievance>> GRIEVANCES = AttachmentRegistry.create(JaymosKingdoms.id("reforms"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Grievance.CODEC)));

	private Reforms() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Reforms::tick);
	}

	private static Map<String, Grievance> all(MinecraftServer server) {
		return Factions.level(server).getAttachedOrElse(GRIEVANCES, Map.of());
	}

	private static @Nullable Grievance of(MinecraftServer server, @Nullable Faction f) {
		return f == null ? null : all(server).get(Integer.toString(f.id()));
	}

	private static void put(MinecraftServer server, int id, @Nullable Grievance g) {
		Map<String, Grievance> next = new HashMap<>(all(server));
		if (g == null) {
			next.remove(Integer.toString(id));
		} else {
			next.put(Integer.toString(id), g);
		}
		Factions.level(server).setAttached(GRIEVANCES, Map.copyOf(next));
	}

	/** {@code f}'s edicts of tolerance (0 to {@link #MOST}). */
	static int tolerance(MinecraftServer server, @Nullable Faction f) {
		Grievance g = of(server, f);
		return g == null ? 0 : g.tolerance();
	}

	/** {@code f}'s ruler dug in, last time he answered his people leaving. */
	static boolean stubborn(MinecraftServer server, @Nullable Faction f) {
		Grievance g = of(server, f);
		return g != null && g.stubborn();
	}

	/** How much townsfolk under ruler {@code f} still mind a blood, after his edicts of tolerance (a share). */
	static float tolerated(MinecraftServer server, @Nullable Faction f) {
		int t = tolerance(server, f);
		return t == 0 ? 1.0F : Math.max(0.0F, 1.0F - TOLERANCE_EASE * t);
	}

	/** {@code f}'s reforms (0 to {@link #MOST}). */
	static int level(MinecraftServer server, @Nullable Faction f) {
		Grievance g = of(server, f);
		return g == null ? 0 : g.level();
	}

	// ---- what the reforms do ------------------------------------------------------------------------------------------

	/** {@code f}'s taxes after his reforms. */
	static long taxes(MinecraftServer server, Faction f, long amount) {
		int l = level(server, f);
		return l == 0 ? amount : Math.round(amount * (1.0 - TAX_CUT * l));
	}

	/** A push to leave ({@link Emigrants#decide}) under ruler {@code f}, after his reforms. */
	static float ease(MinecraftServer server, @Nullable Faction f, float push) {
		int l = level(server, f);
		return l == 0 ? push : push * Math.max(0.0F, 1.0F - EASE * l);
	}

	/** A purist's markup on outsiders ({@link Purists#prices}), after his reforms. */
	static float markup(MinecraftServer server, @Nullable Faction f, float share) {
		return ease(server, f, share);
	}

	/** A guard's keenness for tolls and searches ({@link Approaches}), after his ruler's reforms. */
	static int guards(MinecraftServer server, @Nullable Faction f, int weight) {
		return weight == 0 ? 0 : Math.max(0, weight - GUARD_EASE * level(server, f));
	}

	// ---- leaving and answering -------------------------------------------------------------------------------------------

	/** A household of {@code count} left {@code ruler}'s realm ({@link Emigrants}). */
	static void left(ServerLevel level, @Nullable Faction ruler, int count, String reason) {
		if (ruler == null || Tempers.of(ruler) == null) {
			// (a village or a player's realm: no ruler of ours to answer)
			return;
		}
		MinecraftServer server = level.getServer();
		Grievance g = of(server, ruler);
		long now = level.getGameTime();
		float p = (g == null ? 0.0F : g.pressure()) + PRESSURE * Math.max(1, Math.min(count, 2));
		Map<String, Integer> why = new HashMap<>(g == null ? Map.of() : g.why());
		why.merge(reason, 1, Integer::sum);
		put(server, ruler.id(), g == null ? new Grievance(p, now, 0, 0L, false, Map.copyOf(why), 0)
			: new Grievance(p, now, g.level(), g.answered(), g.stubborn(), Map.copyOf(why), g.tolerance()));
	}

	/** A household trying to leave a stubborn ruler's realm is stopped at the gate this time ({@link Emigrants#decide}). */
	static boolean caught(MinecraftServer server, @Nullable Faction ruler, RandomSource random) {
		Tempers.Temper t = Tempers.of(ruler);
		return stubborn(server, ruler) && (t == Tempers.Temper.TYRANT || t == Tempers.Temper.PARANOID || t == Tempers.Temper.WARLORD)
			&& random.nextFloat() < CAUGHT_CHANCE;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % TICK != 0) {
			return;
		}
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		for (Map.Entry<String, Grievance> e : Map.copyOf(all(server)).entrySet()) {
			int id = Integer.parseInt(e.getKey());
			Faction f = Factions.get(server, id);
			Grievance g = e.getValue();
			if (f == null || f.fallen() || Tempers.of(f) == null) {
				put(server, id, null);
				continue;
			}
			float p = Math.max(0.0F, g.pressure() - EBB_A_DAY * TICK / 24000.0F);
			int l = g.level(), tolerance = g.tolerance();
			boolean stubborn = g.stubborn();
			long answered = g.answered();
			Map<String, Integer> why = g.why();
			if (l > 0 && now - g.lastLeft() > RELAPSE_TICKS && now - answered > RELAPSE_TICKS) {
				// (nobody's left in a long while: the old ways creep back)
				l--;
				tolerance = Math.min(tolerance, l);
				answered = now;
			}
			if (p >= REFORM_AT && now - answered >= ANSWER_GAP) {
				answered = now;
				p = 0.0F;
				Tempers.Temper t = Tempers.of(f);
				// (what drove most of them off: the ridiculed get an edict of tolerance)
				String top = Emigrants.WANDER;
				for (Map.Entry<String, Integer> w : why.entrySet()) {
					if (w.getValue() > why.getOrDefault(top, 0)) {
						top = w.getKey();
					}
				}
				why = Map.of();
				if (l < MOST && level.getRandom().nextFloat() < REFORM_CHANCE[t.ordinal()]) {
					l++;
					stubborn = false;
					boolean edict = Emigrants.RIDICULED.equals(top) && tolerance < MOST;
					if (edict) {
						tolerance++;
					}
					reformed(level, f, t, l, edict);
				} else {
					stubborn = true;
					KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.reform.stubborn." + t.key(), f.name())
						.withStyle(ChatFormatting.GRAY));
				}
			}
			if (p == 0.0F && l == 0 && !stubborn && now - g.lastLeft() > RELAPSE_TICKS) {
				put(server, id, null);
			} else {
				put(server, id, new Grievance(p, g.lastLeft(), l, answered, stubborn, why, tolerance));
			}
		}
	}

	/** {@code f}'s ruler takes step {@code l}: the news, and the townsfolk cheered. */
	private static void reformed(ServerLevel level, Faction f, Tempers.Temper t, int l, boolean edict) {
		KingdomEvents.announce(level.getServer(), Component.translatable("message.jaymoskingdoms.reform." + t.key(), f.name(), Mth.floor(TAX_CUT * l * 100))
			.withStyle(ChatFormatting.GOLD));
		if (edict) {
			KingdomEvents.announce(level.getServer(), Component.translatable("message.jaymoskingdoms.reform.tolerance", f.name()).withStyle(ChatFormatting.GOLD));
		}
		if (!level.isPositionEntityTicking(f.seat())) {
			return;
		}
		for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(f.seat()).inflate(CHEER_RANGE), LivingEntity::isAlive)) {
			McaCompat.cheer(v, CHEER);
		}
	}

	// ---- talk ---------------------------------------------------------------------------------------------------------

	/** (Gossip, from {@link Approaches}) The reforms in {@code ruler}'s realm, or his digging in. Null if not this time. */
	static @Nullable Component gossip(ServerLevel level, ServerPlayer player, @Nullable Faction ruler) {
		Grievance g = of(level.getServer(), ruler);
		RandomSource random = level.getRandom();
		if (g == null || ruler == null || random.nextFloat() >= GOSSIP_CHANCE) {
			return null;
		}
		if (g.stubborn()) {
			return Component.translatable("reform.jaymoskingdoms.gossip.stubborn." + random.nextInt(STUBBORN_GOSSIP_LINES), ruler.name(),
				Identities.shownName(player));
		}
		if (g.tolerance() > 0 && random.nextBoolean()) {
			return Component.translatable("reform.jaymoskingdoms.gossip.tolerance." + random.nextInt(TOLERANCE_GOSSIP_LINES), ruler.name(),
				Identities.shownName(player));
		}
		if (g.level() > 0) {
			return Component.translatable("reform.jaymoskingdoms.gossip.reformed." + random.nextInt(GOSSIP_LINES), ruler.name(), Identities.shownName(player));
		}
		return null;
	}
}
