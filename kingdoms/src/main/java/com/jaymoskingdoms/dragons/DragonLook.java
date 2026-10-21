package com.jaymoskingdoms.dragons;

import net.minecraft.util.RandomSource;

/**
 * (0.7.137) What sets one dragon apart from the next of its kind, Andy: "can you slightly differentiate every dragon
 * visually like the heritages?" Rolled once when it's born, saved and synced as one int: its hide's shade (one of the
 * species' hide textures), its horns (long, short, or long with a second pair over the brows), a back spike lost in
 * some old fight (or none), and its glowing markings (one of the species' emissive patterns, drawn over it in the dark
 * with its eyes). Subtle on purpose: they all still read as their species. Every species uses the same look; each
 * paints its own hides and markings ({@code tools/gen_dragons.py}, {@code textures/entity/dragon/<species>/}).
 */
public record DragonLook(int hide, int horns, int scar, int marks) {
	/** How many of each there are: hides and marking patterns per species, horn shapes, and spikes on the back. */
	public static final int HIDES = 4, HORNS = 3, SPIKES = 5, MARKS = 4;
	/** The share of dragons with a spike missing. */
	static final float SCARRED = 0.3F;
	public static final int LONG_HORNS = 0, SHORT_HORNS = 1, TWIN_HORNS = 2;

	public static DragonLook roll(RandomSource random) {
		int scar = random.nextFloat() < SCARRED ? 1 + random.nextInt(SPIKES) : 0;
		return new DragonLook(random.nextInt(HIDES), random.nextInt(HORNS), scar, random.nextInt(MARKS));
	}

	public static DragonLook decode(int bits) {
		return new DragonLook(Math.floorMod(bits, HIDES), Math.floorMod(bits >> 4, HORNS), Math.floorMod(bits >> 8, SPIKES + 1),
			Math.floorMod(bits >> 12, MARKS));
	}

	public int encode() {
		return this.hide | this.horns << 4 | this.scar << 8 | this.marks << 12;
	}

	/** Whether back spike {@code i} (0 at the shoulders) is still there. */
	public boolean spike(int i) {
		return this.scar != i + 1;
	}
}
