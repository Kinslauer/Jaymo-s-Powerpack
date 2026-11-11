package com.jaymoskingdoms;

import com.jaymoskingdoms.sailing.Fleet;
import com.jaymoskingdoms.sailing.HelmBlock;
import com.jaymoskingdoms.sailing.Ship;
import com.jaymoskingdoms.sailing.Ships;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The sky folk's ferries: the balloon skiff moored at a sky village's mast (built by Jaymo's Powers, her wheel one of
 * ours) taken out by two of the village's own. Now and then in daylight ({@link #moored}) two villagers go aboard, she is cast
 * off as a balloon {@link Ship}, climbs clear of the roofs, crosses the open sky to a point a good way off, comes about
 * and returns to her mast, where her crew haul her in and she is blocks again. Anyone standing on her deck goes with
 * her, and anyone who takes her wheel has it: her crew take her home again when it's let go.
 * <p>
 * She is flown through {@link Ship#skyfaring}, as a player's keys would fly her. Her trip is kept on her
 * ({@link #TRIP}), so she finishes it after the chunk she is in was put away and brought back.
 */
public final class SkyFerries implements Ship.Skyfaring {
	/** By day, one look ({@link SkyCourt#LOOK_EVERY}) in this many at a moored skiff sends her out (about once in five minutes). */
	static final int CAST_OFF_IN = 10;
	/** Her crew. */
	static final int CREW = 2;
	/** How far above her mooring she flies (blocks), and how much higher she goes each time something stops her. */
	static final int CRUISE_UP = 22, OVER = 6;
	/** How far off she turns for home (blocks, least and most). */
	static final int FAR_MIN = 110, FAR_MAX = 200;
	/** She has reached the far point within this (blocks); she is over her mooring within this. */
	static final double ARRIVE = 10.0, OVER_HOME = 3.5;
	/** Near home she eases to this share of her cloth, from this far off (blocks). */
	static final double EASE = 0.25, EASE_FROM = 24.0;
	/** Coming down onto her mooring her crew haul her in on the line this fast (blocks a tick). */
	static final double HAUL = 0.04;
	/** She only comes down once she hangs within this of straight over her mooring (blocks). */
	static final double PLUMB = 1.5;
	/** She is put down once her head is within this of how she lay (degrees). */
	static final float SQUARE = 12.0F;
	/** Making no way for this long (ticks) with her cloth set, she goes {@link #OVER} higher. */
	static final int STUCK_TICKS = 60;
	/** A trip that has taken this long (ticks) is given up: she turns for home, and past twice this moors where she is. */
	static final long LONGEST_TRIP = 4800L;
	/** A village whose skiff couldn't be found (taken, or burnt) is looked at again after this long (ticks). */
	static final long LOOK_AGAIN = 12000L;

	/** The legs of a trip: up from the mast, out, home, down onto the mooring. */
	static final int UP = 0, OUT = 1, HOME = 2, DOWN = 3;

	/** A ferry's trip: where she was moored (her keel's place) and how she lay, where she turns, the leg she is on, when she left. */
	record Trip(Vec3 home, float yaw, BlockPos far, int leg, long began) {
		static final Codec<Trip> CODEC = RecordCodecBuilder.create(i -> i.group(
				Vec3.CODEC.fieldOf("home").forGetter(Trip::home),
				Codec.FLOAT.fieldOf("yaw").forGetter(Trip::yaw),
				BlockPos.CODEC.fieldOf("far").forGetter(Trip::far),
				Codec.INT.fieldOf("leg").forGetter(Trip::leg),
				Codec.LONG.fieldOf("began").forGetter(Trip::began))
			.apply(i, Trip::new));

		Trip on(int leg) {
			return new Trip(this.home, this.yaw, this.far, leg, this.began);
		}
	}

	public static final AttachmentType<Trip> TRIP = AttachmentRegistry.create(JaymosKingdoms.id("sky_ferry_trip"), builder -> builder.persistent(Trip.CODEC));

	/** Where each village's skiff has her wheel (by the village's start chunk), and when a village with none is looked at again. */
	private static final Map<Long, BlockPos> WHEELS = new HashMap<>();
	private static final Map<Long, Long> NONE_UNTIL = new HashMap<>();
	/** Each ferry: how long she has made no way (ticks), and how much higher than her cruise she has gone for it. */
	private static final Map<UUID, int[]> STUCK = new HashMap<>();

	private SkyFerries() {
	}

	static void register() {
		Ship.skyfaring = new SkyFerries();
	}

	// ---- casting off ---------------------------------------------------------------------------------------------------------

	/** One sky village (its bounds, its start chunk): by day, now and then, its moored skiff is taken out. */
	static void moored(ServerLevel level, BoundingBox box, long key) {
		long now = level.getGameTime();
		RandomSource random = level.getRandom();
		if (!level.isBrightOutside() || level.isThundering() || random.nextInt(CAST_OFF_IN) != 0 || NONE_UNTIL.getOrDefault(key, 0L) > now) {
			return;
		}
		// one out at a time
		for (Ship s : Ships.in(level)) {
			Trip t = s.getAttached(TRIP);
			if (t != null && box.inflatedBy(4).isInside(BlockPos.containing(t.home()))) {
				return;
			}
		}
		BlockPos wheel = wheel(level, box, key);
		if (wheel == null) {
			NONE_UNTIL.put(key, now + LOOK_AGAIN);
			return;
		}
		// her crew: the grown villagers nearest her who hold no office
		List<Villager> crew = SkyCourt.people(level, box);
		crew.removeIf(v -> v.isSleeping() || v.isPassenger() || v.entityTags().contains(SkyCourt.LORD_TAG) || v.entityTags().contains(SkyCourt.WARDEN_TAG));
		if (crew.isEmpty()) {
			return;
		}
		crew.sort(Comparator.comparingDouble(v -> v.distanceToSqr(Vec3.atCenterOf(wheel))));
		Ship ship = Fleet.castOff(level, wheel);
		WHEELS.remove(key);
		if (ship == null) {
			NONE_UNTIL.put(key, now + LOOK_AGAIN);
			return;
		}
		Fleet.Stations stations = Fleet.stations(ship);
		if (!ship.flying() || stations.helm() == null) {
			ship.moor();
			NONE_UNTIL.put(key, now + LOOK_AGAIN);
			return;
		}
		ship.takeAboard(crew.get(0), stations.helm());
		List<Vec3> deck = stations.deck();
		for (int i = 1; i < Math.min(CREW, crew.size()) && !deck.isEmpty(); i++) {
			ship.takeAboard(crew.get(i), deck.get(deck.size() * i / CREW));
		}
		double angle = random.nextDouble() * Math.PI * 2.0, far = FAR_MIN + random.nextInt(FAR_MAX - FAR_MIN + 1);
		BlockPos turn = BlockPos.containing(ship.getX() + Math.cos(angle) * far, ship.getY(), ship.getZ() + Math.sin(angle) * far);
		ship.setAttached(TRIP, new Trip(ship.position(), ship.getYRot(), turn, UP, now));
	}

	/** The wheel of the skiff moored in {@code box} (remembered once found), or null. */
	private static @Nullable BlockPos wheel(ServerLevel level, BoundingBox box, long key) {
		BlockPos known = WHEELS.get(key);
		if (known != null && level.getBlockState(known).getBlock() instanceof HelmBlock) {
			return known;
		}
		WHEELS.remove(key);
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (level.getBlockState(p).getBlock() instanceof HelmBlock) {
				BlockPos found = p.immutable();
				WHEELS.put(key, found);
				return found;
			}
		}
		return null;
	}

	// ---- flying her ----------------------------------------------------------------------------------------------------------

	@Override
	public @Nullable Input helm(ServerLevel level, Ship ship) {
		Trip trip = ship.getAttached(TRIP);
		if (trip == null) {
			return null;
		}
		List<LivingEntity> crew = ship.crewAboard();
		long out = level.getGameTime() - trip.began();
		if (crew.isEmpty() || out > LONGEST_TRIP * 2) {
			// nobody left to fly her (or she can't get home): she is let go, and moors herself where she lies
			end(ship);
			return null;
		}
		if (out > LONGEST_TRIP && trip.leg() < HOME) {
			trip = trip.on(HOME);
			ship.setAttached(TRIP, trip);
		}
		int[] stuck = STUCK.computeIfAbsent(ship.getUUID(), id -> new int[2]);
		double cruise = trip.home().y + CRUISE_UP + stuck[1];
		double hx = trip.home().x - ship.getX(), hz = trip.home().z - ship.getZ(), home = Math.sqrt(hx * hx + hz * hz);
		double want = cruise, cloth = 0.0;
		float yaw = ship.getYRot();
		switch (trip.leg()) {
			case UP -> {
				if (ship.getY() >= cruise - 1.0) {
					ship.setAttached(TRIP, trip.on(OUT));
				}
			}
			case OUT -> {
				double dx = trip.far().getX() + 0.5 - ship.getX(), dz = trip.far().getZ() + 0.5 - ship.getZ();
				if (dx * dx + dz * dz <= ARRIVE * ARRIVE) {
					ship.setAttached(TRIP, trip.on(HOME));
				}
				yaw = Fleet.bearing(ship, trip.far().getX() + 0.5, trip.far().getZ() + 0.5);
				cloth = 1.0;
			}
			case HOME -> {
				if (home <= OVER_HOME) {
					ship.setAttached(TRIP, trip.on(DOWN));
				}
				yaw = Fleet.bearing(ship, trip.home().x, trip.home().z);
				cloth = home < EASE_FROM ? EASE : 1.0;
			}
			default -> {
				// down onto her mooring, hauled in on the line, her head brought round to how she lay
				want = home < PLUMB ? trip.home().y : cruise;
				yaw = trip.yaw();
				if (home > 0.05) {
					double pull = Math.min(HAUL, home);
					ship.tow(new Vec3(hx / home * pull, 0.0, hz / home * pull));
				}
				if (Math.abs(ship.getY() - want) < 0.2 && home < 0.3 && Math.abs(ship.way()) < 0.02
					&& Math.abs(Mth.wrapDegrees(yaw - ship.getYRot())) < SQUARE) {
					end(ship);
					ship.moor();
					return Input.EMPTY;
				}
			}
		}
		// held up by something in her way: over it
		if (cloth > 0.5 && Math.abs(ship.way()) < 0.02 && ship.sailsSet() > 0.5) {
			if (++stuck[0] > STUCK_TICKS) {
				stuck[0] = 0;
				stuck[1] += OVER;
			}
		} else {
			stuck[0] = 0;
		}
		Input rudder = Fleet.steer(ship, yaw, cloth, false, false);
		boolean up = ship.aloft() < want - 0.15, down = ship.aloft() > want + 0.15;
		return new Input(rudder.forward(), rudder.backward(), rudder.left(), rudder.right(), up, false, down);
	}

	/** Her trip is over: she is nobody's ferry until she is taken out again. */
	private static void end(Ship ship) {
		ship.removeAttached(TRIP);
		STUCK.remove(ship.getUUID());
	}
}
