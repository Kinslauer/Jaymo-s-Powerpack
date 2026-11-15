package com.jaymoskingdoms.oldage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (medieval picks, cv-rulers, cv-weapons) One of an old ruler's guard ({@link OldRuler}), asleep round their tomb in
 * their people's arms ({@link People}): the first of each pair with the people's own blade (and the Pillared folk's tall
 * shield), the second with the people's other (the Pillared folk's leaf spear and round shield). They wake with their
 * ruler, when struck, or when someone walks right up to them. A shield turns aside some of the blows struck at its front.
 * Now and then one drops its weapon: the old age's arms are found in their guards' hands.
 */
public class TombGuard extends Monster {
	// ---- tuning ----
	static final double HEALTH = 34.0, ARMOR = 4.0, SPEED = 0.25, DAMAGE = 3.0, FOLLOW = 24.0;
	/** How far they keep to the tomb (blocks), and how near a player must come to wake one. */
	static final int TERRITORY = 16;
	static final double WAKE_RANGE = 2.5;
	/** Their weapon's and shield's drop chance. */
	static final float ARMS_DROP = 0.08F;
	/** Of the blows at a shield's front, how many it turns aside, and how much of each it lets through; how far to the front counts. */
	static final float SHIELD_CHANCE = 0.4F, SHIELD_LETS = 0.3F;
	static final double SHIELD_DOT = 0.3;

	private static final EntityDataAccessor<Integer> PEOPLE = SynchedEntityData.defineId(TombGuard.class, EntityDataSerializers.INT);

	private boolean dormant;
	private @Nullable BlockPos tomb;

	public TombGuard(EntityType<? extends TombGuard> type, Level level) {
		super(type, level);
		this.xpReward = 10;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.ARMOR, ARMOR).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.ATTACK_DAMAGE, DAMAGE).add(Attributes.FOLLOW_RANGE, FOLLOW);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(PEOPLE, 0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
		this.goalSelector.addGoal(3, new MoveTowardsRestrictionGoal(this, 0.9));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	public People people() {
		return People.byId(this.entityData.get(PEOPLE));
	}

	/** Stands them by their ruler's tomb at {@code at}, in their people's arms ({@code second}: the second guard's). */
	void settle(People people, BlockPos at, boolean asleep, boolean second) {
		this.entityData.set(PEOPLE, people.ordinal());
		this.tomb = at;
		this.setHomeTo(at, TERRITORY);
		this.setPersistenceRequired();
		String weapon = second ? people.weapon2 : people.weapon, shield = second ? people.shield2 : people.shield;
		this.setItemSlot(EquipmentSlot.MAINHAND, OldRuler.arm(weapon, people == People.MOUND ? Items.IRON_AXE : Items.IRON_SWORD));
		if (!shield.isEmpty()) {
			this.setItemSlot(EquipmentSlot.OFFHAND, OldRuler.arm(shield, Items.SHIELD));
		}
		this.setDropChance(EquipmentSlot.MAINHAND, ARMS_DROP);
		this.setDropChance(EquipmentSlot.OFFHAND, ARMS_DROP);
		this.setCustomName(Component.translatable("entity.jaymoskingdoms.tomb_guard." + people.id));
		this.setCustomNameVisible(false);
		this.dormant = asleep;
		this.setNoAi(asleep);
	}

	void wake(ServerLevel level) {
		if (!this.dormant) {
			return;
		}
		this.dormant = false;
		this.setNoAi(false);
		level.sendParticles(ParticleTypes.ASH, this.getX(), this.getY() + 1.0, this.getZ(), 12, 0.3, 0.6, 0.3, 0.02);
		this.playSound(SoundEvents.SKELETON_AMBIENT, 1.0F, 0.6F);
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.dormant && this.level() instanceof ServerLevel level && this.tickCount % 10 == 0
			&& level.getNearestPlayer(this, WAKE_RANGE) instanceof ServerPlayer p && !p.isCreative() && !p.isSpectator()) {
			this.wake(level);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		this.wake(level);
		if (this.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS) && source.getSourcePosition() != null && this.getRandom().nextFloat() < SHIELD_CHANCE) {
			Vec3 to = source.getSourcePosition().subtract(this.position());
			if (to.lengthSqr() > 0.01 && to.normalize().dot(Vec3.directionFromRotation(0.0F, this.yBodyRot)) > SHIELD_DOT) {
				this.playSound(SoundEvents.SHIELD_BLOCK.value(), 0.9F, 0.9F);
				amount *= SHIELD_LETS;
			}
		}
		return super.hurtServer(level, source, amount);
	}

	@Override
	public boolean isPushable() {
		return !this.dormant && super.isPushable();
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("people", this.entityData.get(PEOPLE));
		output.putBoolean("dormant", this.dormant);
		if (this.tomb != null) {
			output.putLong("tomb", this.tomb.asLong());
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(PEOPLE, input.getIntOr("people", 0));
		this.dormant = input.getBooleanOr("dormant", false);
		long t = input.getLongOr("tomb", Long.MIN_VALUE);
		if (t != Long.MIN_VALUE) {
			this.tomb = BlockPos.of(t);
			this.setHomeTo(this.tomb, TERRITORY);
		}
		this.setNoAi(this.dormant);
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return this.dormant ? null : SoundEvents.SKELETON_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.SKELETON_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.SKELETON_DEATH;
	}
}
