package com.jaymoskingdoms;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Toughness: how well a player's body takes a blow. Andy (2026-11-25): "Grit should give you more toughness, and toughness
 * should help you shrug off more hits to feel a bit more like vanilla in that you can take more hits in a fight. Right now
 * it feels good to me that a few hits are decisive, but it needs balancing for a broader audience."
 *
 * <p>A player's toughness ({@link #of}) is {@link #BASE}, plus every point of Grit ({@link Stats}), plus {@link #PER_GEAR}
 * for each point of armour toughness they wear (a full diamond set's 8 is 32, netherite's 12 is 48; a broken piece gives
 * nothing, as vanilla already skips its attributes). It does two things, both only against blows (anything that strikes
 * them: a hit, a shot, a blast; never falls, fire, drowning, hunger or bleeding, and nothing once they are down):
 * <ul>
 * <li><b>Shrug</b> ({@link #shrug}): every blow lands this share lighter, before armour. {@link #SHRUG_TOP} times
 * {@code t / (t + SHRUG_HALF)}, so each point is worth less than the last, and never more than {@link #SHRUG_CAP}. Grit
 * and gear add to the base: a new character's 20 is 10% (about one more hit against most foes), Grit 30 is 20%, Grit 100
 * is 33% (half again as many hits), Grit 100 in netherite 38%. The parts' pools ({@code Wounds} in Jaymo's Powers)
 * take what's left, so a blow that would have blacked out the head or the body may not now.</li>
 * <li><b>Poise</b> ({@link #poise}): a blow that arrives at no more than {@link #POISE_PER_POINT} damage per point of
 * toughness doesn't throw them back ({@code PoiseKnockbackMixin}): they set their feet and take it. A new character stands
 * through a 0.8, Grit 50 a 2.8, Grit 100 a 4.8 (a zombie's bite), Grit 100 in netherite about 6.7.</li>
 * </ul>
 * It comes after the Hearth tier's cap ({@link WorldTiers#hearthHit}), so a capped blow is shrugged too. Jaymo's Powers'
 * own finishing step (its {@code wounds} damage, when a part blacks out) is not a blow and is left alone, as are the downed
 * (an execution is an execution).
 */
public final class Toughness {
	/** Everyone's toughness before Grit and gear (Andy's pick, 2026-11-25: a small base, about a tenth off every blow). */
	public static final double BASE = 20.0;
	/** Toughness per point of armour toughness worn. */
	public static final double PER_GEAR = 4.0;
	/** The shrug's curve: at most this share, half of it at this much toughness, and never past the cap. */
	public static final double SHRUG_TOP = 0.6, SHRUG_HALF = 100.0, SHRUG_CAP = 0.4;
	/** A blow arriving at no more than this much damage per point of toughness doesn't throw you back. */
	public static final double POISE_PER_POINT = 0.04;
	/** Jaymo's Powers' wounds damage: a part blacking out finishes the body through it. Not a blow. */
	static final ResourceKey<DamageType> WOUNDS = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath("jaymospowers", "wounds"));

	/** The last blow each player stood through without being thrown back: its source, who struck it, and the game time. */
	private record Steady(DamageSource source, @Nullable Entity by, long time) {
	}

	private static final Map<Player, Steady> STEADY = new WeakHashMap<>();

	private Toughness() {
	}

	/** {@code player}'s toughness: {@link #BASE}, their Grit, and the armour toughness they wear. Either side. */
	public static double of(Player player) {
		return BASE + Stats.value(player, Stats.Stat.GRIT) + gear(player);
	}

	/** The toughness {@code player}'s worn armour gives ({@link #PER_GEAR} a point of armour toughness). */
	public static double gear(Player player) {
		return PER_GEAR * Math.max(0.0, player.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
	}

	/** The share {@code toughness} takes off every blow. */
	public static double shrug(double toughness) {
		double t = Math.max(0.0, toughness);
		return Math.min(SHRUG_CAP, SHRUG_TOP * t / (t + SHRUG_HALF));
	}

	/** The hardest blow (damage as it arrives) {@code toughness} stands through without being thrown back. */
	public static double poise(double toughness) {
		return POISE_PER_POINT * Math.max(0.0, toughness);
	}

	/**
	 * A blow on {@code player} as it lands ({@code HearthHitMixin}, after the Hearth cap and before armour): what's left
	 * once their toughness has shrugged its share. Notes whether they stand through it ({@link #steady}).
	 */
	public static float hit(Player player, DamageSource source, float amount) {
		if (amount <= 0.0F || !blow(player, source)) {
			return amount;
		}
		double t = of(player);
		float left = (float)(amount * (1.0 - shrug(t)));
		if (left <= poise(t)) {
			STEADY.put(player, new Steady(source, source.getEntity(), player.level().getGameTime()));
		} else {
			STEADY.remove(player);
		}
		return left;
	}

	/**
	 * Whether the knockback {@code source} is about to give {@code player} comes with a blow they just stood through: the
	 * same source, or the same striker in the same tick (a mob's own extra shove after its blow).
	 */
	public static boolean steady(Player player, @Nullable DamageSource source) {
		Steady last = STEADY.get(player);
		if (last == null || last.time() != player.level().getGameTime()) {
			return false;
		}
		return source == last.source() || source != null && last.by() != null && source.getEntity() == last.by();
	}

	/** A blow, as toughness counts them: anything that strikes them, not falls, fire and the like, nor the finishing step, nor on the downed. */
	static boolean blow(Player player, DamageSource source) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(WOUNDS) || Downed.isDowned(player)) {
			return false;
		}
		return source.getEntity() != null || source.getDirectEntity() != null || source.is(DamageTypeTags.IS_EXPLOSION);
	}
}
