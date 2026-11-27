package com.jaymoskingdoms.monsters;

import com.jaymoskingdoms.wild.FedPredators;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The Nether scorpion (after Mo' Creatures' scorpion, GPL-3, see {@code licenses/mocreatures}; the Nether one only, the
 * cave and frost kinds were left out: Naturalist's desert and jungle scorpions are another mod's and aren't ours to
 * re-skin). It lives on the dry floors of the Nether wastes and the basalt deltas, hunts the striders and the young
 * hoglins that stray onto them, and stings with a poison.
 * <p>
 * The three rules for living things: <b>sympathy</b> (a mother carries her brood on her back, and when she is hurt or
 * killed the young scatter across the ground; they grow up if they live), <b>feedback</b> (it is in the
 * {@code sated_predators} tag, so a scorpion that has eaten leaves other prey, and people, alone for two days; a
 * scorpion that has fed doesn't empty a stretch of the Nether of its striders), <b>stewardship</b> (it never goes for a
 * player who hasn't come within a few blocks of it or its brood; a player who feeds one raw meat is a friend of it from
 * then on, and it turns on what hunts them).
 */
public class Scorpion extends Monster {
	// ---- body ----
	static final double HEALTH = 22.0, SPEED = 0.28, DAMAGE = 4.0, ARMOR = 4.0;
	/** A hatchling: its health, bite and size (against a grown one). */
	static final double BABY_HEALTH = 4.0, BABY_DAMAGE = 1.0, BABY_SCALE = 0.4;
	/** How long the sting's poison lasts (ticks), more on harder levels. */
	static final int POISON_TICKS = 100, POISON_MORE_ON_HARD = 60;
	/** How long its tail stays raised after a sting (ticks). */
	static final int TAIL_TICKS = 12;

	// ---- the brood ----
	/** The share of grown scorpions that come into the world carrying their young, how many there are (the model carries five), and how fast they run off. */
	static final float BROOD_CHANCE = 0.4F;
	static final int BROOD_SIZE = 5;
	static final double SCATTER_SPEED = 0.35, SCATTER_LIFT = 0.3;
	/** A hatchling grows up after this long (ticks: twenty minutes). */
	static final int GROW_TICKS = 24000;

	// ---- temper ----
	/** A scorpion goes for a player this near (blocks): her brood makes her wary from further off. */
	static final double WARN_RANGE = 4.0, BROOD_WARN_RANGE = 8.0;
	/** A meal of raw meat feeds it this long (ticks); how many friends it keeps. */
	static final int FEED_TICKS = 12000, MAX_FRIENDS = 8;

