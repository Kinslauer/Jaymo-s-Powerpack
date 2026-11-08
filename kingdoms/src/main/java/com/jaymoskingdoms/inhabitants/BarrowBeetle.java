package com.jaymoskingdoms.inhabitants;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The barrow beetle: a harmless little beetle of the cold barrows and caves, the example GeckoLib mob every other one
 * copies (docs/AI-HANDBOOK.md, "Adding a GeckoLib mob"). It scuttles about in the dark, runs when hurt and flutters its
 * wing cases. Model, texture and animations: kingdoms/art/barrow_beetle.bbmodel (our own, made by
 * kingdoms/tools/gen_tomb_undead.py), turned into GeckoLib's files by tools/bbmodel_geckolib.py.
 *
 * <p>What makes it a GeckoLib mob: it implements {@link GeoEntity}, keeps an {@link AnimatableInstanceCache}, and names its
 * animations in {@link #registerControllers}: a looping controller that picks walk or idle each frame, and a second one
 * that only plays what the server triggers ({@link #triggerAnim}, sent to every client watching).
 */
public class BarrowBeetle extends PathfinderMob implements GeoEntity {
	static final double HEALTH = 6.0, SPEED = 0.25, PANIC_SPEED = 1.8;
	/** It only spawns this dark (block light and sky, 0-15) and below the sea. */
	static final int MAX_LIGHT = 4;

	/** Animation names as in its .animation.json. */
	static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
	static final RawAnimation FLUTTER = RawAnimation.begin().thenPlay("flutter");

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	public BarrowBeetle(EntityType<? extends BarrowBeetle> type, Level level) {
		super(type, level);
		this.xpReward = 1;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED);
	}

	/** Its natural spawn: on stone, in the dark, below the sea. */
	public static boolean checkSpawn(EntityType<BarrowBeetle> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos,
		RandomSource random) {
		if (EntitySpawnReason.isSpawner(reason)) {
			return true;
		}
		return pos.getY() < level.getSeaLevel() && level.getMaxLocalRawBrightness(pos) <= MAX_LIGHT
			&& level.getBlockState(pos.below()).is(BlockTags.BASE_STONE_OVERWORLD);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new PanicGoal(this, PANIC_SPEED));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0));
		this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && this.isAlive()) {
			this.triggerAnim("action", "flutter");
		}
		return hurt;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<BarrowBeetle>("move", 3, test -> test.setAndContinue(test.isMoving() ? WALK : IDLE)));
		controllers.add(new AnimationController<BarrowBeetle>("action", test -> PlayState.STOP).triggerableAnim("flutter", FLUTTER));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.SILVERFISH_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.SILVERFISH_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.SILVERFISH_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 0.3F;
	}

	@Override
	public float getVoicePitch() {
		return super.getVoicePitch() * 1.4F;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.SILVERFISH_STEP, 0.08F, 1.6F);
	}
}
