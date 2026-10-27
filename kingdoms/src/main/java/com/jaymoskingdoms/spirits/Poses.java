package com.jaymoskingdoms.spirits;

/**
 * (0.7.245) The poses a spirit's arms hold, as small ints stamped into {@link SpiritState#show()} so viewers can draw them
 * ({@code SpiritArms} on the client keeps a table of arm angles for each). A move names its pose; the arms hold it for a
 * moment and ease back to {@link #IDLE}. Ids are stable: the client and the moves are written against these numbers.
 */
public final class Poses {
	public static final int IDLE = 0, REACH = 1, GRAB = 2, THROW = 3, CATCH = 4, SHOVE = 5, PIN = 6, HOLD = 7, SWORD = 8,
		GREATSWORD = 9, SPEAR = 10, AXE = 11, HAMMER = 12, FLURRY = 13, PARRY = 14, SLAM = 15, JAVELIN = 16, VOLLEY = 17,
		BOMBARD = 18, WALL = 19, MARK = 20, WARD = 21, FINISH = 22;
	/** The second patch's: Ambrose's, the shared armour and ride, Thessaly's, the Quiet One's, the clash. */
	public static final int AEGIS = 23, BLESS = 24, CATCH_ME = 25, MEND = 26, ARMOUR = 27, RIDE = 28, PULL = 29, STEAL = 30,
		TRAP = 31, UNLOCK = 32, JUGGLE = 33, ECHO = 34, MIRROR = 35, STILL = 36, SILENT = 37, SENSE = 38, CLASH = 39;
	/** How many poses there are (every id is below this, and below 64, which {@code show} packs it into). */
	public static final int COUNT = 40;

	private Poses() {
	}
}
