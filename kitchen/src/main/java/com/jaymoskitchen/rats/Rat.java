package com.jaymoskitchen.rats;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Andy: "rat interactions with the kitchen ... better animations and traps for getting them out" (after the Rats mod,
 * LGPL-3.0, by Alexthe666 and its maintainers: its rat model, textures and sounds, our own code). A rat that comes for
 * food left out and unguarded ({@link RatSpawning}): it creeps up to a store of food ({@link FoodStores}), sniffs, sits
 * up and nibbles, then either eats a piece (and may come back later) or runs off with it in its mouth and is gone with
 * it, unless it's killed first (it drops what it took). It runs from players who aren't sneaking and from cats, and
 * baited traps draw it before anything else ({@link SnapTrapBlock}, {@link CageTrapBlock}).
 */
public class Rat extends PathfinderMob {
	/** Hearts (half-hearts) and speed: weak and quick. */
	static final double HEALTH = 4.0, SPEED = 0.3;
	/** How far a rat looks for food and bait (blocks). */
	static final double RAID_RANGE = 16.0;
	/** Speed while creeping up on food, and while running off. */
	static final double CREEP_SPEED = 0.7, RUN_SPEED = 1.5;
	/** How long it sniffs, then nibbles, once it's at the food (ticks). */
	static final int SNIFF_TICKS = 30, NIBBLE_TICKS = 60;
	/** How long a rat that ate leaves the food be before coming back (ticks). */
	static final int FED_COOLDOWN = 1200;
	/** The chance a rat at the food runs off with a piece rather than eating it there. */
	static final float STEAL_CHANCE = 0.5F;
	/** Players closer than this scare it off (unless they're sneaking); cats, from further. */
	static final float PLAYER_FEAR = 6.0F, CAT_FEAR = 10.0F;
	/** A thief that's this far from where it stole, with no player this close, slips away with the food. */
	static final double ESCAPE_DISTANCE = 16.0, UNSEEN_DISTANCE = 12.0;
	/** A thief that can't get away in this long goes anyway (ticks). */
	static final int ESCAPE_GIVE_UP = 600;
	/** The coats a rat can have (the Rats mod's wild rats): brown, black and blue-grey. */
	public static final int COATS = 3;

