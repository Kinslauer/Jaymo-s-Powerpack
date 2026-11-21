package com.jaymoskitchen.cooking.hands;

import com.jaymoskitchen.cooking.Kitchen;
import com.jaymoskitchen.cooking.Quality;
import com.jaymoskitchen.cooking.Step;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

/**
 * A step recipe at the pot, pan or oven, done step by step in the order the recipe says. An ingredient step is a
 * right-click with that ingredient in hand (one is used up; nothing went in when the dish started, so a dish left or
 * burnt loses only what was added); an action is done with its own tool: stir with the spoon, simmer by sneaking with
 * the spoon, flip with the tongs, bake with the peel. The wrong thing (another ingredient, the wrong tool) is a mistake,
 * three mistakes burn it, and it burns if left too long. Judged by {@link Quality#judge} with the par and limit the
 * screen used (randomly stretched or squeezed for each dish). Everyone at the station does the steps of the one dish.
 */
final class SequenceRun extends Run {
	/** A second click this soon after the last is the same click (the key bounced). */
	private static final int DEBOUNCE = 3;
	/** After an ingredient goes in, the same thing still in hand is not a mistake for this long (the use key held repeats). */
	private static final int REPEAT_GRACE = 10;

	private final int parTicks;
	private final int limitTicks;
	private int progress;
	private int mistakes;
	private int lastClick = -DEBOUNCE;
	private Step lastIn;
	private int lastInTick = -REPEAT_GRACE;

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

	/** The action a click with the tool {@code held} is, or null if it is none (a knife, a fan, an ingredient, an empty hand). */
	private static Step stepOf(ItemStack held, boolean sneaking) {
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

	/** Whether {@code held} is an ingredient of any recipe: put in at the wrong time it is a mistake, not a stray click. */
	static boolean ingredient(ItemStack held) {
		for (Step step : Step.values()) {
			if (!step.isAction() && step.matches(held)) {
				return true;
			}
		}
		return false;
	}

	@Override
	boolean use(ServerPlayer player, ItemStack held, boolean sneaking, int now) {
		Step wanted = next();
		boolean goesIn = !wanted.isAction() && wanted.matches(held);
		if (!held.isEmpty() && !CookTools.isTool(held) && !ingredient(held)) {
			return false;
		}
		if (now - lastClick < DEBOUNCE) {
			return true;
		}
		lastClick = now;
		if (held.isEmpty() && !wanted.isAction()) {
			// an empty hand adds nothing: say what goes in next
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.hands.seq.add", Component.translatable(wanted.translationKey())));
			return true;
		}
		if (!goesIn && lastIn != null && now - lastInTick < REPEAT_GRACE && lastIn.matches(held)) {
			// the use key still held from the ingredient that just went in
			return true;
		}
		if (goesIn || stepOf(held, sneaking) == wanted) {
			if (goesIn) {
				lastIn = wanted;
				lastInTick = now;
			}
			if (goesIn && !player.hasInfiniteMaterials()) {
				held.shrink(1);
				if (wanted.remainder() != null) {
					Kitchen.give(player, new ItemStack(wanted.remainder()));
				}
			}
			// the cook whose step finishes the dish is the one it goes to
			touch(player, now);
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
