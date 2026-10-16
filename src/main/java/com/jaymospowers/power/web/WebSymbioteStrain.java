package com.jaymospowers.power.web;

/**
 * Which symbiote it is (saved in {@link WebSymbioteState#strain()} by its {@link #id()}): the black one we always had,
 * the white one (purified: blunt, healing, gentle) and the red one (a rival from its own craters: blades, bloodlust).
 * Not to be confused with {@link WebStrain}, the strain on a web line. See {@link WebSymbioteStrains}.
 */
public enum WebSymbioteStrain {
	BLACK(0, ""),
	WHITE(1, "white."),
	RED(2, "red.");

	private final int id;
	private final String lines;

	WebSymbioteStrain(int id, String lines) {
		this.id = id;
		this.lines = lines;
	}

	/** Its saved number. */
	public int id() {
		return this.id;
	}

	/** The lang-key infix its own lines live under ("" for black, "white." or "red."), as in {@code say.white.kill.1}. */
	public String lines() {
		return this.lines;
	}

	/** The strain saved as {@code id}; anything unknown (or an old save's missing field) is black. */
	public static WebSymbioteStrain of(int id) {
		for (WebSymbioteStrain strain : values()) {
			if (strain.id == id) {
				return strain;
			}
		}
		return BLACK;
	}
}
