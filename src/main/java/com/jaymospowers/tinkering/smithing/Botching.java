package com.jaymospowers.tinkering.smithing;

import java.util.Random;
import net.minecraft.util.Mth;

/**
 * (Smithing) A forging can fail. The harder the piece (more metal in it, a harder metal) and the better the grade the
 * hammering reached for, the likelier the metal cracks under the last blow and is wasted; blows that missed their mark
 * make it likelier still. Small iron and soft-metal pieces never crack, and nothing forged to a plain Well Forged with
 * few misses does: the risk is in pushing a big piece of iron or steel to Expert and above. Tune the numbers here.
 */
public final class Botching {
	/** Ingots a piece can take before its size counts against it, and how much of its hardness its size and its metal are. */
	static final int EASY_INGOTS = 2;
	static final float SIZE_SHARE = 0.7F, METAL_SHARE = 0.5F;
	/** A metal's {@link Forging#difficulty} above 1 that counts as fully hard (steel's). */
	static final float HARDEST_METAL = 0.3F;
	/** The chance the hardest piece cracks when it would have come out Expert, Perfect, a Masterwork. */
	static final float EXPERT_RISK = 0.05F, PERFECT_RISK = 0.12F, MASTER_RISK = 0.2F;
	/** The share of blows that may miss for nothing, and the chance the hardest piece cracks with every blow past that missed. */
	static final float FORGIVEN = 0.2F, MISS_RISK = 0.6F;

	private Botching() {
	}

	/** How hard {@code option} is to bring off, 0 (never fails) to 1. */
	public static float hardness(Forging.Option option) {
		float size = Mth.clamp((option.ingots() - EASY_INGOTS) / (float) (Forging.MAX_INGOTS - EASY_INGOTS), 0.0F, 1.0F);
		float metal = Mth.clamp((Forging.difficulty(option.metal()) - 1.0F) / HARDEST_METAL, 0.0F, 1.0F);
		return Math.min(1.0F, SIZE_SHARE * size + METAL_SHARE * metal);
	}

	/** The chance this finished hammering, about to come out at {@code grade}, cracks instead. */
	public static float chance(Forging.Option option, Smithing.Work work, Grade grade) {
		float hardness = hardness(option);
		if (hardness <= 0.0F || work.hits() <= 0) {
			return 0.0F;
		}
		float reach = switch (grade) {
			case MASTER, LEGENDARY -> MASTER_RISK;
			case PERFECT -> PERFECT_RISK;
			case EXPERT -> EXPERT_RISK;
			default -> 0.0F;
		};
		int goods = Math.round((work.score() - work.perfects()) / Forging.GOOD_SCORE);
		float missed = Math.max(0, work.hits() - work.perfects() - goods) / (float) work.hits();
		return hardness * (reach + MISS_RISK * Math.max(0.0F, missed - FORGIVEN) / (1.0F - FORGIVEN));
	}

	/** Whether it cracks. */
	static boolean botched(Forging.Option option, Smithing.Work work, Grade grade, Random random) {
		return random.nextFloat() < chance(option, work, grade);
	}
}
