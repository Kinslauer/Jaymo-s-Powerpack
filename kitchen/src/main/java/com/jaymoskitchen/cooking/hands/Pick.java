package com.jaymoskitchen.cooking.hands;

import com.jaymoskitchen.cooking.Dish;
import com.jaymoskitchen.cooking.Prep;
import com.jaymoskitchen.cooking.Step;
import java.util.EnumMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * What a station could make from what's been put on it: a dish (cooked by its {@link Dish.Method}) or one of the
 * hand-made things (dough, a joint, a sandwich, a brew, tea, sushi). {@link Kind} says how it is cooked and, for the
 * client, which readout to draw (its ordinal is sent, so only ever append).
 */
public record Pick(Kind kind, @Nullable Dish dish, int extra) {
	public enum Kind {
		CHOP(CookTools.Tool.KNIFE), STIR(CookTools.Tool.SPOON), ROAST(CookTools.Tool.HAND), GRILL(CookTools.Tool.TONGS),
		OVEN(CookTools.Tool.PEEL), SEAR(CookTools.Tool.TONGS), SMOKE(CookTools.Tool.FAN), SEQUENCE(CookTools.Tool.SPOON),
		DOUGH(CookTools.Tool.HAND), JOINT(CookTools.Tool.HAND), SANDWICH(CookTools.Tool.KNIFE), KEG(CookTools.Tool.HAND),
		TEA(CookTools.Tool.HAND), SUSHI(CookTools.Tool.HAND);

		private final CookTools.Tool tool;

		Kind(CookTools.Tool tool) {
			this.tool = tool;
		}

		/** What starts it. */
		public CookTools.Tool tool() {
			return tool;
		}

		public static @Nullable Kind byIndex(int index) {
			Kind[] all = values();
			return index >= 0 && index < all.length ? all[index] : null;
		}
	}

	public static Pick of(Dish dish) {
		return new Pick(switch (dish.method()) {
			case CHOP -> Kind.CHOP;
			case STIR -> Kind.STIR;
			case ROAST -> Kind.ROAST;
			case GRILL -> Kind.GRILL;
			case OVEN -> Kind.OVEN;
			case SEAR -> Kind.SEAR;
			case SMOKE -> Kind.SMOKE;
			case SEQUENCE -> Kind.SEQUENCE;
		}, dish, 0);
	}

	public Map<Step, Integer> ingredients() {
		if (dish != null) {
			return dish.ingredients();
		}
		return switch (kind) {
			case DOUGH -> Prep.ingredients(Prep.Recipe.DOUGH);
			case JOINT -> Prep.ingredients(Prep.Recipe.JOINT);
			default -> new EnumMap<>(Step.class);
		};
	}

	public int dishIndex() {
		return dish == null ? -1 : dish.ordinal();
	}
}
