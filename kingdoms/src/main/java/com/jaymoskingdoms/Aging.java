package com.jaymoskingdoms;

import net.minecraft.world.entity.PathfinderMob;

/** (0.5.54) Someone who grows old ({@link OldAge}): an MCA villager ({@link Enlisted}) or a realm's {@link Folk}. */
public interface Aging {
	PathfinderMob mob();

	/** The game time they came of age, {@link CourtWizards#CHILD} while they're young, {@link CourtWizards#UNKNOWN} before they're first seen. */
	long grownSince();

	void setGrownSince(long time);
}
