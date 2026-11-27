package com.jaymospowers.alchemy.toxic;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.alchemy.brewing.BrewGrade;
import com.jaymospowers.alchemy.brewing.BrewGrades;
import com.jaymospowers.core.wounds.Resting;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.Vec3;

/**
 * Toxicity: the load a strong brew leaves in the body (Hunter's Road, patch 6, "D1"), with tolerance beside it ("T-A1").
 * <p>
 * <b>The load.</b> A number from 0 to {@link #CAPACITY}, in two parts. The <i>ordinary</i> part comes from strong brews and
 * fades by itself in minutes ({@link #FADE_PER_SECOND}). The <i>held</i> part comes from a monster brew: it does not fade
 * and nothing but its own end takes it off ({@link State#heldUntil}). Past {@link #GREEN_AT} (half) the view is
 * tinged green; past the harm line ({@link #HARM_AT} plus a share of tolerance, three quarters to start with) the body is
 * hurt every few seconds, faster the nearer the top. There is no bar: the signs are dark veins round the eyes on the skin
 * and a slower heartbeat (both the client's, {@code ToxicityClient} and {@code ToxicLayer}), and the toxin gauge
 * ({@link ToxinGaugeItem}) for those who carry one. It sits beside {@link com.jaymospowers.alchemy.Corruption}'s fleeting layer
 * and never touches it. Kingdoms' PotionClash reads it (a loaded body keeps its brews apart worse) through its PowersCompat.
 * <p>
 * <b>Tolerance.</b> A second number, 0 to {@link #TOLERANCE_MAX}. Taking a dose stronger than your tolerance raises it
 * (by {@link #TOLERANCE_GAIN} at most); a dose no stronger than it raises nothing. It cuts what each dose adds to the load
 * (up to {@link #MAX_CUT}) and moves the harm line up ({@link #LINE_SHARE} of it), and it fades very slowly. Past the line
 * is real danger until the load is brought down: a golden apple or a healing draught ({@link #DETOX}), milk or a
 * cleansing draught ({@link #clear}, which takes all the ordinary load), a hot spring soak or sitting by a fire (faster fade).
 * <p>
 * <b>Sweating it out</b> (T-N1): a soak in a hot spring ({@link Resting#magmaBelow}) or sitting still by a lit campfire
 * ({@link Resting#nearFire}) draws {@link #SWEAT_PER_SECOND} more out of the ordinary load every second and calls every
 * {@link #SWEATS} listener (a mutation fading sooner, patch 11). The held part is not drawn out: it stays till it ends.
 * <p>
 * <h2>API for the monster brews, draughts, oils and mutagens</h2>
 * <ul>
 * <li>{@link #dose(ServerPlayer, int, int)}: add load (strength in load units, 0 for none; {@code heldTicks} above 0 makes
 * it a monster brew's held load that lasts that long, and earns {@link HunterEyes}). Returns what went in.</li>
 * <li>Put {@link #STRENGTH} (and {@link #HELD_TICKS}) on a potion stack and drinking it does the above by itself
 * ({@link #consumed}); graded brews Fine and up do it without either ({@link #GRADE_STRENGTH}).</li>
 * <li>{@link #of}, {@link #total}, {@link #load}, {@link #held}, {@link #tolerance}, {@link #fraction}, {@link #harmLine},
 * {@link #tinged}, {@link #harmful}, {@link #nextThreshold}: reading (both sides; the state is synced to everyone).</li>
 * <li>{@link #detox(ServerPlayer, int)}, {@link #clear(ServerPlayer, boolean)}, {@link #setTolerance}: bringing it down.</li>
 * <li>{@link #MUTAGENS} (what the gauge lists), {@link #SWEATS}, {@link #ON_SECOND}: hooks for the mutagens (patch 11).</li>
 * <li>{@link Harms}: the five harms (toxin, acid, flammable, cripple, hunt). {@link HunterEyes#earn}.</li>
 * </ul>
 */
