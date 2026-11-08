package com.jaymoskingdoms.inhabitants;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The draugr: a dead Nordic warrior in a horned helm, mail and fur, guarding the Stormborn Tomb's halls (placed by the
 * tomb's spawners). Our own model, texture, animations and code (kingdoms/art/draugr.bbmodel); after the old idea of the
 * barrow-dead only, nothing from any other mod.
 *
 * <p>Each one rises with one of two arms (a real item in its hands, so vanilla does the rest): a sword and shield, and
 * then it takes blows and arrows from in front on its shield now and then (an axe knocks its guard down for a while); or
 * a two-handed bearded axe, slow and heavy, which knocks a player's raised shield aside as any axe does. Its blows carry
 * the grave's cold (a short slowness).
 */
public class Draugr extends Monster implements GeoEntity {
	/** Base damage: a sword (+5) makes it 7, an axe (+8) 10. */
	static final double HEALTH = 40.0, SPEED = 0.22, DAMAGE = 2.0, ARMOR = 8.0, KNOCKBACK_RESISTANCE = 0.3, FOLLOW = 24.0;
	/** Share that rise with the axe; the chance its sword or axe drops. */
	static final float AXE_SHARE = 0.4F, WEAPON_DROP = 0.05F;
	/** With its shield: how often it takes a blow, and an arrow, from in front (degrees either side of its facing). */
	static final float BLOCK_CHANCE = 0.4F, ARROW_BLOCK_CHANCE = 0.7F, BLOCK_ARC = 70.0F;
	/** How long an axe blow on the shield leaves it unable to block, and how long the shield shows raised after a block (ticks). */
	static final int GUARD_BROKEN_TICKS = 100, BLOCK_SHOWN_TICKS = 12;
	/** The cold of its blows: slowness, for this long (ticks), this strong (0 = I). */
	static final int CHILL_TICKS = 50, CHILL_AMPLIFIER = 0;
	public static final byte BLOCKED = 94;

	static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
	static final RawAnimation BLOCK = RawAnimation.begin().thenPlayAndHold("block");
	static final RawAnimation ATTACK_SWORD = RawAnimation.begin().thenPlay("attack_sword");
	static final RawAnimation ATTACK_AXE = RawAnimation.begin().thenPlay("attack_axe");
	static final RawAnimation GUARD_BROKEN = RawAnimation.begin().thenPlay("guard_broken");

	private static final EntityDataAccessor<Boolean> AXE = SynchedEntityData.defineId(Draugr.class, EntityDataSerializers.BOOLEAN);

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private int guardBroken;
	/** (Client) the tick it last took a blow on its shield. */
	private int blockedAt = -1000;

	public Draugr(EntityType<? extends Draugr> type, Level level) {
		super(type, level);
		this.xpReward = 12;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.ATTACK_DAMAGE, DAMAGE).add(Attributes.ARMOR, ARMOR).add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE)
			.add(Attributes.FOLLOW_RANGE, FOLLOW);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(AXE, false);
	}

	/** It fights with the two-handed axe (else sword and shield). */
	public boolean axe() {
		return this.entityData.get(AXE);
	}

	private void arm(boolean axe) {
		this.entityData.set(AXE, axe);
		this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(axe ? Items.IRON_AXE : Items.IRON_SWORD));
		this.setItemSlot(EquipmentSlot.OFFHAND, axe ? ItemStack.EMPTY : new ItemStack(Items.SHIELD));
		this.setDropChance(EquipmentSlot.MAINHAND, WEAPON_DROP);
		this.setDropChance(EquipmentSlot.OFFHAND, 0.0F);
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
		@Nullable SpawnGroupData groupData) {
		SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, groupData);
		this.arm(this.random.nextFloat() < AXE_SHARE);
		return data;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.guardBroken > 0) {
			this.guardBroken--;
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (this.blocks(source)) {
			Entity attacker = source.getEntity();
			boolean axe = !source.is(DamageTypeTags.IS_PROJECTILE) && attacker instanceof LivingEntity living
				&& living.getMainHandItem().is(ItemTags.AXES);
			level.playSound(null, this.getX(), this.getY(), this.getZ(), axe ? SoundEvents.SHIELD_BREAK.value() : SoundEvents.SHIELD_BLOCK.value(),
				SoundSource.HOSTILE, 1.0F, 0.8F + this.random.nextFloat() * 0.4F);
			if (axe) {
				this.guardBroken = GUARD_BROKEN_TICKS;
				this.triggerAnim("action", "guard_broken");
			} else {
				level.broadcastEntityEvent(this, BLOCKED);
			}
			return false;
		}
		return super.hurtServer(level, source, amount);
	}

	/** Whether it takes {@code source} on its shield: sword and shield only, from in front, now and then, its guard up. */
	private boolean blocks(DamageSource source) {
		Vec3 from = source.getSourcePosition();
		if (this.axe() || from == null || this.guardBroken > 0 || source.getEntity() == null || source.is(DamageTypeTags.BYPASSES_SHIELD)) {
			return false;
		}
		float chance = source.is(DamageTypeTags.IS_PROJECTILE) ? ARROW_BLOCK_CHANCE : BLOCK_CHANCE;
		if (this.random.nextFloat() >= chance) {
			return false;
		}
		Vec3 to = from.subtract(this.position());
		float angle = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
		return Math.abs(Mth.wrapDegrees(angle - this.yBodyRot)) <= BLOCK_ARC;
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit) {
			this.triggerAnim("action", this.axe() ? "attack_axe" : "attack_sword");
			if (target instanceof LivingEntity living) {
				living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, CHILL_TICKS, CHILL_AMPLIFIER), this);
			}
		}
		return hit;
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == BLOCKED) {
			this.blockedAt = this.tickCount;
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<Draugr>("move", 4, test -> {
			if (test.animatable().tickCount - test.animatable().blockedAt < BLOCK_SHOWN_TICKS) {
				return test.setAndContinue(BLOCK);
			}
			return test.setAndContinue(test.isMoving() ? WALK : IDLE);
		}));
		controllers.add(new AnimationController<Draugr>("action", test -> PlayState.STOP).triggerableAnim("attack_sword", ATTACK_SWORD)
			.triggerableAnim("attack_axe", ATTACK_AXE).triggerableAnim("guard_broken", GUARD_BROKEN));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ZOMBIE_AMBIENT;
	}

	@Override
	public float getVoicePitch() {
		return super.getVoicePitch() * 0.6F;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.WITHER_SKELETON_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.WITHER_SKELETON_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.ZOMBIE_STEP, 0.4F, 0.7F);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("axe", this.axe());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(AXE, input.getBooleanOr("axe", false));
	}
}
