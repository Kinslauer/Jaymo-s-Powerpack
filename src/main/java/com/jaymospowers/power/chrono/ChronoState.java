package com.jaymospowers.power.chrono;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * One chrono player's server-side state: the rewind history (a ring buffer, one sample per tick), cooldowns, and
 * whatever is running right now. Kept in memory only (not saved), keyed by player UUID; it survives death and
 * relogging (so a relog doesn't reset cooldowns) but not a server restart.
 */
final class ChronoState {
	/** Samples kept: now plus {@link ChronoPower#REWIND_TICKS} ticks back. */
	static final int HISTORY = ChronoPower.REWIND_TICKS + 1;

	private final double[] x = new double[HISTORY];
	private final double[] y = new double[HISTORY];
	private final double[] z = new double[HISTORY];
	private final float[] yRot = new float[HISTORY];
	private final float[] xRot = new float[HISTORY];
	private final float[] health = new float[HISTORY];
	@SuppressWarnings("unchecked")
	private final ResourceKey<Level>[] dimension = new ResourceKey[HISTORY];
	private int head;
	private int size;

	/** Server tick (MinecraftServer#getTickCount) at which each ability is ready again, and how long its cooldown was. */
	final int[] readyAt = new int[ChronoSync.ABILITIES];
	final int[] cooldownTotal = new int[ChronoSync.ABILITIES];

	/** Chrono Punch (the ascended ability): server tick when it's ready again, and how long its cooldown was. */
	int ultimateReadyAt;
	int ultimateTotal;

	/** Where the player was last tick, to notice a dimension change (their close echoes follow). */
	net.minecraft.server.level.@Nullable ServerLevel lastLevel;
	@Nullable Vec3 lastPos;
	/** Slept long enough this night (set in bed, checked on waking). */
	boolean restedFully;

	@Nullable StasisField stasis;
	@Nullable TimeBloom bloom;
	int overclockLeft;
	/** (0.11.40) Max rank (all four abilities) only: Overclock was switched on with V and runs until V switches it off (or you die). */
	boolean overclockHeld;
	/** (0.10.28) Server tick when V may phase through a wall again (mid-Overclock). */
	int phaseReadyAt;
	@Nullable RewindTrail trail;

	/** False until the client has been sent the current state (new state, relog, or something changed). */
	boolean synced;
	/** (0.15.81) Down last tick: the history then held still, kept from before the fall for a Rewind back up. */
	boolean downed;
	int lastSync;

	void record(Vec3 pos, float yRot, float xRot, float health, ResourceKey<Level> dimension) {
		this.x[head] = pos.x;
		this.y[head] = pos.y;
		this.z[head] = pos.z;
		this.yRot[head] = yRot;
		this.xRot[head] = xRot;
		this.health[head] = health;
		this.dimension[head] = dimension;
		head = (head + 1) % HISTORY;
		if (size < HISTORY) {
			size++;
		}
	}

	void clearHistory() {
		size = 0;
		head = 0;
	}

	/** Samples available; age 0 is the newest, {@code size() - 1} the oldest. */
	int size() {
		return size;
	}

	private int index(int age) {
		return Math.floorMod(head - 1 - age, HISTORY);
	}

	Vec3 pos(int age) {
		int i = index(age);
		return new Vec3(x[i], y[i], z[i]);
	}

	float yRot(int age) {
		return yRot[index(age)];
	}

	float xRot(int age) {
		return xRot[index(age)];
	}

	float health(int age) {
		return health[index(age)];
	}

	/** True if any recorded sample was taken in a different dimension than {@code current}. */
	boolean spansOtherDimension(ResourceKey<Level> current) {
		for (int age = 0; age < size; age++) {
			if (dimension[index(age)] != current) {
				return true;
			}
		}
		return false;
	}

	/** Positions from now (age 0) back to {@code oldestAge}, for the trail. */
	List<Vec3> path(int oldestAge) {
		List<Vec3> out = new ArrayList<>(oldestAge + 1);
		for (int age = 0; age <= oldestAge; age++) {
			out.add(pos(age));
		}
		return out;
	}

	int cooldownLeft(int ability, int now) {
		return Math.max(0, readyAt[ability] - now);
	}

	int ultimateLeft(int now) {
		return Math.max(0, ultimateReadyAt - now);
	}

	boolean anythingTicking(int now) {
		if (ultimateLeft(now) > 0) {
			return true;
		}
		for (int i = 0; i < ChronoSync.ABILITIES; i++) {
			if (cooldownLeft(i, now) > 0) {
				return true;
			}
		}
		return stasis != null || bloom != null || overclockLeft > 0;
	}
}
