package com.jaymoskitchen.cooking.hands;

import com.jaymoskitchen.brewing.Brew;
import com.jaymoskitchen.brewing.Brewing;
import com.jaymoskitchen.cooking.Prep;
import com.jaymoskitchen.cooking.Quality;
import net.minecraft.world.item.ItemStack;

/**
 * The new stations that are poured by hand like dough (hold use, let go inside the band: see {@link DoughRun}, whose bands and
 * feel they share): the brewing keg (first pour the water, then the mash), the tea kettle (first the water, then the steep),
 * and the sushi mat (first the rice, then the vinegar, then it is rolled by right-click and left-click in turn).
 */
final class PourRuns {
	/** Drinks a batch of the keg or the kettle gives: a batch when it is perfect, one fewer when it is only good. */
	static final int BATCH = com.jaymoskitchen.brewing.CaskEntity.DRINKS_PER_BATCH;

	private PourRuns() {
	}

	private static int drinks(Prep.Result result) {
		return result == Prep.Result.PERFECT ? BATCH : result == Prep.Result.GOOD ? BATCH - 1 : 0;
	}

	/** The brewing keg: the fermented drinks the cask makes, but worked by hand in a sitting. */
	static final class Keg extends DoughRun {
		Keg(Table table, Pick pick, int now) {
			super(table, pick, now);
		}

		@Override
		boolean kneads() {
			return false;
		}

		@Override
		void conclude(Prep.Result result) {
			Brew brew = Brew.values()[Math.floorMod(pick.extra(), Brew.values().length)];
			endWith(result.ordinal(), () -> {
				if (drinks(result) > 0) {
					table.handOver(this, new ItemStack(Brewing.drink(brew), drinks(result)));
				}
				cue(result);
			});
		}
	}

	/** The tea kettle, over a fire. */
	static final class Tea extends DoughRun {
		Tea(Table table, Pick pick, int now) {
			super(table, pick, now);
		}

		@Override
		boolean kneads() {
			return false;
		}

		@Override
		void conclude(Prep.Result result) {
			endWith(result.ordinal(), () -> {
				if (drinks(result) > 0) {
					table.handOver(this, new ItemStack(HandsBlocks.TEA, drinks(result)));
				}
				cue(result);
			});
		}
	}

	/** The sushi mat: the rolls the board's chopping also makes, done by hand; judged by the same pours and the roll. */
	static final class Sushi extends DoughRun {
		Sushi(Table table, Pick pick, int now) {
			super(table, pick, now);
		}

		@Override
		void conclude(Prep.Result result) {
			if (result == Prep.Result.RUINED) {
				endWith(result.ordinal(), () -> cue(result));
			} else {
				end(result == Prep.Result.PERFECT ? Quality.PERFECT : Quality.GOOD);
			}
		}
	}
}
