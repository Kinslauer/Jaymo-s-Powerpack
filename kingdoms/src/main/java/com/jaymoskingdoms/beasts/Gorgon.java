package com.jaymoskingdoms.beasts;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.336) The gorgon, drawn as eanncrod's Hydra Guardian (Blockbench Workshop, CC BY 4.0): a snake-haired archer
 * with a serpent bow who guards the jungles and swamps, rare and alone. Its arrows bring a stiffness like stone
 * (Slowness), it strafes as it shoots, and now and then it looses a volley into the sky that rains down round its foe.
 */
public class Gorgon extends ActingMonster implements RangedAttackMob {
	static final double HEALTH = 80.0, ARMOR = 6.0, SPEED = 0.28;
	/** One in this many natural spawn checks passes, never another within {@link #ALONE_RANGE} blocks. */
	static final int RARITY = 8;
	static final double ALONE_RANGE = 64.0;
	/** Ticks between its shots, and how far it shoots. */
	static final int SHOT_INTERVAL = 25;
	static final float SHOT_RANGE = 20.0F;
	/** Its stone-stiffening arrows: Slowness, how long (ticks) and how strong (0 = I). */
	static final int STONE_TICKS = 80, STONE_AMP = 1;
	/** The arrow rain: how long its move runs, when the arrows fall, how many, how wide, how often. */
	static final int RAIN_TICKS = 21, RAIN_LOOSE = 12, RAIN_ARROWS = 10, RAIN_COOLDOWN = 200;
	static final double RAIN_RADIUS = 3.5;

	private static final String[] ACTIONS = {"rain_shot"};

	private int rainCooldown = RAIN_COOLDOWN / 2;

	public Gorgon(EntityType<? extends Gorgon> type, Level level) {
		super(type, level);
		this.xpReward = 30;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.ARMOR, ARMOR).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.FOLLOW_RANGE, 32.0);
	}

	/** Rare, and alone. */
	public static boolean checkSpawn(EntityType<Gorgon> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		return (reason != EntitySpawnReason.NATURAL || random.nextInt(RARITY) == 0 && Beasts.alone(level, pos, Gorgon.class, ALONE_RANGE))
			&& checkMonsterSpawnRules(type, level, reason, pos, random);
	}

	@Override
	protected String[] actions() {
		return ACTIONS;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new RangedBowAttackGoal<>(this, 1.0, SHOT_INTERVAL, SHOT_RANGE));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true,
			(target, level) -> level.getDifficulty() != Difficulty.PEACEFUL));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		LivingEntity target = this.getTarget();
		if (this.acting("rain_shot")) {
			this.getNavigation().stop();
			if (target != null && this.actionRun() == RAIN_LOOSE) {
				this.rain(level, target);
			}
			return;
		}
		if (--this.rainCooldown <= 0 && target != null && target.isAlive() && this.hasLineOfSight(target) && this.distanceTo(target) < SHOT_RANGE) {
			this.rainCooldown = RAIN_COOLDOWN;
			this.stopUsingItem();
			this.act("rain_shot", RAIN_TICKS);
			this.playSound(SoundEvents.CROSSBOW_LOADING_END.value(), 1.0F, 0.6F);
		}
	}

	/** A volley loosed into the sky comes down round its foe. */
	private void rain(ServerLevel level, LivingEntity target) {
		for (int i = 0; i < RAIN_ARROWS; i++) {
			double angle = this.random.nextDouble() * Math.PI * 2.0;
			double r = Math.sqrt(this.random.nextDouble()) * RAIN_RADIUS;
			Arrow arrow = new Arrow(level, target.getX() + Math.cos(angle) * r, target.getY() + 12.0 + this.random.nextDouble() * 4.0,
				target.getZ() + Math.sin(angle) * r, new ItemStack(Items.ARROW), null);
			arrow.setOwner(this);
			arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
			arrow.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STONE_TICKS, STONE_AMP));
			arrow.setDeltaMovement(0.0, -1.2, 0.0);
			level.addFreshEntity(arrow);
		}
		level.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY() + 2.5, this.getZ(), 20, 0.3, 0.5, 0.3, 0.3);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SKELETON_SHOOT, SoundSource.HOSTILE, 1.5F, 0.6F);
	}

	@Override
	public void performRangedAttack(LivingEntity target, float power) {
		if (this.acting()) {
			return;
		}
		ItemStack bow = this.getItemInHand(InteractionHand.MAIN_HAND);
		ItemStack arrowStack = new ItemStack(Items.ARROW);
		AbstractArrow arrow = ProjectileUtil.getMobArrow(this, arrowStack, power, bow);
		if (arrow instanceof Arrow plain) {
			plain.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STONE_TICKS, STONE_AMP));
		}
		double xd = target.getX() - this.getX();
		double yd = target.getY(0.3333333333333333) - arrow.getY();
		double zd = target.getZ() - this.getZ();
		double distance = Math.sqrt(xd * xd + zd * zd);
		if (this.level() instanceof ServerLevel level) {
			Projectile.spawnProjectileUsingShoot(arrow, level, arrowStack, xd, yd + distance * 0.2F, zd, 1.6F, 14 - level.getDifficulty().getId() * 4);
		}
		this.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 0.8F);
	}

	@Override
	protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
		this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
		@Nullable SpawnGroupData data) {
		data = super.finalizeSpawn(level, difficulty, reason, data);
		this.populateDefaultEquipmentSlots(level.getRandom(), difficulty);
		return data;
	}

	/** (Client) its arrow rain; drawing its bow, "fire"; ready for a fight, its "hostile" stance. */
	@Override
	public @Nullable String action() {
		String move = super.action();
		return move != null ? move : this.isUsingItem() ? "fire" : this.isAggressive() ? "hostile" : null;
	}

	@Override
	public int actionAge() {
		return super.action() != null ? super.actionAge() : this.isUsingItem() ? this.getTicksUsingItem() : this.tickCount;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.CREEPER_PRIMED;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.CREEPER_PRIMED;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.EVOKER_DEATH;
	}
}
