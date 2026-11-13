package com.jaymoskingdoms.blossomland;

import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The raccoon dog: a shy scavenger of the village edge.
 * <ul>
 * <li><b>What it does.</b> It sleeps the day away, curled nose to tail, and comes out at night to eat what has been
 * left lying: any food on the ground, rotten flesh, bones. It is the one dog that sleeps the winter through. It
 * keeps clear of people.</li>
 * <li><b>What comes back.</b> Scraps left out overnight are gone by morning; where there is nothing left out it
 * moves on and is not seen.</li>
 * <li><b>Looked after.</b> One that has eaten {@link #TRUSTS_AFTER} times of what a place leaves out stops running
 * from people there for good, and can be watched at its work.</li>
 * </ul>
 */
public class RaccoonDog extends BlossomAnimal {
	static final double HEALTH = 8.0, SPEED = 0.26;
	static final double SCENT = 12.0, EAT_REACH = 1.4;
	static final int TRUSTS_AFTER = 5, WOKEN_TICKS = 300;
	static final long MEAL_TICKS = 6000L;

	private int meals;

	public RaccoonDog(EntityType<? extends RaccoonDog> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createAnimalAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED);
	}

	static boolean scrap(ItemStack stack) {
		return stack.has(DataComponents.FOOD) || stack.is(Items.BONE);
	}

	boolean trusts() {
		return this.meals >= TRUSTS_AFTER;
	}

	/** Asleep by day and all winter, unless something has just hurt it. */
	boolean asleep() {
		boolean woken = this.getLastHurtByMob() != null && this.tickCount - this.getLastHurtByMobTimestamp() < WOKEN_TICKS;
		return !woken && (this.level().isBrightOutside() || winter(this.level()));
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new PanicGoal(this, 1.6));
		this.goalSelector.addGoal(2, new Sleep());
		this.goalSelector.addGoal(3, this.fleePredators(10.0F, 1.4));
		this.goalSelector.addGoal(3, new AvoidEntityGoal<>(this, Player.class, 8.0F, 1.2, 1.6, e -> !this.trusts()));
		this.goalSelector.addGoal(4, new Scavenge());
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.FOX_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.FOX_DEATH;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("meals", this.meals);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.meals = input.getIntOr("meals", 0);
	}

	private final class Sleep extends Goal {
		Sleep() {
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			return RaccoonDog.this.asleep() && RaccoonDog.this.onGround();
		}

		@Override
		public void start() {
			RaccoonDog.this.getNavigation().stop();
			RaccoonDog.this.setLook(HEAD_DOWN);
		}

		@Override
		public void stop() {
			RaccoonDog.this.setLook(STANDING);
		}
	}

	/** Follows its nose to food lying on the ground and eats one piece of it. */
	private final class Scavenge extends Goal {
		private ItemEntity found;

		Scavenge() {
			this.setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			RaccoonDog me = RaccoonDog.this;
			if (me.fed() || (me.tickCount + me.getId()) % 40 != 0) {
				return false;
			}
			List<ItemEntity> near = me.level().getEntitiesOfClass(ItemEntity.class, me.getBoundingBox().inflate(SCENT, 4.0, SCENT), i -> i.isAlive() && scrap(i.getItem()));
			this.found = near.isEmpty() ? null : near.get(0);
			return this.found != null;
		}

		@Override
		public boolean canContinueToUse() {
			return this.found != null && this.found.isAlive() && !RaccoonDog.this.fed();
		}

		@Override
		public void tick() {
			RaccoonDog me = RaccoonDog.this;
			if (me.distanceToSqr(this.found) > EAT_REACH * EAT_REACH) {
				if (me.tickCount % 10 == 0) {
					me.getNavigation().moveTo(this.found, 1.1);
				}
				return;
			}
			ItemStack stack = this.found.getItem();
			stack.shrink(1);
			if (stack.isEmpty()) {
				this.found.discard();
			} else {
				this.found.setItem(stack);
			}
			me.meals++;
			if (me.level() instanceof ServerLevel level) {
				me.feed(level, MEAL_TICKS);
			}
		}

		@Override
		public void stop() {
			this.found = null;
		}
	}
}
