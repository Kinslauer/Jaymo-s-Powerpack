package com.jaymospowers.gear.arrow;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * The arrowheads a bowyer fits to plain arrows at the Fletching Station. A fitted arrow is a {@link HeadedArrowItem} (an
 * ordinary arrow, in the {@code minecraft:arrows} tag, so any bow, crossbow, dispenser or quiver takes it) whose head is
 * its custom model data string; {@link HeadFx} does what the head does when the arrow lands.
 */
public enum ArrowHead {
	/** Trips whoever it hits (the bolas' own trip). */
	BOLA("bola", 0x9A7A4A, true),
	/** Roots everything near where it lands. */
	NET("net", 0xCFC8A8, true),
	/** Scatters caltrops where it lands. */
	CALTROP("caltrop", 0x7A7C84, true),
	/** Drags what it hits toward the archer on a line. */
	HARPOON("harpoon", 0xA8AEB8, false),
	/** Pulls the archer to where it sticks. */
	GRAPPLE("grapple", 0x8AA0B4, false),
	/** A stinging cloud that hides the archer's side. */
	SMOKE("smoke", 0x808086, true),
	/** Lights and marks the ground where it lands for a while. */
	FLARE("flare", 0xFF5A30, true),
	/** Calls mobs to where it lands. */
	WHISTLE("whistle", 0xE0C878, true),
	/** Barbs that open a bleeding cut. */
	BARBED("barbed", 0x9A2424, false),
	/** Hits harder and flies a little slower, so it drops sooner. */
	BROADHEAD("broadhead", 0xD6DAE2, false),
	/** Sets the arrow alight. */
	FIRE("fire", 0xFF8A20, false),
	/** A blinding flash where it lands. */
	FLASH("flash", 0xFFFFF0, true),
	/** Pierces a second target, and goes through a raised shield. */
	DRILL("drill", 0x5A6C7E, false),
	/** Comes back to the archer if it misses. */
	BOOMERANG("boomerang", 0xB07A30, false);

	public final String id;
	/** The head's colour on its icon and in its effects. */
	public final int color;
	/** Whether the head is used up where it lands (the arrow is gone), rather than left in the arrow to pick up. */
	public final boolean spent;

	ArrowHead(String id, int color, boolean spent) {
		this.id = id;
		this.color = color;
		this.spent = spent;
	}

	public String itemId() {
		return "head_" + id;
	}

	public String tag() {
		return HeadedArrowItem.TAG + id;
	}

	public static @Nullable ArrowHead byId(@Nullable String id) {
		if (id == null) {
			return null;
		}
		for (ArrowHead head : values()) {
			if (head.id.equals(id.toLowerCase(Locale.ROOT))) {
				return head;
			}
		}
		return null;
	}
}
