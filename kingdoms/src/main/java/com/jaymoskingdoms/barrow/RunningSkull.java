package com.jaymoskingdoms.barrow;

import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.beasts.Beasts;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The skull of a felled {@link LaughingBones}: small, quick, harmless, running from every player. After
 * {@link #REGROW_TICKS} it grows its bones back where it is and fights again. Smashed, it spills what the bones carried
 * and that is the end of it. Drawn as a skeleton skull (the item).
 */
public class RunningSkull extends Monster implements ItemSupplier {
	static final double HEALTH = 6.0, SPEED = 0.33;
	/** How long it runs before it is whole again (ticks), and how far it shies from a player (blocks). */
	static final int REGROW_TICKS = 240;
	static final float SHY = 10.0F;
	/** What a smashed skull spills: bones, arrows, experience, and now and then the skull itself. */
	static final int BONES_MAX = 2, ARROWS_MAX = 2, XP = 7;
	static final float SKULL_DROP = 0.08F;
	private static final ItemStack LOOK = new ItemStack(Items.SKELETON_SKULL);

	private int ran;

	public RunningSkull(EntityType<? extends RunningSkull> type, Level level) {
		super(type, level);
		this.xpReward = 0;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Player.class, SHY, 1.0, 1.25));
		this.goalSelector.addGoal(3, new MoveTowardsRestrictionGoal(this, 1.0));
		this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.8));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		this.ran++;
		if (this.ran % 10 == 0) {
			Fx.burst(level, this.getX(), this.getY() + 0.3, this.getZ(), LaughingBones.LAUGH_COLOR, 0.5F, 2, 0.15, 0.15, 0.15);
		}
		if (this.ran % 60 == 30) {
			this.playSound(SoundEvents.WITCH_AMBIENT, 0.6F, 1.8F);
		}
		if (this.ran >= REGROW_TICKS && this.onGround()) {
			this.regrow(level);
		}
	}

	/** The bones grow back under it. */
	private void regrow(ServerLevel level) {
		LaughingBones bones = Beasts.LAUGHING_BONES == null ? null : Beasts.LAUGHING_BONES.create(level, EntitySpawnReason.TRIGGERED);
		if (bones == null) {
			return;
		}
		bones.snapTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), 0.0F);
		if (!level.noCollision(bones)) {
			// no room to stand here: keeps running, and tries again
			bones.discard();
			this.ran = REGROW_TICKS - 40;
			return;
		}
		bones.finalizeSpawn(level, level.getCurrentDifficultyAt(this.blockPosition()), EntitySpawnReason.TRIGGERED, null);
		bones.setPersistenceRequired();
		if (this.hasHome()) {
			bones.setHomeTo(this.getHomePosition(), this.getHomeRadius());
		}
		level.addFreshEntity(bones);
		Fx.blast(level, this.position().add(0.0, 1.0, 0.0), 1.2F, LaughingBones.LAUGH_COLOR);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SKELETON_CONVERTED_TO_STRAY, SoundSource.HOSTILE, 1.0F, 0.8F);
		this.discard();
	}

	@Override
	protected boolean shouldDropLoot(ServerLevel level) {
		return false;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel level) {
			spill(level, this.position().add(0.0, 0.2, 0.0));
			if (this.random.nextFloat() < SKULL_DROP) {
				level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY() + 0.2, this.getZ(), new ItemStack(Items.SKELETON_SKULL)));
			}
		}
	}

	/** What the bones carried, dropped at {@code at} now the laughter is out of them. */
	static void spill(ServerLevel level, Vec3 at) {
		level.addFreshEntity(new ItemEntity(level, at.x, at.y, at.z, new ItemStack(Items.BONE, 1 + level.getRandom().nextInt(BONES_MAX))));
		int arrows = level.getRandom().nextInt(ARROWS_MAX + 1);
		if (arrows > 0) {
			level.addFreshEntity(new ItemEntity(level, at.x, at.y, at.z, new ItemStack(Items.ARROW, arrows)));
		}
		ExperienceOrb.award(level, at, XP);
	}

	@Override
	public ItemStack getItem() {
		return LOOK;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.SKELETON_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.TURTLE_EGG_BREAK;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("ran", this.ran);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.ran = input.getIntOr("ran", 0);
	}
}
