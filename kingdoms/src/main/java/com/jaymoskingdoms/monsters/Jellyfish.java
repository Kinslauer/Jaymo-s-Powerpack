package com.jaymoskingdoms.monsters;

import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The jellyfish (after Mo' Creatures' jellyfish, GPL-3, see {@code licenses/mocreatures}), twelve colours of it, drifting in
 * blooms in the warm seas.
 * <ul>
 * <li>Sympathy: it only drifts, never hunts; whoever brushes it is stung (a hurt, poison and a numb hand), and a sting
 * spends it: it cannot sting again for {@link #SPENT_TICKS}.</li>
 * <li>Feedback: a bloom is born of warm water and dies of cold. One that drifts into water colder than {@link #COLD} (a
 * temperate or cold sea) weakens and dies, so a bloom never spreads beyond the warm seas; and one that has stung is spent,
 * so a crowded bloom thins as it is waded through.</li>
 * <li>Stewardship: it harms nothing that leaves it alone. A swimmer who keeps their distance is never touched.</li>
 * </ul>
 */
public class Jellyfish extends AbstractFish {
	static final double HEALTH = 4.0, SPEED = 0.2;
	/** The sting: the hurt, the poison and the numbing of the hand (ticks), how long a jellyfish is spent after it (ticks). */
	static final float STING_HURT = 1.0F;
	static final int POISON_TICKS = 80, NUMB_TICKS = 100, SPENT_TICKS = 600;
	/** How often it looks for something touching it (ticks); the temperature under which a bloom dies. */
	static final int LOOK_EVERY = 5;
	static final float COLD = 0.6F;
	/** The colours it comes in. */
	public static final int COATS = 12;

	private static final EntityDataAccessor<Integer> COAT = SynchedEntityData.defineId(Jellyfish.class, EntityDataSerializers.INT);

	private long spentUntil;

	public Jellyfish(EntityType<? extends Jellyfish> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return AbstractFish.createAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(COAT, 0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(4, new RandomSwimmingGoal(this, 0.6, 80));
	}

	public int coat() {
		return this.entityData.get(COAT);
	}

	public void setCoat(int coat) {
		this.entityData.set(COAT, Math.floorMod(coat, COATS));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.tickCount % LOOK_EVERY != 0) {
			return;
		}
		if (this.tickCount % 100 == 0 && level.getBiome(this.blockPosition()).value().getBaseTemperature() < COLD) {
			this.hurtServer(level, level.damageSources().freeze(), 1.0F);
		}
		long now = level.getGameTime();
		if (now < this.spentUntil) {
			return;
		}
		for (Player swimmer : level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(0.2), p -> p.isAlive() && !p.isSpectator() && !p.isCreative())) {
			swimmer.hurtServer(level, level.damageSources().mobAttack(this), STING_HURT);
			swimmer.addEffect(new MobEffectInstance(MobEffects.POISON, POISON_TICKS, 0));
			swimmer.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, NUMB_TICKS, 0));
			this.spentUntil = now + SPENT_TICKS;
			level.sendParticles(ParticleTypes.GLOW, this.getX(), this.getY() + 0.3, this.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
			this.playSound(SoundEvents.PUFFER_FISH_STING, 0.6F, 1.6F);
			break;
		}
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, @Nullable SpawnGroupData groupData) {
		this.setCoat(this.random.nextInt(COATS));
		return super.finalizeSpawn(level, difficulty, reason, groupData);
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		return InteractionResult.PASS;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return !this.isPersistenceRequired() && !this.hasCustomName();
	}

	@Override
	public ItemStack getBucketItemStack() {
		return new ItemStack(Items.WATER_BUCKET);
	}

	@Override
	protected SoundEvent getFlopSound() {
		return SoundEvents.COD_FLOP;
	}

	@Override
	public void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("coat", this.coat());
	}

	@Override
	public void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.setCoat(input.getIntOr("coat", 0));
	}
}
