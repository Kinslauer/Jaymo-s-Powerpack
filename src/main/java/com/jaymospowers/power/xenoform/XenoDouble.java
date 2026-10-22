package com.jaymospowers.power.xenoform;

import com.jaymospowers.JaymosPowers;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * One of Duplex's doubles: a copy of the alien that steps out of it, follows it about and fights what it fights. It
 * belongs to its owner only while they are Duplex and nearby; otherwise it pops. It's never saved (a reload ends it).
 * Its owner is synced, so their HUD can count their doubles.
 */
public class XenoDouble extends PathfinderMob {
	// ---- tuning ----
	public static final float MAX_HEALTH = 6.0F;
	public static final double ATTACK_DAMAGE = 3.0, MOVE_SPEED = 0.3, CHASE_SPEED = 1.3, FOLLOW_SPEED = 1.25;
	/** It walks back to its owner past {@link #FOLLOW_START} blocks, stops at {@link #FOLLOW_STOP}, and blinks to them past {@link #FOLLOW_TELEPORT}. */
	public static final double FOLLOW_START = 4.0, FOLLOW_STOP = 2.0, FOLLOW_TELEPORT = 24.0;
	/** How often (ticks) it checks its owner is still there and still Duplex. */
	static final int OWNER_CHECK = 10;

	public static EntityType<XenoDouble> TYPE;

	private static final EntityDataAccessor<Optional<EntityReference<LivingEntity>>> DATA_OWNER = SynchedEntityData.defineId(XenoDouble.class,
		EntityDataSerializers.OPTIONAL_LIVING_ENTITY_REFERENCE);

	public XenoDouble(EntityType<? extends XenoDouble> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
	}

	static void init() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("xeno_double"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
			EntityType.Builder.<XenoDouble>of(XenoDouble::new, MobCategory.MISC)
				.sized(0.55F, 1.65F)
				.eyeHeight(1.45F)
				.clientTrackingRange(8)
				.noLootTable()
				.build(key));
		FabricDefaultAttributeRegistry.register(TYPE, createAttributes());
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, MAX_HEALTH)
			.add(Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE)
			.add(Attributes.MOVEMENT_SPEED, MOVE_SPEED)
			.add(Attributes.FOLLOW_RANGE, 24.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_OWNER, Optional.empty());
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, CHASE_SPEED, true));
		this.goalSelector.addGoal(4, new FollowGoal(this));
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this, XenoDouble.class));
	}

	// ---------------------------------------------------------------- owner

	public void bindTo(ServerPlayer player) {
		this.entityData.set(DATA_OWNER, Optional.of(EntityReference.of(player.getUUID())));
		this.setCustomName(player.getDisplayName());
		this.setCustomNameVisible(false);
	}

	public @Nullable UUID owner() {
		return this.entityData.get(DATA_OWNER).map(EntityReference::getUUID).orElse(null);
	}

	public boolean isDoubleOf(Entity player) {
		return player.getUUID().equals(this.owner());
	}

	/** The owner, if they're online and in this level. */
	public @Nullable ServerPlayer ownerPlayer() {
		UUID owner = this.owner();
		if (owner == null || !(this.level() instanceof ServerLevel level)) {
			return null;
		}
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
		return player != null && player.level() == level ? player : null;
	}

	/** One owner's doubles stand together, with their owner. */
	@Override
	protected boolean considersEntityAsAlly(Entity other) {
		UUID owner = this.owner();
		if (owner != null && (other.getUUID().equals(owner) || other instanceof XenoDouble twin && owner.equals(twin.owner()))) {
			return true;
		}
		return super.considersEntityAsAlly(other);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level) || this.isRemoved() || this.tickCount % OWNER_CHECK != 0) {
			return;
		}
		ServerPlayer player = this.ownerPlayer();
		if (player == null || !player.isAlive() || XenoformState.form(player) != XenoForms.DUPLEX) {
			this.pop(level);
			return;
		}
		LivingEntity target = this.getTarget();
		if (target != null && (target == player || target instanceof XenoDouble twin && twin.isDoubleOf(player))) {
			this.setTarget(null);
		}
	}

	/** Gone in a puff of copper (merged back, or its owner stopped being Duplex). */
	public void pop(ServerLevel level) {
		level.sendParticles(ParticleTypes.WAX_OFF, this.getX(), this.getY() + this.getBbHeight() * 0.5, this.getZ(), 10, 0.3, 0.5, 0.3, 0.05);
		level.sendParticles(ParticleTypes.POOF, this.getX(), this.getY() + this.getBbHeight() * 0.5, this.getZ(), 6, 0.25, 0.4, 0.25, 0.02);
		this.discard();
	}

	// ---------------------------------------------------------------- fighting

	/** Its owner's blows pass through it. */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (source.getEntity() != null && this.isDoubleOf(source.getEntity())) {
			return false;
		}
		return super.hurtServer(level, source, damage);
	}

	/** A double's death stings its owner. */
	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel level) {
			ServerPlayer player = this.ownerPlayer();
			if (player != null) {
				com.jaymospowers.power.xenoform.forms.Duplex.doubleDied(player, level);
			}
		}
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	/** A double is a moment's copy: never written to disk. */
	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.COPPER_GOLEM_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.COPPER_GOLEM_DEATH;
	}

	/** Walks back to its owner when it strays, and blinks to them when it's far behind. */
	static final class FollowGoal extends Goal {
		private final XenoDouble mob;
		private @Nullable ServerPlayer player;
		private int repath;

		FollowGoal(XenoDouble mob) {
			this.mob = mob;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			if (this.mob.getTarget() != null) {
				return false;
			}
			this.player = this.mob.ownerPlayer();
			return this.player != null && this.mob.distanceToSqr(this.player) > FOLLOW_START * FOLLOW_START;
		}

		@Override
		public boolean canContinueToUse() {
			return this.player != null && this.mob.getTarget() == null && !this.mob.getNavigation().isDone()
				&& this.mob.distanceToSqr(this.player) > FOLLOW_STOP * FOLLOW_STOP;
		}

		@Override
		public void start() {
			this.repath = 0;
		}

		@Override
		public void stop() {
			this.player = null;
			this.mob.getNavigation().stop();
		}

		@Override
		public void tick() {
			if (this.player == null) {
				return;
			}
			this.mob.getLookControl().setLookAt(this.player, 10.0F, this.mob.getMaxHeadXRot());
			if (--this.repath > 0) {
				return;
			}
			this.repath = 10;
			if (this.mob.distanceToSqr(this.player) > FOLLOW_TELEPORT * FOLLOW_TELEPORT) {
				this.mob.teleportTo(this.player.getX(), this.player.getY(), this.player.getZ());
				this.mob.getNavigation().stop();
			} else {
				this.mob.getNavigation().moveTo(this.player, FOLLOW_SPEED);
			}
		}
	}
}
