package com.jaymoskingdoms.deep;

import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A deep beast (Andy, 2026-11-25, from Rim's sea beasts: "can keep the shapes", repainted and renamed): a giant that comes up
 * out of the Abyssal Chasm on a rising schedule ({@link Deeps}) and, like a real deep predator, lives by three rules.
 * <ul>
 * <li><b>Hunger.</b> It hunts (players, villagers, grazing animals) only while hungry. A kill is a meal
 * ({@link #MEAL_TICKS}), a fish or meat thrown to it is a snack ({@link #SNACK_TICKS}); a fed beast lets everything alone
 * and goes home, to hunt again when the meal is gone.</li>
 * <li><b>Territory.</b> It keeps to the water and ground round its lair, where it first came up: it never chases past
 * {@link #TERRITORY} blocks of it, and a fed one wanders back.</li>
 * <li><b>Retreat.</b> Hurt past {@link #RETREAT_AT} of its health it breaks off and makes for its lair to heal
 * ({@link #HEAL_WET}, {@link #HEAL_DRY} health a second), and only turns out again at {@link #RECOVERED_AT}.</li>
 * </ul>
 * Stewardship: whoever feeds one is its friend for a day ({@link #FRIEND_TICKS}): it won't go for them (unless they strike
 * it), and now and then it sheds a prismarine shard or a nautilus shell for them. Leaving it alone costs nothing: they only
 * come at the schedule, and go back down.
 * <p>
 * Darktide's risen (Powers raises a corpse's shadow and marks it with {@link #RISEN_TAG}) are commanded: a commanded beast
 * has none of the above, fights what its master fights and goes where its master goes.
 * Every kind is its own entity type; the model is Rim's rig, ported (client {@code DeepBeastRenderer}).
 */
public final class DeepBeast extends Monster {
	// ---- tuning ----
	/** Ticks a kill and a snack keep it fed, and the most a belly holds (a day and a half). */
	static final int MEAL_TICKS = 24000, SNACK_TICKS = 3000, FULL_TICKS = 36000;
	/** A fed beast's friend (the one who fed it) is spared this long. */
	static final int FRIEND_TICKS = 24000;
	/** The chance a snack sheds a gift for the friend. */
	static final float GIFT_CHANCE = 0.3F;
	/** How far (blocks) it ranges from its lair, and how far it sees prey. */
	static final double TERRITORY = 112.0, SIGHT = 40.0;
	/** Its health share it breaks off at and turns out again at, and the health it heals a second in water and out of it. */
	static final float RETREAT_AT = 0.35F, RECOVERED_AT = 0.7F;
	static final float HEAL_WET = 1.5F, HEAL_DRY = 0.3F;
	/** How long its jaw is open after a bite (ticks). */
	static final int BITE_TICKS = 12;
	/** Strength of its swim toward prey (blocks a tick, up and along). */
	static final double SWIM_PUSH = 0.05;
	/** The scoreboard tag Darktide puts on what it raises ({@code DarktideTide.NO_CORPSE_TAG}, read by name). */
	public static final String RISEN_TAG = "jaymoskitchen.no_corpse";

	private static final EntityDataAccessor<Integer> DATA_BITE = SynchedEntityData.defineId(DeepBeast.class, EntityDataSerializers.INT);

	private final DeepSpecies species;
	private long fedUntil;
	private @Nullable BlockPos lair;
	private boolean retreating;
	private @Nullable UUID friend;
	private long friendUntil;
	private @Nullable LivingEntity lastTarget;

	public DeepBeast(EntityType<? extends DeepBeast> type, Level level) {
		super(type, level);
		this.species = Deeps.speciesOf(type);
		this.xpReward = 120;
		this.setPathfindingMalus(PathType.WATER, 0.0F);
	}

	public DeepSpecies species() {
		return this.species;
	}

	public static AttributeSupplier.Builder createAttributes(DeepSpecies species) {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, species.health).add(Attributes.ATTACK_DAMAGE, species.damage)
			.add(Attributes.MOVEMENT_SPEED, species.speed).add(Attributes.FOLLOW_RANGE, SIGHT).add(Attributes.ARMOR, 12.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.ATTACK_KNOCKBACK, 2.0).add(Attributes.STEP_HEIGHT, 2.0)
			.add(Attributes.ENTITY_INTERACTION_RANGE, 6.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_BITE, 0);
	}

	/** How open its jaw is, 0 (shut) to 1, for the client's model. */
	public float bite() {
		return this.entityData.get(DATA_BITE) / (float) BITE_TICKS;
	}

	/** Whether a master (Darktide) commands it. */
	public boolean commanded() {
		return this.entityTags().contains(RISEN_TAG);
	}

	/** Whether it is hungry: not fed, and no one's servant. */
	public boolean hungry() {
		return !commanded() && this.level().getGameTime() >= this.fedUntil;
	}

	boolean retreating() {
		return this.retreating;
	}

	/** Where it lives: the spot it first came up at. */
	BlockPos lair() {
		if (this.lair == null) {
			this.lair = this.blockPosition();
		}
		return this.lair;
	}

	void setLair(BlockPos pos) {
		this.lair = pos;
	}

	double lairDistance() {
		BlockPos l = lair();
		return Math.sqrt(this.distanceToSqr(l.getX() + 0.5, l.getY(), l.getZ() + 0.5));
	}

	/** Whether {@code prey} is something it would hunt now. */
	boolean preys(LivingEntity prey) {
		if (!hungry() || retreating || !prey.isAlive()) {
			return false;
		}
		if (prey instanceof Player p) {
			if (p.isCreative() || p.isSpectator() || isFriend(p)) {
				return false;
			}
		} else if (!(prey instanceof AbstractVillager) && !(prey instanceof Animal)) {
			return false;
		}
		BlockPos l = lair();
		return prey.distanceToSqr(l.getX() + 0.5, l.getY(), l.getZ() + 0.5) <= TERRITORY * TERRITORY;
	}

	private boolean isFriend(Player player) {
		return this.friend != null && this.friend.equals(player.getUUID()) && this.level().getGameTime() < this.friendUntil;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new Retreat());
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
		this.goalSelector.addGoal(3, new GoHome());
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 32.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false, (e, level) -> preys(e)));
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		this.entityData.set(DATA_BITE, BITE_TICKS);
		boolean hit = super.doHurtTarget(level, target);
		if (hit) {
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 3.0F, 0.5F);
		}
		return hit;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && !commanded() && !this.retreating && this.getHealth() < this.getMaxHealth() * RETREAT_AT && this.isAlive()) {
			// a deep predator that is losing breaks off and goes to ground
			this.retreating = true;
			this.setTarget(null);
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 2.0F, 0.6F);
		}
		return hurt;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		int bite = this.entityData.get(DATA_BITE);
		if (bite > 0) {
			this.entityData.set(DATA_BITE, bite - 1);
		}
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		lair();
		LivingEntity target = this.getTarget();
		// in the water it swims straight at what it hunts, where its legs would only walk the bottom
		if (target != null && this.isInWater() && !commanded()) {
			Vec3 to = target.getEyePosition().subtract(this.position().add(0.0, this.getBbHeight() * 0.5, 0.0));
			if (to.lengthSqr() > 4.0) {
				this.setDeltaMovement(this.getDeltaMovement().add(to.normalize().scale(SWIM_PUSH)));
			}
		}
		// a kill is a meal
		if (this.lastTarget != null && this.lastTarget != target && this.lastTarget.isDeadOrDying() && !commanded()
			&& this.distanceToSqr(this.lastTarget) < SIGHT * SIGHT) {
			this.fedUntil = Math.min(level.getGameTime() + MEAL_TICKS, level.getGameTime() + FULL_TICKS);
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.0F, 0.5F);
		}
		this.lastTarget = target;
		if (this.tickCount % 20 == 0 && this.isAlive()) {
			if (this.retreating) {
				this.heal(this.isInWater() ? HEAL_WET : HEAL_DRY);
				if (this.getHealth() >= this.getMaxHealth() * RECOVERED_AT) {
					this.retreating = false;
				}
			}
			if (this.tickCount % 40 == 0 && !commanded()) {
				// a beast that has come onto a town's land tolls its bell
				com.jaymoskingdoms.AlarmBells.toll(level, this);
			}
		}
		if (this.isInWater() && this.tickCount % 6 == 0) {
			level.sendParticles(ParticleTypes.BUBBLE, this.getX(), this.getY() + this.getBbHeight() * 0.6, this.getZ(), 3, this.getBbWidth() * 0.3, 0.5,
				this.getBbWidth() * 0.3, 0.02);
		}
	}

	/** A fish or meat thrown to it (use it on it): a snack, and a friend for the one who fed it. */
	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (commanded() || !(held.is(ItemTags.FISHES) || held.is(ItemTags.MEAT))) {
			return super.mobInteract(player, hand);
		}
		if (this.level() instanceof ServerLevel level) {
			long now = level.getGameTime();
			this.fedUntil = Math.min(Math.max(this.fedUntil, now) + SNACK_TICKS, now + FULL_TICKS);
			this.friend = player.getUUID();
			this.friendUntil = now + FRIEND_TICKS;
			if (this.getTarget() == player) {
				this.setTarget(null);
			}
			if (!player.getAbilities().instabuild) {
				held.shrink(1);
			}
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EAT, SoundSource.HOSTILE, 2.0F, 0.6F);
			if (this.random.nextFloat() < GIFT_CHANCE) {
				this.spawnAtLocation(level, new ItemStack(this.random.nextBoolean() ? Items.PRISMARINE_SHARD : Items.NAUTILUS_SHELL), 1.0F);
			}
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.deep.fed", this.getDisplayName()));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return !this.hasCustomName() && !commanded();
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putLong("FedUntil", this.fedUntil);
		output.putBoolean("Retreating", this.retreating);
		BlockPos l = lair();
		output.putInt("LairX", l.getX());
		output.putInt("LairY", l.getY());
		output.putInt("LairZ", l.getZ());
		if (this.friend != null) {
			output.putString("Friend", this.friend.toString());
			output.putLong("FriendUntil", this.friendUntil);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.fedUntil = input.getLongOr("FedUntil", 0L);
		this.retreating = input.getBooleanOr("Retreating", false);
		if (input.getInt("LairX").isPresent()) {
			this.lair = new BlockPos(input.getIntOr("LairX", 0), input.getIntOr("LairY", 0), input.getIntOr("LairZ", 0));
		}
		String id = input.getStringOr("Friend", "");
		if (!id.isEmpty()) {
			try {
				this.friend = UUID.fromString(id);
				this.friendUntil = input.getLongOr("FriendUntil", 0L);
			} catch (IllegalArgumentException e) {
				this.friend = null;
			}
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.WARDEN_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.WARDEN_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.WARDEN_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
		this.playSound(SoundEvents.RAVAGER_STEP, 1.2F, 0.5F);
	}

	/** Broken off, a beast makes for its lair (the deep water it came up from) and stays there until it has healed. */
	private final class Retreat extends Goal {
		Retreat() {
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return DeepBeast.this.retreating && !commanded();
		}

		@Override
		public void tick() {
			DeepBeast.this.setTarget(null);
			BlockPos l = lair();
			if (DeepBeast.this.lairDistance() > 6.0 && DeepBeast.this.tickCount % 10 == 0) {
				DeepBeast.this.getNavigation().moveTo(l.getX() + 0.5, l.getY(), l.getZ() + 0.5, 1.3);
			}
		}
	}

	/** Fed, or past its territory: back toward its lair. */
	private final class GoHome extends Goal {
		GoHome() {
			this.setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return !commanded() && DeepBeast.this.getTarget() == null && DeepBeast.this.lairDistance() > 24.0
				&& (!hungry() || DeepBeast.this.lairDistance() > TERRITORY);
		}

		@Override
		public void tick() {
			BlockPos l = lair();
			if (DeepBeast.this.tickCount % 10 == 0) {
				DeepBeast.this.getNavigation().moveTo(l.getX() + 0.5, l.getY(), l.getZ() + 0.5, 0.9);
			}
		}
	}
}
