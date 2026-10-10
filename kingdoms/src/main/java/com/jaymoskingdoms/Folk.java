package com.jaymoskingdoms;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
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
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A kingdom's people: its {@link #KING} (a boss: heavy armour, a great sword, a boss bar, and he hits back hard, with
 * phases as he weakens) and its {@link #GUARD}s, who keep to the capital, cut down monsters, and attack anyone the
 * kingdom counts as hostile ({@link Reputation#HOSTILE}) or who strikes one of their own. Never despawn.
 */
public class Folk extends PathfinderMob implements net.minecraft.world.entity.monster.RangedAttackMob {
	/** Guards fight hand to hand; archers keep their posts on walls and towers and shoot; the king is the boss. */
	public static final int GUARD = 0, KING = 1, ARCHER = 2;
	/** An archer's reach, and how often they loose (ticks). */
	static final double ARCHER_RANGE = 28.0;
	static final int ARCHER_INTERVAL = 30;
	/** How far from the capital they wander. */
	static final int HOME_RADIUS = 24;
	/** The king's second wind: at this share of his health he rallies (strength, resistance, his guards called in). */
	static final float RALLY_AT = 0.4F;

	private static final EntityDataAccessor<Integer> ROLE = SynchedEntityData.defineId(Folk.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> KINGDOM = SynchedEntityData.defineId(Folk.class, EntityDataSerializers.INT);

	private ServerBossEvent bossBar;
	private boolean rallied;

	public Folk(EntityType<? extends Folk> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder guardAttributes() {
		return PathfinderMob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 40.0)
			.add(Attributes.ATTACK_DAMAGE, 4.0)
			.add(Attributes.MOVEMENT_SPEED, 0.3)
			.add(Attributes.FOLLOW_RANGE, 24.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(ROLE, GUARD);
		entityData.define(KINGDOM, -1);
	}

	public int role() {
		return this.entityData.get(ROLE);
	}

	public int kingdomId() {
		return this.entityData.get(KINGDOM);
	}

	/** Set up as {@code role} of {@code kingdom}: their gear, and the post they keep to ({@code radius} of {@code home}). */
	void setup(int role, Kingdom kingdom, BlockPos home, int radius) {
		this.entityData.set(ROLE, role);
		this.entityData.set(KINGDOM, kingdom.id());
		this.setHomeTo(home, radius);
		if (role == KING) {
			this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(300.0);
			this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(9.0);
			this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.8);
			this.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(4.0);
			this.setHealth(this.getMaxHealth());
			this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
			this.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
			this.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
			this.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
			this.setCustomName(Component.translatable("entity.jaymoskingdoms.king_of", kingdom.name()));
		} else if (role == ARCHER) {
			this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
			this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
			this.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
			this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(ARCHER_RANGE + 4.0);
			this.setCustomName(Component.translatable("entity.jaymoskingdoms.archer_of", kingdom.name()));
		} else {
			this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
			this.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
			this.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.CHAINMAIL_LEGGINGS));
			this.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
			this.setCustomName(Component.translatable("entity.jaymoskingdoms.guard_of", kingdom.name()));
		}
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			this.setDropChance(slot, 0.0F);
		}
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new Shoot(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, true) {
			@Override
			public boolean canUse() {
				return Folk.this.role() != ARCHER && super.canUse();
			}
		});
		this.goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 1.0));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this, Folk.class).setAlertOthers(Folk.class));
		// anyone the kingdom counts as hostile
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true,
			(target, level) -> target instanceof ServerPlayer player && !player.isCreative() && Reputation.get(player, this.kingdomId()) <= Reputation.HOSTILE));
		// and monsters on their land
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, net.minecraft.world.entity.Mob.class, true,
			(target, level) -> target instanceof Enemy && !(target instanceof net.minecraft.world.entity.monster.Creeper)));
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (this.bossBar != null) {
			this.bossBar.setProgress(this.getHealth() / this.getMaxHealth());
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.role() == KING && !this.rallied && this.getHealth() <= this.getMaxHealth() * RALLY_AT) {
			// his second wind: he rallies, and calls his guards in
			this.rallied = true;
			this.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 20 * 60, 1));
			this.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 60, 1));
			this.addEffect(new MobEffectInstance(MobEffects.SPEED, 20 * 60, 0));
			LivingEntity foe = this.getTarget();
			for (Folk guard : level.getEntitiesOfClass(Folk.class, this.getBoundingBox().inflate(48.0), f -> f.kingdomId() == this.kingdomId() && f != this)) {
				if (foe != null) {
					guard.setTarget(foe);
				}
			}
			level.playSound(null, this.getX(), this.getY(), this.getZ(), net.minecraft.sounds.SoundEvents.RAID_HORN.value(),
				net.minecraft.sounds.SoundSource.HOSTILE, 4.0F, 1.0F);
			for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(32.0))) {
				player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.king_rallies", this.getDisplayName()).withStyle(ChatFormatting.GOLD));
			}
		}
	}

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
		if (this.role() == KING) {
			this.bar().addPlayer(player);
		}
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		if (this.bossBar != null) {
			this.bossBar.removePlayer(player);
		}
	}

	private ServerBossEvent bar() {
		if (this.bossBar == null) {
			this.bossBar = new ServerBossEvent(this.getUUID(), this.getDisplayName(), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10);
		}
		return this.bossBar;
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			KingdomEvents.audience(serverPlayer, this);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel level) {
			KingdomEvents.folkDied(level, this, source);
			if (this.bossBar != null) {
				this.bossBar.removeAllPlayers();
			}
		}
	}

	/** An archer's shot (as a skeleton looses one). */
	@Override
	public void performRangedAttack(LivingEntity target, float power) {
		ItemStack bow = this.getMainHandItem();
		ItemStack arrowStack = new ItemStack(Items.ARROW);
		net.minecraft.world.entity.projectile.arrow.AbstractArrow arrow = net.minecraft.world.entity.projectile.ProjectileUtil.getMobArrow(this, arrowStack, power, bow);
		double xd = target.getX() - this.getX();
		double yd = target.getY(0.3333333333333333) - arrow.getY();
		double zd = target.getZ() - this.getZ();
		double distance = Math.sqrt(xd * xd + zd * zd);
		if (this.level() instanceof ServerLevel level) {
			net.minecraft.world.entity.projectile.Projectile.spawnProjectileUsingShoot(arrow, level, arrowStack, xd, yd + distance * 0.2F, zd, 1.6F, 4.0F);
		}
		this.playSound(net.minecraft.sounds.SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
	}

	/** An archer at its post: it stays put, faces its target, and looses an arrow every so often while it can see it. */
	static final class Shoot extends net.minecraft.world.entity.ai.goal.Goal {
		private final Folk archer;
		private int cooldown;

		Shoot(Folk archer) {
			this.archer = archer;
			this.setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = this.archer.getTarget();
			return this.archer.role() == ARCHER && target != null && target.isAlive() && this.archer.distanceTo(target) <= ARCHER_RANGE;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity target = this.archer.getTarget();
			if (target == null) {
				return;
			}
			this.archer.getNavigation().stop();
			this.archer.getLookControl().setLookAt(target, 30.0F, 30.0F);
			if (--this.cooldown <= 0 && this.archer.getSensing().hasLineOfSight(target)) {
				this.cooldown = ARCHER_INTERVAL;
				this.archer.performRangedAttack(target, 1.0F);
			}
		}
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("role", this.role());
		output.putInt("kingdom", this.kingdomId());
		output.putBoolean("rallied", this.rallied);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(ROLE, input.getIntOr("role", GUARD));
		this.entityData.set(KINGDOM, input.getIntOr("kingdom", -1));
		this.rallied = input.getBooleanOr("rallied", false);
	}
}
