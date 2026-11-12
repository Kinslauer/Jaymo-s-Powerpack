package com.jaymospowers.boxing;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.stamina.Feathers;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (Jung) <b>Boxing</b>: three fist styles, for anyone, with fist gear ({@link BoxingGear}) in the main hand. A boxer is
 * weak outside {@link #RANGE} blocks and quick inside it. This class holds who knows which style at what level, and
 * the punch itself: the rest of the design (evades, strong attacks, blocks, blur, the combo breaker, the looks, the
 * teachers) comes in later patches; {@code docs/Boxing-Plan.md} is the whole of it.
 * <ul>
 * <li><b>Styles</b> ({@link Style}): the Arrow Slit (inside pressure), the Long Guard (range) and the Bastion
 * (counters). A player learns one, and another only once every style they know is mastered ({@link #MAX_LEVEL});
 * {@link #learn} enforces it. Each style has its own level, 1 to {@link #MAX_LEVEL}.</li>
 * <li><b>The punch</b>: a left click on a living thing. The server throws it in place of the game's own attack, so
 * it does {@link #PUNCH_DAMAGE} whatever the level (a cestus adds its own): levels never add damage. Which punch it is comes from the style's
 * string ({@link Style#punch}); a pause of {@link #STRING_RESET} ticks starts the string again.</li>
 * <li><b>The windup</b>: a punch lands {@link #WINDUP} ticks after it is thrown (its stamina is spent as it starts),
 * on the one it was aimed at if they are still in reach then; the next can't start until it has.</li>
 * <li><b>Speed</b>: one punch every {@link #interval} ticks, {@link #SLOWEST} at level 1 down to {@link #FASTEST}
 * at level {@link #MAX_LEVEL}, but never quicker than the windup, which is what sets the pace from level 2 on. The
 * attack-speed attribute is set to match, so the game's own attack indicator shows the rhythm.</li>
 * <li><b>Hit immunity</b>: a boxer's punch is never swallowed by the immunity their own last punch left, so the
 * levels whose rhythm is quicker than the game's ten ticks (8 and up) really land that fast. Other weapons gain
 * nothing from it.</li>
 * <li><b>Stamina</b>: a punch costs {@link #COST_FIRST} half-feathers at level 1, down to {@link #COST_LAST} at level
 * {@link #MAX_LEVEL} (four punches to an icon). A boxer who is exhausted ({@link Feathers}) is <b>gassed</b>: one
 * punch every {@link #GASSED_INTERVAL} ticks, {@link #GASSED_CUT} weaker and never more than {@link #GASSED_CAP}.</li>
 * </ul>
 * Without a style, fist gear is a plain weak weapon and none of this runs. A truly empty hand is never boxing.
 */
public final class Boxing {
	// ---- tuning ----
	/** The top level of a style: full mastery. */
	public static final int MAX_LEVEL = 10;
	/** Ticks between punches at level 1 and at level {@link #MAX_LEVEL} (one tick quicker a level; 7 is just under three a second). */
	public static final int SLOWEST = 16, FASTEST = 7;
	/**
	 * Every punch winds up first: it lands this many ticks after it is thrown (0.75 s), and the next can't start until
	 * it has, so no boxer punches faster than this whatever their level.
	 */
	public static final int WINDUP = 15;
	/** A punch that comes this many ticks early still counts (lag); the rhythm is kept on average. */
	static final int EARLY_SLACK = 2;
	/** A basic punch, at any level. */
	public static final float PUNCH_DAMAGE = 1.5F;
	/** How far a punch reaches (blocks, eye to the target's body). */
	public static final double RANGE = 2.0;
	/** The share of the game's knockback a punch keeps, so a flurry doesn't shove its target out of reach. */
	static final double PUNCH_KNOCKBACK = 0.25;
	/** A punch's stamina (half-feathers; two to an icon) at level 1 and at level {@link #MAX_LEVEL}. */
	public static final float COST_FIRST = 2.0F, COST_LAST = 0.5F;
	/** Gassed: ticks between punches, the share of the damage lost, and the most one punch can do. */
	public static final int GASSED_INTERVAL = 32;
	public static final float GASSED_CUT = 0.45F, GASSED_CAP = 2.0F;
	/** This long (ticks) without a punch and the string starts again. */
	static final int STRING_RESET = 30;
	/** A boxer's block, per blow stopped (half-feathers; one icon), and the Arrow Slit's tight guard (an icon and a half). */
	public static final float BLOCK_COST = 2.0F, ARROW_SLIT_BLOCK_COST = 3.0F;
	/** The breath of a tiring and of a gassed boxer: a puff every this many ticks, and how many wisps a gassed one is. */
	static final int TIRING_BREATH = 50, GASSED_BREATH = 20, GASSED_PUFFS = 3;
	/** Where the breath shows: this far ahead of the eyes, this far below them (blocks). */
	static final double BREATH_AHEAD = 0.35, BREATH_BELOW = 0.2;
	/** With this many half-feathers or fewer left in the row (four icons), a boxer is tiring: a warning, nothing more. */
	public static final int TIRING_FEATHERS = 8;
	/** Gassed: Mining Fatigue is kept up in spells this long (ticks), renewed this often. */
	static final int FATIGUE_TICKS = 40, FATIGUE_EVERY = 20;
	/** The game's own hit immunity lasts this long (ticks) after a hit. */
	static final int IMMUNITY_GATE = 10;

	/** The punches of the strings. The lead hand is the left, but the Bastion stands southpaw: its lead is the right. */
	public enum Punch { JAB, CROSS, LEAD_HOOK, REAR_HOOK, UPPERCUT, PULL_COUNTER, STRAIGHT }

	/** The three styles. Their order is saved: add new ones at the end. */
	public enum Style {
		/** Inside pressure: a one-two, then hooks from side to side for as long as the flurry lasts. */
		ARROW_SLIT("arrow_slit"),
		/** Range and control: jab, jab, cross. */
		LONG_GUARD("long_guard"),
		/** Counters, southpaw: the right jab, and nothing else until the counters come. */
		BASTION("bastion");

		public final String id;

		Style(String id) {
			this.id = id;
		}

		/** The punch at {@code step} of this style's string (0 = the first). */
		public Punch punch(int step) {
			return switch (this) {
				case ARROW_SLIT -> step == 0 ? Punch.JAB : step == 1 ? Punch.CROSS : step % 2 == 0 ? Punch.LEAD_HOOK : Punch.REAR_HOOK;
				case LONG_GUARD -> step % 3 == 2 ? Punch.CROSS : Punch.JAB;
				case BASTION -> Punch.JAB;
			};
		}

		/** The style with this {@link #id}, or null. */
		public static @Nullable Style byId(String id) {
			for (Style style : values()) {
				if (style.id.equals(id)) {
					return style;
				}
			}
			return null;
		}
	}

	/**
	 * The style a player boxes in: 0 for none, else the style's place in {@link Style} plus one. Saved, kept through
	 * death, and told to everyone (a stance is there to be read).
	 */
	public static final AttachmentType<Integer> STYLE = AttachmentRegistry.create(JaymosPowers.id("boxing_style"),
		builder -> builder.persistent(Codec.INT).copyOnDeath().syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
	/** Every style's level, four bits each in {@link Style} order (0: not learned). Saved, kept through death, told to its owner. */
	public static final AttachmentType<Integer> LEVELS = AttachmentRegistry.create(JaymosPowers.id("boxing_levels"),
		builder -> builder.persistent(Codec.INT).copyOnDeath().syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));
	/**
	 * How a boxer looks just now, told to everyone (not saved): how spent they are in the low two bits ({@link #FRESH},
	 * {@link #TIRING}, {@link #GASSED}), the last punch thrown above them ({@link Punch}'s place plus one), and a count
	 * that turns with every punch so each one is told. The client's poses read it.
	 */
	public static final AttachmentType<Integer> LOOK = AttachmentRegistry.create(JaymosPowers.id("boxing_look"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
	public static final int FRESH = 0, TIRING = 1, GASSED = 2;

	private static final Identifier SPEED = JaymosPowers.id("boxing_speed");

	/** A boxer's rhythm (server). */
	private static final class State {
		/** The game time the next punch is due. */
		long readyAt;
		/** The game time of the last punch thrown, and where the string stands. */
		long lastPunch = Long.MIN_VALUE / 2;
		int step;
		@Nullable Punch last;
		/** Stamina owed that hasn't come to a whole half-feather yet. */
		float owed;
		/** The last one a punch landed on (entity id), and when. */
		int victim = -1;
		long landed = Long.MIN_VALUE / 2;
		/** Punches told to everyone so far (so each {@link #LOOK} differs from the last). */
		int shown;
		/** What the attack-speed modifier was last set for (0: taken off). */
		int applied;
		/** A click that came too early: the one it was aimed at (entity id), thrown as soon as the rhythm allows; -1 for none. */
		int queued = -1;
		/** A punch winding up: the one it is aimed at (entity id; -1 for none), when it lands, its strong attack if it is one, and whether it was thrown gassed. */
		int winding = -1;
		long landAt;
		BoxingEvades.@Nullable Strong strong;
		boolean windGassed;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	private Boxing() {
	}

	public static void init() {
		BoxingGear.init();
		// the punch takes the left click only after every other rule has had its say (a downed or armless player, a
		// finisher, Kingdoms' tag with a child, the fight pit, captives...), so in a later phase than theirs
		Identifier late = JaymosPowers.id("boxing_punch");
		AttackEntityCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, late);
		AttackEntityCallback.EVENT.register(late, (player, level, hand, entity, hit) -> {
			if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer boxer)
				|| !(entity instanceof LivingEntity target) || !boxing(boxer)) {
				// (the client's own swing and attack indicator run as usual; the server decides the punch)
				return InteractionResult.PASS;
			}
			punch(boxer, target, false);
			return InteractionResult.SUCCESS;
		});
		ServerTickEvents.END_SERVER_TICK.register(Boxing::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STATES.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> STATES.clear());
		BoxingEvades.init();
		BoxingTraining.init();
		PressurePoints.init();
		BoxingDebug.register();
	}

	// ---- who knows what ----------------------------------------------------------------------------------------------

	/** The style {@code player} boxes in, or null. */
	public static @Nullable Style style(Player player) {
		Integer stored = player.getAttached(STYLE);
		int index = stored == null ? 0 : stored;
		return index >= 1 && index <= Style.values().length ? Style.values()[index - 1] : null;
	}

	/** {@code player}'s level in {@code style}: 0 if not learned, else 1 to {@link #MAX_LEVEL}. */
	public static int level(Player player, Style style) {
		Integer stored = player.getAttached(LEVELS);
		int packed = stored == null ? 0 : stored;
		return Mth.clamp(packed >> style.ordinal() * 4 & 0xF, 0, MAX_LEVEL);
	}

	/** Whether {@code player} has {@code style} at {@link #MAX_LEVEL}. */
	public static boolean mastered(Player player, Style style) {
		return level(player, style) >= MAX_LEVEL;
	}

	/** Whether {@code player} may learn {@code style} now: not known yet, and every style they do know is mastered. */
	public static boolean canLearn(Player player, Style style) {
		if (level(player, style) > 0) {
			return false;
		}
		for (Style known : Style.values()) {
			int level = level(player, known);
			if (level > 0 && level < MAX_LEVEL) {
				return false;
			}
		}
		return true;
	}

	/** Teaches {@code style} at level 1 and makes it the one {@code player} boxes in; false if they can't learn it yet. */
	public static boolean learn(ServerPlayer player, Style style) {
		if (!canLearn(player, style)) {
			return false;
		}
		setLevel(player, style, 1);
		use(player, style);
		return true;
	}

	/** Makes {@code style} (null: none) the one {@code player} boxes in; false if they haven't learned it. */
	public static boolean use(ServerPlayer player, @Nullable Style style) {
		if (style != null && level(player, style) <= 0) {
			return false;
		}
		player.setAttached(STYLE, style == null ? 0 : style.ordinal() + 1);
		return true;
	}

	/** Sets {@code player}'s level in {@code style} (0 forgets it; the style they box in is dropped with it). */
	public static void setLevel(ServerPlayer player, Style style, int level) {
		Integer stored = player.getAttached(LEVELS);
		int packed = stored == null ? 0 : stored;
		int shift = style.ordinal() * 4;
		player.setAttached(LEVELS, packed & ~(0xF << shift) | Mth.clamp(level, 0, MAX_LEVEL) << shift);
		if (level <= 0 && style(player) == style) {
			player.setAttached(STYLE, 0);
		}
	}

	/** Whether {@code player} is boxing right now: a learned style, and fist gear in the main hand. */
	public static boolean boxing(Player player) {
		Style style = style(player);
		return style != null && level(player, style) > 0 && !player.isSpectator() && BoxingGear.isGear(player.getMainHandItem());
	}

	/** How spent {@code player} looks ({@link #FRESH}, {@link #TIRING}, {@link #GASSED}), as everyone is told it (both sides). */
	public static int spent(Player player) {
		Integer look = player.getAttached(LOOK);
		return look == null ? FRESH : look & 3;
	}

	/** The last punch {@code player} was seen to throw, or null (both sides). */
	public static @Nullable Punch shownPunch(Player player) {
		Integer look = player.getAttached(LOOK);
		int index = look == null ? 0 : look >> 2 & 0xF;
		return index >= 1 && index <= Punch.values().length ? Punch.values()[index - 1] : null;
	}

	/** Whether {@code player} is boxing and running low: {@link #TIRING_FEATHERS} or fewer left, but not yet {@link #gassed}. */
	public static boolean tiring(Player player) {
		return boxing(player) && !player.isCreative() && !gassed(player) && Feathers.view(player).feathers() <= TIRING_FEATHERS;
	}

	/** Whether {@code player} is out of stamina ({@link Feathers}' exhausted state). */
	public static boolean gassed(Player player) {
		return !player.isCreative() && Feathers.view(player).exhausted();
	}

	/** Ticks between punches at {@code level}: never fewer than the {@link #WINDUP}. */
	public static int intervalAt(int level) {
		return Math.max(WINDUP, SLOWEST - (SLOWEST - FASTEST) * (Mth.clamp(level, 1, MAX_LEVEL) - 1) / (MAX_LEVEL - 1));
	}

	/** Ticks between {@code player}'s punches now (their style's level, or the gassed pace). */
	public static int interval(Player player) {
		Style style = style(player);
		return gassed(player) || style == null ? GASSED_INTERVAL : intervalAt(level(player, style));
	}

	/** A punch's stamina (half-feathers) at {@code level}. */
	public static float costAt(int level) {
		return COST_FIRST + (COST_LAST - COST_FIRST) * (Mth.clamp(level, 1, MAX_LEVEL) - 1) / (MAX_LEVEL - 1);
	}

	/** The last punch {@code player} threw, or null (server). */
	public static @Nullable Punch lastPunch(ServerPlayer player) {
		State state = STATES.get(player.getUUID());
		return state == null ? null : state.last;
	}

	// ---- the punch ---------------------------------------------------------------------------------------------------

	/**
	 * What one blocked blow costs {@code boxer} (half-feathers): by the blow, whatever its weight, more in the Arrow Slit's
	 * tight guard, less the better the gear raised ({@link BoxingGear#blockCostShare}). For {@code combat/Guard}.
	 */
	public static float blockCost(Player boxer) {
		return (style(boxer) == Style.ARROW_SLIT ? ARROW_SLIT_BLOCK_COST : BLOCK_COST) * BoxingGear.blockCostShare(boxer.getMainHandItem());
	}

	/** Whether {@code target} has a guard up that {@code boxer}'s punch runs into (a block, from their front). */
	private static boolean blockedBy(LivingEntity target, ServerPlayer boxer) {
		if (!target.isBlocking()) {
			return false;
		}
		Vec3 facing = target.getLookAngle().multiply(1.0, 0.0, 1.0);
		Vec3 toBoxer = boxer.position().subtract(target.position()).multiply(1.0, 0.0, 1.0);
		return facing.dot(toBoxer) > 0.0;
	}

	/** Spends {@code cost} half-feathers of {@code boxer}'s stamina, fractions carried over; false if there is none to spend. */
	public static boolean spend(ServerPlayer boxer, float cost) {
		if (gassed(boxer)) {
			return false;
		}
		State state = STATES.computeIfAbsent(boxer.getUUID(), id -> new State());
		state.owed += cost;
		int whole = (int)state.owed;
		if (whole > 0) {
			state.owed -= whole;
			return Feathers.spend(boxer, whole);
		}
		return true;
	}

	/** {@code queued}: a click that came early, thrown now that the rhythm allows. */
	private static void punch(ServerPlayer boxer, LivingEntity target, boolean queued) {
		ServerLevel level = boxer.level();
		Style style = style(boxer);
		if (style == null) {
			return;
		}
		State state = STATES.computeIfAbsent(boxer.getUUID(), id -> new State());
		long now = level.getGameTime();
		if (state.winding >= 0 || now < state.readyAt - EARLY_SLACK) {
			// too soon (or the last punch is still winding up): one punch waits its turn ({@link #tick}); nothing is spent yet
			state.queued = target.getId();
			return;
		}
		state.queued = -1;
		boolean gassed = gassed(boxer);
		int interval = interval(boxer);
		// an early punch borrows from the next one, so the rhythm holds on average
		state.readyAt = Math.max(now, state.readyAt) + interval;
		if (now - state.lastPunch > STRING_RESET) {
			state.step = 0;
		}
		// a strong attack an evade earned comes before the string (and never when gassed)
		BoxingEvades.Strong strong = gassed ? null : BoxingEvades.takeStrong(boxer, now);
		Punch punch = strong != null ? strong.punch() : style.punch(state.step++);
		state.last = punch;
		// everyone is told which punch it is (and how spent the boxer is), for the poses
		boxer.setAttached(LOOK, (gassed ? GASSED : tiring(boxer) ? TIRING : FRESH) | punch.ordinal() + 1 << 2 | (state.shown++ & 0xFF) << 6);
		state.lastPunch = now;
		boolean heavy = strong != null && strong.heavy();
		if (heavy) {
			// (a strong attack costs the same at every level)
			spend(boxer, BoxingEvades.STRONG_COST);
		} else if (!gassed) {
			spend(boxer, costAt(level(boxer, style)));
		}
		// the windup: the blow lands in a moment ({@link #tick}, {@link #land}), if the foe is still there to hit
		state.winding = target.getId();
		state.landAt = now + WINDUP;
		state.strong = strong;
		state.windGassed = gassed;
	}

	/** The punch {@code boxer} wound up lands on {@code target}, if it is still in reach. */
	private static void land(ServerPlayer boxer, LivingEntity target, State state) {
		ServerLevel level = boxer.level();
		long now = level.getGameTime();
		Punch punch = state.last;
		BoxingEvades.Strong strong = state.strong;
		boolean gassed = state.windGassed;
		boolean heavy = strong != null && strong.heavy();
		state.strong = null;
		float pitch = punch == Punch.JAB ? 1.3F : punch == Punch.CROSS ? 0.9F : heavy ? 0.7F : 1.05F;
		double reach = RANGE + (strong == null ? 0.0 : strong.reach());
		if (!target.isAlive() || target.getBoundingBox().distanceToSqr(boxer.getEyePosition()) > reach * reach
			|| BoxingEvades.outOfReach(boxer, target, now)) {
			// out of reach (or slipped clear by the combo breaker): the punch is thrown at the air
			level.playSound(null, boxer.getX(), boxer.getEyeY(), boxer.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.4F, pitch + 0.3F);
			return;
		}
		// (a cestus hits harder; levels never do)
		float damage = (strong != null ? strong.damage() : PUNCH_DAMAGE) + BoxingGear.punchBonus(boxer.getMainHandItem());
		if (gassed) {
			damage = Math.min(GASSED_CAP, damage * (1.0F - GASSED_CUT));
		}
		if (state.victim == target.getId() && now - state.landed <= IMMUNITY_GATE) {
			// the immunity this boxer's own last punch left never swallows the next one
			target.setInvulnerableTime(0);
		}
		// (a punch into a raised guard still lands what gets through, but it isn't one of the combo breaker's row)
		boolean blocked = blockedBy(target, boxer);
		Vec3 before = target.getDeltaMovement();
		if (!target.hurtServer(level, level.damageSources().playerAttack(boxer), damage)) {
			return;
		}
		state.victim = target.getId();
		state.landed = now;
		// most of the shove is taken back, so a flurry stays in reach (the pull counter keeps all of it and more)
		Vec3 after = target.getDeltaMovement();
		target.setDeltaMovement(before.add(after.subtract(before).scale(strong != null ? strong.knockback() : PUNCH_KNOCKBACK)));
		target.syncVelocity = true;
		level.playSound(null, target.getX(), target.getEyeY(), target.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, heavy ? 1.0F : 0.8F, pitch);
		if (strong != null) {
			BoxingEvades.strongLanded(boxer, target, strong, now);
		}
		if (!blocked) {
			BoxingEvades.landed(boxer, target, now);
		}
		BoxingTraining.landed(boxer, target, now);
		if (!blocked) {
			PressurePoints.landed(boxer, target);
		}
		ItemStack gear = boxer.getMainHandItem();
		if (BoxingGear.isGear(gear)) {
			gear.hurtAndBreak(1, boxer, InteractionHand.MAIN_HAND);
		}
	}

	// ---- the rhythm, shown ---------------------------------------------------------------------------------------------

	/**
	 * Tiring or gassed, a boxer shows it: everyone is told ({@link #LOOK}), and their breath shows, a faint puff now and
	 * then when tiring, a clear one with every breath when gassed. No sound.
	 */
	private static void breath(ServerPlayer player, boolean boxing) {
		Integer look = player.getAttached(LOOK);
		if (!boxing) {
			if (look != null) {
				player.removeAttached(LOOK);
			}
			return;
		}
		int spent = gassed(player) ? GASSED : tiring(player) ? TIRING : FRESH;
		if ((look == null ? FRESH : look & 3) != spent) {
			player.setAttached(LOOK, (look == null ? 0 : look & ~3) | spent);
		}
		if (spent == FRESH || player.tickCount % (spent == GASSED ? GASSED_BREATH : TIRING_BREATH) != 0) {
			return;
		}
		Vec3 mouth = player.getEyePosition().add(player.getLookAngle().scale(BREATH_AHEAD)).add(0.0, -BREATH_BELOW, 0.0);
		player.level().sendParticles(ParticleTypes.WHITE_SMOKE, mouth.x, mouth.y, mouth.z, spent == GASSED ? GASSED_PUFFS : 1, 0.03, 0.02, 0.03, 0.005);
	}

	/** Keeps each boxer's attack speed at their rhythm, so the game's own attack indicator fills in time with it. */
	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			boolean boxing = boxing(player);
			State state = STATES.get(player.getUUID());
			if (state != null && state.winding >= 0 && (!boxing || player.level().getGameTime() >= state.landAt)) {
				// the windup is over: the blow lands (a boxer who put the gear away meanwhile throws nothing)
				int id = state.winding;
				state.winding = -1;
				if (boxing && player.level().getEntity(id) instanceof LivingEntity target) {
					land(player, target, state);
				}
			}
			if (state != null && state.queued >= 0) {
				if (!boxing) {
					state.queued = -1;
				} else if (player.level().getGameTime() >= state.readyAt) {
					// the click that came early, in its turn
					int id = state.queued;
					state.queued = -1;
					if (player.level().getEntity(id) instanceof LivingEntity target) {
						punch(player, target, true);
					}
				}
			}
			breath(player, boxing);
			if (boxing && player.tickCount % FATIGUE_EVERY == 0 && gassed(player)) {
				// gassed: the arms are lead
				player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, FATIGUE_TICKS, 0));
			}
			if (!boxing && (state == null || state.applied == 0)) {
				continue;
			}
			if (state == null) {
				state = new State();
				STATES.put(player.getUUID(), state);
			}
			// (the held item is part of it: other gear has other speeds of its own)
			int wanted = boxing ? interval(player) << 16 | System.identityHashCode(player.getMainHandItem().getItem()) & 0xFFFF : 0;
			if (wanted == state.applied) {
				continue;
			}
			state.applied = wanted;
			AttributeInstance speed = player.getAttribute(Attributes.ATTACK_SPEED);
			if (speed == null) {
				continue;
			}
			speed.removeModifier(SPEED);
			if (boxing) {
				// what the hand and the gear give by themselves, brought to one punch every interval
				speed.addOrUpdateTransientModifier(new AttributeModifier(SPEED, 20.0 / interval(player) - speed.getValue(),
					AttributeModifier.Operation.ADD_VALUE));
			}
		}
	}
}
