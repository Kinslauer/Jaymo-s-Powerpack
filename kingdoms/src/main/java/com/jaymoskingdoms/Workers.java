package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.21) A settlement's {@link Worker}s: who they are ({@link #near}), taking one on ({@link #body}), and their day.
 * With Minecraft Comes Alive a realm's workers are its own MCA villagers ({@link #inPlace}): one is promoted to the
 * job where they stand (the settlement's pick, or a player's through MCA's Professions menu), and keeps their name,
 * family and hearts. A realm's guards are MCA's guards and archers, counted on its roster and fighting its foes
 * ({@link McaCompat#foe}). By day their work takes the place of their brain ({@link #step}); at night, when they're
 * frightened or hurt, when someone's talking to them, or when there's nothing to do, they live as MCA's villagers do.
 */
public final class Workers {
	/** What an idle worker's task says: nothing in hand and nothing to do, so they live as villagers meanwhile. */
	static final Set<String> IDLE = Set.of("", "idle", "resting", "waiting for materials", "waiting for the mine", "the mine is worked out",
		"no trees to fell");
	/** How often a villager's job is squared with its profession (ticks). */
	static final int RECONCILE_TICKS = 20;
	/** How long after being hurt a worker runs instead of working (ticks). */
	static final int FLEE_TICKS = 100;

	private Workers() {
	}

	/**
	 * {@code role} is filled in place from {@code f}'s own MCA villagers: MCA's loaded, it isn't a camp, and it's a
	 * worker's job, or a realm's guard (made an MCA guard or archer; its archers on their posts stay soldiers).
	 */
	static boolean inPlace(Faction f, int role) {
		return McaCompat.LOADED && f.type() != FactionType.BARBARIAN && (Worker.isWork(role) || role == Folk.GUARD && f.type().isRealm());
	}

	/** A worker's day's work (a {@link Folk}'s from its own tick; an MCA villager's from {@link #step}). */
	static void work(ServerLevel level, Worker w) {
		if (Folk.isBuilder(w.role())) {
			Builders.tick(level, w);
		} else if (w.role() == Folk.FARMER) {
			Farmers.tick(level, w);
		} else if (Gatherers.isGatherer(w.role())) {
			Gatherers.tick(level, w);
		} else if (w.role() == Folk.PULLER) {
			Rickshaws.tick(level, w);
		} else if (Stables.isStableWorker(w.role())) {
			// (0.5.47)
			Stables.tick(level, w);
		}
	}

	/** The workers in {@code box} that pass {@code test}: folk, and (with MCA) villagers in a job. */
	static List<Worker> near(ServerLevel level, AABB box, Predicate<Worker> test) {
		List<Worker> found = new ArrayList<>(level.getEntitiesOfClass(Folk.class, box, test::test));
		if (McaCompat.LOADED) {
			found.addAll(McaCompat.workers(level, box, test));
		}
		return found;
	}

	/**
	 * A new {@code role} for {@code f}, keeping to {@code radius} of {@code home}: one of its villagers promoted (with
	 * MCA), else a new {@link Folk} at {@code at}. The caller counts them in the roster. Null if none could be had.
	 */
	static @Nullable Worker body(ServerLevel level, Faction f, int role, BlockPos at, BlockPos home, int radius) {
		if (inPlace(f, role)) {
			Worker w = McaCompat.enlist(level, f, role, at, home, radius);
			if (w != null) {
				return w;
			}
		}
		Folk folk = JaymosKingdoms.FOLK.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (folk == null) {
			return null;
		}
		folk.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		folk.setup(role, f, home, radius);
		level.addFreshEntity(folk);
		return folk;
	}

	/**
	 * A villager's server step (from {@code VillagerWorkMixin}, before its brain): true if it's at work, its work done
	 * in place of its brain this tick; false to let its brain run (not a worker, bedtime, frightened, hurt, talking to
	 * someone, or idle).
	 */
	public static boolean step(ServerLevel level, Worker w) {
		PathfinderMob mob = w.mob();
		if (mob.tickCount % RECONCILE_TICKS == 0 && !McaCompat.reconcile(level, w)) {
			return false;
		}
		if (!Worker.isWork(w.role()) || Homes.bedtime(level) && !Rickshaws.onDuty(mob) || mob.isSleeping() || mob.isBaby() || Frights.afraid(mob.getUUID())
			|| mob.getLastHurtByMob() != null && mob.tickCount - mob.getLastHurtByMobTimestamp() < FLEE_TICKS || McaCompat.busy(mob)) {
			return false;
		}
		if (!w.isCarrying() && !w.getMainHandItem().is(w.tool().getItem())) {
			// (back at work: their tool back in hand, whatever their own day had them holding)
			w.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
		}
		work(level, w);
		return w.isCarrying() || !IDLE.contains(w.task());
	}

	/** A worker died: its place is to be filled, and whoever killed it is remembered. */
	public static void died(ServerLevel level, Worker w, DamageSource source) {
		if (w.isCarrying()) {
			// whatever they were hauling falls where they do
			w.mob().spawnAtLocation(level, w.takeCarried());
		}
		KingdomEvents.workerDied(level, w, source);
	}
}
