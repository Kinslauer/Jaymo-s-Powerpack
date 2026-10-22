package com.jaymospowers.power.xenoform.nadir;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Nadir's scratch for G V B, one per player while the band is on them (not saved; cleared when the form ends). */
public final class NadirState {
	// ---- G, the Rift ----
	/** When G went down (-1: not held), and whether the hold has already fired (Surface). */
	public long riftDownAt = -1L;
	public boolean riftFired;
	/** Recursive Drop: the creature falling (entity id, -1 none), passes left, its column, and the two maws' heights. */
	public int dropTarget = -1;
	public int dropPasses;
	public double dropX, dropZ, dropFloor, dropSky;

	// ---- V, the Graviton ----
	/** When V went down (-1: not held), the creature gripped (entity id, -1: none, aimed at nothing), the hold has fired. */
	public long gravDownAt = -1L;
	public int gravTarget = -1;
	public boolean gravFired;
	/** Slammed creatures held to the ground: entity id to the tick they're let go. */
	public final Map<Integer, Long> pinned = new HashMap<>();
	/** The weightless zone (null: none) and when it closes; who it has lifted (entity id to the tick they come down). */
	public @Nullable Vec3 zone;
	public long zoneUntil;
	public final Map<Integer, Long> floaters = new HashMap<>();
	/** The event horizon's core (null: none), the tick it opened, and when the next may open. */
	public @Nullable Vec3 horizon;
	public long horizonAt;
	public long horizonNext;

	// ---- B, the Command ----
	/** Rule hits on creatures: (entity id, rule slot) to the tick the rule may take hold of it again. */
	public final Map<Long, Long> ruleHits = new HashMap<>();
	/** The ticks each rule slot's instant word (push, blast, ...) may fire again, when it acts on me or around me. */
	public final long[] ruleNext = new long[NadirRules.SLOTS];
	/** A rule is acting right now (its own damage mustn't set off "what I hit" rules again). */
	public boolean ruling;

	public void clear() {
		riftDownAt = -1L;
		riftFired = false;
		dropTarget = -1;
		dropPasses = 0;
		gravDownAt = -1L;
		gravTarget = -1;
		gravFired = false;
		pinned.clear();
		zone = null;
		zoneUntil = 0L;
		floaters.clear();
		horizon = null;
		horizonAt = 0L;
		ruleHits.clear();
		java.util.Arrays.fill(ruleNext, 0L);
		ruling = false;
	}
}
