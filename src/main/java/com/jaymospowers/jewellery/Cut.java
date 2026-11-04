package com.jaymospowers.jewellery;

import net.minecraft.network.chat.Component;

/**
 * (0.19.87) The four ways a gem is cut on the lapidary wheel. A harder cut takes more strokes, is harder to time and
 * gives a bigger bonus. The ids are saved on cut gems: never rename them.
 */
public enum Cut {
	CABOCHON("cabochon", 3, 0.85F, 0.9F),
	TABLE("table", 4, 1.0F, 1.0F),
	ROSE("rose", 5, 1.15F, 1.1F),
	BRILLIANT("brilliant", 6, 1.3F, 1.2F);

	public final String id;
	/** Strokes on the wheel, the factor on a gem's bonus and the factor on the wheel's difficulty. */
	public final int strokes;
	public final float bonus, difficulty;

	Cut(String id, int strokes, float bonus, float difficulty) {
		this.id = id;
		this.strokes = strokes;
		this.bonus = bonus;
		this.difficulty = difficulty;
	}

	public Component displayName() {
		return Component.translatable("cut.jaymospowers." + id);
	}

	public static Cut byId(String id) {
		for (Cut cut : values()) {
			if (cut.id.equals(id)) {
				return cut;
			}
		}
		return TABLE;
	}

	/** The cut with this id, or null. */
	public static Cut find(String id) {
		for (Cut cut : values()) {
			if (cut.id.equals(id)) {
				return cut;
			}
		}
		return null;
	}
}
