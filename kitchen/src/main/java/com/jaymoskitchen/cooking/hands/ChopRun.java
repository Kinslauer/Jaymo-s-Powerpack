package com.jaymoskitchen.cooking.hands;

import com.jaymoskitchen.cooking.Minigames;
import com.jaymoskitchen.cooking.Quality;
import com.jaymoskitchen.cooking.Step;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Chopping on the board: the vegetables slide under the knife, faster after every cut; swing (left-click) with a knife
 * when a mark is under the blade. A mark that slips past uncut is a miss. The same game and numbers as the screen's
 * (see {@link Minigames}), run here so everyone at the board chops the same vegetable.
 */
final class ChopRun extends Run {
	/** Ticks the knife takes to come back up; a swing before it is ignored. */
	private static final int CHOP_ANIM = 6;
	/** Ticks after the last cut before it is judged. */
	private static final int DONE_TICKS = 12;

	private float slide = -0.25F;
	private float speed = Minigames.CHOP_SPEED;
	private int next;
	/** Per cut: 2 clean, 1 near, 0 ragged. */
	private final List<Integer> results = new ArrayList<>();
	private int chopAnim = -1;
	private int doneTicks = -1;
	private final Item sample;

	ChopRun(Table table, Pick pick, int now) {
		super(table, pick, now);
		Item first = null;
		for (Step step : pick.dish().steps()) {
			if (!step.isAction() && step.available()) {
				first = step.item();
				break;
			}
		}
		this.sample = first;
	}

	private static float mark(int i) {
		return (i + 0.5F) * Minigames.MARK_GAP;
	}

	@Override
	void tick(int now) {
		if (chopAnim >= 0 && ++chopAnim > CHOP_ANIM) {
			chopAnim = -1;
		}
		if (doneTicks >= 0) {
			if (++doneTicks > DONE_TICKS) {
				finish();
			}
			return;
		}
		slide += speed;
		// a mark that slips past the knife uncut
		if (next < Minigames.CUTS && slide > mark(next) + Minigames.CHOP_GOOD) {
			cut(0);
		}
	}

	@Override
	boolean use(ServerPlayer player, ItemStack held, boolean sneaking, int now) {
		return CookTools.Tool.KNIFE.holds(held);
	}

	@Override
	boolean attack(ServerPlayer player, ItemStack held, int now) {
		if (!CookTools.Tool.KNIFE.holds(held)) {
			return false;
		}
		if (doneTicks >= 0 || next >= Minigames.CUTS || chopAnim >= 0) {
			return true;
		}
		chopAnim = 0;
		float off = Math.abs(slide - mark(next));
		cut(off <= Minigames.CHOP_PERFECT ? 2 : off <= Minigames.CHOP_GOOD ? 1 : 0);
		return true;
	}

	private void cut(int result) {
		results.add(result);
		next++;
		speed += Minigames.CHOP_SPEED_STEP;
		if (next >= Minigames.CUTS) {
			doneTicks = 0;
		}
		table.playBlock(result == 0 ? SoundEvents.WOOD_HIT : SoundEvents.AXE_STRIP.value(), 0.7F, result == 2 ? 1.5F : result == 1 ? 1.2F : 0.8F);
		if (sample != null) {
			table.level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, sample), table.pos.getX() + 0.5,
				table.pos.getY() + 0.2, table.pos.getZ() + 0.5, 6, 0.12, 0.04, 0.12, 0.05);
		}
		table.sync(table.level.getServer().getTickCount(), true);
	}

	private void finish() {
		int perfects = 0;
		int goods = 0;
		for (int r : results) {
			perfects += r == 2 ? 1 : 0;
			goods += r == 1 ? 1 : 0;
		}
		Quality quality = Minigames.judgeChop(perfects, goods, results.size() - perfects - goods);
		end(quality);
	}

	/** Chopping left half-done is a mess. */
	@Override
	void leave() {
		end(Quality.BURNT);
	}

	/** ints: cuts made, then each cut's result; floats: how far it has slid, and how fast. */
	@Override
	void fill(List<Integer> ints, List<Float> floats) {
		ints.add(next);
		ints.addAll(results);
		floats.add(slide);
		floats.add(doneTicks >= 0 ? 0.0F : speed);
	}
}
