package com.jaymoskingdoms;

/** (0.5.21) A villager who can be one of a settlement's {@link Worker}s (MCA's villagers, through {@code McaWorkerMixin}). */
public interface Enlisted extends Worker {
	/** Takes on {@code role} for {@code factionId} (their profession is set apart from this). */
	void enlist(int role, int factionId);

	/** No longer a worker: their load put down, their tool put away, free to wander. */
	void discharge();

	/** (0.5.53) The game time they came of age, {@link CourtWizards#CHILD} while they're young, {@link CourtWizards#UNKNOWN} before they're first seen. */
	long grownSince();

	void setGrownSince(long time);

	/** (0.5.53) A court wizard's spell timers ({@link Sorcery#TIMERS} of them). */
	int[] spellTimers();
}
