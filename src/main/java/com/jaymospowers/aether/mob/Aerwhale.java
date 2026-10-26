package com.jaymospowers.aether.mob;

import java.util.EnumSet;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The aerwhale: a huge, quiet whale (as long as Kingdoms' great whales, about fifteen blocks) that drifts across the sky on its own, picking a spot 16 to 32 blocks away and sailing
 * to it, never into a wall. It does nothing else (no riding, no drops). Its call carries far.
 */
public class Aerwhale extends Mob {
	// ---- tuning ----
	public static final double HEALTH = 20.0, FLYING_SPEED = 0.2, SPEED = 0.2;
	/** The next waypoint is this far (blocks), and it pushes this hard toward one (scaled by its flying speed). */
	public static final int TRAVEL_MIN = 16, TRAVEL_MAX = 32;
	public static final double PUSH = 0.05;
	/** Air drag each tick, and the song's gap (ticks). */
	public static final double DRAG = 0.91;
	public static final int SONG_EVERY = 1200;
	/** The sky it stays inside (blocks from the world's ends). */
	public static final int MARGIN = 24;
	/** Its body (blocks; the head and fluke reach far past it, about fifteen blocks nose to tail). At least the great whale's 4 x 2.8. */
	public static final float WIDTH = 5.0F, HEIGHT = 3.6F;
	/** A wild one, once it has found room on the ground, rises this far (blocks) into open sky before it is seen. */
	public static final int SPAWN_RISE_MIN = 8, SPAWN_RISE_MAX = 20;

	public Aerwhale(EntityType<? extends Aerwhale> type, Level level) {
		super(type, level);
		this.moveControl = new WhaleMoveControl(this);
		this.xpReward = 2;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.FLYING_SPEED, FLYING_SPEED).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.FOLLOW_RANGE, 64.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(5, new FloatAroundGoal());
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
		@Nullable SpawnGroupData groupData) {
		// spawn rules place it on the ground; a wild one starts up in the sky instead, where there is room for all of it
		if (reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.CHUNK_GENERATION) {
			int high = level.getMaxY() - MARGIN;
			for (int attempt = 0; attempt < 4; attempt++) {
				double y = Math.min(this.getY() + SPAWN_RISE_MIN + this.random.nextInt(SPAWN_RISE_MAX - SPAWN_RISE_MIN + 1), high);
				if (y > this.getY() && level.noCollision(this, this.getBoundingBox().move(0.0, y - this.getY(), 0.0))) {
					this.setPos(this.getX(), y, this.getZ());
					break;
				}
			}
		}
		return super.finalizeSpawn(level, difficulty, reason, groupData);
	}

	@Override
	public void travel(Vec3 input) {
		// it floats: moved by its own push, slowed by the air, never pulled down
		this.move(MoverType.SELF, this.getDeltaMovement());
		this.setDeltaMovement(this.getDeltaMovement().scale(DRAG));
	}

	@Override
	public boolean isNoGravity() {
		return true;
	}

	@Override
	public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	protected void checkFallDamage(double ya, boolean onGround, BlockState state, net.minecraft.core.BlockPos pos) {
	}

	@Override
	public int getAmbientSoundInterval() {
		return SONG_EVERY;
	}

	@Override
	protected float getSoundVolume() {
		return 2.0F;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return MobSounds.get("entity.dolphin.ambient");
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.DOLPHIN_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.DOLPHIN_DEATH;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return true;
	}

	/** Pushes toward its waypoint, stops if the way is blocked, and turns to face where it goes. */
	private static final class WhaleMoveControl extends MoveControl<Aerwhale> {
		private int pause;

		WhaleMoveControl(Aerwhale whale) {
			super(whale);
		}

		@Override
		public void tick() {
			if (this.operation != MoveControl.Operation.MOVE_TO) {
				return;
			}
			if (this.pause-- <= 0) {
				this.pause += this.mob.getRandom().nextInt(5) + 2;
				Vec3 way = new Vec3(this.wantedX - this.mob.getX(), this.wantedY - this.mob.getY(), this.wantedZ - this.mob.getZ());
				double length = way.length();
				if (length < 2.0) {
					this.operation = MoveControl.Operation.WAIT;
					return;
				}
				way = way.scale(1.0 / length);
				if (clear(this.mob, way, (int)Math.ceil(length))) {
					double push = PUSH * this.mob.getAttributeValue(Attributes.FLYING_SPEED) * 10.0 * this.speedModifier;
					this.mob.setDeltaMovement(this.mob.getDeltaMovement().add(way.scale(push)));
				} else {
					this.operation = MoveControl.Operation.WAIT;
				}
			}
			Vec3 motion = this.mob.getDeltaMovement();
			if (motion.horizontalDistanceSqr() > 1.0E-4) {
				float yaw = -((float)Mth.atan2(motion.x, motion.z)) * Mth.RAD_TO_DEG;
				this.mob.setYRot(yaw);
				this.mob.yBodyRot = yaw;
			}
			this.mob.setXRot((float)(-Math.atan2(motion.y, motion.horizontalDistance()) * Mth.RAD_TO_DEG) * 0.6F);
		}

		/** Whether its box can travel {@code steps} blocks along {@code way}. */
		static boolean clear(Aerwhale whale, Vec3 way, int steps) {
			AABB box = whale.getBoundingBox();
			for (int i = 1; i <= steps; i += 2) {
				box = box.move(way.scale(2.0));
				if (!whale.level().noCollision(whale, box)) {
					return false;
				}
			}
			return true;
		}
	}

	/** Picks a spot 16 to 32 blocks off that it can sail to in a straight line, and goes there. */
	private final class FloatAroundGoal extends Goal {
		FloatAroundGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return !Aerwhale.this.getMoveControl().hasWanted();
		}

		@Override
		public boolean canContinueToUse() {
			return false;
		}

		@Override
		public void start() {
			Aerwhale self = Aerwhale.this;
			var random = self.getRandom();
			int low = self.level().getMinY() + MARGIN, high = self.level().getMaxY() - MARGIN;
			for (int attempt = 0; attempt < 6; attempt++) {
				double angle = random.nextDouble() * Math.PI * 2.0;
				double dist = TRAVEL_MIN + random.nextDouble() * (TRAVEL_MAX - TRAVEL_MIN);
				double y = Mth.clamp(self.getY() + (random.nextDouble() - 0.5) * 12.0, low, high);
				Vec3 to = new Vec3(self.getX() + Math.cos(angle) * dist, y, self.getZ() + Math.sin(angle) * dist);
				Vec3 way = to.subtract(self.position());
				if (WhaleMoveControl.clear(self, way.normalize(), (int)Math.ceil(way.length()))) {
					self.getMoveControl().setWantedPosition(to.x, to.y, to.z, 1.0);
					return;
				}
			}
		}
	}
}
