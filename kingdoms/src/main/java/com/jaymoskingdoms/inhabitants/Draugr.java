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
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
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
 * The draugr: a long-dead Warrior-born, a Z Fighter of the old raids gone to sinew, in a horned helm, mail and fur, its
 * tail wrapped round its waist, guarding the Stormborn Tomb's halls (placed by the tomb's spawners). Our own model, texture, animations and code (kingdoms/art/draugr.bbmodel); after the old idea of the
 * barrow-dead only, nothing from any other mod.
 *
 * <p>Each one rises with one of two arms (a real item in its hands, so vanilla does the rest): a sword and shield, and
 * then it takes blows and arrows from in front on its shield now and then (an axe knocks its guard down for a while); or
 * a two-handed bearded axe, slow and heavy, which knocks a player's raised shield aside as any axe does. Its blows carry
 * the grave's cold (a short slowness).
 *
 * <p>Every draugr is a Warrior-born ancestor (it wears a Warrior-born's tail, mangled its own way: the client's
 * {@code InhabitantsClient.DraugrRenderer}), bound where it stands: it keeps within {@link #HOME_RADIUS} of the spot it
 * first stood on, gives up a foe who leaves that ground and walks back to it, so the tomb's dead never leave the tomb.
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
	/** How far from where it first stood a draugr will go, chasing or wandering (blocks). */
	static final int HOME_RADIUS = 24;
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
	/** It has been given its arms (a structure may place it without finalizeSpawn, so it arms itself on its first tick). */
	private boolean armed;
	/** (Client, a lore film's actor only) drawn as the living Warrior-born it was, in the same gear in better condition. */
	private boolean living;
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

	/**
	 * Makes this (a client-only lore film actor, never a real draugr) the living Warrior-born it was: drawn alive, its
	 * tail whole, armed with the axe or the sword and shield.
	 */
	public void stageLiving(boolean axe) {
		this.living = true;
		this.entityData.set(AXE, axe);
	}

	/** (A lore film's actor) what a finisher took off it: {@link #MAIM_HEAD}, {@link #MAIM_ARM} (its weapon arm). */
	public static final int MAIM_HEAD = 1, MAIM_ARM = 2;
	private int maimed;

	/** (A lore film's actor) loses its head or its weapon arm to a finisher: no longer drawn. */
	public void stageMaim(int bits) {
		this.maimed |= bits;
	}

	public int maimed() {
		return this.maimed;
	}

	/** (A lore film's actor) the loops it stands and moves in ({@link com.jaymoskingdoms.films.Moves}); null for a real draugr. */
	private @Nullable String stageStill, stageMoving;
	private net.minecraft.world.phys.Vec3 stageLast = net.minecraft.world.phys.Vec3.ZERO;
	private double stageSpeed;

	/** (A lore film's actor) stands and moves in these loops of the humanoid move library from now on. */
	public void stageGait(String still, String moving) {
		this.stageStill = still;
		this.stageMoving = moving;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.stageStill != null) {
			net.minecraft.world.phys.Vec3 now = this.position();
			this.stageSpeed = Math.sqrt((now.x - this.stageLast.x) * (now.x - this.stageLast.x) + (now.z - this.stageLast.z) * (now.z - this.stageLast.z));
			this.stageLast = now;
		}
	}

	/** Whether this is a lore film's living Warrior-born ({@link #stageLiving}). */
	public boolean living() {
		return this.living;
	}

	/** It fights with the two-handed axe (else sword and shield). */
	public boolean axe() {
		return this.entityData.get(AXE);
	}

	private void arm(boolean axe) {
		this.armed = true;
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
		this.goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 1.0));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (!this.armed) {
			this.arm(this.random.nextFloat() < AXE_SHARE);
		}
		if (!this.hasHome()) {
			// bound where it first stands (draugr from before this have none yet: theirs is where they are now)
			this.setHomeTo(this.blockPosition(), HOME_RADIUS);
		}
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
			Draugr draugr = test.animatable();
			if (draugr.stageStill != null && draugr.stageMoving != null) {
				// a lore film's Warrior-born: the move library's loops, in time with how fast the film moves it
				boolean going = draugr.stageSpeed > 0.02;
				test.setControllerSpeed(going ? com.jaymoskingdoms.films.Moves.rate(draugr.stageMoving, draugr.stageSpeed, 1.2) : 1.0F);
				return test.setAndContinue(com.jaymoskingdoms.films.Moves.loop(going ? draugr.stageMoving : draugr.stageStill));
			}
			if (test.animatable().tickCount - test.animatable().blockedAt < BLOCK_SHOWN_TICKS) {
				return test.setAndContinue(BLOCK);
			}
			return test.setAndContinue(test.isMoving() ? WALK : IDLE);
		}));
		// its own blows, and every move of the humanoid library (the lore films' Warrior-born use them)
		controllers.add(com.jaymoskingdoms.films.Moves.actions(new AnimationController<Draugr>("action", 2, test -> PlayState.STOP))
			.triggerableAnim("attack_sword", ATTACK_SWORD).triggerableAnim("attack_axe", ATTACK_AXE).triggerableAnim("guard_broken", GUARD_BROKEN));
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
		output.putBoolean("armed", this.armed);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(AXE, input.getBooleanOr("axe", false));
		this.armed = input.getBooleanOr("armed", false);
	}
}
