package com.jaymoskingdoms;

import com.jaymoskingdoms.beasts.Acting;
import com.jaymoskingdoms.beasts.Beasts;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.327) A scalebane sellsword, drawn as eanncrod's Scalebane Archer (Blockbench Workshop, CC BY 4.0): lizardfolk
 * bowmen who sell their aim to barbarian camps and goblin villages (Andy: "common sellswords among barbarians and goblin
 * encampments"). A camp or village takes on {@link #START} when it rises and keeps up to {@link #MAX} about its fire
 * ({@link #hire}, from its daily growth). They shoot what their paymasters would ({@link Barbarians#prey},
 * {@link Goblins#prey}: an outlaw the camp spares, a player passing for a goblin, go unshot too) and never their own
 * side. When their camp is cleared they've no one to pay them: they turn rogue and wander off like any monster.
 */
public class ScalebaneArcher extends Monster implements RangedAttackMob, Acting {
	static final double HEALTH = 30.0, ARMOR = 4.0, SPEED = 0.27;
	/** How many a new camp takes on, the most it keeps, how near its fire they stay (blocks), and how far it counts them. */
	static final int START = 2, MAX = 3, HOME_RADIUS = 20;
	static final double COUNT_RANGE = 48.0;
	/** The chance a camp short of them takes one on, each day it grows. */
	static final float HIRE_CHANCE = 0.35F;
	/** Ticks between shots, and how far it shoots. */
	static final int SHOT_INTERVAL = 30;
	static final float SHOT_RANGE = 18.0F;

	/** The camp (or goblin village) paying it; -1 for none. */
	private int camp = -1;

	public ScalebaneArcher(EntityType<? extends ScalebaneArcher> type, Level level) {
		super(type, level);
		this.xpReward = 8;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.ARMOR, ARMOR).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.FOLLOW_RANGE, 28.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new RangedBowAttackGoal<>(this, 1.0, SHOT_INTERVAL, SHOT_RANGE));
		this.goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 1.0));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, true, (target, level) -> this.prey(level, target)));
	}

	/** What it shoots on sight: its paymasters' prey, or (rogue) any player. */
	private boolean prey(ServerLevel level, LivingEntity target) {
		if (target instanceof ScalebaneArcher) {
			return false;
		}
		if (this.camp < 0) {
			return target instanceof Player player && !player.isCreative() && !player.isSpectator();
		}
		return GoblinVillages.is(level.getServer(), this.camp) ? Goblins.prey(this.camp, target) : Barbarians.prey(this.camp, target);
	}

	/** Whether {@code e} is on its side: its camp's folk or villagers, or another sellsword. */
	private boolean ally(LivingEntity e) {
		if (e instanceof ScalebaneArcher || this.camp < 0) {
			return e instanceof ScalebaneArcher;
		}
		if (e instanceof Folk folk) {
			return folk.factionId() == this.camp;
		}
		return Barbarians.isCampVillager(e) || Goblins.isGoblin(e);
	}

	@Override
	public void setTarget(@Nullable LivingEntity target) {
		// (it doesn't turn on its own side, even when one of them hits it by mistake)
		super.setTarget(target != null && this.ally(target) ? null : target);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.camp >= 0 && this.tickCount % 100 == 0) {
			Camps.CampData data = Camps.data(level.getServer(), this.camp);
			if (Factions.get(level.getServer(), this.camp) == null || data == null || data.clearedAt() >= 0) {
				// its camp is gone: no one pays it now
				this.camp = -1;
				this.clearHome();
			} else if (!this.hasHome()) {
				this.setHomeTo(data.center(), HOME_RADIUS);
			}
		}
	}

	@Override
	public void performRangedAttack(LivingEntity target, float power) {
		ItemStack bow = this.getItemInHand(InteractionHand.MAIN_HAND);
		ItemStack arrowStack = new ItemStack(Items.ARROW);
		AbstractArrow arrow = ProjectileUtil.getMobArrow(this, arrowStack, power, bow);
		double xd = target.getX() - this.getX();
		double yd = target.getY(0.3333333333333333) - arrow.getY();
		double zd = target.getZ() - this.getZ();
		double distance = Math.sqrt(xd * xd + zd * zd);
		if (this.level() instanceof ServerLevel level) {
			Projectile.spawnProjectileUsingShoot(arrow, level, arrowStack, xd, yd + distance * 0.2F, zd, 1.6F, 10 - level.getDifficulty().getId() * 3);
		}
		this.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
	}

	@Override
	protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
		this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		this.setDropChance(EquipmentSlot.MAINHAND, 0.1F);
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
		@Nullable SpawnGroupData data) {
		data = super.finalizeSpawn(level, difficulty, reason, data);
		this.populateDefaultEquipmentSlots(level.getRandom(), difficulty);
		return data;
	}

	/** (Client) drawing its bow: the model's "fire"; ready for a fight: its "hostile" stance. */
	@Override
	public @Nullable String action() {
		return this.isUsingItem() ? "fire" : this.isAggressive() ? "hostile" : null;
	}

	@Override
	public int actionAge() {
		return this.isUsingItem() ? this.getTicksUsingItem() : this.tickCount;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("camp", this.camp);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.camp = input.getIntOr("camp", -1);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.FROG_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.FROG_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.FROG_DEATH;
	}

	// ---- hiring --------------------------------------------------------------------------------------------------------

	/**
	 * Camp {@code camp} (fire at {@code fire}) takes on sellswords: {@code count} of them, never past {@link #MAX} about
	 * its fire. Only while the fire's ground is loaded.
	 */
	static void hire(ServerLevel level, int camp, BlockPos fire, int count) {
		if (Beasts.SCALEBANE == null || !level.isLoaded(fire)) {
			return;
		}
		int near = level.getEntitiesOfClass(ScalebaneArcher.class, new net.minecraft.world.phys.AABB(fire).inflate(COUNT_RANGE), s -> s.camp == camp).size();
		RandomSource random = level.getRandom();
		for (int i = 0; i < count && near < MAX; i++, near++) {
			ScalebaneArcher sword = Beasts.SCALEBANE.create(level, EntitySpawnReason.EVENT);
			if (sword == null) {
				return;
			}
			BlockPos at = GoblinVillages.spot(level, fire, random);
			sword.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			sword.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
			sword.camp = camp;
			sword.setHomeTo(fire, HOME_RADIUS);
			sword.setPersistenceRequired();
			level.addFreshEntity(sword);
		}
	}

	/** A day's growth: a camp short of sellswords now and then takes one on. */
	static void grow(ServerLevel level, int camp, BlockPos fire, int days) {
		MinecraftServer server = level.getServer();
		Camps.CampData data = Camps.data(server, camp);
		if (Factions.get(server, camp) != null && data != null && data.clearedAt() < 0 && level.getRandom().nextFloat() < 1.0F - Math.pow(1.0F - HIRE_CHANCE, days)) {
			hire(level, camp, fire, 1);
		}
	}
}
