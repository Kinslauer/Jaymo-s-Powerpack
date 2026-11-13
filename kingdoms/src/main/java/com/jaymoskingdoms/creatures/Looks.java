package com.jaymoskingdoms.creatures;

/**
 * What a creature shows its renderer beyond what every living thing already shows: a small pose number ({@link #look}, what
 * it is doing) and a fullness from 0 to 1 ({@link #fullness}, how fat, how far gone, how wound up). Both are kept in the
 * creature's synced data, so the client draws the same thing the server does.
 */
public interface Looks {
	/** What it is doing now (each creature says what its numbers mean). */
	int look();

	/** How full it is, from 0 to 1. */
	float fullness();
}
