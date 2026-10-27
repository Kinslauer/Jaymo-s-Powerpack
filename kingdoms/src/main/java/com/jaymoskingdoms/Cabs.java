package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.26) A rich realm's hired carriage ({@link Wagon#CAB}): a driver in the saddle of a horse that draws a plain
 * carriage in the realm's colour. While its treasury holds more than {@link #RICH} emeralds a realm keeps one waiting
 * beside its rickshaw rank, and it, not the rickshaws, takes players to other settlements, for the same fares and faster.
 * Its pullers on foot ({@link Rickshaws}) see to everything else. When the treasury runs down the cab is let go (once it's
 * back and empty).
 */
public final class Cabs {
	/** A realm keeps a cab while its treasury holds more than this. */
	static final long RICH = 300L;
	/** How often realms are looked over (ticks), and how far from its seat its cab is looked for. */
	static final int CHECK_TICKS = 100;
	static final double RANGE = 160.0;
	/** The driver's pace with a fare, and back. */
	static final double FARE_SPEED = 1.3, BACK_SPEED = 1.1;
	static final double ARRIVE = 7.0, STAND_REACH = 3.0;
	/** No nearer for this long (ticks): a hop ahead of this many blocks. */
	static final int STUCK_TICKS = 100, HOP = 10;
	/** A ride given up after this long (ticks). */
	static final long TRIP_MOST = 24000L / 2;
	/** Its driver's task (and how he's known). */
	static final String TASK = "cab";

	private static final int WAIT = 0, HIRED = 1, RETURN = 2;

	private static final class Job {
		int phase = WAIT;
		@Nullable BlockPos goal;
		final Gatherers.Trip trip = new Gatherers.Trip();
		long startedAt;
	}

	private static final Map<UUID, Job> JOBS = new HashMap<>();

