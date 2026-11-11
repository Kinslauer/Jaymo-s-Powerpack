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
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
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
 * it does {@link #PUNCH_DAMAGE} whatever the level: levels never add damage. Which punch it is comes from the style's
 * string ({@link Style#punch}); a pause of {@link #STRING_RESET} ticks starts the string again.</li>
 * <li><b>Speed</b>: one punch every {@link #interval} ticks, {@link #SLOWEST} at level 1 down to {@link #FASTEST}
 * at level {@link #MAX_LEVEL}. The attack-speed attribute is set to match, so the game's own attack indicator shows
 * the rhythm.</li>
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
	/** The game's own hit immunity: damage is refused while more than this is left of it. */
	static final int IMMUNITY_GATE = 10;

	/** The punches of the strings. The lead hand is the left, but the Bastion stands southpaw: its lead is the right. */
	public enum Punch { JAB, CROSS, LEAD_HOOK, REAR_HOOK }

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
		/** What the attack-speed modifier was last set for (0: taken off). */
		int applied;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	private Boxing() {
	}

	public static void init() {
		BoxingGear.init();
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer boxer)
				|| !(entity instanceof LivingEntity target) || !boxing(boxer)) {
				// (the client's own swing and attack indicator run as usual; the server decides the punch)
				return InteractionResult.PASS;
			}
			punch(boxer, target);
			return InteractionResult.SUCCESS;
		});
		ServerTickEvents.END_SERVER_TICK.register(Boxing::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STATES.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> STATES.clear());
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

	/** Whether {@code player} is out of stamina ({@link Feathers}' exhausted state). */
	public static boolean gassed(Player player) {
		return !player.isCreative() && Feathers.view(player).exhausted();
	}

	/** Ticks between punches at {@code level}. */
	public static int intervalAt(int level) {
		return SLOWEST - (SLOWEST - FASTEST) * (Mth.clamp(level, 1, MAX_LEVEL) - 1) / (MAX_LEVEL - 1);
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

	private static void punch(ServerPlayer boxer, LivingEntity target) {
		ServerLevel level = boxer.level();
		Style style = style(boxer);
		if (style == null) {
			return;
		}
		State state = STATES.computeIfAbsent(boxer.getUUID(), id -> new State());
		long now = level.getGameTime();
		if (now < state.readyAt - EARLY_SLACK) {
			// too soon: nothing is thrown, and nothing is spent
			return;
		}
		boolean gassed = gassed(boxer);
		int interval = interval(boxer);
		// an early punch borrows from the next one, so the rhythm holds on average
		state.readyAt = Math.max(now, state.readyAt) + interval;
		if (now - state.lastPunch > STRING_RESET) {
			state.step = 0;
		}
		Punch punch = style.punch(state.step++);
		state.last = punch;
		state.lastPunch = now;
		if (!gassed) {
			state.owed += costAt(level(boxer, style));
			int whole = (int)state.owed;
			if (whole > 0) {
				state.owed -= whole;
				Feathers.spend(boxer, whole);
			}
		}
		float pitch = punch == Punch.JAB ? 1.3F : punch == Punch.CROSS ? 0.9F : 1.05F;
		if (!target.isAlive() || target.getBoundingBox().distanceToSqr(boxer.getEyePosition()) > RANGE * RANGE) {
			// out of reach: the punch is thrown at the air
			level.playSound(null, boxer.getX(), boxer.getEyeY(), boxer.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.4F, pitch + 0.3F);
			return;
		}
		float damage = PUNCH_DAMAGE;
		if (gassed) {
			damage = Math.min(GASSED_CAP, damage * (1.0F - GASSED_CUT));
		}
		if (target.invulnerableTime > IMMUNITY_GATE && state.victim == target.getId() && now - state.landed <= IMMUNITY_GATE) {
			// the immunity this boxer's own last punch left never swallows the next one
			target.invulnerableTime = IMMUNITY_GATE;
		}
		Vec3 before = target.getDeltaMovement();
		if (!target.hurtServer(level, level.damageSources().playerAttack(boxer), damage)) {
			return;
		}
		state.victim = target.getId();
		state.landed = now;
		// most of the shove is taken back, so a flurry stays in reach
		Vec3 after = target.getDeltaMovement();
		target.setDeltaMovement(before.add(after.subtract(before).scale(PUNCH_KNOCKBACK)));
		target.syncVelocity = true;
		level.playSound(null, target.getX(), target.getEyeY(), target.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.8F, pitch);
		ItemStack gear = boxer.getMainHandItem();
		if (BoxingGear.isGear(gear)) {
			gear.hurtAndBreak(1, boxer, InteractionHand.MAIN_HAND);
		}
	}

	// ---- the rhythm, shown ---------------------------------------------------------------------------------------------

	/** Keeps each boxer's attack speed at their rhythm, so the game's own attack indicator fills in time with it. */
	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			boolean boxing = boxing(player);
			State state = STATES.get(player.getUUID());
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
