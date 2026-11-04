package com.jaymospowers.alchemy.brewing;

import com.jaymospowers.alchemy.cauldron.CauldronBrewing;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.ToDoubleFunction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * (0.20.13) The numbers of brewing quality: the grinding and stirring bars (the same on both sides, no world access) and
 * how a step and a whole brew are scored. Tune everything here.
 */
public final class BrewQuality {
	public static final int MISS = 0, GOOD = 1, PERFECT = 2;
	public static final int GRIND_GAME = 0, STIR_GAME = 1;

	/** One timing bar's numbers (0 to 100 across). */
	public record Bar(int strokes, float perfectHalf, float shrink, float minPerfectHalf, float goodScale,
		float startSpeed, float speedUp, float maxSpeed, float centreMargin) {
	}

	/** The pestle: 5 strokes, a kind zone that narrows a little, a slow arrow. */
	public static final Bar GRIND = new Bar(5, 7.0F, 0.92F, 3.0F, 2.5F, 2.0F, 0.35F, 4.5F, 20.0F);
	/** The stir: CauldronBrewing.STIR_TURNS turns, a wider zone that narrows faster, a quicker arrow. */
	public static final Bar STIR = new Bar(CauldronBrewing.STIR_TURNS, 8.0F, 0.9F, 3.0F, 2.5F, 2.2F, 0.3F, 5.0F, 20.0F);
	/** Score of a Good stroke (Perfect 1, Miss 0). */
	public static final float GOOD_SCORE = 0.6F;
	/** Mean stroke score a batch needs to come out Ground to Dust (3), Finely (2); anything less is Coarse (1). */
	public static final float DUST_AT = 0.85F, FINE_AT = 0.55F;
	/** What a step's grind counts: ground Coarse, Fine, Dust, and a grindable thing thrown in whole. */
	public static final float COARSE_SCORE = 0.6F, FINE_SCORE = 0.85F, DUST_SCORE = 1.0F, UNGROUND_SCORE = 0.3F;
	/** How much of a step's score is its grind (the rest is the stir). */
	public static final float GRIND_WEIGHT = 0.4F;
	/** Mean step score each grade needs. */
	public static final float MASTERWORK_AT = 0.95F, EXPERT_AT = 0.8F, FINE_AT_GRADE = 0.6F, PLAIN_AT = 0.35F;
	/** Widens the perfect zone (bar units) for this brewer; empty in 0.20.13 (a later blessing can add to it). */
	public static final List<ToDoubleFunction<ServerPlayer>> ZONE_BONUS = new ArrayList<>();

	private BrewQuality() {
	}

	public static Bar bar(int game) {
		return game == GRIND_GAME ? GRIND : STIR;
	}

	public static float perfectHalf(Bar b, int done, float bonus) {
		return Math.max(b.minPerfectHalf(), b.perfectHalf() * (float) Math.pow(b.shrink(), done)) + bonus;
	}

	public static float goodHalf(Bar b, int done, float bonus) {
		return perfectHalf(b, done, bonus) * b.goodScale();
	}

	public static float speed(Bar b, int done) {
		return Math.min(b.maxSpeed(), b.startSpeed() + b.speedUp() * done);
	}

	/** Where this stroke's zones sit on the bar. */
	public static float centre(Bar b, long seed, int done) {
		return b.centreMargin() + new Random(seed * 31 + done).nextFloat() * (100.0F - 2 * b.centreMargin());
	}

	/** How a stroke at {@code arrow} (0 to 100) lands. */
	public static int judge(Bar b, float arrow, long seed, int done, float bonus) {
		float off = Math.abs(arrow - centre(b, seed, done));
		if (off <= perfectHalf(b, done, bonus)) {
			return PERFECT;
		}
		return off <= goodHalf(b, done, bonus) ? GOOD : MISS;
	}

	public static float score(int result) {
		return result == PERFECT ? 1.0F : result == GOOD ? GOOD_SCORE : 0.0F;
	}

	/** The ground level (1 to 3) a batch earns from its mean stroke score. */
	public static int groundLevel(float mean) {
		return mean >= DUST_AT ? 3 : mean >= FINE_AT ? 2 : 1;
	}

	/** What this ingredient's grind counts for its step, or -1 when it needs no grinding. */
	public static float grindScore(ItemStack reagent) {
		int ground = BrewGrades.ground(reagent);
		if (ground >= 3) {
			return DUST_SCORE;
		}
		if (ground == 2) {
			return FINE_SCORE;
		}
		if (ground == 1) {
			return COARSE_SCORE;
		}
		return reagent.is(Brewing.GRINDABLE) ? UNGROUND_SCORE : -1.0F;
	}

	public static float stepScore(float stirMean, float grind) {
		return grind < 0.0F ? stirMean : stirMean * (1.0F - GRIND_WEIGHT) + grind * GRIND_WEIGHT;
	}

	/** The grade of a brew from the sum of its step scores and how many steps there were. */
	public static BrewGrade grade(float quality, int steps) {
		if (steps <= 0) {
			return BrewGrade.PLAIN;
		}
		float mean = quality / steps;
		if (mean >= MASTERWORK_AT) {
			return BrewGrade.MASTERWORK;
		}
		if (mean >= EXPERT_AT) {
			return BrewGrade.EXPERT;
		}
		if (mean >= FINE_AT_GRADE) {
			return BrewGrade.FINE;
		}
		return mean >= PLAIN_AT ? BrewGrade.PLAIN : BrewGrade.CRUDE;
	}

	public static float bonus(ServerPlayer player) {
		double sum = 0;
		for (ToDoubleFunction<ServerPlayer> bonus : ZONE_BONUS) {
			sum += bonus.applyAsDouble(player);
		}
		return (float) sum;
	}
}
