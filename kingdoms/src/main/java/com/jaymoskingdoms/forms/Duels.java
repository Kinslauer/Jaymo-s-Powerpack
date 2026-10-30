package com.jaymoskingdoms.forms;

import com.jaymoskingdoms.Downed;
import com.jaymoskingdoms.Faction;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Post;
import com.jaymoskingdoms.Reputation;
import com.jaymoskingdoms.Rulers;
import com.jaymoskingdoms.Speech;
import com.jaymoskingdoms.Temperaments;
import com.jaymoskingdoms.forms.Schools.School;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.SwingAnimation;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.385) Honour duels (Weapon Forms, patch 5). A <b>student</b> is a realm's guard (or the folk of a master's hall) whose
 * post lies in a school's land, or a school's master ({@link Schools#isMaster}). A duel is a spar like {@link GuardDrills}'s:
 * <ul>
 * <li><b>Offered</b> two ways. A student who sees you (within {@link #SEE} blocks) may challenge you, if you have joined a
 * <i>different</i> school or your standing in theirs is {@link #CHALLENGE_STANDING} or more ({@link #OFFER_CHANCE} at each look
 * every {@link #OFFER_EVERY} ticks, {@link Rivals#CHALLENGE_FACTOR} times as likely if your school is a rival of theirs, and no
 * more than one challenge to you every {@link #PLAYER_GAP} ticks): a line in a bubble and a note in your Tidings. The offer
 * stays open {@link #OFFER_TICKS} ticks. Or you ask: <b>"Face an honour duel"</b> on their MCA talk menu ({@link #ANSWER}),
 * which both takes up an open challenge and challenges them (a master only accepts a member of their own school).
 * (The accepting is the talk menu only; there is no bow.)</li>
 * <li><b>Fought</b> without killing: the student strikes you every {@link #STRIKE_EVERY} ticks ({@link #HARM}), now and then
 * with a move of their school ({@link SchooledGuards#showForm}, softer); whoever would drop under {@link #FLOOR} of their
 * most health first has fallen: that blow is stopped and the duel ends. Neither is ever killed by the other. It is a draw after
 * {@link #DUEL_TICKS}, and called off if you walk {@link #LEAVE} blocks away or either is downed by something else.</li>
 * <li><b>Ended</b>: the student stands down, is healed to {@link #HEAL_TO} and says a line chosen by their temperament
 * ({@link Temperaments}): the warm (kindhearted, cheerful, devout) are gracious and, if you lost, tend you and don't dock
 * your standing; the cold (proud, spiteful, hotheaded) jeer and dock it double. A win is {@link #WIN_STANDING} standing
 * ({@link #RIVAL_BONUS} more against a rival's student; {@link #MASTER_STANDING} for a master) in your school and
 * {@link #WIN_REP} reputation with the realm; beating a master opens that school's masterstroke. A loss costs
 * {@link #LOSS_STANDING}. A student fights you once a day (a bout, {@link Bouts}, does not count).</li>
 * </ul>
 * Blows between the two are no assault, theft or memory ({@link #dueling}).
 */
public final class Duels {
	// ---- tuning ----
	/** The share of most health whoever would drop under it has fallen; the share you must have to begin. */
	static final float FLOOR = 0.35F, START_HEALTH = 0.6F;
	/** The longest a duel lasts (ticks); how far (blocks) you may go from the student; the student's reach. */
	static final int DUEL_TICKS = 2400;
	static final double LEAVE = 24.0, REACH = 2.6;
	/** How often the student strikes (ticks) and how hard (health); a master's blow. */
	static final int STRIKE_EVERY = 26;
	static final float HARM = 3.0F, MASTER_HARM = 4.0F;
	/** The chance a strike is one of their school's moves instead. */
	static final float FORM_CHANCE = 0.35F;
	/** How far a student sees to challenge (blocks), how often they look (ticks), the chance at a look, the wait before another challenge to the same player (ticks), and how long an offer stays open (ticks). */
	static final double SEE = 7.0;
	static final int OFFER_EVERY = 40, OFFER_TICKS = 1200;
	static final float OFFER_CHANCE = 0.04F;
	static final long PLAYER_GAP = 6000L;
	/** Standing in a student's own school at which they test you anyway. */
	static final int CHALLENGE_STANDING = 10;
	/** Standing for a win, a win against a rival's student (more), a win against a master, a loss; reputation with the realm for a win (a master's: double). */
	static final int WIN_STANDING = 3, RIVAL_BONUS = 2, MASTER_STANDING = 8, LOSS_STANDING = 1, WIN_REP = 8;
	/** The share of its health the student is healed to when it's over (at least). */
	static final float HEAL_TO = 0.8F;
	/** How long (ticks) the kind one's tending of a loser lasts (regeneration). */
	static final int TEND_TICKS = 100;
	static final long DAY_TICKS = 24000L;
	/** How many variants each line has. */
	static final int LINES = 2;
	/** The MCA {@code main} menu answer (our dialogue data): "Face an honour duel". */
	public static final String ANSWER = "jaymoskingdoms_duel";

	/** How a duel ended. */
	public enum Outcome {
		/** The player won. */
		WON,
		/** The student won. */
		LOST,
		/** Time ran out. */
		DRAW,
		/** Called off (someone left, was downed by another, or the server stopped). */
		OFF
	}

	/** On a player: the day number each challenger (UUID) last fought them, and each festival bout entered ("bout:realm"). Saved, kept through death. */
	public static final AttachmentType<Map<String, Long>> DAYS = AttachmentRegistry.create(JaymosKingdoms.id("duel_days"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)).copyOnDeath());

	/** A challenge waiting to be taken up. */
	private record Offer(UUID student, School school, long until) {
	}

	private static final class Duel {
		final UUID player, foe;
		final School school;
		final boolean master, ranked;
		final long until;
		final @Nullable Consumer<Outcome> done;
		long nextBlow;
		int blows;

		Duel(UUID player, UUID foe, School school, boolean master, boolean ranked, long until, @Nullable Consumer<Outcome> done) {
			this.player = player;
			this.foe = foe;
			this.school = school;
			this.master = master;
			this.ranked = ranked;
			this.until = until;
			this.done = done;
		}
	}

	private static final Map<UUID, Duel> DUELS = new HashMap<>();
	private static final Map<UUID, Offer> OFFERS = new HashMap<>();
	/** player → the game time before which no student challenges them unasked. */
	private static final Map<UUID, Long> NEXT_OFFER = new HashMap<>();

	private Duels() {
	}

	public static void register() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.getPlayer().getUUID();
			OFFERS.remove(id);
			NEXT_OFFER.remove(id);
			Duel duel = DUELS.get(id);
			if (duel != null && handler.getPlayer().level() instanceof ServerLevel at) {
				// (the foe is looked up where the player stood, so it's healed even outside the Overworld)
				finish(at, null, duel, Outcome.OFF);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Duel duel : new ArrayList<>(DUELS.values())) {
				finish(server.overworld(), null, duel, Outcome.OFF);
			}
			DUELS.clear();
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			DUELS.clear();
			OFFERS.clear();
			NEXT_OFFER.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(Duels::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(Duels::allow);
		ServerLivingEntityEvents.ALLOW_DEATH.register(Duels::allowDeath);
	}

	// ---- who ---------------------------------------------------------------------------------------------------------

	/** Whether {@code who} can be asked for a duel at all: a school's master, or a grown guard of a school's land (not a ruler). */
	public static @Nullable School studentOf(LivingEntity who) {
		if (!(who instanceof PathfinderMob mob) || !(mob.level() instanceof ServerLevel level) || !mob.isAlive() || mob.isBaby()
			|| Downed.isDowned(mob) || Rulers.is(mob)) {
			return null;
		}
		if (!Schools.isMaster(mob) && (!Downed.isGuard(mob) || mob.getMainHandItem().getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem)) {
			return null;
		}
		return SchooledGuards.schoolOf(level, mob);
	}

	/** Whether "Face an honour duel" shows on {@code who}. */
	public static boolean canOffer(ServerPlayer player, LivingEntity who) {
		return studentOf(who) != null;
	}

	/** Whether a blow between {@code a} and {@code b} is a duel's (no crime, no grudge). */
	public static boolean dueling(Entity a, Entity b) {
		if (DUELS.isEmpty()) {
			return false;
		}
		for (Map.Entry<UUID, Duel> e : DUELS.entrySet()) {
			Duel d = e.getValue();
			if (a.getUUID().equals(e.getKey()) && b.getUUID().equals(d.foe) || b.getUUID().equals(e.getKey()) && a.getUUID().equals(d.foe)) {
				return true;
			}
		}
		return false;
	}

	/** Whether {@code id} (a student, or a player) is in a duel just now. */
	static boolean busy(UUID id) {
		for (Duel d : DUELS.values()) {
			if (d.foe.equals(id) || d.player.equals(id)) {
				return true;
			}
		}
		return false;
	}

	private static long today(ServerLevel level) {
		// (the overworld clock's day, as festivals count it, so sleeping or /time can't open a second one)
		return Math.floorDiv(level.getOverworldClockTime(), DAY_TICKS);
	}

	/** Whether the student has fought {@code player} today. */
	private static boolean foughtToday(ServerLevel level, Player player, UUID student) {
		Long last = player.getAttachedOrElse(DAYS, Map.<String, Long>of()).get(student.toString());
		return last != null && last >= today(level);
	}

	/** Records {@code key} (a student's UUID or a bout) as done today, forgetting what is two days old. */
	static void markDay(ServerLevel level, ServerPlayer player, String key) {
		long today = today(level);
		Map<String, Long> next = new HashMap<>();
		for (Map.Entry<String, Long> e : player.getAttachedOrElse(DAYS, Map.<String, Long>of()).entrySet()) {
			if (e.getValue() >= today - 1) {
				next.put(e.getKey(), e.getValue());
			}
		}
		next.put(key, today);
		player.setAttached(DAYS, Map.copyOf(next));
	}

	/** Whether {@code key} was marked today. */
	static boolean markedToday(ServerLevel level, Player player, String key) {
		Long last = player.getAttachedOrElse(DAYS, Map.<String, Long>of()).get(key);
		return last != null && last >= today(level);
	}

	/** Whether the student {@code school} would challenge {@code player} unasked: they've joined another school, or stand high in this. */
	static boolean willing(Player player, School school) {
		List<School> joined = Schools.joined(player);
		return !joined.isEmpty() && !joined.contains(school) || Schools.standing(player, school) >= CHALLENGE_STANDING;
	}

	/** The school of {@code player} that a result counts for: one they've joined whose weapon they hold, else the first, else {@code fallback}. */
	static School schoolFor(Player player, School fallback) {
		List<School> joined = Schools.joined(player);
		for (School s : joined) {
			if (s.family().holds(player.getMainHandItem())) {
				return s;
			}
		}
		return joined.isEmpty() ? fallback : joined.get(0);
	}

	/** Whether {@code player} is in a duel or has a challenge open. */
	public static boolean engaged(Player player) {
		return DUELS.containsKey(player.getUUID()) || OFFERS.containsKey(player.getUUID());
	}

	// ---- offering and taking up --------------------------------------------------------------------------------------

	/** "Face an honour duel" ({@link #ANSWER}): takes up their challenge if they've made one, else challenges them. */
	public static void offer(ServerPlayer player, LivingEntity who) {
		School school = studentOf(who);
		if (school == null || !(who instanceof PathfinderMob foe) || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		Duel mine = DUELS.get(player.getUUID());
		if (mine != null) {
			say(level, foe, player, mine.foe.equals(foe.getUUID()) ? "duel.jaymoskingdoms.under_way" : "duel.jaymoskingdoms.elsewhere");
			return;
		}
		if (busy(foe.getUUID()) || Bouts.entered(player.getUUID()) || GuardDrills.busy(player.getUUID())) {
			say(level, foe, player, "duel.jaymoskingdoms.busy");
			return;
		}
		Faction realm = SchooledGuards.realmOf(level, foe);
		if (realm != null && Reputation.get(player, realm.id()) <= Reputation.HOSTILE) {
			// (a realm that hates you won't cross blades for honour)
			say(level, foe, player, "duel.jaymoskingdoms.not_now");
			return;
		}
		Offer open = OFFERS.get(player.getUUID());
		if (open != null && open.student.equals(foe.getUUID())) {
			OFFERS.remove(player.getUUID());
			if (begin(level, player, foe, school, true, null)) {
				return;
			}
			say(level, foe, player, "duel.jaymoskingdoms.not_now");
			return;
		}
		boolean master = Schools.isMaster(foe);
		if (master && !Schools.joined(player).contains(school)) {
			say(level, foe, player, "duel.jaymoskingdoms.not_yours");
			return;
		}
		if (foughtToday(level, player, foe.getUUID())) {
			say(level, foe, player, "duel.jaymoskingdoms.tomorrow");
			return;
		}
		if (!hale(player) || foe.getTarget() != null || foe.getHealth() < foe.getMaxHealth() * START_HEALTH) {
			say(level, foe, player, "duel.jaymoskingdoms.not_now");
			return;
		}
		OFFERS.remove(player.getUUID());
		if (!begin(level, player, foe, school, true, null)) {
			say(level, foe, player, "duel.jaymoskingdoms.not_now");
		}
	}

	private static boolean hale(ServerPlayer player) {
		return player.isAlive() && !Downed.isDowned(player) && !player.isSpectator() && player.getHealth() >= player.getMaxHealth() * START_HEALTH;
	}

	/**
	 * Begins a duel between {@code player} and {@code foe} (of {@code school}). {@code ranked}: it counts (standing, reputation,
	 * a master's masterstroke, the student's day); a bout's duels don't. {@code done} is told how it ended. False if it can't begin
	 * (either is hurt or busy).
	 */
	static boolean begin(ServerLevel level, ServerPlayer player, PathfinderMob foe, School school, boolean ranked, @Nullable Consumer<Outcome> done) {
		if (DUELS.containsKey(player.getUUID()) || busy(foe.getUUID()) || GuardDrills.busy(foe.getUUID()) || GuardDrills.busy(player.getUUID()) || !hale(player) || !foe.isAlive() || Downed.isDowned(foe)
			|| foe.getHealth() < foe.getMaxHealth() * START_HEALTH || foe.level() != level) {
			return false;
		}
		long now = level.getGameTime();
		boolean master = Schools.isMaster(foe);
		Duel duel = new Duel(player.getUUID(), foe.getUUID(), school, master, ranked, now + DUEL_TICKS, done);
		duel.nextBlow = now + 50;
		DUELS.put(player.getUUID(), duel);
		if (ranked) {
			markDay(level, player, foe.getUUID().toString());
		}
		foe.setTarget(null);
		foe.getNavigation().stop();
		say(level, foe, player, "duel.jaymoskingdoms.start");
		return true;
	}

	/** The students who may challenge a player just now, asked of each player every {@link #OFFER_EVERY} ticks. */
	private static void challenge(MinecraftServer server, ServerPlayer player, ServerLevel level) {
		long now = level.getGameTime();
		Long next = NEXT_OFFER.get(player.getUUID());
		if (engaged(player) || Bouts.entered(player.getUUID()) || GuardDrills.busy(player.getUUID()) || next != null && now < next || !hale(player)) {
			return;
		}
		for (PathfinderMob foe : level.getEntitiesOfClass(PathfinderMob.class, player.getBoundingBox().inflate(SEE),
			m -> studentOf(m) != null && !Schools.isMaster(m))) {
			School school = studentOf(foe);
			if (school == null || foe.getTarget() != null || busy(foe.getUUID()) || GuardDrills.busy(foe.getUUID()) || foughtToday(level, player, foe.getUUID())
				|| foe.getHealth() < foe.getMaxHealth() * START_HEALTH || !willing(player, school) || !foe.hasLineOfSight(player)) {
				continue;
			}
			Faction realm = SchooledGuards.realmOf(level, foe);
			if (realm != null && Reputation.get(player, realm.id()) <= Reputation.HOSTILE) {
				continue;
			}
			School rival = Rivals.rivalOf(server, player, school);
			float chance = OFFER_CHANCE * (rival != null ? Rivals.CHALLENGE_FACTOR : 1.0F);
			if (level.getRandom().nextFloat() >= chance) {
				continue;
			}
			OFFERS.put(player.getUUID(), new Offer(foe.getUUID(), school, now + OFFER_TICKS));
			NEXT_OFFER.put(player.getUUID(), now + PLAYER_GAP);
			// asked once today, answered or not
			markDay(level, player, foe.getUUID().toString());
			String key = rival != null ? "duel.jaymoskingdoms.challenge.rival." : "duel.jaymoskingdoms.challenge.";
			Speech.tell(level, foe, player, Component.translatable(key + level.getRandom().nextInt(LINES), player.getDisplayName(),
				Rivals.name(school), rival != null ? Rivals.name(rival) : Rivals.name(school)), Speech.GOLD);
			Post.tidings(player.getUUID(), Component.translatable("tidings.jaymoskingdoms.duel.challenged", foe.getDisplayName(), Rivals.name(school)));
			return;
		}
	}

	// ---- the tick ----------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % OFFER_EVERY == 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.level() instanceof ServerLevel level && !player.isSpectator() && player.isAlive()) {
					challenge(server, player, level);
				}
			}
			lapse(server);
		}
		if (DUELS.isEmpty()) {
			return;
		}
		for (Duel duel : new ArrayList<>(DUELS.values())) {
			ServerPlayer player = server.getPlayerList().getPlayer(duel.player);
			if (player == null) {
				finish(server.overworld(), null, duel, Outcome.OFF);
				continue;
			}
			ServerLevel level = player.level();
			if (!(level.getEntity(duel.foe) instanceof PathfinderMob foe) || !foe.isAlive() || foe.level() != level) {
				finish(level, player, duel, Outcome.OFF);
				continue;
			}
			if (!player.isAlive() || Downed.isDowned(player)) {
				// downed by something else (a blow of the foe's would have been stopped first)
				finish(level, player, duel, Outcome.OFF);
				continue;
			}
			if (Downed.isDowned(foe)) {
				finish(level, player, duel, Outcome.OFF);
				continue;
			}
			if (foe.distanceTo(player) > LEAVE) {
				finish(level, player, duel, Outcome.OFF);
				continue;
			}
			long now = level.getGameTime();
			if (now >= duel.until) {
				finish(level, player, duel, Outcome.DRAW);
				continue;
			}
			fight(level, player, foe, duel, now);
		}
	}

	/** Challenges not taken up lapse (with a word, if the player is still near). */
	private static void lapse(MinecraftServer server) {
		if (OFFERS.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, Offer> e : new ArrayList<>(OFFERS.entrySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			Offer offer = e.getValue();
			if (player == null) {
				OFFERS.remove(e.getKey());
				continue;
			}
			ServerLevel level = player.level();
			Entity student = level.getEntity(offer.student);
			boolean gone = !(student instanceof PathfinderMob s) || !s.isAlive() || Downed.isDowned(s) || s.getTarget() != null;
			if (gone || level.getGameTime() >= offer.until || student.distanceTo(player) > LEAVE) {
				OFFERS.remove(e.getKey());
				if (!gone && student.distanceTo(player) <= SEE * 2) {
					say(level, (PathfinderMob) student, player, "duel.jaymoskingdoms.declined");
				}
			}
		}
	}

	private static void fight(ServerLevel level, ServerPlayer player, PathfinderMob foe, Duel duel, long now) {
		// their own fighting goals take no part: the duel's blows are the ones below
		if (foe.getTarget() != null) {
			foe.setTarget(null);
		}
		foe.setLastHurtByMob(null);
		foe.getLookControl().setLookAt(player, 30.0F, 30.0F);
		if (foe.distanceTo(player) > REACH) {
			foe.getNavigation().moveTo(player, 1.1);
			return;
		}
		foe.getNavigation().stop();
		if (now < duel.nextBlow) {
			return;
		}
		duel.nextBlow = now + STRIKE_EVERY;
		duel.blows++;
		if (level.getRandom().nextFloat() < FORM_CHANCE && SchooledGuards.showForm(level, foe, player, true)) {
			return;
		}
		foe.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		player.hurtServer(level, level.damageSources().mobAttack(foe), duel.master ? MASTER_HARM : HARM);
	}

	// ---- damage ------------------------------------------------------------------------------------------------------

	/** The blow that would take either duellist under the floor is stopped, and the duel is over. */
	private static boolean allow(LivingEntity entity, DamageSource source, float amount) {
		if (DUELS.isEmpty() || amount <= 0.0F) {
			return true;
		}
		if (source.getEntity() == null) {
			// fire, poison, a fall: it can't take a duelling student under the floor either (the duel goes on)
			return !(foeInDuel(entity) && entity.getHealth() - amount <= entity.getMaxHealth() * FLOOR);
		}
		Entity by = source.getEntity();
		if (entity instanceof ServerPlayer player) {
			Duel duel = DUELS.get(player.getUUID());
			if (duel == null || !by.getUUID().equals(duel.foe) || !(player.level() instanceof ServerLevel level)) {
				return true;
			}
			if (player.getHealth() - amount <= player.getMaxHealth() * FLOOR) {
				finish(level, player, duel, Outcome.LOST);
				return false;
			}
			return true;
		}
		if (by instanceof ServerPlayer attacker) {
			Duel duel = DUELS.get(attacker.getUUID());
			if (duel != null && duel.foe.equals(entity.getUUID()) && attacker.level() instanceof ServerLevel level
				&& entity.getHealth() - amount <= entity.getMaxHealth() * FLOOR) {
				finish(level, attacker, duel, Outcome.WON);
				return false;
			}
		}
		return true;
	}

	/** A safety: a death in a duel at its opponent's hand (a damage path the floor didn't see) is stopped, and the duel ended. */
	private static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
		if (DUELS.isEmpty()) {
			return true;
		}
		if (source.getEntity() == null) {
			if (foeInDuel(entity)) {
				entity.setHealth(Math.max(1.0F, entity.getMaxHealth() * FLOOR));
				return false;
			}
			return true;
		}
		Entity by = source.getEntity();
		if (entity instanceof ServerPlayer player) {
			Duel duel = DUELS.get(player.getUUID());
			if (duel != null && by.getUUID().equals(duel.foe) && player.level() instanceof ServerLevel level) {
				player.setHealth(Math.max(1.0F, player.getMaxHealth() * FLOOR));
				finish(level, player, duel, Outcome.LOST);
				return false;
			}
		} else if (by instanceof ServerPlayer attacker) {
			Duel duel = DUELS.get(attacker.getUUID());
			if (duel != null && duel.foe.equals(entity.getUUID()) && attacker.level() instanceof ServerLevel level) {
				entity.setHealth(Math.max(1.0F, entity.getMaxHealth() * FLOOR));
				finish(level, attacker, duel, Outcome.WON);
				return false;
			}
		}
		return true;
	}

	private static boolean foeInDuel(LivingEntity entity) {
		for (Duel duel : DUELS.values()) {
			if (duel.foe.equals(entity.getUUID())) {
				return true;
			}
		}
		return false;
	}

	// ---- endings -----------------------------------------------------------------------------------------------------

	/** The duel is over: the student stands down, and what it was worth is paid. {@code player} null: they are gone. */
	private static void finish(ServerLevel level, @Nullable ServerPlayer player, Duel duel, Outcome outcome) {
		if (!DUELS.remove(duel.player, duel)) {
			return;
		}
		PathfinderMob foe = level.getEntity(duel.foe) instanceof PathfinderMob f && f.isAlive() ? f : null;
		if (foe != null) {
			foe.setTarget(null);
			foe.getNavigation().stop();
			float least = foe.getMaxHealth() * HEAL_TO;
			if (!Downed.isDowned(foe) && foe.getHealth() < least) {
				foe.setHealth(least);
			}
		}
		if (player != null && foe != null && outcome != Outcome.OFF) {
			conclude(level, player, foe, duel, outcome);
		}
		if (duel.done != null) {
			duel.done.accept(outcome);
		}
	}

	private enum Mood {
		WARM, PLAIN, COLD;

		String key() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	private static Mood moodOf(PathfinderMob foe) {
		Temperaments.Temperament t = Temperaments.of(foe);
		if (t == null) {
			return Mood.PLAIN;
		}
		return switch (t) {
			case KINDHEARTED, CHEERFUL, DEVOUT -> Mood.WARM;
			case PROUD, SPITEFUL, HOTHEADED -> Mood.COLD;
			default -> Mood.PLAIN;
		};
	}

	private static void conclude(ServerLevel level, ServerPlayer player, PathfinderMob foe, Duel duel, Outcome outcome) {
		Mood mood = moodOf(foe);
		String end = outcome == Outcome.WON ? "won" : outcome == Outcome.LOST ? "lost" : "draw";
		Speech.tell(level, foe, player, Component.translatable("duel.jaymoskingdoms.end." + end + "." + mood.key() + "." + level.getRandom().nextInt(LINES),
			player.getDisplayName()), mood == Mood.COLD ? Speech.RED : Speech.GOLD);
		level.sendParticles(outcome == Outcome.WON ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.CRIT, foe.getX(), foe.getY() + 1.6, foe.getZ(), 8, 0.3, 0.3, 0.3, 0.1);
		if (outcome == Outcome.LOST && mood == Mood.WARM) {
			// the kind one tends the loser
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, TEND_TICKS, 1, true, false));
		}
		if (!duel.ranked) {
			return;
		}
		// (against a master it's always their school, whatever weapon you hold)
		School mine = duel.master ? duel.school : schoolFor(player, duel.school);
		boolean joined = Schools.joined(player).contains(mine);
		Faction realm = SchooledGuards.realmOf(level, foe);
		if (outcome == Outcome.WON) {
			boolean rival = Rivals.rival(level.getServer(), mine, duel.school);
			int gain = (duel.master ? MASTER_STANDING : WIN_STANDING) + (rival ? RIVAL_BONUS : 0);
			if (joined) {
				Schools.addStanding(player, mine, gain);
			}
			if (realm != null) {
				Reputation.change(player, realm, duel.master ? WIN_REP * 2 : WIN_REP);
			}
			// (beating the master opens it only once your standing has earned mastery; before that it's a lesson and standing)
			if (duel.master && joined && !Schools.masterstrokeOpen(player, duel.school)
				&& Schools.standing(player, duel.school) >= Schools.MASTERY_STANDING) {
				Schools.openMasterstroke(player, duel.school);
			}
			Post.tidings(player.getUUID(), Component.translatable(duel.master ? "tidings.jaymoskingdoms.duel.won_master" : "tidings.jaymoskingdoms.duel.won",
				foe.getDisplayName(), Rivals.name(mine)));
		} else if (outcome == Outcome.LOST) {
			int dock = mood == Mood.WARM ? 0 : mood == Mood.COLD ? LOSS_STANDING * 2 : LOSS_STANDING;
			if (joined && dock > 0 && Schools.standing(player, mine) > 0) {
				Schools.addStanding(player, mine, -Math.min(dock, Schools.standing(player, mine)));
			}
			Post.tidings(player.getUUID(), Component.translatable("tidings.jaymoskingdoms.duel.lost." + mood.key(), foe.getDisplayName()));
		}
	}

	/** The student speaks one of {@link #LINES} variants of {@code key} (a speech bubble, not chat). */
	private static void say(ServerLevel level, PathfinderMob foe, ServerPlayer player, String key) {
		Speech.tell(level, foe, player, Component.translatable(key + "." + level.getRandom().nextInt(LINES), player.getDisplayName()), Speech.GOLD);
	}
}
