package com.jaymospowers.client.power.web;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Wind lanes for the web wings. Andy's pick from Legends: Superheroes, "gliding on the web wings, faint streaks of wind
 * show in the air between tall buildings and over cliffs; fly into one for a burst of speed". Nothing is placed in the
 * world: while the wings (or the symbiote's) glide, the ground round you is read every {@link #REFRESH} ticks from its
 * height map ({@link #scan}), and a lane is found wherever, at your height, the open air lies
 * <ul>
 * <li>in a gap between two walls that both reach up to you ({@link #GAP}) a canyon or a street between tall buildings, or</li>
 * <li>over the lip of a cliff, where the ground beside you drops away ({@link #DROP}) while the plateau stays under you.</li>
 * </ul>
 * Each lane runs along its gap or its cliff edge; faint streaks of cloud run along it, and a glider inside it is pushed
 * along it ({@link #boost}), past the wings' usual top speed ({@link #extra}). Only drawn as particles and felt as the push;
 * a lane is never a block.
 */
final class WebWindLanes {
	// ===== feel: the knobs to tweak after playtesting ===============================================================
	/** The ground is read this far round you (blocks) on a grid of this step, every this many ticks. */
	static final int RADIUS = 28, STEP = 4, REFRESH = 15;
	/** The walls of a gap are this far to either side of its middle (blocks); each must reach this much above the glider to count. */
	static final int GAP = 5, WALL_ABOVE = 1;
	/** A cliff's drop beside you is at least this much (blocks) lower than the ground under you. */
	static final int DROP = 10;
	/** The glider must be at least this far over the ground under it (blocks) for the air to be a lane. */
	static final double CLEARANCE = 2.5;
	/** A lane's box: half its length along, across and up (blocks). */
	static final double HALF_ALONG = 2.6, HALF_ACROSS = 3.4, HALF_UP = 5.0;
	/** The most lanes kept (the nearest ones). */
	static final int MAX_LANES = 14;
	/** Inside a lane the glide is pushed along it this much each tick (blocks/tick), and may then go this much faster than the wings' top speed. */
	static final double PUSH = 0.09, EXTRA_CAP = 0.55;
	/** The extra top speed lasts this many ticks after leaving a lane. */
	static final int EXTRA_TICKS = 25;
	// ================================================================================================================

	/** A lane: its middle, and the unit direction it runs along (a horizontal axis; the push picks the way you are flying). */
	private record Lane(Vec3 center, Vec3 along) {
	}

	private final List<Lane> lanes = new ArrayList<>();
	private int refreshIn;
	private boolean inside;
	private int extraTicks;

	void reset() {
		lanes.clear();
		refreshIn = 0;
		inside = false;
		extraTicks = 0;
	}

	/** Every tick of a glide: the lanes are read again now and then, and their streaks shown. */
	void tick(LocalPlayer player) {
		if (extraTicks > 0) {
			extraTicks--;
		}
		if (--refreshIn <= 0) {
			refreshIn = REFRESH;
			scan(player);
		}
		Vec3 flying = flightDirection(player, player.getDeltaMovement());
		for (Lane lane : lanes) {
			if (player.getRandom().nextFloat() > 0.55F) {
				continue;
			}
			Vec3 along = lane.along().scale(lane.along().dot(flying) >= 0.0 ? 1.0 : -1.0);
			Vec3 across = new Vec3(-lane.along().z, 0.0, lane.along().x);
			double a = (player.getRandom().nextDouble() - 0.5) * 2.0 * HALF_ALONG;
			double c = (player.getRandom().nextDouble() - 0.5) * 2.0 * HALF_ACROSS;
			double u = (player.getRandom().nextDouble() - 0.5) * 2.0 * HALF_UP;
			Vec3 at = lane.center().add(lane.along().scale(a)).add(across.scale(c)).add(0.0, u, 0.0);
			player.level().addParticle(ParticleTypes.CLOUD, at.x, at.y, at.z, along.x * 0.4, 0.0, along.z * 0.4);
		}
	}

	/**
	 * The glide's velocity {@code m}, pushed along the lane the player is in (if any): the way along the lane that is
	 * nearest the way they are flying.
	 */
	Vec3 boost(LocalPlayer player, Vec3 m) {
		Vec3 p = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
		Lane in = null;
		for (Lane lane : lanes) {
			Vec3 rel = p.subtract(lane.center());
			double across = rel.x * -lane.along().z + rel.z * lane.along().x;
			if (Math.abs(rel.dot(lane.along())) <= HALF_ALONG && Math.abs(across) <= HALF_ACROSS && Math.abs(rel.y) <= HALF_UP) {
				in = lane;
				break;
			}
		}
		if (in == null) {
			inside = false;
			return m;
		}
		if (!inside) {
			inside = true;
			WebSwing.play(player, SoundEvents.WIND_CHARGE_BURST.value(), 0.5F, 1.6F);
		}
		extraTicks = EXTRA_TICKS;
		Vec3 way = in.along().scale(in.along().dot(flightDirection(player, m)) >= 0.0 ? 1.0 : -1.0);
		return m.add(way.x * PUSH, 0.0, way.z * PUSH);
	}

	/** How much faster than the wings' usual top speed a glide may go now (blocks/tick): in a lane, and for a moment after. */
	double extra() {
		return extraTicks > 0 ? EXTRA_CAP : 0.0;
	}

	/** The way the player is flying (horizontally), from their motion, else where they look. */
	private static Vec3 flightDirection(LocalPlayer player, Vec3 m) {
		Vec3 flat = new Vec3(m.x, 0.0, m.z);
		if (flat.lengthSqr() < 1.0E-4) {
			Vec3 look = player.getLookAngle();
			flat = new Vec3(look.x, 0.0, look.z);
		}
		return flat.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
	}

	/** Reads the ground round the player for gaps and cliff edges at their height. */
	private void scan(LocalPlayer player) {
		lanes.clear();
		Level level = player.level();
		double altitude = player.getY();
		int px = (int)Math.floor(player.getX()), pz = (int)Math.floor(player.getZ());
		List<Lane> found = new ArrayList<>();
		for (int gx = -RADIUS; gx <= RADIUS; gx += STEP) {
			for (int gz = -RADIUS; gz <= RADIUS; gz += STEP) {
				if (gx * gx + gz * gz > RADIUS * RADIUS) {
					continue;
				}
				int x = px + gx, z = pz + gz;
				int here = height(level, x, z);
				if (here == Integer.MIN_VALUE || here > altitude - CLEARANCE) {
					continue;
				}
				for (int axis = 0; axis < 2; axis++) {
					// axis 0: the two sides are along x, so the lane runs along z; axis 1: the other way round
					int sx = axis == 0 ? GAP : 0, sz = axis == 0 ? 0 : GAP;
					int low = height(level, x - sx, z - sz), high = height(level, x + sx, z + sz);
					if (low == Integer.MIN_VALUE || high == Integer.MIN_VALUE) {
						continue;
					}
					boolean gap = low >= altitude + WALL_ABOVE && high >= altitude + WALL_ABOVE;
					boolean cliff = here - low >= DROP && high >= here - 3 || here - high >= DROP && low >= here - 3;
					if (!gap && !cliff) {
						continue;
					}
					// the lane goes on: the next cell along it is open too
					int ax = axis == 0 ? 0 : STEP, az = axis == 0 ? STEP : 0;
					int next = height(level, x + ax, z + az), prev = height(level, x - ax, z - az);
					if (next == Integer.MIN_VALUE || prev == Integer.MIN_VALUE || next > altitude - CLEARANCE || prev > altitude - CLEARANCE) {
						continue;
					}
					found.add(new Lane(new Vec3(x + 0.5, altitude, z + 0.5), axis == 0 ? new Vec3(0.0, 0.0, 1.0) : new Vec3(1.0, 0.0, 0.0)));
				}
			}
		}
		Vec3 at = player.position();
		found.sort(Comparator.comparingDouble(lane -> lane.center().distanceToSqr(at)));
		for (Lane lane : found) {
			if (lanes.size() >= MAX_LANES) {
				break;
			}
			lanes.add(lane);
		}
	}

	/** The height of the ground at a column (the first free block over it), or {@link Integer#MIN_VALUE} where it isn't loaded. */
	private static int height(Level level, int x, int z) {
		if (!level.hasChunkAt(new BlockPos(x, level.getMinY(), z))) {
			return Integer.MIN_VALUE;
		}
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
	}
}
