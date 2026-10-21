package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.19) Villagers have feelings about each other, not just about you. Every pair of grown townsfolk has a regard,
 * -100 (can't stand each other) to +100 (devoted): where it starts comes from who they are (some people just rub each
 * other up the wrong way; MCA's partners and kin start close, its crabby and greedy sorts start sour, its friendly ones
 * warm), and it drifts with every meeting. Now and then two of a realm's free villagers who are near each other meet,
 * and what they do depends on it:
 * <ul>
 * <li>low: they argue, snub one another, shove, and at the bottom come to blows (a scuffle; no one is hurt, but the
 * village gathers round to watch, and a guard nearby breaks it up); a rare apology mends things a little;</li>
 * <li>middling: a greeting, small talk, a joke, the odd spat;</li>
 * <li>high: gifts, a shared bite to eat, hugs; unattached sweethearts court, partners are affectionate, and (with MCA)
 * two who come to adore each other may marry.</li>
 * </ul>
 * Every meeting moves the regard (and an MCA villager's mood) its way, so friendships grow and feuds deepen. Their talk
 * floats over their heads ({@link Speech}). A meeting takes its two out of their {@link Routines} while it lasts.
 */
final class Neighbours {
	/** How often pairs are thought about (ticks); how often a meeting under way is (ticks). */
	static final int EVERY = 20, STEP = 2;
	/** The chance each think that a realm's folk start a meeting; the most under way in one realm at once. */
	static final float MEET_CHANCE = 0.12F;
	static final int MOST_AT_ONCE = 3;
	/** How far from a seat its villagers are looked at; how near two must be to meet; how far apart ends it. */
	static final double WATCHED = 96.0, MEET_RANGE = 14.0, APART = 28.0;
	/** After a meeting, each of the two is left be for this long, and a bit (ticks). */
	static final int REST_MIN = 900, REST_MORE = 1500;
	/** How long the opener tries to reach the other (ticks); how close is close enough to talk (a hug, closer). */
	static final int REACH_TICKS = 300;
	static final double CLOSE = 2.2, HUG_CLOSE = 1.3;
	/** Walking pace over; the time between one remark and the answer (ticks), and between blows in a fight. */
	static final float WALK = 0.55F;
	static final int BEAT = 36, BLOW = 12;
	/** Speech is heard this far; a fight is watched from this far; a guard breaks one up from this far. */
	static final double SPEECH_RANGE = 12.0, WATCH_RANGE = 12.0, GUARD_RANGE = 16.0;
	/** A shove's knock; a blow's. */
	static final double SHOVE_KNOCK = 0.55, BLOW_KNOCK = 0.35;

	/** Regard runs between these; where it starts is drawn from START_LOW..START_HIGH (the pair's own draw, always the same). */
	static final int LEAST = -100, MOST = 100, START_LOW = -40, START_HIGH = 60;
	/** The regard's bands: below HATE they fight; below DISLIKE they bicker; from LIKE they're friends; from LOVE close. */
	static final int HATE = -50, DISLIKE = -15, LIKE = 15, LOVE = 50;
	/** (MCA) two unattached grown-ups of this regard or more may marry, this likely each time they court. */
	static final int MARRY = 88;
	static final float PROPOSE_CHANCE = 0.35F;
	/** Where partners and kin start, at the least; how much each step of temper (MCA's personality) adds to a start. */
	static final int PARTNER_START = 70, KIN_START = 35, TEMPER_STEP = 10;
	/** A glum (or cheery) MCA villager acts as if their regard were this much lower (higher). */
	static final int MOOD_LEAN = 15, MOOD_LOW = -5, MOOD_HIGH = 5;
	/** A grudge escalates: an argument to a shove, a shove to a fight, this likely (only below HATE). */
	static final float ESCALATE = 0.4F;
	/** The most others' regard each villager remembers. */
	static final int REMEMBERED = 48;
	/** Lines each meeting has (lang {@code neighbours.jaymoskingdoms.<kind>.<open|answer>.N}). */
	static final int LINES = 8;

	/** Whom each villager has a regard for, and how much. */
	static final AttachmentType<Map<UUID, Integer>> REGARD = AttachmentRegistry.create(JaymosKingdoms.id("neighbour_regard"),
		builder -> builder.persistent(Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT)));

	enum Kind {
		GREET(1, 2, Speech.WHITE), CHAT(2, 4, Speech.WHITE), JOKE(2, 2, Speech.WHITE), GIFT(4, 2, Speech.GOLD), MEAL(3, 4, Speech.WHITE),
		HUG(3, 2, Speech.GOLD), COURT(4, 3, Speech.GOLD), AFFECTION(3, 2, Speech.GOLD), PROPOSE(10, 2, Speech.GOLD),
		MAKEUP(10, 2, Speech.WHITE), SPAT(-2, 2, Speech.YELLOW), SNUB(-2, 2, Speech.YELLOW), ARGUE(-4, 4, Speech.RED),
		SHOVE(-3, 2, Speech.RED), FIGHT(-8, 7, Speech.RED);

		/** What the meeting does to the regard; how many remarks (or blows) it lasts; the colour of its talk. */
		final int regard, beats, color;

		Kind(int regard, int beats, int color) {
			this.regard = regard;
			this.beats = beats;
			this.color = color;
		}

		String key() {
			return name().toLowerCase(Locale.ROOT);
		}

		boolean sour() {
			return regard < 0;
		}
	}

	private static final class Meeting {
		final Villager a, b;
		final int faction;
		Kind kind;
		final long started;
		long arrived = -1L, next;
		int beat;
		/** Blows landed in a fight, by the opener and by the other. */
		int aHits, bHits;
		/** (0.7.21) An ambush: the opener lands every blow. */
		boolean ambush;
		/** (0.7.102) A hug that burned one of them ({@link EmberHugs}): it ends there and comes to nothing (no regard, no mood). */
		boolean burned;

		Meeting(Villager a, Villager b, int faction, Kind kind, long now) {
			this.a = a;
			this.b = b;
			this.faction = faction;
			this.kind = kind;
			this.started = now;
		}
	}

	private static final Map<UUID, Meeting> MEETING = new HashMap<>();
	private static final Map<UUID, Long> RESTING = new HashMap<>();
	/** Each line family's lines, dealt as a shuffled deck so the same one doesn't come round again soon. */
	private static final Map<String, ArrayDeque<Integer>> DECKS = new HashMap<>();

	private Neighbours() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Neighbours::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			MEETING.clear();
			RESTING.clear();
		});
	}

	/** {@code v} is meeting someone just now (an argument, a hug...). */
	static boolean meeting(Villager v) {
		return MEETING.containsKey(v.getUUID());
	}

	// ---- regard ------------------------------------------------------------------------------------------------------

	/** What {@code a} thinks of {@code b}, -100..100 (where they'd start, if they've never met). */
	static int regard(Villager a, Villager b) {
		Integer known = a.getAttachedOrElse(REGARD, Map.of()).get(b.getUUID());
		if (known == null) {
			known = b.getAttachedOrElse(REGARD, Map.of()).get(a.getUUID());
		}
		return known != null ? known : start(a, b);
	}

	/** Where {@code a} and {@code b} start: their own draw, warmed or soured by their tempers; partners and kin start close. */
	private static int start(Villager a, Villager b) {
		long seed = a.getUUID().getLeastSignificantBits() ^ b.getUUID().getLeastSignificantBits()
			^ a.getUUID().getMostSignificantBits() * 31 ^ b.getUUID().getMostSignificantBits() * 31;
		int r = START_LOW + (int)Math.floorMod(seed * 0x9E3779B97F4A7C15L >>> 17, (long)(START_HIGH - START_LOW + 1));
		r += TEMPER_STEP * (McaCompat.temper(a) + McaCompat.temper(b));
		int kin = McaCompat.kin(a, b);
		if (kin == McaCompat.PARTNER) {
			r = Math.max(r, PARTNER_START);
		} else if (kin == McaCompat.KIN) {
			r = Math.max(r, KIN_START);
		}
		return Mth.clamp(r, LEAST, MOST);
	}

	/** {@code a} and {@code b} feel {@code by} more (or less) for each other. */
	static void shift(Villager a, Villager b, int by) {
		int r = Mth.clamp(regard(a, b) + by, LEAST, MOST);
		remember(a, b.getUUID(), r);
		remember(b, a.getUUID(), r);
	}

	private static void remember(Villager v, UUID other, int r) {
		Map<UUID, Integer> mine = new HashMap<>(v.getAttachedOrElse(REGARD, Map.of()));
		mine.put(other, r);
		while (mine.size() > REMEMBERED) {
			// (the one they feel least about either way is forgotten)
			UUID dullest = Collections.min(mine.entrySet(), (x, y) -> Integer.compare(Math.abs(x.getValue()), Math.abs(y.getValue()))).getKey();
			if (dullest.equals(other)) {
				break;
			}
			mine.remove(dullest);
		}
		v.setAttached(REGARD, mine);
	}

	// ---- meeting -----------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % STEP == 0 && !MEETING.isEmpty()) {
			for (Meeting m : new java.util.LinkedHashSet<>(MEETING.values())) {
				if (MEETING.get(m.a.getUUID()) == m && !step(level, m, now)) {
					end(level, m, now);
				}
			}
		}
		if (now % EVERY != 0) {
			return;
		}
		long t = Royals.timeOfDay(level);
		if (t >= Routines.NIGHT) {
			return;
		}
		RESTING.values().removeIf(until -> until < now);
		RandomSource random = level.getRandom();
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || !f.type().isRealm() || !level.isPositionEntityTicking(f.seat()) || random.nextFloat() >= MEET_CHANCE) {
				continue;
			}
			int under = 0;
			for (Meeting m : MEETING.values()) {
				if (m.faction == f.id()) {
					under++;
				}
			}
			if (under >= MOST_AT_ONCE * 2) {
				// (each meeting is in the map twice, once for each of the two)
				continue;
			}
			List<Villager> folk = level.getEntitiesOfClass(Villager.class, new AABB(f.seat()).inflate(WATCHED), v -> v.isAlive() && free(v, t));
			if (folk.size() < 2) {
				continue;
			}
			Collections.shuffle(folk, new java.util.Random(random.nextLong()));
			Villager a = folk.getFirst();
			Villager b = null;
			for (Villager o : folk) {
				if (o != a && o.distanceToSqr(a) < MEET_RANGE * MEET_RANGE) {
					b = o;
					break;
				}
			}
			if (b != null) {
				begin(level, f.id(), a, b, choose(level, a, b, t), now);
			}
		}
	}

	/** Free to meet someone: grown, awake, out of their routines and work, not resting from the last meeting. */
	private static boolean free(Villager v, long t) {
		return !v.isBaby() && !MEETING.containsKey(v.getUUID()) && !RESTING.containsKey(v.getUUID()) && !Routines.busy(v) && Routines.free(v, t);
	}

	/** What {@code a} and {@code b} do, meeting: it hangs on what they think of each other (and {@code a}'s mood). */
	static Kind choose(ServerLevel level, Villager a, Villager b, long t) {
		RandomSource random = level.getRandom();
		int r = regard(a, b);
		int mood = McaCompat.mood(a);
		if (mood <= MOOD_LOW) {
			r -= MOOD_LEAN;
		} else if (mood >= MOOD_HIGH) {
			r += MOOD_LEAN;
		}
		int kin = McaCompat.kin(a, b);
		boolean courting = McaCompat.courtable(a, b);
		boolean lunch = t >= Routines.LUNCH_FROM && t < Routines.LUNCH_TO;
		List<Kind> deck = new ArrayList<>();
		if (r <= HATE) {
			add(deck, Kind.FIGHT, 3, Kind.ARGUE, 4, Kind.SHOVE, 3, Kind.SNUB, 2, Kind.MAKEUP, 1);
		} else if (r <= DISLIKE) {
			add(deck, Kind.ARGUE, 4, Kind.SNUB, 4, Kind.SHOVE, 2, Kind.SPAT, 2, Kind.MAKEUP, 1, Kind.GREET, 1);
		} else if (r < LIKE) {
			add(deck, Kind.GREET, 4, Kind.CHAT, 4, Kind.JOKE, 2, Kind.SPAT, 2, Kind.SNUB, 1, Kind.MEAL, lunch ? 2 : 0);
		} else if (r < LOVE) {
			add(deck, Kind.GREET, 2, Kind.CHAT, 3, Kind.JOKE, 2, Kind.GIFT, 2, Kind.MEAL, lunch ? 4 : 1, Kind.HUG, 1, Kind.SPAT, 1);
		} else {
			add(deck, Kind.CHAT, 2, Kind.JOKE, 1, Kind.GIFT, 2, Kind.MEAL, lunch ? 4 : 1, Kind.HUG, 3);
			if (kin == McaCompat.PARTNER) {
				add(deck, Kind.AFFECTION, 4);
			} else if (courting) {
				add(deck, Kind.COURT, 3);
				if (r >= MARRY && random.nextFloat() < PROPOSE_CHANCE) {
					return Kind.PROPOSE;
				}
			}
		}
		Kind kind = deck.get(random.nextInt(deck.size()));
		if ((kind == Kind.HUG || kind == Kind.AFFECTION) && EmberHugs.burned(a, b) != null && random.nextFloat() >= EmberHugs.TRY_ANYWAY) {
			// (0.7.102) one of them is Emberborn and the other can't take the heat: they mostly know better than to try
			kind = Kind.CHAT;
		}
		return kind;
	}

	private static void add(List<Kind> deck, Object... kindsAndWeights) {
		for (int i = 0; i < kindsAndWeights.length; i += 2) {
			for (int n = 0; n < (Integer)kindsAndWeights[i + 1]; n++) {
				deck.add((Kind)kindsAndWeights[i]);
			}
		}
	}

	/**
	 * (0.7.21) {@code a} falls on {@code b}, lured to where they lay in wait: a fight where only {@code a} lands blows. Whatever
	 * either was about is dropped.
	 */
	static void ambush(ServerLevel level, int faction, Villager a, Villager b, long now) {
		for (Villager v : List.of(a, b)) {
			Meeting old = MEETING.get(v.getUUID());
			if (old != null) {
				end(level, old, now);
			}
		}
		begin(level, faction, a, b, Kind.FIGHT, now);
		Meeting m = MEETING.get(a.getUUID());
		m.ambush = true;
		m.arrived = now;
		m.next = now;
	}

	/** {@code a} goes over to {@code b} to {@code kind}. */
	private static void begin(ServerLevel level, int faction, Villager a, Villager b, Kind kind, long now) {
		Meeting m = new Meeting(a, b, faction, kind, now);
		MEETING.put(a.getUUID(), m);
		MEETING.put(b.getUUID(), m);
		a.getNavigation().stop();
		b.getNavigation().stop();
	}

	/** One think of a meeting; false once it's over (or can't go on). */
	private static boolean step(ServerLevel level, Meeting m, long now) {
		Villager a = m.a, b = m.b;
		if (!still(a) || !still(b) || a.level() != level || b.level() != level || a.distanceToSqr(b) > APART * APART) {
			return false;
		}
		if (m.arrived < 0) {
			double close = m.kind == Kind.HUG || m.kind == Kind.AFFECTION || m.kind == Kind.PROPOSE ? HUG_CLOSE : CLOSE;
			if (a.distanceTo(b) > close) {
				if (now - m.started > REACH_TICKS) {
					return false;
				}
				BehaviorUtils.setWalkAndLookTargetMemories(a, b, WALK, 1);
				hold(b, a);
				return true;
			}
			m.arrived = now;
			m.next = now;
		}
		hold(a, b);
		if (m.kind != Kind.SNUB || m.beat < 2) {
			hold(b, a);
		}
		if (now < m.next) {
			return true;
		}
		if (m.beat >= m.kind.beats) {
			return finish(level, m, now);
		}
		beat(level, m, now);
		if (m.burned) {
			// (0.7.102) a hug that burned: they let go at once, so the burned one is free to pull away
			return false;
		}
		m.beat++;
		m.next = now + (m.kind == Kind.FIGHT ? BLOW : BEAT);
		return true;
	}

	/** Still able to carry on: alive, awake, unafraid, not set on by anything, not talking to a player. */
	private static boolean still(Villager v) {
		return v.isAlive() && !v.isSleeping() && !v.isPassenger() && v.getTarget() == null && !Frights.afraid(v.getUUID()) && !Theft.rioting(v)
			&& !Approaches.talking(v) && !Quests.listening(v) && !McaCompat.busy(v) && !McaCompat.panicking(v);
	}

	/** {@code v} stands where they are, facing {@code other}. */
	private static void hold(Villager v, Villager other) {
		v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		v.getNavigation().stop();
		v.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(other, true));
		v.getLookControl().setLookAt(other, 30.0F, 30.0F);
	}

	/** One remark (and what goes with it): the opener on even beats, the other answering on odd ones. */
	private static void beat(ServerLevel level, Meeting m, long now) {
		RandomSource random = level.getRandom();
		boolean opener = m.beat % 2 == 0;
		Villager speaker = opener ? m.a : m.b, other = opener ? m.b : m.a;
		switch (m.kind) {
			case FIGHT -> {
				if (m.beat == 0) {
					say(level, m.a, m.b, Kind.FIGHT, "open");
					onlookers(level, m);
				}
				// (blows trade back and forth, the angrier one, the opener, swinging more)
				boolean aSwings = m.ambush || random.nextFloat() < 0.6F;
				Villager hitter = aSwings ? m.a : m.b, hit = aSwings ? m.b : m.a;
				blow(level, hitter, hit, BLOW_KNOCK);
				if (aSwings) {
					m.aHits++;
				} else {
					m.bHits++;
				}
				if (m.beat > 0 && random.nextFloat() < 0.3F) {
					say(level, hit, hitter, Kind.FIGHT, "answer");
				}
				if (guard(level, m) != null) {
					m.beat = m.kind.beats;
				}
				return;
			}
			case SHOVE -> {
				if (opener) {
					blow(level, m.a, m.b, SHOVE_KNOCK);
				}
			}
			case GREET, MAKEUP, COURT -> speaker.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			case CHAT -> sound(level, speaker, SoundEvents.VILLAGER_AMBIENT, 0.5F);
			case JOKE -> {
				if (!opener) {
					sound(level, speaker, SoundEvents.VILLAGER_CELEBRATE, 0.6F);
					particles(level, speaker, ParticleTypes.HAPPY_VILLAGER, 4);
				}
			}
			case GIFT -> {
				if (opener) {
					m.a.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
					Item gift = List.of(Items.POPPY, Items.DANDELION, Items.CORNFLOWER, Items.BREAD, Items.APPLE, Items.COOKIE, Items.EMERALD, Items.CAKE)
						.get(random.nextInt(8));
					Vec3 mid = m.a.position().add(m.b.position()).scale(0.5);
					level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, gift), mid.x, m.a.getEyeY() - 0.4, mid.z, 6, 0.15, 0.1, 0.15, 0.03);
				} else {
					particles(level, m.b, ParticleTypes.HAPPY_VILLAGER, 5);
					sound(level, m.b, SoundEvents.VILLAGER_YES, 0.6F);
				}
			}
			case MEAL -> {
				for (Villager v : List.of(m.a, m.b)) {
					v.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
					level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, random.nextInt(3) == 0 ? Items.APPLE : Items.BREAD), v.getX(),
						v.getEyeY() - 0.2, v.getZ(), 4, 0.1, 0.1, 0.1, 0.05);
				}
				level.playSound(null, m.a.getX(), m.a.getY(), m.a.getZ(), SoundEvents.GENERIC_EAT.value(), SoundSource.NEUTRAL, 0.5F, 1.0F);
			}
			case HUG, AFFECTION -> {
				LivingEntity burned = opener ? null : EmberHugs.burned(m.a, m.b);
				if (burned != null) {
					// (0.7.102) an Emberborn is too hot to hold: the other is burned and pulls away, and that's the end of it
					EmberHugs.burn(level, burned == m.a ? m.b : m.a, burned);
					say(level, burned, "ember_hug", Speech.YELLOW, burned == m.a ? m.b : m.a);
					m.burned = true;
					return;
				}
				if (!opener) {
					m.a.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
					m.b.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
					particles(level, m.a, ParticleTypes.HEART, 2);
					particles(level, m.b, ParticleTypes.HEART, 2);
				}
			}
			case PROPOSE -> {
				if (!opener) {
					particles(level, m.a, ParticleTypes.HEART, 5);
					particles(level, m.b, ParticleTypes.HEART, 5);
					sound(level, m.b, SoundEvents.VILLAGER_CELEBRATE, 0.8F);
				}
			}
			case ARGUE, SPAT -> {
				particles(level, speaker, ParticleTypes.ANGRY_VILLAGER, m.kind == Kind.ARGUE ? 2 : 1);
				sound(level, speaker, SoundEvents.VILLAGER_NO, 0.6F);
			}
			case SNUB -> {
				if (!opener) {
					// (they turn their back and walk off)
					Vec3 away = m.b.position().subtract(m.a.position()).multiply(1, 0, 1);
					away = away.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : away.normalize();
					BehaviorUtils.setWalkAndLookTargetMemories(m.b, BlockPos.containing(m.b.position().add(away.scale(7))), WALK, 1);
				}
			}
		}
		say(level, speaker, other, m.kind, opener ? "open" : "answer");
	}

	/** {@code hitter} strikes (or shoves) {@code hit}: a swing, a knock back, the sound of it; no one is hurt. */
	private static void blow(ServerLevel level, Villager hitter, Villager hit, double knock) {
		hitter.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		hit.knockback(knock, hitter.getX() - hit.getX(), hitter.getZ() - hit.getZ(), level.damageSources().mobAttack(hitter), 0.0F);
		hit.syncVelocity = true;
		sound(level, hit, knock >= SHOVE_KNOCK ? SoundEvents.PLAYER_ATTACK_KNOCKBACK : SoundEvents.PLAYER_ATTACK_WEAK, 0.7F);
		if (level.getRandom().nextBoolean()) {
			sound(level, hit, SoundEvents.VILLAGER_HURT, 0.6F);
		}
		level.sendParticles(ParticleTypes.CRIT, hit.getX(), hit.getY() + 1.2, hit.getZ(), 4, 0.2, 0.3, 0.2, 0.1);
	}

	/** A fight draws a crowd: those free about it stop to watch and call out. */
	private static void onlookers(ServerLevel level, Meeting m) {
		List<Villager> about = level.getEntitiesOfClass(Villager.class, m.a.getBoundingBox().inflate(WATCH_RANGE),
			v -> v != m.a && v != m.b && v.isAlive() && !v.isSleeping() && !MEETING.containsKey(v.getUUID()));
		RandomSource random = level.getRandom();
		int calls = 0;
		for (Villager v : about) {
			v.getNavigation().stop();
			v.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(m.a, true));
			if (calls < 2 && random.nextFloat() < 0.5F) {
				say(level, v, v.isBaby() ? "watch.child" : "watch", Speech.YELLOW, m.a);
				calls++;
			}
		}
	}

	/** A guard of the realm near enough to see a fight steps in: they're told to break it up. */
	private static @Nullable LivingEntity guard(ServerLevel level, Meeting m) {
		List<LivingEntity> guards = level.getEntitiesOfClass(LivingEntity.class, m.a.getBoundingBox().inflate(GUARD_RANGE),
			e -> e.isAlive() && e instanceof Worker w && w.role() == Folk.GUARD && w.factionId() == m.faction && e.hasLineOfSight(m.a));
		if (guards.isEmpty()) {
			return null;
		}
		LivingEntity g = guards.getFirst();
		say(level, g, "guard", Speech.GOLD, m.a);
		return g;
	}

	/** The meeting's done: they think the more (or less) of each other for it, and a grudge may come to more. */
	private static boolean finish(ServerLevel level, Meeting m, long now) {
		RandomSource random = level.getRandom();
		shift(m.a, m.b, m.kind.regard);
		if (m.kind.sour() && !m.ambush) {
			// (0.7.21) a bitter enough grudge looks for someone to settle it
			Grudges.brew(level, m.a, m.b, m.faction);
		}
		int moodBy = m.kind == Kind.FIGHT ? -3 : m.kind.sour() ? -1 : m.kind.regard >= 4 ? 2 : 1;
		McaCompat.cheer(m.a, moodBy);
		McaCompat.cheer(m.b, moodBy);
		switch (m.kind) {
			case FIGHT -> {
				// (whoever took the more blows has the last, sore word)
				Villager loser = m.aHits >= m.bHits ? m.b : m.a;
				say(level, loser, "fight.lost", Speech.RED, loser == m.a ? m.b : m.a);
			}
			case PROPOSE -> {
				if (McaCompat.marry(m.a, m.b)) {
					say(level, m.a, "wed", Speech.GOLD, m.b);
					level.sendParticles(ParticleTypes.HEART, m.a.getX(), m.a.getY() + 2.0, m.a.getZ(), 10, 0.6, 0.4, 0.6, 0.0);
				}
			}
			case ARGUE, SHOVE -> {
				if (regard(m.a, m.b) <= HATE && random.nextFloat() < ESCALATE) {
					m.kind = m.kind == Kind.ARGUE ? Kind.SHOVE : Kind.FIGHT;
					m.beat = 0;
					m.next = now + BEAT / 2;
					return true;
				}
			}
			default -> {
			}
		}
		return false;
	}

	private static void end(ServerLevel level, Meeting m, long now) {
		MEETING.remove(m.a.getUUID(), m);
		MEETING.remove(m.b.getUUID(), m);
		RandomSource random = level.getRandom();
		RESTING.put(m.a.getUUID(), now + REST_MIN + random.nextInt(REST_MORE));
		RESTING.put(m.b.getUUID(), now + REST_MIN + random.nextInt(REST_MORE));
	}

	// ---- talk --------------------------------------------------------------------------------------------------------

	private static void say(ServerLevel level, Villager speaker, Villager to, Kind kind, String part) {
		say(level, speaker, kind.key() + "." + part, kind.color, to);
	}

	/** {@code speaker} says one of family {@code family}'s lines (to {@code to}, whose name a line may use). */
	static void say(ServerLevel level, LivingEntity speaker, String family, int color, LivingEntity to) {
		Speech.say(level, speaker, Component.translatable("neighbours.jaymoskingdoms." + family + "." + deal(family, level.getRandom()),
			to.getDisplayName()), color, SPEECH_RANGE);
	}

	/** The next of {@code family}'s lines, a fresh shuffle when the deck runs out. */
	private static int deal(String family, RandomSource random) {
		ArrayDeque<Integer> deck = DECKS.computeIfAbsent(family, k -> new ArrayDeque<>());
		if (deck.isEmpty()) {
			List<Integer> all = new ArrayList<>();
			for (int i = 0; i < LINES; i++) {
				all.add(i);
			}
			Collections.shuffle(all, new java.util.Random(random.nextLong()));
			deck.addAll(all);
		}
		return deck.poll();
	}

	private static void sound(ServerLevel level, LivingEntity v, SoundEvent sound, float volume) {
		level.playSound(null, v.getX(), v.getY(), v.getZ(), sound, SoundSource.NEUTRAL, volume, v.isBaby() ? 1.5F : 0.9F + level.getRandom().nextFloat() * 0.2F);
	}

	private static void particles(ServerLevel level, LivingEntity v, ParticleOptions particle, int count) {
		level.sendParticles(particle, v.getX(), v.getY() + v.getBbHeight() + 0.3, v.getZ(), count, 0.3, 0.2, 0.3, 0.0);
	}

	// ---- the test command --------------------------------------------------------------------------------------------

	/**
	 * {@code /kingdom neighbours [kind]} (ops): the two grown villagers nearest you meet now ({@code kind}, if given, else
	 * what their regard makes it), and you're told what they think of each other.
	 */
	static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("neighbours").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(c -> force(c.getSource(), null))
			.then(Commands.argument("kind", com.mojang.brigadier.arguments.StringArgumentType.word()).suggests((c, b) -> {
				for (Kind k : Kind.values()) {
					b.suggest(k.key());
				}
				b.suggest("grudge_beat");
				b.suggest("grudge_lure");
				return b.buildFuture();
			}).executes(c -> {
				String name = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "kind").toUpperCase(Locale.ROOT);
				if (name.startsWith("GRUDGE_")) {
					return grudge(c.getSource(), name.equals("GRUDGE_BEAT"));
				}
				for (Kind k : Kind.values()) {
					if (k.name().equals(name)) {
						return force(c.getSource(), k);
					}
				}
				c.getSource().sendFailure(Component.translatable("neighbours.jaymoskingdoms.command.unknown", name.toLowerCase(Locale.ROOT)));
				return 0;
			}));
	}

	/** (0.7.21) The nearest grown villager comes to hate the next nearest, and has a grudge quest for you. */
	private static int grudge(CommandSourceStack source, boolean beating) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		if (!(player.level() instanceof ServerLevel level)) {
			return 0;
		}
		List<Villager> near = level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(MEET_RANGE), v -> v.isAlive() && !v.isBaby());
		near.sort((x, y) -> Double.compare(x.distanceToSqr(player), y.distanceToSqr(player)));
		if (near.size() < 2 || !Grudges.force(level, near.get(0), near.get(1), beating)) {
			source.sendFailure(Component.translatable("neighbours.jaymoskingdoms.command.nobody"));
			return 0;
		}
		source.sendSuccess(() -> Component.translatable("neighbours.jaymoskingdoms.command.grudge", near.get(0).getDisplayName(), near.get(1).getDisplayName())
			.withStyle(ChatFormatting.GRAY), false);
		return 1;
	}

	private static int force(CommandSourceStack source, @Nullable Kind kind) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		if (!(player.level() instanceof ServerLevel level)) {
			return 0;
		}
		List<Villager> near = level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(MEET_RANGE),
			v -> v.isAlive() && !v.isBaby() && !MEETING.containsKey(v.getUUID()));
		near.sort((x, y) -> Double.compare(x.distanceToSqr(player), y.distanceToSqr(player)));
		if (near.size() < 2) {
			source.sendFailure(Component.translatable("neighbours.jaymoskingdoms.command.nobody"));
			return 0;
		}
		Villager a = near.get(0), b = near.get(1);
		Faction f = Factions.around(level.getServer(), level, a.blockPosition(), Villages.VILLAGE_RADIUS);
		Kind k = kind != null ? kind : choose(level, a, b, Royals.timeOfDay(level));
		RESTING.remove(a.getUUID());
		RESTING.remove(b.getUUID());
		begin(level, f != null ? f.id() : -1, a, b, k, level.getGameTime());
		int r = regard(a, b);
		source.sendSuccess(() -> Component.translatable("neighbours.jaymoskingdoms.command.meet", a.getDisplayName(), b.getDisplayName(), r, k.key())
			.withStyle(ChatFormatting.GRAY), false);
		return 1;
	}
}