public final class Toxicity {
	// ---- tuning (load units: CAPACITY is a full body) ----
	/** A full load. */
	public static final int CAPACITY = 1000;
	/** Past this (half) the view is tinged green and the signs show. */
	public static final int GREEN_AT = 500;
	/** The harm line of an untrained body (three quarters); tolerance moves it up by {@link #LINE_SHARE} of itself. */
	public static final int HARM_AT = 750;
	/** The most tolerance, what a full tolerance cuts off each dose (a share), and how far it moves the harm line (a share of itself). */
	public static final int TOLERANCE_MAX = 500;
	public static final float MAX_CUT = 0.4F, LINE_SHARE = 0.4F;
	/** The most one dose raises tolerance by, and how often (ticks) a body with no load loses one point of it. */
	public static final int TOLERANCE_GAIN = 12, TOLERANCE_FADE_EVERY = 2400;
	/** Ordinary load lost each second by itself (a full body clears in about four minutes), and the extra a soak or a fire draws. */
	public static final int FADE_PER_SECOND = 4, SWEAT_PER_SECOND = 14;
	/** What a golden apple, an enchanted one and a healing draught take off the ordinary load. */
	public static final int DETOX = 220, DETOX_BIG = 450;
	/** Harm past the line: the ticks between hurts at the line and at the top, the hurt, and the hurt at the very top (health points). */
	public static final int HARM_SLOW = 120, HARM_FAST = 30;
	public static final float HARM_HURT = 1.0F, HARM_HURT_TOP = 2.0F;
	/** Below this much health a load that is not full will not hurt any further (only a full load can kill). */
	public static final float SPARED_AT = 2.0F;
	/** What a brew of each grade adds when it carries no strength of its own (Plain and Crude add none: only strong brews do). */
	public static final int[] GRADE_STRENGTH = {0, 0, 90, 150, 240};
	/** A monster brew's held load, in ticks, when one is made with no length of its own (ten minutes). */
	public static final int HELD_DEFAULT = 20 * 60 * 10;
	/** A sit by the fire or a soak counts only when moved no more than this (blocks, squared) in a second. */
	private static final double STILL_SQR = 0.04;

