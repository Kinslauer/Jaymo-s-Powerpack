package com.jaymoskingdoms.wild;

/** What the client needs to draw a wild beast: which of its looks it wears, and whether it's out for blood. */
public interface WildLook {
	int variant();

	/** Hostile right now (the coyote shows its fangs, and its eyes glow). */
	default boolean hostileLook() {
		return true;
	}
}
