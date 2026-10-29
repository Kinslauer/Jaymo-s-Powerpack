package com.jaymoskingdoms;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.341) Andy (2026-10-29): MCA hunter villagers follow blood trails. A town's hunters (fletchers, who work the
 * hunting lodge, and leatherworkers) notice a wounded beast's blood near them (Jaymo's Powers' lasting stains,
 * {@code core.blood.BloodTrail}, read by reflection) and walk it stain by stain to the animal, then finish it with
 * their knife. They keep to {@link #ROAM} of where they set out, give up after {@link #GIVE_UP}, and leave off for
 * bedtime, a fright, a blow, or someone talking to them. Only game: wild animals, never anyone's pet or a person.
 */
final class BloodHunters {
	/** How far a hunter notices blood (blocks), how far along it looks for the next stain, and how far from where it set out it goes. */
	static final double SCENT = 20.0, STEP = 12.0, ROAM = 48.0;
	/** It closes in on the beast once it sees it this close (blocks), and strikes within this reach. */
	static final double SIGHT = 14.0, REACH = 2.5;
	/** Its knife's blow, and the ticks between blows. */
	static final float STAB = 4.0F;
	static final int STAB_TICKS = 20;
	/** It looks for blood this often (ticks), and gives a trail up after this long. */
	static final int SNIFF = 60, GIVE_UP = 1800;
	static final double SPEED = 0.7, CHASE_SPEED = 0.9;

	/** One hunter on a trail. */
	private static final class Hunt {
		final UUID quarry;
		final Vec3 from;
		Vec3 next;
		long laid;
		int left = GIVE_UP;
		int nextStab;

		Hunt(UUID quarry, Vec3 from, Vec3 next, long laid) {
			this.quarry = quarry;
			this.from = from;
			this.next = next;
			this.laid = laid;
		}
	}

	private static final Map<PathfinderMob, Hunt> HUNTS = new WeakHashMap<>();
	private static @Nullable MethodHandle follow;
	private static boolean looked;

	private BloodHunters() {
	}

	/** (from {@link Workers#step}) True while {@code w} is out on a blood trail (its brain waits). */
	static boolean step(ServerLevel level, Worker w) {
		PathfinderMob mob = w.mob();
		Hunt hunt = HUNTS.get(mob);
		if (!(mob instanceof Villager villager) || !hunter(villager) || mob.isBaby() || mob.isSleeping() || Homes.bedtime(level)
			|| Frights.afraid(mob.getUUID()) || mob.getLastHurtByMob() != null && mob.tickCount - mob.getLastHurtByMobTimestamp() < Workers.FLEE_TICKS
			|| McaCompat.busy(mob) || Downed.helping(mob)) {
			if (hunt != null) {
				stop(mob);
			}
			return false;
		}
		if (hunt == null) {
			if ((mob.tickCount + mob.getId()) % SNIFF != 0) {
				return false;
			}
			Object[] found = follow(level, mob.position(), SCENT, null, 0L);
			if (found == null) {
				return false;
			}
			hunt = new Hunt((UUID) found[1], mob.position(), (Vec3) found[0], (Long) found[2]);
			HUNTS.put(mob, hunt);
			walk(mob, hunt.next, SPEED);
		}
		if (--hunt.left <= 0 || mob.position().distanceTo(hunt.from) > ROAM) {
			stop(mob);
			return false;
		}
		Entity quarry = level.getEntity(hunt.quarry);
		if (!(quarry instanceof LivingEntity prey) || !game(prey)) {
			stop(mob);
			return false;
		}
		if (mob.distanceToSqr(prey) < SIGHT * SIGHT && mob.hasLineOfSight(prey)) {
			mob.getLookControl().setLookAt(prey);
			if (mob.distanceToSqr(prey) <= REACH * REACH) {
				mob.getNavigation().stop();
				if (mob.tickCount >= hunt.nextStab) {
					hunt.nextStab = mob.tickCount + STAB_TICKS;
					mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
					prey.hurtServer(level, mob.damageSources().mobAttack(mob), STAB);
				}
			} else if (mob.tickCount % 10 == 0) {
				mob.getNavigation().moveTo(prey, CHASE_SPEED);
			}
			return true;
		}
		boolean there = mob.position().distanceToSqr(hunt.next) < 2.25;
		if (!there && !mob.getNavigation().isDone()) {
			return true;
		}
		Object[] fresher = follow(level, hunt.next, STEP, hunt.quarry, hunt.laid);
		if (fresher != null) {
			hunt.next = (Vec3) fresher[0];
			hunt.laid = (Long) fresher[2];
			walk(mob, hunt.next, SPEED);
		} else if (mob.distanceToSqr(prey) < 24.0 * 24.0) {
			// the trail ends here and it can't be far: head where the blood leads
			mob.getNavigation().moveTo(prey, SPEED);
		} else {
			stop(mob);
			return false;
		}
		return true;
	}

	/** A hunter by trade: the hunting lodge's fletcher, or a leatherworker. */
	private static boolean hunter(Villager villager) {
		var trade = villager.getVillagerData().profession();
		return trade.is(VillagerProfession.FLETCHER) || trade.is(VillagerProfession.LEATHERWORKER);
	}

	/** Game to hunt: a wild grown animal, nobody's pet. */
	private static boolean game(LivingEntity prey) {
		return prey.isAlive() && prey instanceof Animal animal && !animal.isBaby() && !(animal instanceof TamableAnimal tame && tame.isTame());
	}

	private static void walk(PathfinderMob mob, Vec3 at, double speed) {
		mob.getNavigation().moveTo(at.x, at.y, at.z, speed);
	}

	private static void stop(PathfinderMob mob) {
		HUNTS.remove(mob);
		mob.getNavigation().stop();
	}

	/** Powers' {@code BloodTrail.follow}, or null without Powers (or before 0.17.182). */
	private static Object @Nullable [] follow(ServerLevel level, Vec3 from, double range, @Nullable UUID by, long after) {
		if (!looked) {
			looked = true;
			if (FabricLoader.getInstance().isModLoaded("jaymospowers")) {
				try {
					Class<?> trail = Class.forName("com.jaymospowers.core.blood.BloodTrail");
					follow = MethodHandles.publicLookup().findStatic(trail, "follow", MethodType.methodType(Object[].class, ServerLevel.class,
						Vec3.class, double.class, UUID.class, long.class, Predicate.class));
				} catch (ReflectiveOperationException e) {
					JaymosKingdoms.LOGGER.warn("Jaymo's Powers has no blood trails to hunt by", e);
				}
			}
		}
		if (follow == null) {
			return null;
		}
		try {
			Predicate<LivingEntity> prey = BloodHunters::game;
			return (Object[]) follow.invoke(level, from, range, by, after, prey);
		} catch (Throwable e) {
			follow = null;
			JaymosKingdoms.LOGGER.warn("Couldn't follow Jaymo's Powers' blood trails", e);
			return null;
		}
	}
}
