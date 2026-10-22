package com.jaymospowers.power.xenoform;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.CarryBlock;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
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
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * One of Duplex's doubles: a copy of the alien that steps out of it, follows it about and fights what it fights. It
 * belongs to its owner only while they are Duplex and nearby; otherwise it pops. It's never saved (a reload ends it).
 * Its owner is synced, so their HUD can count their doubles.
 * <p>
 * (0.15.96) Doubles fight as a swarm ({@link SwarmGoal}): every double on the same target takes a place in a ring
 * round it and they strike one after another, each blow harder the more of them there are. A double can also fetch
 * any solid block for its owner and carry it on its head ({@link #fetch}, {@link #deliver}); it lets the block fall if
 * it pops or dies.
 */
public class XenoDouble extends PathfinderMob {
	// ---- tuning ----
	public static final float MAX_HEALTH = 6.0F;
	public static final double ATTACK_DAMAGE = 3.0, MOVE_SPEED = 0.3, CHASE_SPEED = 1.3, FOLLOW_SPEED = 1.25;
	/** It walks back to its owner past {@link #FOLLOW_START} blocks, stops at {@link #FOLLOW_STOP}, and blinks to them past {@link #FOLLOW_TELEPORT}. */
	public static final double FOLLOW_START = 4.0, FOLLOW_STOP = 2.0, FOLLOW_TELEPORT = 24.0;
	/** How often (ticks) it checks its owner is still there and still Duplex. */
	static final int OWNER_CHECK = 10;
	/** The swarm: each extra double on the target adds this share to every blow (3 doubles: x1.7, 6: x2.75). */
	public static final double SWARM_BONUS = 0.35;
	/** Ticks between one double's blow and the next one's: {@link #SWARM_BASE_GAP} less one per double, never under {@link #SWARM_MIN_GAP}. */
	public static final int SWARM_BASE_GAP = 7, SWARM_MIN_GAP = 3;
	/** The ring round the target (blocks past its edge), how fast it turns (radians a tick), and reach for a blow (past its edge). */
	public static final double SWARM_RING = 1.1, SWARM_SPIN = 0.04, STRIKE_REACH = 2.2;
	/** The lunge with each blow (along the ground, up), and the shove the target takes. */
	public static final double LUNGE = 0.45, LUNGE_UP = 0.2, STRIKE_SHOVE = 0.15;
	/** Doubles this far from the target (or farther) aren't part of its swarm, and give up the chase. */
	public static final double SWARM_RANGE = 24.0;
	/** Fetching: how close it gets to a block to take it or set it down (blocks), and how long it tries (ticks). */
	public static final double ERRAND_REACH = 2.5;
	public static final int ERRAND_TICKS = 200;

	public static EntityType<XenoDouble> TYPE;

	private static final EntityDataAccessor<Optional<EntityReference<LivingEntity>>> DATA_OWNER = SynchedEntityData.defineId(XenoDouble.class,
		EntityDataSerializers.OPTIONAL_LIVING_ENTITY_REFERENCE);

	public XenoDouble(EntityType<? extends XenoDouble> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
		// sized like its owner as Duplex: a player's box at Duplex's scale (copper golem size)
		this.refreshDimensions();
	}

	static void init() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("xeno_double"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
			EntityType.Builder.<XenoDouble>of(XenoDouble::new, MobCategory.MISC)
				.sized(0.6F, 1.8F)
				.eyeHeight(1.62F)
				.clientTrackingRange(8)
				.noLootTable()
				.build(key));
		FabricDefaultAttributeRegistry.register(TYPE, createAttributes());
		// doubles aren't saved, so whatever they carry would go with them: set it down first
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (ServerLevel level : server.getAllLevels()) {
				for (XenoDouble twin : level.getEntities(TYPE, XenoDouble::carrying)) {
					twin.dropBlock();
				}
			}
		});
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
			.add(Attributes.SCALE, com.jaymospowers.power.xenoform.forms.Duplex.SCALE)
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
		this.goalSelector.addGoal(1, new ErrandGoal(this));
		this.goalSelector.addGoal(2, new SwarmGoal(this));
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
		if (!(this.level() instanceof ServerLevel level) || this.isRemoved()) {
			return;
		}
		Entity carried = this.carried();
		if (carried != null) {
			// the block on its head turns with it
			carried.setYRot(this.yBodyRot);
		}
		if (this.tickCount % OWNER_CHECK != 0) {
			return;
		}
		ServerPlayer player = this.ownerPlayer();
		if (player == null || !player.isAlive() || XenoformState.form(player) != XenoForms.DUPLEX) {
			this.pop(level);
			return;
		}
		LivingEntity target = this.getTarget();
		if (target != null && (target == player || target instanceof XenoDouble twin && twin.isDoubleOf(player) || this.busy())) {
			// (a double with a block on its head, or on its way to one, doesn't fight)
			this.setTarget(null);
		}
		// (0.15.80) it goes by the name its owner shows now (a character name given late, a costume put on), never a stale username
		Component name = player.getDisplayName();
		if (!name.equals(this.getCustomName())) {
			this.setCustomName(name);
		}
	}

	/** Gone in a puff of copper (merged back, or its owner stopped being Duplex); whatever it carried falls. */
	public void pop(ServerLevel level) {
		level.sendParticles(ParticleTypes.WAX_OFF, this.getX(), this.getY() + this.getBbHeight() * 0.5, this.getZ(), 10, 0.3, 0.5, 0.3, 0.05);
		level.sendParticles(ParticleTypes.POOF, this.getX(), this.getY() + this.getBbHeight() * 0.5, this.getZ(), 6, 0.25, 0.4, 0.25, 0.02);
		this.discard();
	}

	// ---------------------------------------------------------------- fighting

	/** Every double of the same owner on {@code target} (this one too, if it is), in a fixed order. */
	List<XenoDouble> swarm(LivingEntity target) {
		UUID owner = this.owner();
		List<XenoDouble> out = this.level().getEntities(TYPE, target.getBoundingBox().inflate(SWARM_RANGE),
			twin -> twin.isAlive() && twin.getTarget() == target && !twin.busy() && owner != null && owner.equals(twin.owner()));
		out.sort(Comparator.comparingInt(Entity::getId));
		return out;
	}

	/** A blow at {@code target}, one of {@code swarm} attackers: it leaps in and hits, harder the more there are. */
	void strike(ServerLevel level, LivingEntity target, int swarm) {
		Vec3 at = target.position().subtract(this.position()).horizontal();
		at = at.lengthSqr() < 1.0E-4 ? Vec3.ZERO : at.normalize();
		this.setDeltaMovement(this.getDeltaMovement().add(at.scale(LUNGE)).add(0.0, this.onGround() ? LUNGE_UP : 0.0, 0.0));
		this.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
		float damage = (float)(this.getAttributeValue(Attributes.ATTACK_DAMAGE) * (1.0 + SWARM_BONUS * (swarm - 1)));
		// blow on blow: the last double's hit doesn't shield the target from this one
		target.setInvulnerableTime(0);
		if (!target.hurtServer(level, this.damageSources().mobAttack(this), damage)) {
			return;
		}
		this.setLastHurtMob(target);
		target.setDeltaMovement(target.getDeltaMovement().add(at.x * STRIKE_SHOVE, 0.05, at.z * STRIKE_SHOVE));
		target.syncVelocity = true;
		level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 4 + swarm * 2, 0.3, 0.3, 0.3, 0.2);
		if (swarm > 1) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), swarm * 2, 0.3, 0.3, 0.3, 0.1);
		}
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.NEUTRAL, 0.6F,
			1.1F + swarm * 0.08F + this.random.nextFloat() * 0.1F);
	}

	/** (0.15.96) It drops whatever it carries when it goes for good (popped or killed). */
	@Override
	public void remove(Entity.RemovalReason reason) {
		if (reason.shouldDestroy()) {
			this.dropBlock();
		}
		super.remove(reason);
	}

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
		this.dropBlock();
		super.die(source);
		if (this.level() instanceof ServerLevel level) {
			ServerPlayer player = this.ownerPlayer();
			if (player != null) {
				com.jaymospowers.power.xenoform.forms.Duplex.doubleDied(player, level);
			}
		}
	}

	// ---------------------------------------------------------------- carrying (0.15.96)

	private @Nullable BlockPos errandPos;
	/** Fetching: the block it was sent for (it gives up if that changes). Null when setting down. */
	private @Nullable BlockState errandState;
	/** Setting down: which way its owner faced when they chose the spot (the block is turned as if they placed it). */
	private Direction errandFacing = Direction.NORTH;
	private int errandTicks;

	/** The carried block's display on its head, if any. */
	public @Nullable Entity carried() {
		for (Entity passenger : this.getPassengers()) {
			if (CarryBlock.is(passenger)) {
				return passenger;
			}
		}
		return null;
	}

	public boolean carrying() {
		return this.carried() != null;
	}

	/** Carrying something, or on its way to fetch or set something down: it doesn't fight. */
	public boolean busy() {
		return this.errandPos != null || this.carrying();
	}

	/** Sends it for the block at {@code pos} (its owner may take it: checked already). */
	public void fetch(BlockPos pos, BlockState state) {
		this.errandPos = pos.immutable();
		this.errandState = state;
		this.errandTicks = ERRAND_TICKS;
		this.setTarget(null);
	}

	/** Sends it to set its block down at {@code pos} (a free spot its owner may build in: checked already). */
	public void deliver(BlockPos pos, Direction facing) {
		this.errandPos = pos.immutable();
		this.errandState = null;
		this.errandFacing = facing;
		this.errandTicks = ERRAND_TICKS;
		this.setTarget(null);
	}

	/** Lets whatever it carries fall where it is (as a falling block, or its item if there's no room). */
	public void dropBlock() {
		this.errandPos = null;
		Entity carried = this.carried();
		if (carried != null && this.level() instanceof ServerLevel level) {
			carried.stopRiding();
			CarryBlock.land(level, carried, null, Vec3.ZERO);
		}
	}

	/** At the errand's spot: takes the block, or sets its own down. Returns whether the errand is over. */
	private boolean finishErrand(ServerLevel level, BlockPos pos) {
		Entity carried = this.carried();
		ServerPlayer player = this.ownerPlayer();
		if (this.errandState != null) {
			BlockState state = level.getBlockState(pos);
			if (carried != null || player == null || state != this.errandState || !CarryBlock.liftableAny(level, pos, state)
				|| !player.mayInteract(level, pos) || player.blockActionRestricted(level, pos, player.gameMode())) {
				return true;
			}
			Entity display = CarryBlock.takeOut(level, pos, state, this, true);
			display.startRiding(this, true, false);
			level.sendParticles(ParticleTypes.WAX_ON, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.05);
			level.playSound(null, this, SoundEvents.COPPER_GOLEM_ITEM_GET, SoundSource.NEUTRAL, 1.0F, 1.0F);
			return true;
		}
		if (carried == null) {
			return true;
		}
		BlockState state = CarryBlock.facing(CarryBlock.stateOf(carried), this.errandFacing);
		if (!level.getBlockState(pos).canBeReplaced() || !state.canSurvive(level, pos)
			|| !level.isUnobstructed(state, pos, net.minecraft.world.phys.shapes.CollisionContext.empty())
			|| player != null && (!player.mayInteract(level, pos) || player.blockActionRestricted(level, pos, player.gameMode()))) {
			// somebody's in the way now: it keeps hold of it
			level.playSound(null, this, SoundEvents.COPPER_GOLEM_ITEM_NO_DROP, SoundSource.NEUTRAL, 1.0F, 1.0F);
			return true;
		}
		carried.stopRiding();
		CarryBlock.place(level, carried, pos, this.errandFacing);
		level.playSound(null, this, SoundEvents.COPPER_GOLEM_ITEM_DROP, SoundSource.NEUTRAL, 1.0F, 1.0F);
		return true;
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

	/**
	 * (0.15.96) The swarm: every double on a target takes its own place in a ring round it (the ring slowly turning), and
	 * they strike in turn, one right after another; the more of them there are, the quicker and harder the blows.
	 */
	static final class SwarmGoal extends Goal {
		private final XenoDouble mob;
		private int repath;

		SwarmGoal(XenoDouble mob) {
			this.mob = mob;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		private @Nullable LivingEntity quarry() {
			LivingEntity target = this.mob.getTarget();
			return target != null && target.isAlive() && !this.mob.busy() && this.mob.canAttack(target)
				&& this.mob.distanceToSqr(target) < SWARM_RANGE * SWARM_RANGE ? target : null;
		}

		@Override
		public boolean canUse() {
			return this.quarry() != null;
		}

		@Override
		public boolean canContinueToUse() {
			return this.quarry() != null;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void start() {
			this.repath = 0;
			this.mob.setAggressive(true);
		}

		@Override
		public void stop() {
			this.mob.setAggressive(false);
			this.mob.getNavigation().stop();
		}

		@Override
		public void tick() {
			LivingEntity target = this.mob.getTarget();
			if (target == null || !(this.mob.level() instanceof ServerLevel level)) {
				return;
			}
			this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
			long time = level.getGameTime();
			List<XenoDouble> swarm = this.mob.swarm(target);
			int n = Math.max(1, swarm.size());
			int i = Math.max(0, swarm.indexOf(this.mob));
			int gap = Math.max(SWARM_MIN_GAP, SWARM_BASE_GAP - n);
			double reach = target.getBbWidth() * 0.5 + STRIKE_REACH;
			// my turn: the beat goes round the ring
			if (time % gap == 0 && (time / gap) % n == i && this.mob.distanceToSqr(target) <= reach * reach && this.mob.hasLineOfSight(target)) {
				this.mob.strike(level, target, n);
			}
			if (--this.repath <= 0) {
				this.repath = 4;
				double angle = time * SWARM_SPIN + Math.PI * 2.0 * i / n;
				double r = target.getBbWidth() * 0.5 + SWARM_RING;
				this.mob.getNavigation().moveTo(target.getX() + Math.cos(angle) * r, target.getY(), target.getZ() + Math.sin(angle) * r, CHASE_SPEED);
			}
		}
	}

	/** (0.15.96) Walks to a block to take it, or to a spot to set its own down, and gives up after a while. */
	static final class ErrandGoal extends Goal {
		private final XenoDouble mob;
		private int repath;

		ErrandGoal(XenoDouble mob) {
			this.mob = mob;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return this.mob.errandPos != null;
		}

		@Override
		public void start() {
			this.repath = 0;
		}

		@Override
		public void stop() {
			this.mob.getNavigation().stop();
		}

		@Override
		public void tick() {
			BlockPos pos = this.mob.errandPos;
			if (pos == null || !(this.mob.level() instanceof ServerLevel level)) {
				return;
			}
			Vec3 centre = Vec3.atCenterOf(pos);
			this.mob.getLookControl().setLookAt(centre.x, centre.y, centre.z);
			if (this.mob.position().add(0.0, this.mob.getBbHeight() * 0.5, 0.0).distanceToSqr(centre) <= ERRAND_REACH * ERRAND_REACH) {
				this.mob.finishErrand(level, pos);
				this.mob.errandPos = null;
				return;
			}
			if (--this.mob.errandTicks <= 0) {
				// can't get there
				this.mob.errandPos = null;
				return;
			}
			if (--this.repath <= 0) {
				this.repath = 10;
				this.mob.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, FOLLOW_SPEED);
			}
		}
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
