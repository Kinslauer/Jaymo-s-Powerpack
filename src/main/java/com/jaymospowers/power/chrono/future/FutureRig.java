package com.jaymospowers.power.chrono.future;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A giant rig of the future (Andy, 2026-11-25: "larger rigs made by the frostborn and tinkerer, and they only appear in the
 * future to fight the hordes and mutants"): the Frostborn Siege Rig and the Tinkerer's Colossus, each about eight blocks of
 * piloted walker on our own android mech model ({@link FutureMech}, drawn {@code FutureMechRenderer#RIG_SCALE} times its size,
 * in its own skin). A rig fights the dead (the {@link FutureFerals} hordes) and whatever else has turned monstrous there, with
 * the war mech's cannon and shells; it leaves the living alone unless they hit it ({@link HurtByTargetGoal}), and it is
 * theirs to wreck for the scrap. When it is beaten its pilot climbs out: a Frostborn trooper from the Frostborn rig, one of the
 * Tinkerer's androids from his. Per-part hits are the Wounds' (it is a tagged giant: head, arms, legs, core), see Giants.
 */
public class FutureRig extends FutureMech {
	/** What it is made of: it is stronger than the war mech all round. */
	static final double RIG_HEALTH = 420.0, RIG_DAMAGE = 26.0, RIG_SPEED = 0.22, RIG_ARMOR = 26.0, RIG_TOUGHNESS = 10.0;

	public FutureRig(EntityType<? extends FutureRig> type, Level level) {
		super(type, level);
		this.xpReward = 120;
	}

	public boolean frostborn() {
		return getType() == FutureRigs.FROSTBORN;
	}

	@Override
	public int build() {
		return frostborn() ? 4 : 5;
	}

	public static AttributeSupplier.Builder createRigAttributes() {
		return FutureMech.createAttributes()
			.add(Attributes.MAX_HEALTH, RIG_HEALTH)
			.add(Attributes.ATTACK_DAMAGE, RIG_DAMAGE)
			.add(Attributes.MOVEMENT_SPEED, RIG_SPEED)
			.add(Attributes.ARMOR, RIG_ARMOR)
			.add(Attributes.ARMOR_TOUGHNESS, RIG_TOUGHNESS)
			.add(Attributes.STEP_HEIGHT, 2.5)
			.add(Attributes.ENTITY_INTERACTION_RANGE, 5.0);
	}

	/** The dead and the monstrous, not the machines and not the living. */
	static boolean hordeOrMutant(LivingEntity e) {
		if (!e.isAlive() || e instanceof FutureMech || e instanceof FutureAndroid || e instanceof FutureOmen || e instanceof FutureTrooper) {
			return false;
		}
		return e instanceof Zombie || e instanceof Enemy;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
		this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.7));
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 24.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<Mob>(this, Mob.class, 10, true, false, (e, level) -> hordeOrMutant(e)));
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (level() instanceof ServerLevel level) {
			Mob pilot = (frostborn() ? FutureMachines.TROOPER : FutureMachines.ANDROID).create(level, EntitySpawnReason.EVENT);
			if (pilot != null) {
				pilot.snapTo(getX(), getY() + 1.0, getZ(), getYRot(), 0.0F);
				if (pilot instanceof FutureTrooper trooper) {
					trooper.outfit();
				} else if (pilot instanceof FutureAndroid android) {
					android.outfit(level);
				}
				level.addFreshEntity(pilot);
			}
		}
	}
}
