package com.jaymoskingdoms;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.PathfinderMob;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.294) Andy, retiring the folk: "all mca please." Whoever drives a realm's horses (a cab's driver, a king's
 * coachman, a milestone coach's coachman: {@link Caravans#CARTER}s on a task of their own) is, with MCA, one of its
 * people: a man in the saddle ({@link #hire}, {@code McaBridge.driver}), his brain set aside while he drives
 * ({@link #step}). Drivers still folk from before take their seat on a villager ({@link #recast}).
 */
final class Drivers {
	private Drivers() {
	}

	/**
	 * A driver for {@code f} at {@code at} facing {@code yaw}, on {@code task}, kept to {@code radius} of it: with MCA one
	 * of its villagers, else a folk carter named {@code folkName} (a translation key taking the realm's name).
	 */
	static @Nullable Worker hire(ServerLevel level, Faction f, BlockPos at, float yaw, String task, int radius, @Nullable String folkName) {
		if (McaCompat.LOADED && f.type().isRealm()) {
			Worker w = McaCompat.driver(level, f, at, yaw, task, radius);
			if (w != null) {
				return w;
			}
		}
		Folk driver = JaymosKingdoms.FOLK.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (driver == null) {
			return null;
		}
		driver.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0.0F);
		driver.setup(Caravans.CARTER, f, at, radius);
		driver.setTask(task);
		if (folkName != null) {
			driver.setCustomName(Component.translatable(folkName, f.name()));
		}
		level.addFreshEntity(driver);
		return driver;
	}

	/** The driver in {@code horse}'s saddle (a folk's or a villager's), or null. */
	static @Nullable Worker of(@Nullable Entity horse) {
		return horse != null && horse.getFirstPassenger() instanceof Worker w && w.role() == Caravans.CARTER ? w : null;
	}

	/** {@code w} is one of the drivers here, on {@code task}. */
	static boolean on(Worker w, String task) {
		return w.role() == Caravans.CARTER && task.equals(w.task());
	}

	/** A driver of {@code f}'s on {@code task} still a folk: an MCA villager takes his seat. True if so (the folk is gone). */
	static boolean recast(ServerLevel level, Faction f, Worker w, String task, int radius) {
		if (!(w.mob() instanceof Folk folk) || !McaCompat.LOADED || !f.type().isRealm()) {
			return false;
		}
		Entity horse = folk.getVehicle();
		Worker man = McaCompat.driver(level, f, folk.blockPosition(), folk.getYRot(), task, radius);
		if (man == null) {
			return false;
		}
		man.mob().setHomeTo(folk.getHomePosition(), (int)folk.getHomeRadius());
		folk.stopRiding();
		level.sendParticles(ParticleTypes.POOF, folk.getX(), folk.getY() + 1.0, folk.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
		folk.discard();
		if (horse != null) {
			man.mob().snapTo(horse.getX(), horse.getY(), horse.getZ(), horse.getYRot(), 0.0F);
			man.mob().startRiding(horse);
		}
		return true;
	}

	/** A villager driver's step ({@code Workers.step}): true (his brain set aside) while he's in the saddle. */
	static boolean step(ServerLevel level, Worker w) {
		PathfinderMob mob = w.mob();
		return !(mob instanceof Folk) && !w.task().isEmpty() && mob.isPassenger();
	}
}
