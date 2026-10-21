package com.jaymospowers.stamina;

import com.jaymospowers.JaymosPowers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToIntFunction;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/**
 * (0.15.0) Feathers: the pack's stamina, our own after the ideas of Elenai's Feathers and Darkona's Feathers of
 * Fatigue (both Forge/NeoForge only and GPL / NC-SA, so nothing of theirs is copied; see {@code docs/dodge.md}). A row
 * of feathers above the hunger bar, two to an icon, that dodges ({@link Dodge}) and powers spend.
 * <ul>
 * <li><b>Armour weight</b>: every armour point holds back {@link #ARMOR_WEIGHT} of a feather; the held-back ones show
 * grey at the left end of the row.</li>
 * <li><b>Regeneration</b>: one feather every {@link #REGEN_TICKS}, after a pause ({@link #REGEN_DELAY}) that grows with
 * each spend in a row ({@link #REGEN_DELAY_STEP}, at most {@link #REGEN_DELAY_MAX}). Cold (snowy ground, freezing)
 * and hunger ({@link #HUNGRY_FOOD} or less) each halve it. A long enough sleep refills everything.</li>
 * <li><b>Strain</b>: with no feathers left you can still spend {@link #STRAIN_MAX} more, as red feathers; they're paid
 * back first, at half speed (twice as fast standing still or crouching). Anything left to spend lets one more move
 * through, but a move that runs past the strain leaves you <b>exhausted</b>: nothing more until the row is
 * {@link #RECOVER_SHARE} full again.</li>
 * <li><b>Heat</b>: in the Nether (without Fire Resistance) {@link #NETHER_PENALTY} feathers are held back.</li>
 * <li><b>Bonus</b>: powers can add golden feathers on top ({@link #addBonus}); they're spent first and regrow last.</li>
 * </ul>
 * The server keeps the numbers and syncs them to the owner only ({@link #STATE}, packed by {@link #pack}); both sides
 * read them through {@link #canSpend} and friends.
 */
public final class Feathers {
	// ===== the knobs ================================================================================================
	/** A full row (half-feathers: 20 is ten icons, like hunger). */
	public static final int MAX = 20;
	/** Feathers held back per armour point (full netherite, 20 points: 7). */
	public static final float ARMOR_WEIGHT = 0.35F;
	/** Ticks per feather regrown, and the pause after a spend before regrowth starts (ticks). */
	public static final int REGEN_TICKS = 10;
	public static final int REGEN_DELAY = 20, REGEN_DELAY_STEP = 10, REGEN_DELAY_MAX = 60;
	/** A spend this soon (ticks) after the last counts as "in a row" and lengthens the pause. */
	public static final int STREAK_TICKS = 40;
	/** Cold and hunger slow regrowth to this share each; at or under this food level you count as hungry. */
	public static final float COLD_REGEN = 0.5F, HUNGRY_REGEN = 0.5F;
	public static final int HUNGRY_FOOD = 6;
	/** Strain: red feathers you may go into once the row is empty; repaid at this share of the regrowth speed (doubled at rest). */
	public static final int STRAIN_MAX = 6;
	public static final float STRAIN_REGEN = 0.5F, REST_STRAIN_REGEN = 2.0F;
	/** Exhausted: no spending until the row is this full again (share of the row you can hold). */
	public static final float RECOVER_SHARE = 0.3F;
	/** In the Nether: feathers held back (Fire Resistance stops it). */
	public static final int NETHER_PENALTY = 4;
	/** The most golden bonus feathers anyone has. */
	public static final int BONUS_MAX = 20;
	// ================================================================================================================

	/**
	 * The owner's feathers, for their HUD and their own client's checks: {@link #pack}ed into one long (feathers, cap,
	 * bonus, bonus cap, strain, weight, exhausted and cold flags). Missing: no numbers yet (spending is allowed).
	 */
	public static final AttachmentType<Long> STATE = AttachmentRegistry.create(JaymosPowers.id("feathers"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.targetOnly()));

