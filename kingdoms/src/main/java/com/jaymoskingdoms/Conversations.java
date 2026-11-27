package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (Andy 2026-11-19, clip: "the npcs should stop moving and return their message from the separate cam. the convo ends
 * when it ends and only then do they resume their tasks") A talk with an MCA villager is a cutscene
 * ({@link com.jaymoskingdoms.client.DialogueCinematic}): from the moment their talk menu opens until the client says the
 * talk is over (their reply has been said and read, {@link DialogueCue.Done}), the villager stands where they are,
 * turned to you, and goes back to their work only after. MCA shuts its menu as soon as a chat, joke or story is picked,
 * which used to let them walk off mid-sentence; now the hold outlasts the menu for the reply.
 *
 * <p>As in every cutscene (standing ruling): the speaker stops to talk, and the player is held and can't be hurt while it
 * runs.
 *
 * <p>Andy (2026-11-27, clip): "conversations end prematurely it should go back to the wheel unless you end the
 * conversation, they get bored or refuse talking to you, or something outside influences it". After any talk action
 * (an answer on the wheel, a reply, a command from Ask of them...) MCA shuts its menu; once the villager's reply has been
 * said, the client asks for the talk again ({@link DialogueCue.Again}) and their menu opens back on the wheel, with no new
 * greeting ({@link #takeResumed}). The talk ends only when:
 * <ul>
 * <li>the player ends it: Esc, or "Take your leave" ({@link #GOODBYE}) on the wheel;</li>
 * <li>they get bored: each pick on the wheel spends their patience for this talk ({@link #patience}: hearts, mood and
 * nature, worked out once when the talk starts, so they keep one attitude all through it), a refused pick spends more,
 * and leaving them waiting on the wheel ({@link #IDLE_TICKS}) does it too. They say so, and won't start another talk
 * with you for a while ({@link #WORN_OUT_TICKS});</li>
 * <li>they refuse to talk to you: a grudge turns a chat down, they've talked enough today (MCA's fatigue), they turn
 * their back on you ({@link #refusedTalk}); or what you asked ends it by its nature (an audience, a duel: {@link #ends});</li>
 * <li>something outside breaks in: either of you hurt or set upon, a monster close or coming for either of you, fire on
 * either of you or a fire alarm, someone asleep, something they must see to ({@link TalkBusy#why}), distance, water, a
 * mount, falling out of play.</li>
 * </ul>
 */
public final class Conversations {
	/** "Take your leave": the wheel's goodbye, an MCA main answer handled by us ({@code McaDialogueMixin}). */
	public static final String GOODBYE = "jaymoskingdoms_goodbye";
	/** A talk with no word from the client in this long ends anyway (ticks): the longest reply shot, and some. */
	static final int REPLY_TICKS = 20 * 16;
	/** Farther apart than this (blocks), the talk is over. */
	static final double RANGE = 10.0;
	/** A menu opened and shut with nothing said in it ends the talk after this long (ticks: time for the menu to show). */
	static final int MENU_GRACE = 10;
	/**
	 * Patience: how many picks on the wheel a talk lasts, before hearts, mood and nature; hearts for one more (fewer below
	 * none), at most {@link #HEARTS_PATIENCE_MAX} more or {@link #HEARTS_PATIENCE_MIN} fewer; mood points for one more or
	 * fewer (MCA's mood runs -15 to 15); never fewer than {@link #PATIENCE_MIN}.
	 */
	static final int PATIENCE = 6, HEARTS_PER_PATIENCE = 20, HEARTS_PATIENCE_MIN = -2, HEARTS_PATIENCE_MAX = 5, MOOD_PER_PATIENCE = 5,
		PATIENCE_MIN = 2;
	/** What a refused pick costs on top of the pick itself. */
	static final int REFUSAL_COST = 1;
	/** Left waiting on the wheel this long with nothing picked (ticks), they lose interest. */
	static final int IDLE_TICKS = 20 * 45;
	/** Once they've had enough of you, they won't start another talk with you for this long (ticks). */
	static final int WORN_OUT_TICKS = 20 * 60;
	/** A talk set aside for Family Tree or one of our own talk screens keeps this long at most (ticks). */
	static final int ASIDE_TICKS = 20 * 60 * 5;
	/** A monster this close to the villager (blocks) breaks the talk off; or one this near that's after either of you. */
	static final double DANGER_CLOSE = 4.0, DANGER_RANGE = 10.0;
	/** How often the monster check runs (ticks). */
	static final int DANGER_EVERY = 10;
	/** How many lines each way of ending has, per tone. */
	static final int BORED_LINES = 2, GOODBYE_LINES = 2;
	/** One "not now" line to a player every this many ticks while they've had enough of them. */
	static final int WORN_GAP = 40;

	private static final Identifier HOLD = JaymosKingdoms.id("talk_hold");

	/** How a talk is ending: their last word is said ({@code PARTED}), or still to say, bored of you ({@code BORED}). */
	enum End {
		PARTED, BORED
	}

	private static final class Talk {
		final Mob villager;
		final long started;
		long until;
		/** Something was said in it (a pick, a reply): the talk outlasts the menu for the reply. */
		boolean said;
		/** Picks on the wheel this talk lasts (worked out once, when it starts), the picks made, and the last one's time. */
		final int patience;
		int picks;
		long lastPick;
		/** Set once the talk is to end after the reply on screen; null while it goes on. */
		@Nullable End ending;
		/** Set aside for another screen (Family Tree, one of our talk screens); gone back to the wheel, menu not yet open. */
		boolean aside, resumed;

		Talk(Mob villager, long started, int patience) {
			this.villager = villager;
			this.started = started;
			this.until = started + REPLY_TICKS;
			this.patience = patience;
			this.lastPick = started;
		}
	}

	/** Player → the one they're talking to. */
	private static final Map<UUID, Talk> TALKS = new HashMap<>();
	/** "villager/player" → the game time they'll talk to that player again, having had enough of them. */
	private static final Map<String, Long> WORN = new HashMap<>();
	/** player → the game time of their last "not now". */
	private static final Map<UUID, Long> WORN_SAID = new HashMap<>();
	/** The menu is being opened again by {@link #again}, not by a click: a click's checks don't apply. */
	private static boolean reopening;

	private Conversations() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Conversations::tick);
		// (the player is out of harm's way while the cutscene runs)
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer player) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
				return true;
			}
			Talk talk = TALKS.get(player.getUUID());
			if (talk == null) {
				return true;
			}
			if (source.getEntity() instanceof LivingEntity attacker && attacker != talk.villager) {
				// (Andy 2026-11-27: "something outside influences it") someone going for you breaks the talk off; this
				// blow is still kept off you, the cutscene being on when it came
				end(player, true);
			}
			return false;
		});
		// (the villager is hurt: they break off to see to it)
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof Mob mob && !TALKS.isEmpty()) {
				breakOff(mob);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> end(handler.player, false));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			TALKS.clear();
			WORN.clear();
			WORN_SAID.clear();
		});
	}

	/**
	 * {@code player} is talking to {@code villager}: their menu opened, or ({@code said}) a line was said in it. Holds both
	 * till it's over.
	 */
	public static void begin(ServerPlayer player, Entity villager, boolean said) {
		if (!(villager instanceof Mob mob) || !mob.isAlive() || mob.level() != player.level()) {
			return;
		}
		long now = mob.level().getGameTime();
		Talk talk = TALKS.get(player.getUUID());
		if (talk != null && talk.villager == mob) {
			talk.until = Math.max(talk.until, now + REPLY_TICKS);
			talk.said |= said;
			return;
		}
		if (talk != null) {
			end(player, true);
		}
		for (Talk other : TALKS.values()) {
			if (other.villager == mob) {
				// (one talk at a time: they're already busy with someone)
				return;
			}
		}
		Talk fresh = new Talk(mob, now, patience(player, mob));
		fresh.said = said;
		TALKS.put(player.getUUID(), fresh);
		hold(mob, true);
		hold(player, true);
		still(mob, player);
	}

	/**
	 * How many picks {@code mob}'s patience with {@code player} lasts this talk: {@link #PATIENCE}, more for hearts, a good
	 * mood and a warm nature, fewer for none, a low mood, a timid nature or one cold to you ({@link Replies#tone}).
	 */
	static int patience(ServerPlayer player, Mob mob) {
		int p = PATIENCE;
		if (mob instanceof Villager v) {
			p += Math.clamp(Math.floorDiv(McaCompat.heartsOf(v, player), HEARTS_PER_PATIENCE), HEARTS_PATIENCE_MIN, HEARTS_PATIENCE_MAX);
			p += McaCompat.mood(v) / MOOD_PER_PATIENCE;
		}
		p += switch (Replies.tone(player, mob)) {
			case "gentle" -> 1;
			case "timid", "cold" -> -1;
			default -> 0;
		};
		return Math.max(PATIENCE_MIN, p);
	}

	/** {@code player} picked something on {@code villager}'s wheel: it spends their patience. */
	static void picked(ServerPlayer player, Entity villager) {
		Talk talk = of(player, villager);
		if (talk != null) {
			talk.picks++;
			talk.lastPick = villager.level().getGameTime();
			tire(talk);
		}
	}

	/**
	 * {@code villager} turned down {@code player}'s pick (MCA's dice, {@code McaRefusalMixin}): it costs more patience; and
	 * if it was that they've talked enough today ({@code tired}), the talk ends with that.
	 */
	public static void refused(ServerPlayer player, Entity villager, boolean tired) {
		Talk talk = of(player, villager);
		if (talk == null) {
			return;
		}
		if (tired) {
			talk.ending = End.PARTED;
			return;
		}
		talk.picks += REFUSAL_COST;
		tire(talk);
	}

	/** {@code villager} won't talk with {@code player} (a grudge, a back turned): what they said is their last word. */
	public static void refusedTalk(ServerPlayer player, Entity villager) {
		ends(player, villager);
	}

	/** What {@code player} asked of {@code villager} ends the talk by its nature (an audience, a duel, a spar). */
	public static void ends(ServerPlayer player, Entity villager) {
		Talk talk = of(player, villager);
		if (talk != null) {
			talk.ending = End.PARTED;
		}
	}

	/** "Take your leave": {@code villager} bids {@code player} goodbye, and the talk ends after it. */
	public static void goodbye(ServerPlayer player, Entity villager) {
		// (a guard you keep walking off from tells you to speak plain instead: Quips)
		if (!Quips.leftTalk(player, villager)) {
			say(player, villager, "goodbye", GOODBYE_LINES);
		}
		ends(player, villager);
	}

	private static void tire(Talk talk) {
		if (talk.ending == null && talk.picks >= talk.patience) {
			talk.ending = End.BORED;
		}
	}

	/**
	 * {@code villager}'s line for {@code kind} (bored, idle, goodbye, worn) to {@code player}, in their tone; a gesture to
	 * one who doesn't share their tongue.
	 */
	private static void say(ServerPlayer player, Entity villager, String kind, int lines) {
		if (!(villager instanceof LivingEntity who) || !(who.level() instanceof ServerLevel level)) {
			return;
		}
		if (!Languages.understands(player, who)) {
			Speech.sayPlainly(level, who, player, Component.translatable("talk_end.jaymoskingdoms.gesture." + kind), Speech.WHITE);
			return;
		}
		String key = "talk_end.jaymoskingdoms." + kind + "." + Replies.tone(player, who) + (lines > 1 ? "." + who.getRandom().nextInt(lines) : "");
		Component line = Component.translatable(key, Recollections.nameTo(player, who));
		if (kind.equals("worn")) {
			// (no talk is on: over their head)
			Speech.tell(level, who, player, line, Speech.WHITE);
		} else {
			McaCompat.talkSay(who, player, line);
		}
	}

	/**
	 * The client has shown {@code villagerId}'s reply to {@code player}'s last pick: the talk goes back to the wheel (their
	 * menu opens again), or, if it's ending, ends with their last word.
	 */
	static void again(ServerPlayer player, int villagerId) {
		Talk talk = TALKS.get(player.getUUID());
		if (talk == null || talk.villager.getId() != villagerId) {
			DialogueCue.end(player, villagerId);
			return;
		}
		Mob mob = talk.villager;
		long now = mob.level().getGameTime();
		talk.aside = false;
		talk.until = Math.max(talk.until, now + REPLY_TICKS);
		if (talk.ending == End.BORED) {
			// bored of you: they say so, and that's the end of it
			talk.ending = End.PARTED;
			WORN.put(mob.getUUID() + "/" + player.getUUID(), now + WORN_OUT_TICKS);
			say(player, mob, "bored", BORED_LINES);
			DialogueCue.last(player, mob);
			return;
		}
		if (talk.ending != null) {
			end(player, true);
			return;
		}
		talk.lastPick = now;
		if (McaCompat.interactingWith(mob, player)) {
			// (already back on it: Family Tree shut, the bow to a ruler done)
			return;
		}
		talk.resumed = true;
		reopening = true;
		try {
			if (!McaCompat.talkReopen(mob, player)) {
				talk.resumed = false;
				end(player, true);
			}
		} finally {
			reopening = false;
		}
	}

	/** The client set the talk with {@code villagerId} aside for another screen (Family Tree, one of our talk screens). */
	static void aside(ServerPlayer player, int villagerId) {
		Talk talk = TALKS.get(player.getUUID());
		if (talk != null && talk.villager.getId() == villagerId) {
			talk.aside = true;
			talk.until = talk.villager.level().getGameTime() + ASIDE_TICKS;
		}
	}

	/** Whether the talk menu is being opened again by {@link #again} (a click's checks don't apply). */
	public static boolean reopening() {
		return reopening;
	}

	/**
	 * Whether {@code player}'s talk with {@code villager} has just gone back to the wheel ({@link #again}): the talk goes
	 * on, so the menu opening shows the wheel and no new greeting. True once.
	 */
	public static boolean takeResumed(ServerPlayer player, Entity villager) {
		Talk talk = of(player, villager);
		if (talk == null || !talk.resumed) {
			return false;
		}
		talk.resumed = false;
		return true;
	}

	/**
	 * Right click on {@code entity}: if they had enough of {@code player} a moment ago, they say "not now" (now and then:
	 * not on every click) and true is returned, so the menu stays shut.
	 */
	public static boolean wornOut(ServerPlayer player, Entity entity) {
		if (WORN.isEmpty()) {
			return false;
		}
		String pair = entity.getUUID() + "/" + player.getUUID();
		Long until = WORN.get(pair);
		if (until == null) {
			return false;
		}
		long now = entity.level().getGameTime();
		if (now >= until || until - now > WORN_OUT_TICKS) {
			WORN.remove(pair);
			return false;
		}
		Long said = WORN_SAID.get(player.getUUID());
		if (said == null || now < said || now - said >= WORN_GAP) {
			WORN_SAID.put(player.getUUID(), now);
			say(player, entity, "worn", 1);
		}
		return true;
	}

	private static @Nullable Talk of(ServerPlayer player, Entity villager) {
		Talk talk = TALKS.get(player.getUUID());
		return talk != null && talk.villager == villager ? talk : null;
	}

	/** The client says the talk with {@code villagerId} is over (the reply shot ended, or the menu was left). */
	static void done(ServerPlayer player, int villagerId) {
		Talk talk = TALKS.get(player.getUUID());
		if (talk != null && (villagerId < 0 || talk.villager.getId() == villagerId)) {
			end(player, false);
		}
	}

	private static void tick(MinecraftServer server) {
		if (TALKS.isEmpty()) {
			return;
		}
		boolean look = server.getTickCount() % DANGER_EVERY == 0;
		for (Iterator<Map.Entry<UUID, Talk>> it = TALKS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Talk> e = it.next();
			Talk talk = e.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			Mob mob = talk.villager;
			boolean over = player == null || !player.isAlive() || !mob.isAlive() || mob.isRemoved() || mob.level() != player.level()
				|| mob.distanceTo(player) > RANGE || mob.getTarget() != null || mob.isPassenger() || mob.isInWater()
				|| PowersCompat.outOfPlay(player) || PowersCompat.outOfPlay(mob)
				// (Andy 2026-11-27: "something outside influences it") fire on either of them, a fire alarm, sleep,
				// something they must see to now, a monster about
				|| mob.isOnFire() || player.isOnFire() || mob.isSleeping() || player.isSleeping() || FireAlarm.alarmed(mob)
				|| mob instanceof PathfinderMob pm && TalkBusy.why(pm, player) != null
				|| look && danger(mob, player);
			long now = mob.level().getGameTime();
			if (!over && !talk.said && !talk.aside && now - talk.started > MENU_GRACE) {
				// (the menu left with nothing said in it: nothing to wait for)
				over = !inMenu(mob, player);
			}
			if (!over && now > talk.until) {
				// (still in their menu: the talk goes on as long as it's open)
				over = talk.aside || !inMenu(mob, player);
				if (!over) {
					talk.until = now + REPLY_TICKS;
				}
			}
			if (over) {
				it.remove();
				release(player, mob, true);
				continue;
			}
			if (talk.ending == null && !talk.aside && now - talk.lastPick > IDLE_TICKS && inMenu(mob, player)) {
				// left waiting on the wheel: they lose interest, say so, and the talk ends with that
				talk.ending = End.PARTED;
				talk.until = now + REPLY_TICKS;
				WORN.put(mob.getUUID() + "/" + player.getUUID(), now + WORN_OUT_TICKS);
				say(player, mob, "idle", 1);
				McaCompat.talkClose(mob, player);
				DialogueCue.last(player, mob);
			}
			still(mob, player);
		}
	}

	/** A monster close to {@code mob}, or one near that's after either of them. */
	private static boolean danger(Mob mob, ServerPlayer player) {
		return !mob.level().getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(DANGER_RANGE), m -> m != mob && m instanceof Enemy
			&& m.isAlive() && (m.getTarget() == player || m.getTarget() == mob || m.distanceTo(mob) < DANGER_CLOSE)).isEmpty();
	}

	/** Whether {@code mob} is in a talk with someone other than {@code player}. */
	static boolean talkingWithOther(Mob mob, ServerPlayer player) {
		for (Map.Entry<UUID, Talk> e : TALKS.entrySet()) {
			if (e.getValue().villager == mob && !e.getKey().equals(player.getUUID())) {
				return true;
			}
		}
		return false;
	}

	/** Whether MCA's menu on {@code mob} is open for {@code player}. */
	private static boolean inMenu(Mob mob, ServerPlayer player) {
		return McaCompat.interactingWith(mob, player);
	}

	/** Whatever talk {@code mob} is in is broken off. */
	private static void breakOff(Mob mob) {
		for (Iterator<Map.Entry<UUID, Talk>> it = TALKS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Talk> e = it.next();
			if (e.getValue().villager == mob) {
				it.remove();
				release(mob.level().getServer().getPlayerList().getPlayer(e.getKey()), mob, true);
			}
		}
	}

	private static void end(ServerPlayer player, boolean tell) {
		Talk talk = TALKS.remove(player.getUUID());
		if (talk != null) {
			release(player, talk.villager, tell);
		}
	}

	/** Both let go; {@code tell}: the talk was ended from here, so their menu shuts and the client's cutscene ends. */
	private static void release(@Nullable ServerPlayer player, Mob mob, boolean tell) {
		hold(mob, false);
		if (player != null) {
			hold(player, false);
			if (tell) {
				McaCompat.talkClose(mob, player);
				DialogueCue.end(player, mob);
			}
		}
	}

	/** The villager stands where they are, turned to the player. */
	private static void still(Mob mob, ServerPlayer player) {
		mob.getNavigation().stop();
		Vec3 motion = mob.getDeltaMovement();
		mob.setDeltaMovement(0.0, Math.min(motion.y, 0.0), 0.0);
		if (mob.getBrain().checkMemory(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED)) {
			mob.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		}
		if (mob.getBrain().checkMemory(MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED)) {
			mob.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(player, true));
		}
		mob.getLookControl().setLookAt(player, 30.0F, 30.0F);
	}

	/** Held still (no walking, no jumping) while the talk runs, or let go. */
	private static void hold(LivingEntity entity, boolean on) {
		for (AttributeInstance a : new AttributeInstance[] {entity.getAttribute(Attributes.MOVEMENT_SPEED), entity.getAttribute(Attributes.JUMP_STRENGTH)}) {
			if (a == null) {
				continue;
			}
			if (on) {
				a.addOrUpdateTransientModifier(new AttributeModifier(HOLD, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			} else {
				a.removeModifier(HOLD);
			}
		}
	}
}
