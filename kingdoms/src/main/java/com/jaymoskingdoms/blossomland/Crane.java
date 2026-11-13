package com.jaymoskingdoms.blossomland;

import com.jaymoskingdoms.KitchenCompat;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The red-crowned crane of the paddies, in pairs.
 * <ul>
 * <li><b>What it does.</b> It wades the fields by day picking the pests off the young crop
 * ({@link #FORAGE_EVERY}), keeps by its mate, and in spring the pair dance: wings out, bowing and leaping.
 * It will not be run at: it leaves a sprinting player well alone, and lets a walking one come close.</li>
 * <li><b>What comes back.</b> Every crop a crane has picked over grows on a stage, so a village whose paddies keep
 * their cranes takes in more rice than one that has driven them off.</li>
 * <li><b>Looked after.</b> Nothing to give it: leave the paddies wet and walk, don't run, among them.</li>
 * </ul>
 */
public class Crane extends BlossomAnimal {
	/** The dancing pose (wings out); the model flaps from this pose number up. */
	public static final int DANCING = 2;
	static final double HEALTH = 12.0, SPEED = 0.22;
	static final int FORAGE_EVERY = 1200, FORAGE_JITTER = 600, PECK_TICKS = 40;
	static final int DANCE_TICKS = 120, DANCE_REST = 6000, HOP_EVERY = 20;
	static final double MATE_RANGE = 5.0;

	private int forageIn, danceRest, danceLeft;

	public Crane(EntityType<? extends Crane> type, Level level) {
		super(type, level);
		this.forageIn = this.random.nextInt(FORAGE_EVERY);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createAnimalAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.SAFE_FALL_DISTANCE, 12.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, this.fleePredators(12.0F, 1.5));
		this.goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Player.class, 10.0F, 1.4, 1.8, e -> e.isSprinting()));
		this.goalSelector.addGoal(1, new PanicGoal(this, 1.8));
		this.goalSelector.addGoal(2, new Dance());
		this.goalSelector.addGoal(3, new SeekBlock(8, 2, 100, 1.0, 1.8) {
			@Override
			protected boolean wants() {
				return Crane.this.forageIn == 0 && Crane.this.level().isBrightOutside();
			}

			@Override
			protected boolean matches(ServerLevel level, BlockPos pos, BlockState state) {
				return state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state);
			}

			@Override
			protected void arrive(ServerLevel level, BlockPos pos) {
				// the pests it takes off a young plant are a stage of growth given back
				BlockState state = level.getBlockState(pos);
				CropBlock crop = (CropBlock) state.getBlock();
				level.setBlock(pos, crop.getStateForAge(Math.min(crop.getMaxAge(), crop.getAge(state) + 1)), 2);
				Crane.this.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
				Crane.this.headDown(PECK_TICKS);
				Crane.this.forageIn = FORAGE_EVERY + Crane.this.random.nextInt(FORAGE_JITTER);
			}
		});
		this.goalSelector.addGoal(4, new KeepTogether(8.0, 1.0));
		this.goalSelector.addGoal(6, new RandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.forageIn > 0) {
			this.forageIn--;
		}
		if (this.danceRest > 0) {
			this.danceRest--;
		}
	}

	/** Spring, or a world with no seasons. */
	private boolean courting() {
		String season = KitchenCompat.season(this.level());
		return season == null || season.equals("spring");
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.PARROT_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.PARROT_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.PARROT_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.6F;
	}

	/** The pair's dance: both stand facing, wings out, and hop. */
	private final class Dance extends Goal {
		private Crane mate;

		Dance() {
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			Crane me = Crane.this;
			if (me.danceLeft > 0) {
				return true;
			}
			if (me.danceRest > 0 || (me.tickCount + me.getId()) % 60 != 0 || !me.level().isBrightOutside() || !me.courting()) {
				return false;
			}
			List<Crane> near = me.level().getEntitiesOfClass(Crane.class, me.getBoundingBox().inflate(MATE_RANGE), c -> c != me && c.danceRest == 0 && c.danceLeft == 0);
			if (near.isEmpty()) {
				return false;
			}
			this.mate = near.get(0);
			me.danceLeft = DANCE_TICKS;
			this.mate.danceLeft = DANCE_TICKS;
			return true;
		}

		@Override
		public boolean canContinueToUse() {
			return Crane.this.danceLeft > 0;
		}

		@Override
		public void start() {
			Crane.this.getNavigation().stop();
			Crane.this.setLook(DANCING);
		}

		@Override
		public void tick() {
			Crane me = Crane.this;
			me.danceLeft--;
			Crane partner = this.mate != null ? this.mate : me.level().getEntitiesOfClass(Crane.class, me.getBoundingBox().inflate(MATE_RANGE), c -> c != me).stream().findFirst().orElse(null);
			if (partner != null) {
				me.getLookControl().setLookAt(partner);
			}
			if (me.danceLeft % HOP_EVERY == (me.getId() & 1) * (HOP_EVERY / 2) && me.onGround()) {
				me.getJumpControl().jump();
			}
		}

		@Override
		public void stop() {
			Crane.this.danceLeft = 0;
			Crane.this.danceRest = DANCE_REST;
			Crane.this.setLook(STANDING);
			this.mate = null;
		}
	}
}
