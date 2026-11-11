package com.jaymospowers.power.chrono.future;

import com.jaymospowers.rig.Rig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A Frostborn trooper of the future (Andy: "There should also be frostborn troopers in rigs that fight in the future,
 * causing a three pronged battle between them the androids and zombie warrior born"). A Frostborn in a whole Rig, in
 * squads ({@link FutureMachines#check}), at war with the androids, the mechs and the Warrior-born dead; the androids
 * and the dead fight them back and fight each other. They keep their distance and fire the Rig's palm blast, and fall
 * slowly on their thrusters. A player they leave alone (they have a war to fight), until one is hurt: then the whole
 * squad turns on whoever did it. The Omens (the city's own last pilots) and they leave each other be.
 * <p>The Rig they wear stays on them (it doesn't drop); a beaten trooper leaves some Rig Salvage, the same as the
 * Dead Rigs in the ruins, which are troopers who fell.
 */
public class FutureTrooper extends PathfinderMob implements RangedAttackMob {
	static final double HEALTH = 34.0, DAMAGE = 4.0, SPEED = 0.3, FOLLOW = 40.0;
	/** The palm blast: how far, how hard, how often (ticks, slowest and fastest), and the knockback. */
	static final float BLAST_RANGE = 18.0F, BLAST_DAMAGE = 5.0F, BLAST_KNOCKBACK = 0.6F;
	static final int BLAST_MIN = 30, BLAST_MAX = 50;
	/** The thrusters catch a fall faster than this (blocks a tick). */
	static final double FALL_CAP = -0.28;
	/** What a beaten trooper leaves: Rig Salvage, this many at most, this often. */
	static final float SALVAGE_CHANCE = 0.35F;
	static final int SALVAGE_MAX = 2;
	/** How often it looks for a target. */
	static final int SCAN_EVERY = 10;

	public FutureTrooper(EntityType<? extends FutureTrooper> type, Level level) {
		super(type, level);
		this.xpReward = 12;
	}

	public static AttributeSupplier.Builder createAttributes() {
		// the Rig's own pieces give its armour, as on a player
		return PathfinderMob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, HEALTH)
			.add(Attributes.ATTACK_DAMAGE, DAMAGE)
			.add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.FOLLOW_RANGE, FOLLOW)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.2);
	}

	/** Who a trooper fights on sight: the androids and their mechs, and the dead. */
	static boolean hostileTo(LivingEntity entity) {
		return entity.isAlive() && (entity instanceof FutureAndroid || entity instanceof FutureMech || entity instanceof Zombie);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new RangedAttackGoal(this, 1.0, BLAST_MIN, BLAST_MAX, BLAST_RANGE));
		this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.7));
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		// whoever hurts one (a player included) draws its squad; a trooper's stray blast doesn't turn another on it
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this, FutureTrooper.class).setAlertOthers(FutureTrooper.class));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, SCAN_EVERY, true, false,
			(target, level) -> hostileTo(target)));
	}

	/** Sent out by {@link FutureMachines}: the whole Rig, worn and kept. */
	public void outfit() {
		for (Rig.Piece piece : Rig.Piece.values()) {
			setItemSlot(piece.slot, new ItemStack(piece.item()));
			setDropChance(piece.slot, 0.0F);
		}
	}

	/** The palm blast, the Rig's own: an instant beam from the right palm. */
	@Override
	public void performRangedAttack(LivingEntity target, float power) {
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		Vec3 from = getEyePosition().add(getLookAngle().scale(0.6)).add(0.0, -0.35, 0.0);
		Vec3 end = target.getBoundingBox().getCenter();
		double length = from.distanceTo(end);
		Vec3 dir = end.subtract(from).normalize();
		for (double d = 0.0; d < length; d += 0.5) {
			Vec3 p = from.add(dir.scale(d));
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
		level.sendParticles(ParticleTypes.END_ROD, end.x, end.y, end.z, 8, 0.15, 0.15, 0.15, 0.08);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 0.8F, 1.9F);
		DamageSource source = level.damageSources().mobAttack(this);
		if (target.hurtServer(level, source, BLAST_DAMAGE)) {
			target.knockback(BLAST_KNOCKBACK, -dir.x, -dir.z, source, BLAST_DAMAGE);
			target.syncVelocity = true;
		}
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		Vec3 motion = getDeltaMovement();
		if (!onGround() && !isInWater() && motion.y < FALL_CAP) {
			// the thrusters catch the fall: a flame under each boot
			setDeltaMovement(motion.x, FALL_CAP, motion.z);
			resetFallDistance();
			if (this.tickCount % 2 == 0) {
				level.sendParticles(ParticleTypes.FLAME, getX(), getY() - 0.1, getZ(), 2, 0.15, 0.0, 0.15, 0.01);
			}
		}
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		if (getRandom().nextFloat() < SALVAGE_CHANCE) {
			spawnAtLocation(level, new ItemStack(Rig.SALVAGE, 1 + getRandom().nextInt(SALVAGE_MAX)));
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.0, getZ(), 16, 0.3, 0.6, 0.3, 0.2);
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return distance > 128.0 * 128.0;
	}
}