	/** A power's golden feathers: how many it gives this player right now (0 for none). */
	private static final List<ToIntFunction<ServerPlayer>> BONUSES = new ArrayList<>();

	/** Server: a player's feathers. */
	private static final class State {
		int feathers = MAX, bonus, strain;
		boolean exhausted;
		/** Regrowth paused until this game time; the last spend's time and how many in a row. */
		long pausedUntil, lastSpend = Long.MIN_VALUE / 2;
		int streak;
		/** Part of a feather regrown so far. */
		float growth;
		/** The last cap and bonus cap worked out (for clamping between ticks). */
		int cap = MAX, bonusCap, weight;
		boolean cold;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	private Feathers() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Feathers::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STATES.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> STATES.clear());
	}

	/** Gives players golden feathers on top of their row: {@code bonus} says how many (half-feathers) for each player. */
	public static void addBonus(ToIntFunction<ServerPlayer> bonus) {
		BONUSES.add(bonus);
	}

	// ---- reading (both sides) ---------------------------------------------------------------------------------------

	/** Feathers, cap, bonus, bonus cap, strain and weight, and the flags, in one long (8 bits each, flags above). */
	static long pack(State s) {
		return (long)(s.feathers & 0xFF) | (long)(s.cap & 0xFF) << 8 | (long)(s.bonus & 0xFF) << 16 | (long)(s.bonusCap & 0xFF) << 24
			| (long)(s.strain & 0xFF) << 32 | (long)(s.weight & 0xFF) << 40 | (s.exhausted ? 1L << 48 : 0L) | (s.cold ? 1L << 49 : 0L);
	}

	private static int field(long packed, int index) {
		return (int)(packed >> (index * 8) & 0xFF);
	}

	/** The synced numbers of {@code player} (on a client only its own player has them): see {@link View}. */
	public static View view(Player player) {
		Long packed = player.getAttached(STATE);
		if (packed == null) {
			return new View(MAX, MAX, 0, 0, 0, 0, false, false);
		}
		long p = packed;
		return new View(field(p, 0), field(p, 1), field(p, 2), field(p, 3), field(p, 4), field(p, 5), (p & 1L << 48) != 0, (p & 1L << 49) != 0);
	}

	/**
	 * A player's feathers: {@code feathers} of {@code cap} in the row (the rest of {@link #MAX} is held back:
	 * {@code weight} by armour, the remainder by heat), {@code bonus} golden of {@code bonusCap}, {@code strain} red.
	 */
	public record View(int feathers, int cap, int bonus, int bonusCap, int strain, int weight, boolean exhausted, boolean cold) {
		/** How much can still be spent, strain included. */
		public int spendable() {
			return exhausted ? 0 : bonus + feathers + Math.max(0, STRAIN_MAX - strain);
		}
	}

	/** Whether {@code player} can spend {@code cost} (half-feathers) now: not exhausted, with anything left counting strain. */
	public static boolean canSpend(Player player, int cost) {
		if (player.isCreative() || player.isSpectator() || cost <= 0) {
			return true;
		}
		return view(player).spendable() > 0;
	}

	// ---- spending (server) ------------------------------------------------------------------------------------------

	private static State state(ServerPlayer player) {
		return STATES.computeIfAbsent(player.getUUID(), id -> new State());
	}

	/** Spends {@code cost} (golden first, then the row, then strain; past the strain, exhausted); false (nothing spent) if it can't. */
	public static boolean spend(ServerPlayer player, int cost) {
		if (player.isCreative() || player.isSpectator() || cost <= 0) {
			return true;
		}
		State s = state(player);
		if (s.exhausted || s.bonus + s.feathers + Math.max(0, STRAIN_MAX - s.strain) <= 0) {
			return false;
		}
		int left = cost;
		int fromBonus = Math.min(left, s.bonus);
		s.bonus -= fromBonus;
		left -= fromBonus;
		int fromRow = Math.min(left, s.feathers);
		s.feathers -= fromRow;
		left -= fromRow;
		s.strain += left;
		if (s.strain >= STRAIN_MAX) {
			s.strain = STRAIN_MAX;
			s.exhausted = true;
		}
		long now = player.level().getGameTime();
		s.streak = now - s.lastSpend <= STREAK_TICKS ? s.streak + 1 : 0;
		s.lastSpend = now;
		s.pausedUntil = now + Math.min(REGEN_DELAY_MAX, REGEN_DELAY + s.streak * REGEN_DELAY_STEP);
		s.growth = 0.0F;
		sync(player, s);
		return true;
	}

	/** Gives {@code amount} back (a landed move refunding its cost): strain first, then the row, then golden. */
	public static void refund(ServerPlayer player, int amount) {
		State s = state(player);
		grow(s, amount);
		sync(player, s);
	}

	private static void grow(State s, int amount) {
		for (int i = 0; i < amount; i++) {
			if (s.strain > 0) {
				s.strain--;
			} else if (s.feathers < s.cap) {
				s.feathers++;
			} else if (s.bonus < s.bonusCap) {
				s.bonus++;
			} else {
				break;
			}
		}
		if (s.exhausted && s.strain == 0 && s.feathers >= Math.ceil(s.cap * RECOVER_SHARE)) {
			s.exhausted = false;
		}
	}

	// ---- the tick ---------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isAlive()) {
				STATES.remove(player.getUUID());
				continue;
			}
			State s = state(player);
			long before = pack(s);
			update(player, s);
			if (pack(s) != before || player.getAttached(STATE) == null || server.getTickCount() % 100 == 0) {
				sync(player, s);
			}
		}
	}

	private static void update(ServerPlayer player, State s) {
		ServerLevel level = player.level();
		s.weight = Math.min(MAX, Math.round(player.getArmorValue() * ARMOR_WEIGHT));
		int heat = level.dimension() == Level.NETHER && !player.hasEffect(MobEffects.FIRE_RESISTANCE) ? NETHER_PENALTY : 0;
		s.cap = Math.max(0, MAX - s.weight - heat);
		int bonusCap = 0;
		for (ToIntFunction<ServerPlayer> bonus : BONUSES) {
			bonusCap += Math.max(0, bonus.applyAsInt(player));
		}
		s.bonusCap = Math.min(BONUS_MAX, bonusCap);
		s.feathers = Math.min(s.feathers, s.cap);
		s.bonus = Math.min(s.bonus, s.bonusCap);

		if (player.isSleeping() && player.isSleepingLongEnough()) {
			// a good sleep puts everything back
			s.strain = 0;
			s.feathers = s.cap;
			s.bonus = s.bonusCap;
			s.exhausted = false;
			return;
		}
		BlockPos feet = player.blockPosition();
		s.cold = player.getTicksFrozen() > 0
			|| level.getBiome(feet).value().getPrecipitationAt(feet, level.getSeaLevel()) == Biome.Precipitation.SNOW;
		if (level.getGameTime() < s.pausedUntil) {
			return;
		}
		float rate = 1.0F / REGEN_TICKS;
		if (s.cold) {
			rate *= COLD_REGEN;
		}
		if (player.getFoodData().getFoodLevel() <= HUNGRY_FOOD) {
			rate *= HUNGRY_REGEN;
		}
		if (s.strain > 0) {
			boolean resting = player.isCrouching() || player.getDeltaMovement().horizontalDistanceSqr() < 1.0E-4;
			rate *= STRAIN_REGEN * (resting ? REST_STRAIN_REGEN : 1.0F);
		}
		s.growth += rate;
		if (s.growth >= 1.0F) {
			int whole = (int)s.growth;
			s.growth -= whole;
			grow(s, whole);
		}
	}

	private static void sync(ServerPlayer player, State s) {
		player.setAttached(STATE, pack(s));
	}
}