	private Cabs() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			ServerLevel level = Factions.level(server);
			if (level.getGameTime() % CHECK_TICKS == 0) {
				upkeep(server, level);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> JOBS.clear());
	}

	/** Where its cab waits: across the bell from the rickshaws. */
	static BlockPos stand(ServerLevel level, Faction f) {
		return Construction.standable(level, f.seat().offset(-4, 0, 3), 5);
	}

	/** {@code f}'s cab, if it's about. */
	static @Nullable Wagon of(ServerLevel level, Faction f) {
		List<Wagon> found = level.getEntitiesOfClass(Wagon.class, new AABB(f.seat()).inflate(RANGE),
			w -> w.isAlive() && w.kind() == Wagon.CAB && w.faction() == f.id());
		return found.isEmpty() ? null : found.getFirst();
	}

	static boolean rich(Faction f) {
		return f.type().isRealm() && !f.fallen() && f.treasury() > RICH;
	}

	/** Each realm rich enough gets its cab; one that's grown poor lets its idle cab go. Drivers out of the saddle get back on. */
	private static void upkeep(MinecraftServer server, ServerLevel level) {
		for (Faction f : Factions.all(server)) {
			if (!f.type().isRealm() || f.fallen() || !level.isPositionEntityTicking(f.seat())) {
				continue;
			}
			Wagon cab = of(level, f);
			if (cab == null) {
				if (rich(f) && f.holdings().depot().isPresent() && build(level, f) != null) {
					JaymosKingdoms.LOGGER.debug("{} keeps a cab now", f.name());
				}
				continue;
			}
			Entity horse = cab.horse();
			Folk driver = driver(cab);
			if (!rich(f) && !cab.isVehicle() && cab.ride() < 0) {
				dismiss(level, cab);
				continue;
			}
			if (horse != null && driver == null) {
				for (Folk lost : level.getEntitiesOfClass(Folk.class, new AABB(f.seat()).inflate(RANGE),
					x -> x.isAlive() && x.factionId() == f.id() && TASK.equals(x.task()) && !x.isPassenger())) {
					lost.teleportTo(horse.getX(), horse.getY(), horse.getZ());
					lost.startRiding(horse);
					break;
				}
			}
		}
	}

	/** A driver, his horse and the cab, at its stand. */
	static @Nullable Wagon build(ServerLevel level, Faction f) {
		BlockPos at = stand(level, f);
		Folk driver = JaymosKingdoms.FOLK.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (driver == null) {
			return null;
		}
		driver.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		driver.setup(Caravans.CARTER, f, at, 4);
		driver.setTask(TASK);
		driver.setCustomName(Component.translatable("entity.jaymoskingdoms.cab_driver_of", f.name()));
		level.addFreshEntity(driver);
		AbstractHorse horse = Cavalry.mount(level, driver, f, false);
		if (horse == null) {
			driver.discard();
			return null;
		}
		return Wagon.hitch(level, Wagon.CAB, f, horse, -1, 0);
	}

	/** The cab, its horse and driver go home: gone in a puff. */
	private static void dismiss(ServerLevel level, Wagon cab) {
		Entity horse = cab.horse();
		Folk driver = driver(cab);
		for (Entity e : new Entity[] {driver, horse, cab}) {
			if (e != null) {
				level.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 1.0, e.getZ(), 10, 0.4, 0.5, 0.4, 0.02);
				e.discard();
			}
		}
		JOBS.remove(cab.getUUID());
	}

	/** Its driver (in its horse's saddle), or null. */
	static @Nullable Folk driver(Wagon cab) {
		Entity horse = cab.horse();
		return horse != null && horse.getFirstPassenger() instanceof Folk f ? f : null;
	}

	// ---- its day ----------------------------------------------------------------------------------------------------

	/** One cab's tick (from {@link Wagon#tick}). */
	static void tick(ServerLevel level, Wagon cab, @Nullable Entity horse) {
		Faction f = Factions.get(level.getServer(), cab.faction());
		if (f == null || f.fallen()) {
			return;
		}
		if (horse == null || !horse.isAlive()) {
			// (its horse is gone: in a while, so is the cab)
			if (cab.horseLost() > Wagon.LOST_HORSE_TICKS * 6) {
				cab.ejectPassengers();
				cab.discard();
			}
			return;
		}
		Folk driver = driver(cab);
		if (driver == null) {
			return;
		}
		Job job = JOBS.computeIfAbsent(cab.getUUID(), u -> new Job());
		long now = level.getGameTime();
		Entity aboard = cab.getFirstPassenger();
		if (job.phase == WAIT && aboard instanceof ServerPlayer && cab.ride() >= 0) {
			// (back from a restart with a fare aboard)
			Faction to = Factions.get(level.getServer(), cab.ride());
			if (to != null) {
				start(job, HIRED, Construction.standable(level, to.seat(), 6), now);
			}
		}
		if (job.phase != WAIT && now - job.startedAt > TRIP_MOST) {
			cab.ejectPassengers();
			cab.setRide(-1);
			start(job, RETURN, null, now);
		}
		switch (job.phase) {
			case HIRED -> {
				Faction to = Factions.get(level.getServer(), cab.ride());
				if (to == null || job.goal == null || !(aboard instanceof ServerPlayer player)) {
					cab.setRide(-1);
					start(job, RETURN, null, now);
					return;
				}
				if (!Journeys.travelling(player)) {
					// (back from a restart with them aboard: on the road again)
					Journeys.resume(level, player, cab, Component.literal(to.name()), job.goal, RIDE);
				} else if (Journeys.underway(cab)) {
					// (0.7.266) on the road screen: the cab waits here till the trip's done, then it's moved to the far end
					driver.getNavigation().stop();
					job.trip.reset(now);
				} else {
					// (setting off, seen)
					drive(level, driver, horse, cab, job, job.goal, ARRIVE, FARE_SPEED, now);
				}
			}
			case RETURN -> {
				BlockPos stand = stand(level, f);
				if (homeUnseen(level, horse, cab, stand) || drive(level, driver, horse, cab, job, stand, STAND_REACH, BACK_SPEED, now)) {
					start(job, WAIT, null, now);
				}
			}
			default -> {
				if (now % 40 == 0 && horse.distanceToSqr(Vec3.atBottomCenterOf(stand(level, f))) > 36.0) {
					start(job, RETURN, null, now);
				}
			}
		}
	}

	private static void start(Job job, int phase, @Nullable BlockPos goal, long now) {
		job.phase = phase;
		job.goal = goal;
		job.trip.reset(now);
		job.startedAt = now;
	}

	/** Far from its stand with nobody to see it: back there, cab and all. */
	private static boolean homeUnseen(ServerLevel level, Entity horse, Wagon cab, BlockPos stand) {
		if (horse.blockPosition().closerThan(stand, 96.0)
			|| !level.getEntitiesOfClass(ServerPlayer.class, horse.getBoundingBox().inflate(64.0), p -> !p.isSpectator()).isEmpty()) {
			return false;
		}
		horse.teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
		cab.trail(horse);
		return true;
	}

	/** Drives toward {@code to} a leg at a time; true once within {@code reach}. Stuck, the cab hops ahead {@link #HOP} blocks. */
	private static boolean drive(ServerLevel level, Folk driver, Entity horse, Wagon cab, Job job, BlockPos to, double reach, double speed, long now) {
		Vec3 goal = Vec3.atBottomCenterOf(to);
		double dx = horse.getX() - goal.x, dz = horse.getZ() - goal.z;
		double across = Math.sqrt(dx * dx + dz * dz);
		if (across <= reach && Math.abs(horse.getY() - goal.y) <= Gatherers.VERTICAL_SLACK) {
			driver.getNavigation().stop();
			return true;
		}
		double d = Math.sqrt(horse.distanceToSqr(goal));
		if (d < job.trip.best - 0.5) {
			job.trip.best = d;
			job.trip.improvedAt = now;
		}
		if (now - job.trip.improvedAt > STUCK_TICKS) {
			double step = Math.min(HOP, across);
			BlockPos ahead = BlockPos.containing(horse.getX() - dx / across * step, horse.getY(), horse.getZ() - dz / across * step);
			BlockPos at = Construction.standable(level, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ahead), 3);
			level.sendParticles(ParticleTypes.POOF, horse.getX(), horse.getY() + 1.0, horse.getZ(), 12, 0.4, 0.5, 0.4, 0.02);
			driver.getNavigation().stop();
			horse.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			cab.trail(horse);
			level.sendParticles(ParticleTypes.POOF, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 12, 0.4, 0.5, 0.4, 0.02);
			job.trip.reset(now);
			return false;
		}
		if (driver.getNavigation().isDone() || now % 20 == 0) {
			BlockPos leg = Construction.leg(level, horse, to);
			driver.getNavigation().moveTo(leg.getX() + 0.5, leg.getY(), leg.getZ() + 0.5, speed);
		}
		return false;
	}

	// ---- players ----------------------------------------------------------------------------------------------------

	private static boolean free(Wagon cab) {
		Job job = JOBS.get(cab.getUUID());
		return !cab.isVehicle() && cab.ride() < 0 && (job == null || job.phase != HIRED);
	}

	/** A player right-clicked the cab: where it goes and the fares, or why not. */
	static void hail(ServerLevel level, Wagon cab, ServerPlayer player) {
		Faction f = Factions.get(level.getServer(), cab.faction());
		Folk driver = driver(cab);
		if (f == null || f.fallen() || driver == null) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.cab.no_driver").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (Reputation.get(player, f.id()) <= Reputation.HOSTILE) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.rickshaw.refused", driver.getName()).withStyle(ChatFormatting.RED));
			return;
		}
		if (!free(cab)) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.rickshaw.busy", driver.getName()).withStyle(ChatFormatting.GRAY));
			return;
		}
		Rickshaws.offer(level, f, cab, player, driver);
	}

	/** {@code /kingdom ride} on a cab: the fare paid, the player aboard and away. */
	static int hire(ServerLevel level, ServerPlayer player, Wagon cab, int destination) {
		MinecraftServer server = level.getServer();
		Faction f = Factions.get(server, cab.faction());
		Faction to = Factions.get(server, destination);
		Folk driver = driver(cab);
		if (f == null || to == null || driver == null
			|| Rickshaws.destinations(level, f, player, cab.position()).stream().noneMatch(d -> d.id() == destination)) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.cab.no_driver").withStyle(ChatFormatting.RED));
			return 0;
		}
		if (!free(cab) || player.isPassenger()) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.rickshaw.busy", driver.getName()).withStyle(ChatFormatting.RED));
			return 0;
		}
		int fare = Rickshaws.fare(cab.position(), to);
		if (!player.isCreative() && player.getInventory().countItem(net.minecraft.world.item.Items.EMERALD) < fare) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.rickshaw.cant_pay", fare).withStyle(ChatFormatting.RED));
			return 0;
		}
		if (!player.startRiding(cab)) {
			return 0;
		}
		Rickshaws.pay(player, f, fare);
		cab.setRide(to.id());
		Job job = JOBS.computeIfAbsent(cab.getUUID(), u -> new Job());
		start(job, HIRED, Construction.standable(level, to.seat(), 6), level.getGameTime());
		level.playSound(null, cab.blockPosition(), SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.NEUTRAL, 0.8F, 1.1F);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.cab.off", driver.getName(), to.name(), fare).withStyle(ChatFormatting.GOLD));
		Journeys.begin(level, player, cab, Component.literal(to.name()), job.goal, Journeys.blocks(cab.position(), job.goal), fare, f.id(), RIDE);
		return 1;
	}

	/** (0.7.266) A player's ride, at its end or turned back: the cab's free again and drives home. */
	private static final Journeys.Ride RIDE = new Journeys.Ride() {
		@Override
		public void arrived(ServerLevel level, ServerPlayer player, Wagon cab) {
			Faction to = Factions.get(level.getServer(), cab.ride());
			cab.setRide(-1);
			if (to != null) {
				player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.rickshaw.arrived", to.name()).withStyle(ChatFormatting.GOLD));
			}
			level.playSound(null, cab.blockPosition(), SoundEvents.HORSE_AMBIENT, SoundSource.NEUTRAL, 0.8F, 1.0F);
			homeward(level, cab);
		}

		@Override
		public void turnedBack(ServerLevel level, ServerPlayer player, Wagon cab) {
			cab.setRide(-1);
			homeward(level, cab);
		}

		private void homeward(ServerLevel level, Wagon cab) {
			Job job = JOBS.get(cab.getUUID());
			if (job != null) {
				start(job, RETURN, null, level.getGameTime());
			}
		}
	};
}
