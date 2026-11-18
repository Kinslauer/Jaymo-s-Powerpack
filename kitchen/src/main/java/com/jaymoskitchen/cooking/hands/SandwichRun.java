package com.jaymoskitchen.cooking.hands;

import com.jaymoskitchen.cooking.Minigames;
import com.jaymoskitchen.cooking.Prep;
import com.jaymoskitchen.cooking.Step;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A sandwich stacked on the board: the layers put there in order (bread first, bread last, the fillings between) are
 * cut through with the knife and come out as one sandwich that is as filling as the layers were. Three different fillings
 * or more make it a fine one (the "made well" mark). The cut is timed: once the knife has started it the sandwich slides
 * under the blade as a vegetable does on the chopping board ({@link ChopRun}, the same speed and marks), and the knife's
 * swing (left-click) is the cut, judged like one chop. A clean cut keeps it as fine as it was stacked, a poor one takes
 * it down a step, a miss squashes it (still a sandwich, but it keeps less of its saturation). Walking away is a miss.
 */
final class SandwichRun extends Run {
	/** Different fillings for a fine sandwich. */
	static final int FINE_FILLINGS = 3;
	/** Where the slide starts: the sandwich just off the board's edge, as the readout draws it. */
	private static final float CUT_START = -0.2F;
	/** Where along the slide the cut's mark is: the chopping board's first mark, the middle of the sandwich. */
	private static final float MARK = Minigames.MARK_GAP / 2;
	/** Ticks the cut's result shows before the sandwich is handed over. */
	private static final int DONE_TICKS = 12;
	/** How much of its saturation a squashed sandwich keeps. */
	static final float SQUASHED_SATURATION = 0.5F;

	private final List<ItemStack> layers;
	private float slide = CUT_START;
	/** The cut: 2 clean, 1 poor, 0 a miss; -1 while it is still to make. */
	private int cut = -1;
	private int doneTicks;

	SandwichRun(Table table, Pick pick, int now) {
		super(table, pick, now);
		this.layers = new ArrayList<>(table.taken);
	}

	@Override
	void tick(int now) {
		if (cut >= 0) {
			if (++doneTicks > DONE_TICKS) {
				finish();
			}
			return;
		}
		slide += Minigames.CHOP_SPEED;
		// the mark slips past the knife uncut
		if (slide > MARK + Minigames.CHOP_GOOD) {
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
		if (cut < 0) {
			float off = Math.abs(slide - MARK);
			cut(off <= Minigames.CHOP_PERFECT ? 2 : off <= Minigames.CHOP_GOOD ? 1 : 0);
		}
		return true;
	}

	private void cut(int result) {
		cut = result;
		table.playBlock(result == 0 ? SoundEvents.WOOD_HIT : SoundEvents.AXE_STRIP.value(), 0.7F, result == 2 ? 1.5F : result == 1 ? 1.2F : 0.8F);
		table.sync(table.level.getServer().getTickCount(), true);
	}

	private void finish() {
		int nutrition = 0;
		float saturation = 0.0F;
		Set<Item> fillings = new HashSet<>();
		List<Component> names = new ArrayList<>();
		for (ItemStack layer : layers) {
			FoodProperties food = layer.get(DataComponents.FOOD);
			if (food != null) {
				nutrition += food.nutrition();
				saturation += food.saturation();
			}
			if (!Step.BREAD.matches(layer)) {
				fillings.add(layer.getItem());
			}
			names.add(layer.getHoverName());
		}
		// fine or good as it was stacked; a poor cut takes it down a step, a miss all the way: squashed
		Prep.Result stacked = fillings.size() >= FINE_FILLINGS ? Prep.Result.PERFECT : Prep.Result.GOOD;
		Prep.Result result = cut == 2 ? stacked : cut == 1 && stacked == Prep.Result.PERFECT ? Prep.Result.GOOD : Prep.Result.RUINED;
		boolean squashed = result == Prep.Result.RUINED;
		int total = nutrition;
		float fed = squashed ? saturation * SQUASHED_SATURATION : saturation;
		endWith(result.ordinal(), () -> {
			ItemStack sandwich = new ItemStack(HandsBlocks.SANDWICH);
			HandsBlocks.stamp(sandwich, total, fed, names);
			table.handOver(this, sandwich);
			if (squashed) {
				table.playBlock(SoundEvents.SLIME_SQUISH, 0.6F, 0.8F);
			} else {
				table.playBlock(SoundEvents.PLAYER_LEVELUP, result == Prep.Result.PERFECT ? 1.0F : 0.6F, 1.4F);
			}
		});
	}

	/** Walking away from it cuts it badly: a miss, unless the cut was already made. */
	@Override
	void leave() {
		if (cut < 0) {
			cut = 0;
		}
		finish();
	}

	/** As the chopping board's, with its one cut. ints: cuts made (0 or 1), then the cut's result; floats: how far it has slid, and how fast. */
	@Override
	void fill(List<Integer> ints, List<Float> floats) {
		ints.add(cut < 0 ? 0 : 1);
		if (cut >= 0) {
			ints.add(cut);
		}
		floats.add(slide);
		floats.add(cut >= 0 ? 0.0F : Minigames.CHOP_SPEED);
	}
}
