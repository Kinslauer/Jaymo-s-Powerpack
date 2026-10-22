package com.jaymospowers.aether.mob;

import com.jaymospowers.aether.AetherFlora;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The prismallard, a small bright water bird of the highfields' lakes. It floats, flaps, falls slowly, breeds on arilum
 * bulbs and lays an egg every 5 to 10 minutes: the egg is a throwable item (a thrown one sometimes hatches a chick, see
 * {@link ThrownPrismallardEgg}).
 */
public class Prismallard extends Animal {
	// ---- tuning ----
	public static final double HEALTH = 4.0, SPEED = 0.25;
	/** It lays an egg this often (ticks): 6000 to 11999, adults only. */
	public static final int EGG_MIN = 6000, EGG_SPREAD = 6000;
	/** A falling one's drop is cut to this share of its speed. */
	public static final double FALL_SHARE = 0.6;

	/** Wing flap: the phase and how hard (0 at rest to 1 flapping), with last tick's for smoothing. */
	public float flap, oFlap, flapSpeed, oFlapSpeed;

	private int eggTimer;

	public Prismallard(EntityType<? extends Prismallard> type, Level level) {
		super(type, level);
		this.eggTimer = EGG_MIN + this.random.nextInt(EGG_SPREAD);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createAnimalAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new PanicGoal(this, 1.4));
		this.goalSelector.addGoal(2, new BreedGoal(this, 1.0));
		this.goalSelector.addGoal(3, new TemptGoal(this, 1.0, stack -> stack.is(AetherFlora.ARILUM_BULB), false));
		this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.1));
		this.goalSelector.addGoal(5, new RandomStrollGoal(this, 1.0));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
	}

	@Override
	public void aiStep() {
		super.aiStep();
		Vec3 motion = this.getDeltaMovement();
		if (!this.onGround() && !this.isInWater() && motion.y < 0.0) {
			this.setDeltaMovement(motion.x, motion.y * FALL_SHARE, motion.z);
		}
		this.oFlap = this.flap;
		this.oFlapSpeed = this.flapSpeed;
		boolean flying = !this.onGround() && !this.isInWater() || this.getDeltaMovement().horizontalDistanceSqr() > 0.01;
		this.flapSpeed += ((flying ? 1.0F : 0.0F) - this.flapSpeed) * 0.3F;
		this.flap += this.flapSpeed * 1.2F;
		if (this.level() instanceof ServerLevel level && this.isAlive() && !this.isBaby() && --this.eggTimer <= 0) {
			this.eggTimer = EGG_MIN + this.random.nextInt(EGG_SPREAD);
			this.playSound(SoundEvents.CHICKEN_EGG, 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
			this.spawnAtLocation(level, new ItemStack(AetherAnimals.PRISMALLARD_EGG));
		}
	}

	@Override
	public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return stack.is(AetherFlora.ARILUM_BULB);
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return AetherAnimals.PRISMALLARD.create(level, EntitySpawnReason.BREEDING);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("egg_timer", this.eggTimer);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.eggTimer = input.getIntOr("egg_timer", EGG_MIN);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return MobSounds.get("entity.chicken.ambient");
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return MobSounds.get("entity.chicken.hurt");
	}

	@Override
	protected SoundEvent getDeathSound() {
		return MobSounds.get("entity.chicken.death");
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.CHICKEN_STEP.value(), 0.15F, 1.2F);
	}
}
