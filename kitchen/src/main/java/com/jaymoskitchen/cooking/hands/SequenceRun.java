package com.jaymoskitchen.cooking.hands;

import com.jaymoskitchen.cooking.Quality;
import com.jaymoskitchen.cooking.Step;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

/**
 * A step recipe at the pot, pan or oven, done step by step in the order the recipe says. An ingredient step is a
 * right-click with an empty hand (the ingredients are already in); an action is done with its own tool: stir with the
 * spoon, simmer by sneaking with the spoon, flip with the tongs, bake with the peel. The wrong thing is a mistake, three
 * mistakes burn it, and it burns if left too long. Judged by {@link Quality#judge} with the par and limit the screen
 * used (randomly stretched or squeezed for each dish). Everyone at the station does the steps of the one dish.
 */
final class SequenceRun extends Run {
	/** A second click this soon after the last is the same click (the key bounced). */
	private static final int DEBOUNCE = 3;

	private final int parTicks;
	private final int limitTicks;
	private int progress;
	private int mistakes;
	private int lastClick = -DEBOUNCE;

	SequenceRun(Table table, Pick pick, int now) {
		super(table, pick, now);
		com.jaymoskitchen.cooking.Station at = station == null ? com.jaymoskitchen.cooking.Station.CAMPFIRE : station;
		this.parTicks = Math.round(dish.parTicks(at) * (0.85F + random.nextFloat() * 0.3F));
		this.limitTicks = Math.round(dish.limitTicks(at) * (0.9F + random.nextFloat() * 0.2F));
		table.playBlock(SoundEvents.SMOKER_SMOKE, 1.0F, 1.0F);
	}

	private Step next() {
		return dish.steps().get(Math.min(progress, dish.steps().size() - 1));
	}

	/** What a click with {@code held} does as a step: the action's step, an ingredient (empty hand), or null if it is no step at all. */
	private static Step stepOf(ItemStack held, boolean sneaking, Step wanted) {
		if (held.isEmpty()) {
			return wanted.isAction() ? null : wanted;
		}
		if (CookTools.Tool.SPOON.holds(held)) {
			return sneaking ? Step.SIMMER : Step.STIR;
		}
		if (CookTools.Tool.TONGS.holds(held)) {
			return Step.FLIP;
		}
		if (CookTools.Tool.PEEL.holds(held)) {
			return Step.BAKE;
		}
		return null;
	}

	@Override
	boolean use(ServerPlayer player, ItemStack held, boolean sneaking, int now) {
		boolean tool = CookTools.isTool(held);
		if (!held.isEmpty() && !tool) {
			return false;
		}
		if (now - lastClick < DEBOUNCE) {
			return true;
		}
		lastClick = now;
		Step wanted = next();
		Step done = stepOf(held, sneaking, wanted);
		if (done == wanted) {
			progress++;
			table.playBlock(SoundEvents.NOTE_BLOCK_BELL.value(), 1.0F, 1.0F + progress * 0.1F);
			table.level.sendParticles(ParticleTypes.HAPPY_VILLAGER, table.pos.getX() + 0.5, table.pos.getY() + 1.1, table.pos.getZ() + 0.5, 2, 0.15, 0.05,
				0.15, 0.0);
		} else {
			mistakes++;
			table.playBlock(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6F, 1.0F);
		}
		if (progress >= dish.steps().size() || mistakes >= Quality.MAX_MISTAKES) {
			finish(false, now);
		} else {
			table.sync(now, true);
		}
		return true;
	}

	@Override
	void tick(int now) {
		if (now % 20 == 0) {
			table.level.sendParticles(ParticleTypes.SMALL_FLAME, table.pos.getX() + 0.5, table.pos.getY() + 1.0, table.pos.getZ() + 0.5, 1, 0.2, 0.02, 0.2,
				0.01);
		}
		if (now - startTick > limitTicks) {
			finish(true, now);
		}
	}

	private void finish(boolean timedOut, int now) {
		end(Quality.judge(mistakes, now - startTick, parTicks, timedOut));
	}

	/** Walking away leaves it on the fire: it burns. */
	@Override
	void leave() {
		end(Quality.judge(mistakes, limitTicks + 1, parTicks, true));
	}

	/**
	 * ints: the step it is on, how many steps, mistakes, the next step's ordinal, ticks since it began, the par, the limit.
	 */
	@Override
	void fill(List<Integer> ints, List<Float> floats) {
		int now = table.level.getServer().getTickCount();
		ints.add(progress);
		ints.add(dish.steps().size());
		ints.add(mistakes);
		ints.add(next().ordinal());
		ints.add(now - startTick);
		ints.add(parTicks);
		ints.add(limitTicks);
	}
}
