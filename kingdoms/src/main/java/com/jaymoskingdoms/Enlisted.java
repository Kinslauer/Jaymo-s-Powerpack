package com.jaymoskingdoms;

/** (0.5.21) A villager who can be one of a settlement's {@link Worker}s (MCA's villagers, through {@code McaWorkerMixin}). */
public interface Enlisted extends Worker {
	/** Takes on {@code role} for {@code factionId} (their profession is set apart from this). */
	void enlist(int role, int factionId);

	/** No longer a worker: their load put down, their tool put away, free to wander. */
	void discharge();
}