	/** The numbers of one body. {@code heldUntil} is the game time the held part ends. */
	public record State(int load, int held, long heldUntil, int tolerance) {
		public static final State NONE = new State(0, 0, 0L, 0);
		public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("load").forGetter(State::load),
			Codec.INT.fieldOf("held").forGetter(State::held),
			Codec.LONG.fieldOf("held_until").forGetter(State::heldUntil),
			Codec.INT.fieldOf("tolerance").forGetter(State::tolerance)).apply(i, State::new));
		public static final StreamCodec<io.netty.buffer.ByteBuf, State> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, State::load, ByteBufCodecs.VAR_INT, State::held, ByteBufCodecs.VAR_LONG, State::heldUntil,
			ByteBufCodecs.VAR_INT, State::tolerance, State::new);

		/** Ordinary and held load together, never above {@link #CAPACITY}. */
		public int total() {
			return Math.min(CAPACITY, this.load + this.held);
		}
	}

	/** Kept through death for the tolerance only (a new body has no load). Synced to everyone, so others can see the veins. */
	public static final AttachmentType<State> STATE = AttachmentRegistry.create(JaymosPowers.id("toxicity"),
		builder -> builder.persistent(State.CODEC).copyOnDeath().initializer(() -> State.NONE)
			.syncWith(State.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** On a potion stack: the load drinking it adds (load units), and for how long that load is held (ticks; none: it fades). */
	public static DataComponentType<Integer> STRENGTH, HELD_TICKS;

	/** Added to by the gauge's readers: what each lists for a player's mutagens (patch 11 adds its own; none for now). */
	public static final List<Function<Player, List<Component>>> MUTAGENS = new ArrayList<>();
	/** Called each second for each player sweating it out (a soak or a fire): a mutation fading sooner. */
	public static final List<Consumer<ServerPlayer>> SWEATS = new ArrayList<>();
	/** Called each second for each player with any load, with their state, so a mutation can count how long it was held. */
	public static final List<BiConsumer<ServerPlayer, State>> ON_SECOND = new ArrayList<>();

	/** When each player is next hurt by a load past the line (game time), and where each was a second ago. */
	private static final Map<ServerPlayer, Long> NEXT_HARM = new WeakHashMap<>();
	private static final Map<ServerPlayer, Vec3> LAST = new WeakHashMap<>();

	private Toxicity() {
	}

	public static void init() {
		STRENGTH = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("toxic_strength"),
			DataComponentType.<Integer>builder().persistent(Codec.intRange(0, CAPACITY)).networkSynchronized(ByteBufCodecs.VAR_INT).build());
		HELD_TICKS = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("toxic_held_ticks"),
			DataComponentType.<Integer>builder().persistent(Codec.intRange(0, Integer.MAX_VALUE)).networkSynchronized(ByteBufCodecs.VAR_INT).build());
		Harms.init();
		HunterEyes.init();
		ServerTickEvents.END_SERVER_TICK.register(Toxicity::tick);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			// a new body carries no load, only the habit of it; coming back from the End is not a new body
			if (!alive) {
				State s = of(newPlayer);
				newPlayer.setAttached(STATE, new State(0, 0, 0L, s.tolerance()));
			}
		});
		ServerPlayerEvents.LEAVE.register(player -> {
			NEXT_HARM.remove(player);
			LAST.remove(player);
		});
	}

	// ---- reading (both sides) ----------------------------------------------------------------------------------------

	public static State of(Player player) {
		State s = player.getAttached(STATE);
		return s == null ? State.NONE : s;
	}

	/** Ordinary and held load together (0 to {@link #CAPACITY}). */
	public static int total(Player player) {
		return of(player).total();
	}

	/** The ordinary load, which fades. */
	public static int load(Player player) {
		return of(player).load();
	}

	/** The held load of monster brews. */
	public static int held(Player player) {
		return of(player).held();
	}

	public static int tolerance(Player player) {
		return of(player).tolerance();
	}

	/** The total load as a share of a full one (0 to 1). */
	public static float fraction(Player player) {
		return total(player) / (float) CAPACITY;
	}

	/** The load above which this body is hurt: {@link #HARM_AT} and a share of its tolerance. */
	public static int harmLine(Player player) {
		return harmLine(of(player));
	}

	static int harmLine(State state) {
		return Math.min(CAPACITY - 1, HARM_AT + Math.round(state.tolerance() * LINE_SHARE));
	}

	/** Whether the view is tinged green and the signs show (past half). */
	public static boolean tinged(Player player) {
		return total(player) > GREEN_AT;
	}

	/** Whether the load is past the harm line. */
	public static boolean harmful(Player player) {
		State s = of(player);
		return s.total() > harmLine(s);
	}

	/** The next threshold above the load (the green line, the harm line, then full), or -1 when it is full. */
	public static int nextThreshold(Player player) {
		State s = of(player);
		int total = s.total();
		for (int line : new int[] {GREEN_AT, harmLine(s), CAPACITY}) {
			if (total < line) {
				return line;
			}
		}
		return -1;
	}

	/** What the gauge lists as {@code player}'s mutagens (empty until mutagens exist). */
	public static List<Component> mutagens(Player player) {
		List<Component> out = new ArrayList<>();
		for (Function<Player, List<Component>> source : MUTAGENS) {
			out.addAll(source.apply(player));
		}
		return out;
	}

	// ---- changing it (server) ----------------------------------------------------------------------------------------

	/**
	 * {@code player} takes a dose of {@code strength} (load units): tolerance cuts what goes in, a dose stronger than their
	 * tolerance raises it. With {@code heldTicks} above 0 it is a monster brew's held load for that long (the end moves to
	 * the later of this and any held already), and earns {@link HunterEyes}. Creative and spectating players take none.
	 * Returns the load that went in.
	 */
	public static int dose(ServerPlayer player, int strength, int heldTicks) {
		if (strength <= 0 || player.isCreative() || player.isSpectator()) {
			return 0;
		}
		State s = of(player);
		long now = player.level().getGameTime();
		int tolerance = s.tolerance();
		float cut = Mth.clamp(tolerance / (float) TOLERANCE_MAX, 0.0F, 1.0F) * MAX_CUT;
		int intake = Math.max(1, Math.round(strength * (1.0F - cut)));
		int room = Math.max(0, CAPACITY - s.total());
		int taken = Math.min(intake, room);
		int load = s.load();
		int held = s.held();
		long until = s.heldUntil();
		if (heldTicks > 0) {
			held += taken;
			until = Math.max(held > taken ? until : 0L, now + heldTicks);
			HunterEyes.earn(player);
		} else {
			load += taken;
		}
		if (strength > tolerance) {
			tolerance = Math.min(TOLERANCE_MAX, tolerance + Math.min(TOLERANCE_GAIN, strength - tolerance));
		}
		player.setAttached(STATE, new State(load, held, until, tolerance));
		return taken;
	}

	/** Takes up to {@code amount} off the ordinary load (a golden apple, a healing draught). The held part stays. */
	public static void detox(ServerPlayer player, int amount) {
		State s = of(player);
		if (s.load() > 0 && amount > 0) {
			player.setAttached(STATE, new State(Math.max(0, s.load() - amount), s.held(), s.heldUntil(), s.tolerance()));
		}
	}

	/**
	 * Milk and the cleansing draught: all the ordinary load goes, and with {@code includingHeld} the held part too (a held
	 * load otherwise stays till its brew ends). Tolerance stays.
	 */
	public static void clear(ServerPlayer player, boolean includingHeld) {
		State s = of(player);
		if (s.load() == 0 && (!includingHeld || s.held() == 0)) {
			return;
		}
		player.setAttached(STATE, new State(0, includingHeld ? 0 : s.held(), includingHeld ? 0L : s.heldUntil(), s.tolerance()));
	}

	/** Sets tolerance outright (0 to {@link #TOLERANCE_MAX}). */
	public static void setTolerance(ServerPlayer player, int tolerance) {
		State s = of(player);
		player.setAttached(STATE, new State(s.load(), s.held(), s.heldUntil(), Mth.clamp(tolerance, 0, TOLERANCE_MAX)));
	}

	// ---- eaten and drunk ---------------------------------------------------------------------------------------------

	/**
	 * {@code stack} (as it was before) was eaten or drunk by {@code player} (from the same mixin as the afflictions' cures):
	 * milk clears (the cleanser draught of patch 6 calls {@link #clear} itself) the ordinary load, a golden apple or a healing draught take {@link #DETOX} off it,
	 * and a brew that carries a {@link #STRENGTH} (or is graded Fine or better) adds its load.
	 */
	public static void consumed(ServerPlayer player, ItemStack stack) {
		if (stack.is(Items.MILK_BUCKET) || stack.is(com.jaymospowers.ground.StoneAge.CLAY_MILK_BUCKET)) {
			clear(player, false);
			return;
		}
		if (stack.is(Items.GOLDEN_APPLE)) {
			detox(player, DETOX);
			return;
		}
		if (stack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
			detox(player, DETOX_BIG);
			return;
		}
		if (!stack.is(Items.POTION)) {
			return;
		}
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		if (contents == null) {
			return;
		}
		boolean heals = false;
		for (var effect : contents.getAllEffects()) {
			heals |= effect.getEffect().is(MobEffects.INSTANT_HEALTH);
		}
		if (heals) {
			detox(player, DETOX);
		}
		Integer strength = stack.get(STRENGTH);
		if (strength == null) {
			BrewGrade grade = BrewGrades.of(stack);
			strength = grade == null ? 0 : GRADE_STRENGTH[grade.ordinal()];
		}
		if (strength > 0) {
			Integer held = stack.get(HELD_TICKS);
			dose(player, strength, held == null ? 0 : held);
		}
	}

	// ---- the passing of time -----------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		int tick = server.getTickCount();
		boolean second = tick % 20 == 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isAlive()) {
				continue;
			}
			State s = of(player);
			if (s.total() > harmLine(s)) {
				harm(player, s);
			}
			if (second) {
				everySecond(player, s, server, tick);
			}
		}
	}

	private static void everySecond(ServerPlayer player, State s, MinecraftServer server, int tick) {
		Vec3 now = player.position();
		Vec3 was = LAST.put(player, now);
		long game = player.level().getGameTime();
		int load = s.load();
		int held = s.held();
		long until = s.heldUntil();
		int tolerance = s.tolerance();
		boolean changed = false;
		if (held > 0 && game >= until) {
			// the monster brew's own end
			held = 0;
			until = 0L;
			changed = true;
		}
		if (load > 0) {
			int gone = FADE_PER_SECOND;
			if (sweating(player, was, now)) {
				gone += SWEAT_PER_SECOND;
				sweat(player);
			}
			load = Math.max(0, load - gone);
			changed = true;
		}
		if (tolerance > 0 && load + held == 0 && tick % TOLERANCE_FADE_EVERY == 0) {
			tolerance--;
			changed = true;
		}
		State next = changed ? new State(load, held, until, tolerance) : s;
		if (changed) {
			player.setAttached(STATE, next);
		}
		if (next.total() > 0) {
			for (BiConsumer<ServerPlayer, State> hook : ON_SECOND) {
				hook.accept(player, next);
			}
		}
	}

	/** Whether {@code player} is soaking in a hot spring or sitting still by a lit campfire (and so draws the toxin out). */
	private static boolean sweating(ServerPlayer player, Vec3 was, Vec3 now) {
		if (player.isCreative() || player.isSpectator() || !(player.level() instanceof ServerLevel level)) {
			return false;
		}
		if (player.isInWater() && Resting.magmaBelow(level, player.blockPosition())) {
			return true;
		}
		boolean still = was != null && was.distanceToSqr(now) < STILL_SQR;
		return still && (Resting.restingPose(player) || seated(player)) && Resting.nearFire(level, player.blockPosition());
	}

	/** Whether {@code player} sits on something that is not a mount or a vehicle: a stool or a cushion. */
	private static boolean seated(ServerPlayer player) {
		Entity vehicle = player.getVehicle();
		return vehicle != null && !(vehicle instanceof LivingEntity) && !(vehicle instanceof VehicleEntity);
	}

	private static void sweat(ServerPlayer player) {
		for (Consumer<ServerPlayer> hook : SWEATS) {
			hook.accept(player);
		}
		if (player.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.FALLING_WATER, player.getX(), player.getEyeY() + 0.1, player.getZ(), 3, 0.25, 0.1, 0.25, 0.0);
		}
	}

	/** Past the line: a hurt every few seconds, faster nearer the top; only a full load can kill. */
	private static void harm(ServerPlayer player, State s) {
		if (player.isCreative() || player.isSpectator() || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		long now = level.getGameTime();
		Long next = NEXT_HARM.get(player);
		if (next != null && now < next) {
			return;
		}
		int line = harmLine(s);
		float over = Mth.clamp((s.total() - line) / (float) Math.max(1, CAPACITY - line), 0.0F, 1.0F);
		NEXT_HARM.put(player, now + Math.round(Mth.lerp(over * over, HARM_SLOW, HARM_FAST)));
		float hurt = s.total() >= CAPACITY ? HARM_HURT_TOP : HARM_HURT;
		if (s.total() < CAPACITY && player.getHealth() <= SPARED_AT) {
			return;
		}
		player.hurtServer(level, level.damageSources().magic(), hurt);
	}
}