	private static final EntityDataAccessor<Boolean> BROOD = SynchedEntityData.defineId(Scorpion.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> BABY = SynchedEntityData.defineId(Scorpion.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> STING = SynchedEntityData.defineId(Scorpion.class, EntityDataSerializers.BOOLEAN);

	private int tailTicks, growth;
	private long fedUntil;
	private final List<UUID> friends = new ArrayList<>();

	public Scorpion(EntityType<? extends Scorpion> type, Level level) {
		super(type, level);
		this.xpReward = 8;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.ATTACK_DAMAGE, DAMAGE)
			.add(Attributes.ARMOR, ARMOR).add(Attributes.FOLLOW_RANGE, 16.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Player.class, 8.0F, 1.1, 1.5) {
			@Override
			public boolean canUse() {
				return Scorpion.this.isBaby() && super.canUse();
			}
		});
		this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.1, true) {
			@Override
			public boolean canUse() {
				return !Scorpion.this.isBaby() && super.canUse();
			}
		});
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this) {
			@Override
			public boolean canUse() {
				return !Scorpion.this.isBaby() && super.canUse();
			}
		});
		// a player who comes too near it or its brood, unless it is fed or they are its friend
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true,
			(target, level) -> level.getDifficulty() != Difficulty.PEACEFUL && this.wary(target)));
		// what a hungry one hunts: striders and young hoglins (the fed-predator rule keeps a fed one off them)
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Animal.class, 10, true, false,
			(target, level) -> !this.isBaby() && (target instanceof Strider || target instanceof Hoglin && target.isBaby())));
		// a friend's foes
		this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Mob.class, 5, true, false,
			(target, level) -> !this.isBaby() && !(target instanceof Scorpion) && target instanceof Monster m && m.getTarget() instanceof Player friend
				&& this.friends.contains(friend.getUUID())));
	}

	/** Whether it would go for {@code player}: it is grown, not fed, they aren't its friend, and they are near enough to trouble it. */
	private boolean wary(LivingEntity player) {
		if (this.isBaby() || this.fed() || !(player instanceof Player) || this.friends.contains(player.getUUID())) {
			return false;
		}
		double range = this.hasBrood() ? BROOD_WARN_RANGE : WARN_RANGE;
		return this.distanceToSqr(player) < range * range;
	}

	/** Whether it has eaten lately (its own meal, or a kill: {@link FedPredators}). */
	public boolean fed() {
		return this.level().getGameTime() < this.fedUntil || FedPredators.fed(this);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(BROOD, false);
		entityData.define(BABY, false);
		entityData.define(STING, false);
	}

	public boolean hasBrood() {
		return this.entityData.get(BROOD);
	}

	@Override
	public boolean isBaby() {
		return this.entityData.get(BABY);
	}

	/** Its tail is raised to sting. */
	public boolean stinging() {
		return this.entityData.get(STING);
	}

	/** Makes it a hatchling (small, frail) or a grown one. */
	public void setBaby(boolean baby) {
		this.entityData.set(BABY, baby);
		this.getAttribute(Attributes.SCALE).setBaseValue(baby ? BABY_SCALE : 1.0);
		this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(baby ? BABY_HEALTH : HEALTH);
		this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(baby ? BABY_DAMAGE : DAMAGE);
		this.setHealth(this.getMaxHealth());
		this.growth = 0;
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
		@Nullable SpawnGroupData groupData) {
		if (!this.isBaby() && (reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.CHUNK_GENERATION) && this.random.nextFloat() < BROOD_CHANCE) {
			this.entityData.set(BROOD, true);
		}
		return super.finalizeSpawn(level, difficulty, reason, groupData);
	}

	// ---- fighting ----

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit) {
			this.tailTicks = TAIL_TICKS;
			this.entityData.set(STING, true);
			if (target instanceof LivingEntity victim && !this.isBaby()) {
				int ticks = POISON_TICKS + (level.getDifficulty() == Difficulty.HARD ? POISON_MORE_ON_HARD : 0);
				victim.addEffect(new MobEffectInstance(MobEffects.POISON, ticks, 0), this);
			}
		}
		return hit;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && this.hasBrood() && this.isAlive()) {
			this.scatter(level);
		}
		return hurt;
	}

	@Override
	public void die(DamageSource source) {
		if (this.level() instanceof ServerLevel level && this.hasBrood()) {
			this.scatter(level);
		}
		super.die(source);
	}

	/** Her brood leaves her back and runs off across the ground. */
	private void scatter(ServerLevel level) {
		this.entityData.set(BROOD, false);
		RandomSource random = this.random;
		EntityType<Scorpion> type = MocBeasts.NETHER_SCORPION;
		for (int i = 0; i < BROOD_SIZE; i++) {
			Scorpion young = type.create(level, EntitySpawnReason.EVENT);
			if (young == null) {
				continue;
			}
			double angle = random.nextDouble() * Math.PI * 2.0;
			young.snapTo(this.getX(), this.getY() + 0.6, this.getZ(), random.nextFloat() * 360.0F, 0.0F);
			young.setBaby(true);
			young.setDeltaMovement(Math.cos(angle) * SCATTER_SPEED, SCATTER_LIFT, Math.sin(angle) * SCATTER_SPEED);
			level.addFreshEntity(young);
		}
		level.playSound(null, this.blockPosition(), SoundEvents.SPIDER_HURT, SoundSource.HOSTILE, 1.0F, 1.7F);
		level.sendParticles(ParticleTypes.POOF, this.getX(), this.getY() + 0.7, this.getZ(), 8, 0.4, 0.2, 0.4, 0.03);
	}

	@Override
	public boolean killedEntity(ServerLevel level, LivingEntity entity, DamageSource source) {
		boolean killed = super.killedEntity(level, entity, source);
		if (entity instanceof Animal) {
			this.fedUntil = level.getGameTime() + FEED_TICKS;
		}
		return killed;
	}

	// ---- looking after one ----

	/** Raw meat in hand feeds it and makes the player its friend. */
	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (!this.isBaby() && this.isAlive() && eats(held)) {
			if (!this.level().isClientSide()) {
				this.fedUntil = this.level().getGameTime() + FEED_TICKS;
				if (!this.friends.contains(player.getUUID())) {
					this.friends.add(player.getUUID());
					if (this.friends.size() > MAX_FRIENDS) {
						this.friends.remove(0);
					}
				}
				if (this.getTarget() == player) {
					this.setTarget(null);
				}
				if (!player.isCreative()) {
					held.shrink(1);
				}
				this.heal(4.0F);
				((ServerLevel)this.level()).sendParticles(ParticleTypes.HEART, this.getX(), this.getY() + 0.6, this.getZ(), 3, 0.3, 0.2, 0.3, 0.0);
				this.playSound(SoundEvents.GENERIC_EAT.value(), 1.0F, 0.8F);
			}
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	private static boolean eats(ItemStack stack) {
		return stack.is(Items.PORKCHOP) || stack.is(Items.BEEF) || stack.is(Items.MUTTON) || stack.is(Items.CHICKEN) || stack.is(Items.RABBIT);
	}

	// ---- life ----

	@Override
	public void aiStep() {
		super.aiStep();
		if (this.tailTicks > 0 && --this.tailTicks == 0 && !this.level().isClientSide()) {
			this.entityData.set(STING, false);
		}
		if (!this.level().isClientSide() && this.isBaby() && ++this.growth >= GROW_TICKS) {
			this.setBaby(false);
			this.playSound(SoundEvents.SPIDER_AMBIENT, 1.0F, 0.9F);
		}
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return !this.hasCustomName() && super.removeWhenFarAway(distance);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("brood", this.hasBrood());
		output.putBoolean("baby", this.isBaby());
		output.putInt("growth", this.growth);
		output.putLong("fed_until", this.fedUntil);
		output.store("friends", UUIDUtil.CODEC.listOf(), this.friends);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(BROOD, input.getBooleanOr("brood", false));
		boolean baby = input.getBooleanOr("baby", false);
		if (baby) {
			this.setBaby(true);
		}
		this.growth = input.getIntOr("growth", 0);
		this.fedUntil = input.getLongOr("fed_until", 0L);
		this.friends.clear();
		input.read("friends", UUIDUtil.CODEC.listOf()).ifPresent(this.friends::addAll);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return this.isBaby() ? null : SoundEvents.SPIDER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.SPIDER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.SPIDER_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
		this.playSound(SoundEvents.SPIDER_STEP, 0.15F, 0.8F);
	}

	@Override
	public float getVoicePitch() {
		return super.getVoicePitch() * 0.8F;
	}

	/** On the dry floors of the Nether, as monsters spawn (they aren't driven by the light level there). */
	public static boolean checkSpawn(EntityType<Scorpion> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		return level.getDifficulty() != Difficulty.PEACEFUL && Mob.checkMobSpawnRules(type, level, reason, pos, random)
			&& !level.getBlockState(pos.below()).is(net.minecraft.world.level.block.Blocks.NETHER_WART_BLOCK);
	}
}
