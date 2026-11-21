package com.jaymospowers.core;

import com.jaymospowers.compat.KingdomsCompat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * (Awakening pick) Ability Power: Kingdoms' attribute of that name (its K screen's Attributes tab, for those with a power)
 * read here by reflection, and the relic belt's focus relics on top. It works on what a power does while it acts (its keys
 * and its own tick, {@link Powers#actor}), each lever in its own way:
 * <ul>
 * <li>harm: what the move deals to anyone but its maker is raised by the damage share;</li>
 * <li>reach: a move that picks its target by aim ({@link PickKit#aimed}) looks that much further;</li>
 * <li>duration: what a move lays on someone else (an effect) lasts that much longer;</li>
 * <li>size: the radius of an area move (a blast, ring, field, pool, shockwave, a cone's width) is that much wider
 * ({@link #size}, which each such move asks for itself; what it draws is sized with it).</li>
 * </ul>
 * Without Kingdoms only the focus relics count.
 */
public final class AbilityPower {
	// ---- tuning ----
	/** Size: the share wider an area move is for each point of Ability Power (100 points: a fifth wider), as reach is. */
	public static final double SIZE_PER_POINT = 0.002;

	/** Kingdoms' {@code Stats.Stat.POWER}, looked for once (null without Kingdoms, or if it has no such attribute). */
	private static Object powerStat;
	private static boolean statLooked;
	/** The last answer {@link #points} gave on the server: whose, in which of their ticks. */
	private static java.util.UUID lastFor;
	private static int lastTick, lastPoints;

	private AbilityPower() {
	}

	/** {@code player}'s points of Ability Power (Kingdoms' attribute, {@code Stats.value}); 0 without Kingdoms. Either side. */
	public static int points(Player player) {
		if (!statLooked) {
			statLooked = true;
			try {
				for (Object stat : Class.forName("com.jaymoskingdoms.Stats$Stat").getEnumConstants()) {
					if (((Enum<?>)stat).name().equals("POWER")) {
						powerStat = stat;
					}
				}
			} catch (ReflectiveOperationException | LinkageError e) {
				powerStat = null;
			}
		}
		if (powerStat == null) {
			return 0;
		}
		// asked many times a tick (once for every creature an area move looks at): the server keeps the last answer for the tick
		boolean server = !player.level().isClientSide();
		if (server && player.getUUID().equals(lastFor) && player.tickCount == lastTick) {
			return lastPoints;
		}
		Object got = KingdomsCompat.call("Stats", "value", new Class<?>[] {Player.class, ((Enum<?>)powerStat).getDeclaringClass()}, 0, player, powerStat);
		int points = got instanceof Integer value ? Math.max(0, value) : 0;
		if (server) {
			lastFor = player.getUUID();
			lastTick = player.tickCount;
			lastPoints = points;
		}
		return points;
	}

	/**
	 * The radius (or a cone's width) of one of {@code owner}'s area moves: {@code base}, wider by their Ability Power. The
	 * move asks for itself, so this holds wherever the move runs (its key, its tick, a field ticking by itself later).
	 * Anything but a player (a ruler, an echo, nobody) keeps {@code base}.
	 */
	public static double size(Entity owner, double base) {
		int points = owner instanceof Player player ? points(player) : 0;
		return points <= 0 ? base : base * (1.0 + SIZE_PER_POINT * points);
	}

	/** {@link #size(Entity, double)} for a radius counted in whole blocks (rounded to the nearest). */
	public static int size(Entity owner, int base) {
		return (int)Math.round(size(owner, (double)base));
	}

	/** {@link #size(Entity, double)} for what is drawn (a {@code MagicFx} radius). */
	public static float size(Entity owner, float base) {
		return (float)size(owner, (double)base);
	}

	/** {@link #size(Entity, double)} for a field that only kept its maker's id: {@code base} while they are away. */
	public static double size(net.minecraft.server.level.ServerLevel level, java.util.UUID owner, double base) {
		return owner == null ? base : size(level.getServer().getPlayerList().getPlayer(owner), base);
	}

	/** {@link #size(net.minecraft.server.level.ServerLevel, java.util.UUID, double)} in whole blocks. */
	public static int size(net.minecraft.server.level.ServerLevel level, java.util.UUID owner, int base) {
		return (int)Math.round(size(level, owner, (double)base));
	}

	/** {@code player}'s Ability Power share for {@code what} ("damage", "reach", "length"): Kingdoms' attribute, 0 without it. */
	public static double share(Player player, String what) {
		Object got = KingdomsCompat.call("Stats", "abilityShare", new Class<?>[] {Player.class, String.class}, 0.0, player, what);
		return got instanceof Double d ? d : 0.0;
	}

	/** Harm dealt while a power acts: its maker's share more (not on the maker, and not on behalf of anyone else). */
	public static float damage(LivingEntity victim, DamageSource source, float damage) {
		ServerPlayer actor = Powers.actor();
		if (actor == null || victim == actor || damage <= 0.0F) {
			return damage;
		}
		Entity by = source.getEntity();
		if (by != null && by != actor) {
			return damage;
		}
		double s = share(actor, "damage") + com.jaymospowers.relics.Relics.focus(actor);
		return s <= 0.0 ? damage : (float)(damage * (1.0 + s));
	}

	/** An aimed move's reach, while a power acts. */
	public static double reach(ServerPlayer player, double reach) {
		if (Powers.actor() != player) {
			return reach;
		}
		return reach * (1.0 + share(player, "reach"));
	}

	/** An effect laid on {@code target} while someone else's power acts: longer by its maker's share. */
	public static MobEffectInstance lengthen(LivingEntity target, MobEffectInstance effect) {
		ServerPlayer actor = Powers.actor();
		if (actor == null || target == actor || effect.isInfiniteDuration() || target.level().isClientSide()) {
			return effect;
		}
		double s = share(actor, "length");
		return s <= 0.0 ? effect : effect.withScaledDuration((float)(1.0 + s));
	}
}
