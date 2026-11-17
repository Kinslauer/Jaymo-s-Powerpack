package com.jaymoskitchen.cooking.hands;

import com.jaymoskitchen.cooking.Quality;
import com.jaymoskitchen.cooking.Sear;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

/**
 * Searing in the pan over the blast furnace's heat: a marker sweeps across the heat bar, a little faster each round;
 * right-click with the tongs when it is inside the target. Five rounds. The same game and numbers as the screen's
 * (see {@link Sear}); whoever presses first in a round counts.
 */
final class SearRun extends Run {
	/** Ticks the round's result shows before the next round. */
	private static final int FEEDBACK_TICKS = 12;

	private final List<Integer> results = new ArrayList<>();
	private int round;
	private int feedback;
	private float zone;
	private float speed;
	private float marker;
	private float direction;
	private int roundTicks;

	SearRun(Table table, Pick pick, int now) {
		super(table, pick, now);
		nextRound();
		table.playBlock(SoundEvents.BLASTFURNACE_FIRE_CRACKLE, 1.0F, 1.0F);
	}

	private void nextRound() {
		zone = Sear.ZONE_WIDTH / 2 + random.nextFloat() * (1.0F - Sear.ZONE_WIDTH);
		speed = Sear.SPEED + round * Sear.SPEED_STEP + (random.nextFloat() * 2.0F - 1.0F) * Sear.SPEED_JITTER;
		// start from whichever end is further from the target, heading the other way
		marker = zone > 0.5F ? 0.0F : 1.0F;
		direction = zone > 0.5F ? 1.0F : -1.0F;
		roundTicks = 0;
	}

	@Override
	boolean use(ServerPlayer player, ItemStack held, boolean sneaking, int now) {
		if (!CookTools.Tool.TONGS.holds(held)) {
			return false;
		}
		if (feedback == 0 && round < Sear.ROUNDS) {
			float off = Math.abs(marker - zone);
			endRound(off <= Sear.CORE_WIDTH / 2 ? 2 : off <= Sear.ZONE_WIDTH / 2 ? 1 : 0);
		}
		return true;
	}

	private void endRound(int result) {
		results.add(result);
		feedback = FEEDBACK_TICKS;
		table.playBlock(result == 0 ? SoundEvents.FIRE_EXTINGUISH : SoundEvents.ANVIL_PLACE, 0.5F, result == 2 ? 1.6F : result == 1 ? 1.2F : 0.8F);
		table.level.sendParticles(result == 0 ? ParticleTypes.SMOKE : ParticleTypes.FLAME, table.pos.getX() + 0.5, table.pos.getY() + 1.0,
			table.pos.getZ() + 0.5, 4, 0.15, 0.05, 0.15, 0.02);
		table.sync(table.level.getServer().getTickCount(), true);
	}

	@Override
	void tick(int now) {
		if (feedback > 0) {
			if (--feedback == 0) {
				round++;
				if (round >= Sear.ROUNDS) {
					finish();
				} else {
					nextRound();
				}
			}
			return;
		}
		marker += direction * speed;
		if (marker >= 1.0F) {
			marker = 2.0F - marker;
			direction = -1.0F;
		} else if (marker <= 0.0F) {
			marker = -marker;
			direction = 1.0F;
		}
		if (++roundTicks > Sear.ROUND_TICKS) {
			endRound(0);
		}
	}

	private void finish() {
		int perfects = 0;
		int goods = 0;
		for (int r : results) {
			if (r == 2) {
				perfects++;
			} else if (r == 1) {
				goods++;
			}
		}
		end(Sear.judge(perfects, goods, results.size() - perfects - goods));
	}

	/** Walking away from the heat with meat in it: charred. */
	@Override
	void leave() {
		end(Quality.CHARRED);
	}

	/** ints: the round, ticks of result still showing, then each round's result (-1 not yet); floats: the marker, the target, the marker's speed (signed; 0 while a result shows). */
	@Override
	void fill(List<Integer> ints, List<Float> floats) {
		ints.add(round);
		ints.add(feedback);
		for (int r = 0; r < Sear.ROUNDS; r++) {
			ints.add(r < results.size() ? results.get(r) : -1);
		}
		floats.add(marker);
		floats.add(zone);
		floats.add(feedback > 0 ? 0.0F : direction * speed);
	}
}
