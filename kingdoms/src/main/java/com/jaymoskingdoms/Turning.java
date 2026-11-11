package com.jaymoskingdoms;

import com.jaymoskingdoms.Patrons.Mark;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.animal.polarbear.PolarBear;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.513) Turning away from a patron (docs/Patrons-B-Plan.md). Swear to another patron and the old one marks you: a
 * <b>scar</b> that never fades (a small penalty in its domain, stacking with each leaving), then <b>trials</b>, days in which
 * its omens follow you. A cleric's Leaving Rite ({@link Rites}) halves the trials and dulls the omens, never the scar.
 * Andy: "the harsher the better for this pack". Two gamerules soften it for a server. Every effect is a small function the
 * existing system calls where it already decides something; the marks are {@link Patrons.State#marks()}. The word "omen" is
 * for players only (the raid omens are another system).
 */
public final class Turning {
	// ---- tuning ----------------------------------------------------------------------------------------------------------------
	static final int TRIAL_DAYS_DEFAULT = 7, TRIAL_DAYS_MOST = 60;
	static final int TURNED_FAVOUR = 2;
	static final int SCAR_MOST_EACH = 3, MARKS_MOST = 40;
	/** An eased trial's strength (after a cleric's rite). */
	static final float TRIAL_EASED = 0.5F;
	static final float RITE_SHORTEN = 0.5F;
	static final int RITE_LEAST_DAYS = 1, RITE_KEEP_DAYS = 3;
	// Hearthkeeper
	static final float TRIAL_HEAL = 0.4F, SCAR_HEAL = 0.1F, HEAL_CUT_MOST = 0.6F;
	// Anvil-Father
	static final float TRIAL_WEAR = 0.5F, SCAR_WEAR = 0.1F;
	static final double TRIAL_ZONE = 0.6, SCAR_ZONE = 0.15, ZONE_SLIP_MOST = 1.0;
	// Deep Mother
	static final int TRIAL_AIR = 8, SCAR_AIR = 2;
	static final double TRIAL_TUG = 0.12;
	static final float TRIAL_TUG_CHANCE = 0.3F, TRIAL_BOLT_DRAWN = 0.35F;
	static final double TRIAL_STORM_DAYS = 1.0;
	static final int TRIAL_BOLT_NEAR_MIN = 3, TRIAL_BOLT_NEAR_MAX = 8;
	// Green Huntsman
	static final double TRIAL_BEAST_RANGE = 16.0, TRIAL_BEAST_SIGHT = 0.5, SCAR_BEAST_SIGHT = 0.1;
	static final float TRIAL_BEAST_CHANCE = 0.2F;
	// Lantern Warden
	static final double TRIAL_UNDEAD_RANGE = 40.0, TRIAL_LURE_LOUDNESS = 8.0, TRIAL_UNDEAD_SIGHT = 0.6, SCAR_UNDEAD_SIGHT = 0.1;
	static final float SCAR_GRAVE = 0.1F;
	// Crowned Sun
	static final float TRIAL_HALT = 0.4F, SCAR_HALT = 0.15F, HALT_LEAST = 0.2F, TRIAL_SUN_REP = 0.3F, SCAR_VALOR = 0.1F;
	static final int TRIAL_SEARCH = 1;
	// Ash Whisperer
	static final float TRIAL_WITNESS = 0.5F, TRIAL_WANTED = 0.5F, SCAR_LOCK = 0.08F, SCAR_PICK = 0.08F;
	// The Truth (and every patron's faithful)
	static final float TRIAL_MINDS = 1.0F, SCAR_TRUTH_MINDS = 0.25F, SCAR_ACCUSE = 0.15F;
	static final int TRIAL_EVERY = 100, TRIAL_SIGN_EVERY = 1200;
	static final float TRIAL_SIGN_CHANCE = 0.5F;

	// ---- the marks -------------------------------------------------------------------------------------------------------------
	public static final String SCAR = "scar", TRIAL = "trial", TRIAL_EASED_KIND = "trial_eased", RITE = "rite", HUNTED = "hunted", HUNTED_DONE = "hunted_done";

	/** (0.7.513) jaymoskingdoms:patron_trial_days: days of trials after turning from a patron (0: no trials, no champion's hunt). */
	public static GameRule<Integer> TRIAL_DAYS;
	/** (0.7.513) jaymoskingdoms:patron_scars: turning from a patron leaves its scar. */
	public static GameRule<Boolean> SCARS;

	/** The day each player was last seen by {@link #tick}. Not saved. */
	private static final Map<UUID, Long> SEEN = new HashMap<>();

	/** The rite on offer: who from, to whom, the days left, the days after, and whether it is paid during a turning. */
	record Rite(Patron from, Patron to, int daysLeft, int daysAfter, boolean streak) {
	}

	private Turning() {
	}

	public static void register() {
		TRIAL_DAYS = GameRuleBuilder.forInteger(TRIAL_DAYS_DEFAULT).category(GameRuleCategory.MISC).buildAndRegister(JaymosKingdoms.id("patron_trial_days"));
		SCARS = GameRuleBuilder.forBoolean(true).category(GameRuleCategory.MISC).buildAndRegister(JaymosKingdoms.id("patron_scars"));
	}

	static void forget(UUID id) {
		SEEN.remove(id);
	}

	static void clear() {
		SEEN.clear();
	}

	static int trialDays(MinecraftServer server) {
		return Mth.clamp(server.overworld().getGameRules().get(TRIAL_DAYS), 0, TRIAL_DAYS_MOST);
	}

	static int riteDays(int d) {
		return Math.max(RITE_LEAST_DAYS, (int)Math.ceil(d * RITE_SHORTEN));
	}

	// ---- reading the marks (client-safe) -----------------------------------------------------------------------------------------

	private static long todayOf(Player p) {
		return Patrons.day(p.level().getOverworldClockTime());
	}

	/** 1.0 for a live trial of {@code patron}, {@link #TRIAL_EASED} for an eased one, the largest if several, else 0. */
	public static float trial(Player p, Patron patron) {
		List<Mark> marks = Patrons.state(p).marks();
		if (marks.isEmpty()) {
			return 0.0F;
		}
		long today = todayOf(p);
		float s = 0.0F;
		for (Mark m : marks) {
			if (m.day() > today && m.patron().equals(patron.key)) {
				if (TRIAL.equals(m.kind())) {
					s = Math.max(s, 1.0F);
				} else if (TRIAL_EASED_KIND.equals(m.kind())) {
					s = Math.max(s, TRIAL_EASED);
				}
			}
		}
		return s;
	}

	/** The scars of {@code patron}, at most {@link #SCAR_MOST_EACH}. */
	public static int scars(Player p, Patron patron) {
		List<Mark> marks = Patrons.state(p).marks();
		if (marks.isEmpty()) {
			return 0;
		}
		int n = 0;
		for (Mark m : marks) {
			if (SCAR.equals(m.kind()) && m.patron().equals(patron.key)) {
				n++;
			}
		}
		return Math.min(SCAR_MOST_EACH, n);
	}

	/** Every scar the player holds, uncapped (the rite's price). */
	static int allScars(Player p) {
		int n = 0;
		for (Mark m : Patrons.state(p).marks()) {
			if (SCAR.equals(m.kind())) {
				n++;
			}
		}
		return n;
	}

	public static boolean anyTrial(Player p) {
		List<Mark> marks = Patrons.state(p).marks();
		if (marks.isEmpty()) {
			return false;
		}
		long today = todayOf(p);
		for (Mark m : marks) {
			if (m.day() > today && (TRIAL.equals(m.kind()) || TRIAL_EASED_KIND.equals(m.kind()))) {
				return true;
			}
		}
		return false;
	}

	/** The trial strength plus the scar count of a patron, as the first and second of a pair read from the marks once. */
	private static float s(Player p, Patron patron) {
		return trial(p, patron);
	}

	private static int n(Player p, Patron patron) {
		return scars(p, patron);
	}

	// ---- turning ------------------------------------------------------------------------------------------------------------------

	/** {@code sp}, sworn to {@code from}, has prayed three days at {@code to}'s shrine: they turn. */
	static void turn(ServerPlayer sp, Patron from, Patron to, long today) {
		MinecraftServer server = sp.level().getServer();
		int days = trialDays(server);
		boolean scars = server.overworld().getGameRules().get(SCARS);
		List<Mark> marks = new ArrayList<>(Patrons.state(sp).marks());
		if (scars) {
			marks.add(new Mark(SCAR, from.key, today));
		}
		boolean rite = marks.removeIf(m -> RITE.equals(m.kind()) && m.patron().equals(to.key) && m.day() >= today - RITE_KEEP_DAYS);
		if (days > 0) {
			marks.add(new Mark(rite ? TRIAL_EASED_KIND : TRIAL, from.key, today + (rite ? riteDays(days) : days)));
			if (marks.stream().noneMatch(m -> HUNTED.equals(m.kind()) && m.patron().equals(from.key))) {
				marks.add(new Mark(HUNTED, from.key, today));
			}
		}
		Patrons.swornTurned(sp, to, TURNED_FAVOUR, today, marks);
		// today's omens are not news: keep daily() from replacing the turned message
		SEEN.put(sp.getUUID(), today);
		// the elder guardian's face and curse sound, to this player only
		sp.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.GUARDIAN_ELDER_EFFECT, 1.0F));
		ServerLevel level = sp.level();
		level.playSound(null, sp.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8F, 0.7F);
		Fx.burst(level, sp.getX(), sp.getY() + 1.0, sp.getZ(), from.colour & 0xFFFFFF, 1.4F, 30, 0.5, 0.8, 0.5);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, sp.getX(), sp.getY() + 1.0, sp.getZ(), 12, 0.5, 0.8, 0.5, 0.02);
		sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.shrine.turned", Patrons.coloured(to), Patrons.coloured(from))
			.withStyle(style -> style.withColor(from.colour & 0xFFFFFF)));
	}

	// ---- the rite's terms -------------------------------------------------------------------------------------------------------

	/** The rite {@code sp} could be given now, or null. */
	static @Nullable Rite rite(ServerPlayer sp, long today) {
		Patrons.State state = Patrons.state(sp);
		Patron mine = state.patronOf();
		Mark longest = null;
		for (Mark m : state.marks()) {
			if (TRIAL.equals(m.kind()) && m.day() > today && Patron.byKey(m.patron()) != null && mine != null && (longest == null || m.day() > longest.day())) {
				longest = m;
			}
		}
		if (longest != null) {
			int left = (int)(longest.day() - today);
			return new Rite(Patron.byKey(longest.patron()), mine, left, riteDays(left), false);
		}
		Patrons.Streak st = state.streak();
		int days = trialDays(sp.level().getServer());
		if (mine != null && !st.patron().isEmpty() && !st.patron().equals(mine.key) && st.lastDay() >= today - 1 && days > 0) {
			Patron to = Patron.byKey(st.patron());
			if (to != null && state.marks().stream().noneMatch(m -> RITE.equals(m.kind()) && m.patron().equals(to.key))) {
				return new Rite(mine, to, -1, riteDays(days), true);
			}
		}
		return null;
	}

	// ---- the effects --------------------------------------------------------------------------------------------------------------

	/** Hearthkeeper: how much less every heal gives (0 to {@link #HEAL_CUT_MOST}). */
	public static float healing(Player p) {
		if (Patrons.state(p).marks().isEmpty()) {
			return 0.0F;
		}
		return Math.min(HEAL_CUT_MOST, TRIAL_HEAL * s(p, Patron.HEARTHKEEPER) + SCAR_HEAL * n(p, Patron.HEARTHKEEPER));
	}

	/** Anvil-Father: the wear on a hand item, with the extra points the trial and the scar add. */
	static int wear(Player p, int wear) {
		if (Patrons.state(p).marks().isEmpty()) {
			return wear;
		}
		float trial = TRIAL_WEAR * s(p, Patron.ANVIL_FATHER), scar = SCAR_WEAR * n(p, Patron.ANVIL_FATHER);
		if (trial <= 0.0F && scar <= 0.0F) {
			return wear;
		}
		int out = wear;
		for (int i = 0; i < wear; i++) {
			if (p.getRandom().nextFloat() < trial) {
				out++;
			}
			if (p.getRandom().nextFloat() < scar) {
				out++;
			}
		}
		return out;
	}

	/** Anvil-Father: the smithing zone, never below {@code -ZONE_SLIP_MOST}. */
	static double zone(Player smith) {
		if (Patrons.state(smith).marks().isEmpty()) {
			return 0.0;
		}
		double slip = TRIAL_ZONE * s(smith, Patron.ANVIL_FATHER) + SCAR_ZONE * n(smith, Patron.ANVIL_FATHER);
		return -Math.min(ZONE_SLIP_MOST, slip);
	}

	/** Crowned Sun trial: a gain of good name is worth less (at least 1). */
	static int reputation(Player p, int by) {
		if (by <= 0 || Patrons.state(p).marks().isEmpty()) {
			return by;
		}
		float s = s(p, Patron.CROWNED_SUN);
		return s <= 0.0F ? by : Math.max(1, Math.round(by * (1.0F - TRIAL_SUN_REP * s)));
	}

	/** Crowned Sun: the multiplier on the gap between halts. */
	static float halt(Player p) {
		if (Patrons.state(p).marks().isEmpty()) {
			return 1.0F;
		}
		float s = s(p, Patron.CROWNED_SUN);
		float m = Mth.lerp(s, 1.0F, TRIAL_HALT) * (1.0F - SCAR_HALT * n(p, Patron.CROWNED_SUN));
		return Math.max(HALT_LEAST, m);
	}

	/** Crowned Sun trial: more points of search. */
	static int search(Player p) {
		if (Patrons.state(p).marks().isEmpty()) {
			return 0;
		}
		float s = s(p, Patron.CROWNED_SUN);
		return s <= 0.0F ? 0 : (int)Math.ceil(TRIAL_SEARCH * s);
	}

	/** Crowned Sun scar: Valor feats are worth less. */
	static float valor(Player p) {
		return Patrons.state(p).marks().isEmpty() ? 1.0F : 1.0F - SCAR_VALOR * n(p, Patron.CROWNED_SUN);
	}

	/** Ash Whisperer scar: a pickpocket's hold is longer. */
	static float pickHold(Player p) {
		return Patrons.state(p).marks().isEmpty() ? 1.0F : 1.0F + SCAR_PICK * n(p, Patron.ASH_WHISPERER);
	}

	/** Ash Whisperer scar: a lock's sweet spot is narrower. */
	static float lockWidth(Player p) {
		return Patrons.state(p).marks().isEmpty() ? 1.0F : 1.0F - SCAR_LOCK * n(p, Patron.ASH_WHISPERER);
	}

	/** Ash Whisperer trial: the guards hunt you longer. */
	static long wanted(Player p, long ticks) {
		if (Patrons.state(p).marks().isEmpty()) {
			return ticks;
		}
		float s = s(p, Patron.ASH_WHISPERER);
		return s <= 0.0F ? ticks : Math.round(ticks * (1.0F + TRIAL_WANTED * s));
	}

	/** Ash Whisperer trial: witnesses see you from farther. */
	static double witnessRange(Player p) {
		return Patrons.state(p).marks().isEmpty() ? 1.0 : 1.0 + TRIAL_WITNESS * s(p, Patron.ASH_WHISPERER);
	}

	/** The Truth's scar: a true accusation wins less. */
	static float accuse(Player p) {
		return Patrons.state(p).marks().isEmpty() ? 1.0F : 1.0F - SCAR_ACCUSE * n(p, Patron.TRUTH);
	}

	/** Lantern Warden scar: blows on the dead land weaker. */
	static float graveBlows(Player p) {
		return Patrons.state(p).marks().isEmpty() ? 1.0F : 1.0F - SCAR_GRAVE * n(p, Patron.LANTERN_WARDEN);
	}

	/** A wild beast: an untamed wolf or polar bear, a boar, or any wild beast of ours (the set {@link Patrons} counts for the Huntsman). */
	static boolean wildBeast(Entity e) {
		return e instanceof com.jaymoskingdoms.wild.WildBeast || e instanceof com.jaymoskingdoms.beasts.WildBoar
			|| e instanceof Wolf wolf && !wolf.isTame() || e instanceof PolarBear;
	}

	/** How much farther {@code looker} sees {@code player}: the dead at night (Lantern Warden), wild beasts (Green Huntsman). */
	public static double sight(Player player, Mob looker, ServerLevel level) {
		if (Patrons.state(player).marks().isEmpty()) {
			return 1.0;
		}
		double f = 1.0;
		if (looker.is(EntityTypeTags.UNDEAD) && Vampires.night(level.getOverworldClockTime())) {
			f *= 1.0 + TRIAL_UNDEAD_SIGHT * s(player, Patron.LANTERN_WARDEN) + SCAR_UNDEAD_SIGHT * n(player, Patron.LANTERN_WARDEN);
		}
		if (wildBeast(looker)) {
			f *= 1.0 + TRIAL_BEAST_SIGHT * s(player, Patron.GREEN_HUNTSMAN) + SCAR_BEAST_SIGHT * n(player, Patron.GREEN_HUNTSMAN);
		}
		return f;
	}

	// ---- the Deep Mother's storms ------------------------------------------------------------------------------------------------

	/** One who turned from the Deep Mother, if a storm cell should find them on this roll. */
	public static @Nullable ServerPlayer stormBait(ServerLevel level, RandomSource random, int rollTicks) {
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator() || !p.isAlive()) {
				continue;
			}
			float s = trial(p, Patron.DEEP_MOTHER);
			if (s > 0.0F && random.nextDouble() < rollTicks / (TRIAL_STORM_DAYS * 24000.0) * s) {
				return p;
			}
		}
		return null;
	}

	/** A spot near one who turned from the Deep Mother, if the bolt of a storm at (x, z) is drawn to them. */
	public static @Nullable BlockPos boltDrawn(ServerLevel level, double x, double z, double radius, RandomSource random) {
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator() || !p.isAlive()) {
				continue;
			}
			float s = trial(p, Patron.DEEP_MOTHER);
			if (s <= 0.0F || Math.hypot(p.getX() - x, p.getZ() - z) > radius || random.nextFloat() >= TRIAL_BOLT_DRAWN * s) {
				continue;
			}
			double a = random.nextDouble() * Math.PI * 2.0;
			double r = TRIAL_BOLT_NEAR_MIN + random.nextDouble() * (TRIAL_BOLT_NEAR_MAX - TRIAL_BOLT_NEAR_MIN);
			BlockPos column = new BlockPos(Mth.floor(p.getX() + Math.cos(a) * r), level.getMinY(), Mth.floor(p.getZ() + Math.sin(a) * r));
			if (level.isLoaded(column)) {
				return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
			}
		}
		return null;
	}

	// ---- every second -------------------------------------------------------------------------------------------------------------

	static void tick(MinecraftServer server, long today, int tick) {
		for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
			if (!sp.isAlive() || sp.isSpectator() || Patrons.state(sp).marks().isEmpty()) {
				continue;
			}
			Long seen = SEEN.put(sp.getUUID(), today);
			if (seen == null || seen != today) {
				daily(sp, today);
			}
			ServerLevel level = sp.level();
			float mother = trial(sp, Patron.DEEP_MOTHER);
			int motherScars = scars(sp, Patron.DEEP_MOTHER);
			boolean aboard = aboard(sp);
			if ((mother > 0.0F || motherScars > 0) && sp.isUnderWater() && !aboard) {
				sp.setAirSupply(Math.max(-19, sp.getAirSupply() - Math.round(TRIAL_AIR * mother) - SCAR_AIR * motherScars));
			}
			if (mother > 0.0F && sp.isInWater() && !aboard && sp.getRandom().nextFloat() < TRIAL_TUG_CHANCE * mother) {
				sp.push(0.0, -TRIAL_TUG, 0.0);
				sp.syncVelocity = true;
			}
			if (tick % TRIAL_EVERY == 0) {
				hounds(sp, level);
			}
			if (tick % TRIAL_SIGN_EVERY == 0) {
				signs(sp, level, today);
			}
		}
	}

	private static boolean aboard(ServerPlayer sp) {
		return com.jaymoskingdoms.sailing.Ships.aboard(sp) != null || sp.getVehicle() instanceof AbstractBoat;
	}

	/** The first tick of a new day: marks run out, and a trial says how long is left. */
	private static void daily(ServerPlayer sp, long today) {
		List<Mark> marks = new ArrayList<>(Patrons.state(sp).marks());
		boolean ended = marks.removeIf(m -> (TRIAL.equals(m.kind()) || TRIAL_EASED_KIND.equals(m.kind())) && m.day() <= today);
		marks.removeIf(m -> RITE.equals(m.kind()) && m.day() < today - RITE_KEEP_DAYS);
		while (marks.size() > MARKS_MOST) {
			Mark drop = marks.stream().filter(m -> HUNTED_DONE.equals(m.kind())).findFirst()
				.or(() -> marks.stream().filter(m -> RITE.equals(m.kind())).findFirst()).orElse(null);
			if (drop == null) {
				break;
			}
			marks.remove(drop);
		}
		List<Mark> before = Patrons.state(sp).marks();
		if (marks.size() != before.size()) {
			Patrons.setMarks(sp, marks);
		}
		if (ended) {
			Patron last = null;
			for (Mark m : before) {
				if ((TRIAL.equals(m.kind()) || TRIAL_EASED_KIND.equals(m.kind())) && m.day() <= today) {
					last = Patron.byKey(m.patron());
				}
			}
			if (last != null) {
				sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.trial.over", Patrons.coloured(last)));
				sp.level().playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.6F, 1.2F);
			}
			return;
		}
		for (Mark m : marks) {
			Patron p = (TRIAL.equals(m.kind()) || TRIAL_EASED_KIND.equals(m.kind())) && m.day() > today ? Patron.byKey(m.patron()) : null;
			if (p != null) {
				sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.trial.day", Patrons.coloured(p), m.day() - today));
			}
		}
	}

	/** Green Huntsman: wild beasts turn on you. Lantern Warden: the dead are lured to you at night. */
	private static void hounds(ServerPlayer sp, ServerLevel level) {
		float huntsman = trial(sp, Patron.GREEN_HUNTSMAN);
		if (huntsman > 0.0F && !sp.isCreative()) {
			for (Mob mob : level.getEntitiesOfClass(Mob.class, sp.getBoundingBox().inflate(TRIAL_BEAST_RANGE),
				e -> e.isAlive() && wildBeast(e) && e.getTarget() == null && e.hasLineOfSight(sp))) {
				if (sp.getRandom().nextFloat() < TRIAL_BEAST_CHANCE * huntsman) {
					mob.setTarget(sp);
					if (mob instanceof NeutralMob neutral) {
						neutral.setPersistentAngerTarget(EntityReference.<LivingEntity>of(sp));
						neutral.startPersistentAngerTimer();
					}
				}
			}
		}
		float warden = trial(sp, Patron.LANTERN_WARDEN);
		if (warden > 0.0F && Vampires.night(level.getOverworldClockTime())) {
			for (Mob mob : level.getEntitiesOfClass(Mob.class, sp.getBoundingBox().inflate(TRIAL_UNDEAD_RANGE),
				e -> e.isAlive() && e.is(EntityTypeTags.UNDEAD) && e.getTarget() == null)) {
				Senses.lure(mob, sp.position(), sp.getUUID(), TRIAL_LURE_LOUDNESS * warden);
			}
		}
	}

	/** Now and then a sign of the old patron's omens shows, to this player only. */
	private static void signs(ServerPlayer sp, ServerLevel level, long today) {
		for (Mark m : Patrons.state(sp).marks()) {
			Patron p = (TRIAL.equals(m.kind()) || TRIAL_EASED_KIND.equals(m.kind())) && m.day() > today ? Patron.byKey(m.patron()) : null;
			if (p == null) {
				continue;
			}
			float s = trial(sp, p);
			if (sp.getRandom().nextFloat() >= TRIAL_SIGN_CHANCE * s) {
				continue;
			}
			level.sendParticles(sp, Fx.wisp(p.colour & 0xFFFFFF, 1.0F), true, false, sp.getX(), sp.getY() + 1.0, sp.getZ(), 14, 0.7, 0.9, 0.7, 0.0);
			float volume = 0.5F, pitch = 1.0F;
			SoundEvent sound;
			switch (p) {
				case HEARTHKEEPER -> sound = SoundEvents.FIRE_EXTINGUISH;
				case ANVIL_FATHER -> {
					sound = SoundEvents.ANVIL_LAND;
					volume = 0.3F;
					pitch = 0.5F;
				}
				case DEEP_MOTHER -> sound = SoundEvents.CONDUIT_DEACTIVATE;
				case GREEN_HUNTSMAN -> {
					sound = SoundEvents.POLAR_BEAR_WARNING;
					volume = 0.4F;
					pitch = 0.6F;
				}
				case LANTERN_WARDEN -> {
					sound = SoundEvents.CANDLE_EXTINGUISH;
					level.sendParticles(sp, ParticleTypes.SOUL, true, false, sp.getX(), sp.getY() + 1.0, sp.getZ(), 4, 0.5, 0.7, 0.5, 0.01);
				}
				case CROWNED_SUN -> sound = SoundEvents.BEACON_DEACTIVATE;
				case ASH_WHISPERER -> sound = SoundEvents.SCULK_CLICKING;
				default -> {
					sound = SoundEvents.VILLAGER_NO;
					volume = 0.6F;
					pitch = 0.7F;
				}
			}
			sp.connection.send(new ClientboundSoundPacket(Holder.direct(sound), SoundSource.AMBIENT, sp.getX(), sp.getEyeY(), sp.getZ(), volume, pitch, sp.getRandom().nextLong()));
		}
	}
}