	/** What it's doing, for its animations: nothing much, sniffing, or sitting up nibbling. */
	public static final byte IDLE = 0, SNIFFING = 1, NIBBLING = 2;
	private static final EntityDataAccessor<Byte> DATA_ACTION = SynchedEntityData.defineId(Rat.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Boolean> DATA_FLEEING = SynchedEntityData.defineId(Rat.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> DATA_COAT = SynchedEntityData.defineId(Rat.class, EntityDataSerializers.INT);

	/** Ticks before it raids again (after a meal). */
	private int fedCooldown;
	/** Where it stole from, while it runs off with the food; null otherwise. */
	private @Nullable BlockPos stoleFrom;
	private int escapeTicks;

	public Rat(EntityType<? extends Rat> type, Level level) {
		super(type, level);
		setDropChance(EquipmentSlot.MAINHAND, 2.0F);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, HEALTH)
			.add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.FOLLOW_RANGE, RAID_RANGE);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new PanicGoal(this, RUN_SPEED));
		goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Cat.class, CAT_FEAR, 1.2, RUN_SPEED));
		goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Player.class, e -> !e.isShiftKeyDown(), PLAYER_FEAR, 1.2, RUN_SPEED,
			EntitySelector.NO_CREATIVE_OR_SPECTATOR));
		goalSelector.addGoal(3, new EscapeGoal());
		goalSelector.addGoal(4, new RaidGoal());
		goalSelector.addGoal(5, new SniffGoal());
		goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
		goalSelector.addGoal(7, new RandomLookAroundGoal(this));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ACTION, IDLE);
		builder.define(DATA_FLEEING, false);
		builder.define(DATA_COAT, 0);
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
		@Nullable SpawnGroupData groupData) {
		setCoat(random.nextInt(COATS));
		return super.finalizeSpawn(level, difficulty, reason, groupData);
	}

	public byte action() {
		return entityData.get(DATA_ACTION);
	}

	void setAction(byte action) {
		entityData.set(DATA_ACTION, action);
	}

	/** Running for it (from a player or a cat, hurt, or off with stolen food): its sprint. */
	public boolean fleeing() {
		return entityData.get(DATA_FLEEING);
	}

	public int coat() {
		return entityData.get(DATA_COAT);
	}

	public void setCoat(int coat) {
		entityData.set(DATA_COAT, Math.floorMod(coat, COATS));
	}

	/** Sends it running from {@code from} for a while (a trap went off near it, it was let out of a cage). */
	public void scare(Vec3 from) {
		Vec3 away = DefaultRandomPos.getPosAway(this, 12, 4, from);
		if (away != null) {
			getNavigation().moveTo(away.x, away.y, away.z, RUN_SPEED);
		}
		fedCooldown = Math.max(fedCooldown, FED_COOLDOWN / 2);
	}

	/** Lets a caged rat go: it runs off and doesn't come back. */
	public void release(BlockPos cage) {
		stoleFrom = cage;
		escapeTicks = 0;
	}

	/** (Clients) how far it's sat up to nibble (0-{@link #SIT_FULL}) and into a sniff (0-{@link #SNIFF_FULL}), for its model. */
	public float sitProgress, sitProgressO, sniffProgress, sniffProgressO;
	public static final float SIT_FULL = 20.0F, SNIFF_FULL = 5.0F;
	private static final float SIT_STEP = 2.0F, SNIFF_STEP = 1.0F;

	@Override
	public void aiStep() {
		super.aiStep();
		if (level().isClientSide()) {
			sitProgressO = sitProgress;
			sniffProgressO = sniffProgress;
			byte action = action();
			sitProgress = action == NIBBLING ? Math.min(SIT_FULL, sitProgress + SIT_STEP) : Math.max(0.0F, sitProgress - SIT_STEP);
			sniffProgress = action == SNIFFING ? Math.min(SNIFF_FULL, sniffProgress + SNIFF_STEP) : Math.max(0.0F, sniffProgress - SNIFF_STEP);
		} else {
			if (fedCooldown > 0) {
				fedCooldown--;
			}
			boolean running = isPanicking() || stoleFrom != null
				|| (getNavigation().isInProgress() && getMoveControl().getSpeedModifier() > 1.1);
			if (running != fleeing()) {
				entityData.set(DATA_FLEEING, running);
			}
		}
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("coat", coat());
		output.putInt("fed_cooldown", fedCooldown);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		setCoat(input.getIntOr("coat", 0));
		fedCooldown = input.getIntOr("fed_cooldown", 0);
	}

	@Override
	public int getAmbientSoundInterval() {
		return 200;
	}

	@Override
	protected float getSoundVolume() {
		return 0.6F;
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return Rats.RAT_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return Rats.RAT_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return Rats.RAT_DEATH;
	}

	/** Creeps up to the nearest bait or food store, sniffs, nibbles, and eats or steals a piece. */
	class RaidGoal extends Goal {
		private FoodStores.@Nullable Target target;
		private int scanDelay;
		private int atTicks;
		private int stuckTicks;

		RaidGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			if (fedCooldown > 0 || stoleFrom != null || !getMainHandItem().isEmpty() || --scanDelay > 0) {
				return false;
			}
			scanDelay = reducedTickDelay(40);
			target = FoodStores.nearest((ServerLevel) level(), Rat.this, RAID_RANGE);
			return target != null;
		}

		@Override
		public boolean canContinueToUse() {
			return target != null && target.stillThere((ServerLevel) level()) && stoleFrom == null && fedCooldown == 0
				&& stuckTicks < 200;
		}

		@Override
		public void start() {
			atTicks = 0;
			stuckTicks = 0;
			moveToTarget();
		}

		@Override
		public void stop() {
			setAction(IDLE);
			target = null;
			getNavigation().stop();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		private void moveToTarget() {
			Vec3 at = target.at();
			getNavigation().moveTo(at.x, at.y, at.z, CREEP_SPEED);
		}

		@Override
		public void tick() {
			Vec3 at = target.at();
			getLookControl().setLookAt(at.x, at.y, at.z);
			if (target.trap()) {
				// a trap goes off when the rat's on it (see the traps' ticks): just walk in
				if (!getNavigation().isInProgress()) {
					getNavigation().moveTo(at.x, at.y, at.z, CREEP_SPEED);
				}
				stuckTicks++;
				return;
			}
			if (distanceToSqr(at) > target.reachSqr()) {
				if (!getNavigation().isInProgress()) {
					moveToTarget();
				}
				stuckTicks++;
				setAction(IDLE);
				return;
			}
			getNavigation().stop();
			atTicks++;
			ServerLevel level = (ServerLevel) level();
			if (atTicks < SNIFF_TICKS) {
				setAction(SNIFFING);
				return;
			}
			setAction(NIBBLING);
			if (atTicks % 10 == 0) {
				playSound(SoundEvents.GENERIC_EAT.value(), 0.4F, 1.6F + random.nextFloat() * 0.3F);
				ItemStack shown = target.peek(level);
				if (!shown.isEmpty()) {
					Vec3 mouth = position().add(getLookAngle().scale(0.3)).add(0, 0.3, 0);
					level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, shown.getItem()), mouth.x, mouth.y, mouth.z, 3,
						0.05, 0.05, 0.05, 0.05);
				}
			}
			if (atTicks < SNIFF_TICKS + NIBBLE_TICKS) {
				return;
			}
			boolean steal = !target.carcass() && random.nextFloat() < STEAL_CHANCE;
			ItemStack taken = target.take(level, Rat.this);
			if (steal && !taken.isEmpty()) {
				setItemSlot(EquipmentSlot.MAINHAND, taken);
				stoleFrom = blockPosition();
				escapeTicks = 0;
				level.playSound(null, getX(), getY(), getZ(), Rats.RAT_AMBIENT, SoundSource.NEUTRAL, 0.8F, 1.3F);
			} else {
				fedCooldown = FED_COOLDOWN;
			}
			target = null;
		}
	}

	/** A thief (or a rat let out of a cage) runs off and, once it's far enough and nobody's near, slips away for good. */
	class EscapeGoal extends Goal {
		EscapeGoal() {
			setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return stoleFrom != null;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			escapeTicks++;
			Vec3 from = Vec3.atCenterOf(stoleFrom);
			boolean far = distanceToSqr(from) > ESCAPE_DISTANCE * ESCAPE_DISTANCE;
			Player near = level().getNearestPlayer(Rat.this, UNSEEN_DISTANCE);
			if ((far && (near == null || near.isSpectator())) || escapeTicks > ESCAPE_GIVE_UP) {
				// gone into a hole somewhere, with whatever it took
				((ServerLevel) level()).sendParticles(ParticleTypes.POOF, getX(), getY() + 0.1, getZ(), 4, 0.1, 0.05, 0.1, 0.01);
				discard();
				return;
			}
			if (!getNavigation().isInProgress()) {
				Vec3 away = DefaultRandomPos.getPosAway(Rat.this, 16, 5, from);
				if (away != null) {
					getNavigation().moveTo(away.x, away.y, away.z, RUN_SPEED);
				}
			}
		}
	}

	/** Now and then it stops where it is and sniffs the air. */
	class SniffGoal extends Goal {
		private int ticks;

		SniffGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return random.nextInt(reducedTickDelay(160)) == 0 && onGround() && action() == IDLE;
		}

		@Override
		public boolean canContinueToUse() {
			return ticks > 0;
		}

		@Override
		public void start() {
			ticks = 30 + random.nextInt(40);
			getNavigation().stop();
			setAction(SNIFFING);
		}

		@Override
		public void tick() {
			ticks--;
		}

		@Override
		public void stop() {
			setAction(IDLE);
		}
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return true;
	}
}
