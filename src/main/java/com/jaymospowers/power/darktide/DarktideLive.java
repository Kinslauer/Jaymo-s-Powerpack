package com.jaymospowers.power.darktide;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** One Darktide user's running state on the server (not saved: Flow and cooldowns start fresh after a restart). */
final class DarktideLive {
	float flow;
	final long[] until = new long[DarktideState.SLOTS];
	final int[] length = new int[DarktideState.SLOTS];

	// guard and parry
	boolean guarding;
	long guardStart = -1L;
	long parryReadyAt;
	long guardLockedUntil;
	long empoweredUntil;
	/** Recent hits blocked on this game tick (so a multi-hit attack doesn't count twice). */
	long lastBlockTick = -1L;

	// R: Dark Strike's charge, since when (game time; -1: not charging)
	long strikeStart = -1L;

	// Z: Veil (ascended, cured)
	boolean veil;

	// Z: shadow harvest (corrupted)
	/** The corpse whose shadow is being taken and since when (-1: none); whether Z is held. */
	int harvesting = -1;
	long harvestStart;
	boolean modeDown;

	// C
	int arms;
	long armsSwapAt;
	final List<Integer> risen = new ArrayList<>();

	// the cure: ticks of Conduit Power in a row
	int conduitTicks;
	/** Whether the cure held last tick (to whisper when it changes). */
	boolean wasCured;
	/** Whether the cure's state has been looked at since joining (no whisper for the state you log in with). */
	boolean cureSeen;
	/** The shade has been warned that dawn is near. */
	boolean dawnWarned;
	/** Ticks the shade has stood in sunlight. */
	int sunTicks;

	// Combat Roll
	long rollStart = -1L;
	boolean rollEvaded;
	boolean rollHit;
	/** Roll charges the client last said it has; -1 until it says (no Combat Roll: dashes cost none). */
	int rolls = -1;

	// R/G/V/B in progress
	@Nullable Undertow undertow;
	@Nullable Pounce pounce;
	@Nullable Barrage barrage;
	@Nullable Dash dash;
	@Nullable Gedan gedan;

	// last HUD sent
	DarktideState.@Nullable Hud sent;

	static final class Undertow {
		final Vec3 to;
		final long at;

		Undertow(Vec3 to, long at) {
			this.to = to;
			this.at = at;
		}
	}

	static final class Pounce {
		final long start;
		long launched = -1L;
		boolean empowered;

		Pounce(long start) {
			this.start = start;
		}
	}

	static final class Barrage {
		final long start;
		final Set<Integer> held = new HashSet<>();

		Barrage(long start) {
			this.start = start;
		}
	}

	static final class Dash {
		final long start;
		final Vec3 from;
		final Vec3 dir;
		final Set<Integer> hit = new HashSet<>();
		/** The flurry's target (-1: a plain dash), its hits so far. */
		int target = -1;
		int hits;

		Dash(long start, Vec3 from, Vec3 dir) {
			this.start = start;
			this.from = from;
			this.dir = dir;
		}
	}

	static final class Gedan {
		final long start;
		final Vec3 origin;
		final float yaw;
		int stance;
		int parries;
		/** Released: the slashes being dealt (-1: still holding). */
		long released = -1L;
		int seconds;
		final List<Integer> targets = new ArrayList<>();
		int next;

		Gedan(long start, Vec3 origin, float yaw) {
			this.start = start;
			this.origin = origin;
			this.yaw = yaw;
		}
	}
}
