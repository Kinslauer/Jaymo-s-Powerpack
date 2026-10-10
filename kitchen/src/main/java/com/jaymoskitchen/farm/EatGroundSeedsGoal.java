package com.jaymoskitchen.farm;

import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import org.jspecify.annotations.Nullable;

/**
 * An animal that eats seeds (a chicken, a parrot) wanders over to seeds lying on the ground and pecks them up. Now
 * and then that puts it in the mood to breed, as hand-feeding would.
 */
public class EatGroundSeedsGoal extends Goal {
	private static final double SEARCH = 8.0, REACH_SQ = 1.8 * 1.8;

	private final Animal animal;
	private @Nullable ItemEntity target;
	private int giveUp;

	public EatGroundSeedsGoal(Animal animal) {
		this.animal = animal;
		setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (animal.isVehicle() || animal.isPassenger() || animal.getRandom().nextInt(10) != 0) {
			return false;
		}
		List<ItemEntity> seeds = animal.level().getEntitiesOfClass(ItemEntity.class, animal.getBoundingBox().inflate(SEARCH),
			item -> item.isAlive() && animal.isFood(item.getItem()));
		target = null;
		double best = Double.MAX_VALUE;
		for (ItemEntity item : seeds) {
			double distance = item.distanceToSqr(animal);
			if (distance < best) {
				best = distance;
				target = item;
			}
		}
		return target != null;
	}

	@Override
	public void start() {
		giveUp = 200;
		if (target != null) {
			animal.getNavigation().moveTo(target, 1.1);
		}
	}

	@Override
	public boolean canContinueToUse() {
		return target != null && target.isAlive() && giveUp > 0;
	}

	@Override
	public void tick() {
		giveUp--;
		if (target == null) {
			return;
		}
		animal.getLookControl().setLookAt(target, 30.0F, 30.0F);
		if (animal.distanceToSqr(target) > REACH_SQ) {
			if (animal.getNavigation().isDone()) {
				animal.getNavigation().moveTo(target, 1.1);
			}
			return;
		}
		// peck
		if (animal.level() instanceof ServerLevel level) {
			level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, target.getItem().getItem()),
				target.getX(), target.getY() + 0.1, target.getZ(), 4, 0.1, 0.05, 0.1, 0.05);
			animal.playSound(SoundEvents.GENERIC_EAT.value(), 0.5F, 1.4F);
			animal.heal(1.0F);
			if (animal.getAge() == 0 && animal.canFallInLove() && animal.getRandom().nextInt(4) == 0) {
				animal.setInLove(null);
			}
		}
		target.getItem().shrink(1);
		if (target.getItem().isEmpty()) {
			target.discard();
		}
		target = null;
	}

	@Override
	public void stop() {
		target = null;
		animal.getNavigation().stop();
	}
}
