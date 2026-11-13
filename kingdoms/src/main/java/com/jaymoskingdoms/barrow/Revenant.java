package com.jaymoskingdoms.barrow;

import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.beasts.ActingMonster;
import com.jaymoskingdoms.beasts.Beasts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The cryptbound revenant, drawn from yair's Blockbench model: a lord of the old dead in rusted plate, sword and tall
 * shield, the Lantern Warden's blue light burning in his helm, his breast and along his blade. He swore to keep his
 * barrow's light and is still keeping it. He stands in the deepest of the old barrows ({@link Barrows}) with his
 * household knights about him ({@link ZombieKnight}), and he and they let pass whoever comes carrying a lantern, as the
 * lantern-keepers do; anyone else, or anyone who strikes one of them, he fights. A cleave of the sword, a blow of the
 * shield that throws you back, and his oath: he raises the sword, the blue light rings out round him, and more of his
 * knights climb out of the floor.
 */
public class Revenant extends ActingMonster {
	static final double HEALTH = 120.0, ARMOR = 12.0, DAMAGE = 9.0, SPEED = 0.25, SIGHT = 24.0, KNOCKBACK_RESISTANCE = 0.8;
	/** The cleave and the shield blow: each animation's length, the tick it lands, its reach, and the ticks between blows. */
	static final int CLEAVE_TICKS = 40, CLEAVE_LANDS = 22, BASH_TICKS = 34, BASH_LANDS = 18, GAP = 30;
	static final double REACH = 2.6, BASH_THROW = 1.6;
	static final float CLEAVE_BONUS = 3.0F, BASH_DAMAGE = 4.0F;
	/** His oath: the animation's length, when the light rings out, how far, what it does, and how often at most. */
	static final int OATH_TICKS = 72, OATH_AT = 40, OATH_AGAIN = 500;
	static final double OATH_RANGE = 6.0;
	static final float OATH_DAMAGE = 5.0F;
	/** The shares of his health at which he swears it (each once), how many knights rise each time, how far from him, and
	 * the most of his knights about him at once. */
	static final float[] OATH_HEALTH = {0.66F, 0.33F};
	static final int OATH_KNIGHTS = 2, HOST_MAX = 4;
	static final double RISE_MIN = 2.0, RISE_MAX = 5.0, HOST_NEAR = 20.0;
	/** The Lantern Warden's blue. */
	static final int LIGHT = 0x6FD8FF;
	/** How long his death takes (the animation's length; vanilla's is 20). */
	static final int DEATH_TICKS = 76;
	/** How far he keeps to where he was stood (blocks). */
	public static final int HOME_RADIUS = 12;

	private static final String[] ACTIONS = {"attack_1", "attack_2", "special"};

	private int gap;
	private int oaths;
	private long oathAfter;

	public Revenant(EntityType<? extends Revenant> type, Level level) {
		super(type, level);
		this.xpReward = 40;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.ARMOR, ARMOR).add(Attributes.ATTACK_DAMAGE, DAMAGE)
			.add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.FOLLOW_RANGE, SIGHT).add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE);
	}

	@Override
	protected String[] actions() {
		return ACTIONS;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 1.0));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this, ZombieKnight.class, Revenant.class).setAlertOthers());
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, (target, level) -> !keeper(target)));
	}

	/** Whether {@code e} comes as the lantern-keepers do, a lit lantern in hand: the barrow's dead let such a one pass. */
	static boolean keeper(LivingEntity e) {
		return lantern(e.getMainHandItem().getItem()) || lantern(e.getOffhandItem().getItem());
	}

	private static boolean lantern(net.minecraft.world.item.Item item) {
		return item == Items.LANTERN || item == Items.SOUL_LANTERN;
	}

	static boolean ally(Entity e) {
		return e instanceof ZombieKnight || e instanceof Revenant;
	}

	@Override
	public void setTarget(@Nullable LivingEntity target) {
		super.setTarget(target != null && ally(target) ? null : target);
	}

	@Override
	protected boolean strikes(LivingEntity e) {
		return !ally(e) && super.strikes(e);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.gap > 0) {
			this.gap--;
		}
		if (this.acting("attack_1")) {
			if (this.actionRun() == CLEAVE_LANDS) {
				this.strike(level, (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) + CLEAVE_BONUS, REACH, true, 0.6, 0.0);
				this.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 0.6F);
			}
			return;
		}
		if (this.acting("attack_2")) {
			if (this.actionRun() == BASH_LANDS) {
				this.strike(level, BASH_DAMAGE, REACH - 0.6, true, BASH_THROW, 0.25);
				this.playSound(SoundEvents.SHIELD_BLOCK.value(), 1.2F, 0.7F);
			}
			return;
		}
		if (this.acting("special")) {
			this.getNavigation().stop();
			if (this.actionRun() == OATH_AT) {
				this.oath(level);
			}
			return;
		}
		LivingEntity target = this.getTarget();
		if (target == null || !target.isAlive()) {
			return;
		}
		this.getLookControl().setLookAt(target, 20.0F, 30.0F);
		this.getNavigation().moveTo(target, 1.0);
		if (this.oaths < OATH_HEALTH.length && this.getHealth() <= this.getMaxHealth() * OATH_HEALTH[this.oaths]
			&& level.getGameTime() >= this.oathAfter) {
			this.oaths++;
			this.oathAfter = level.getGameTime() + OATH_AGAIN;
			this.act("special", OATH_TICKS);
			this.playSound(SoundEvents.BELL_RESONATE, 1.6F, 0.6F);
			return;
		}
		double away = this.distanceTo(target) - target.getBbWidth() / 2.0 - this.getBbWidth() / 2.0;
		if (this.gap <= 0 && away < REACH) {
			this.gap = GAP;
			if (this.getRandom().nextInt(3) == 0) {
				this.act("attack_2", BASH_TICKS);
			} else {
				this.act("attack_1", CLEAVE_TICKS);
			}
		}
	}

	/** His oath: the blue light rings out, burning whoever stands near, and more of his knights climb out of the floor. */
	private void oath(ServerLevel level) {
		Fx.ring(level, this.position().add(0.0, 0.3, 0.0), (float) OATH_RANGE, LIGHT);
		this.playSound(SoundEvents.SOUL_ESCAPE.value(), 2.0F, 0.6F);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(OATH_RANGE, 2.0, OATH_RANGE),
			e -> e != this && e.isAlive() && this.strikes(e) && e.distanceTo(this) <= OATH_RANGE + e.getBbWidth() / 2.0)) {
			e.hurtServer(level, this.damageSources().indirectMagic(this, this), OATH_DAMAGE);
		}
		int host = level.getEntitiesOfClass(ZombieKnight.class, this.getBoundingBox().inflate(HOST_NEAR), ZombieKnight::isAlive).size();
		for (int i = 0, tries = 0; i < OATH_KNIGHTS && host < HOST_MAX && tries < 24; tries++) {
			float turn = this.getRandom().nextFloat() * Mth.TWO_PI;
			double far = Mth.lerp(this.getRandom().nextDouble(), RISE_MIN, RISE_MAX);
			BlockPos at = BlockPos.containing(this.getX() + Mth.cos(turn) * far, this.getY(), this.getZ() + Mth.sin(turn) * far);
			ZombieKnight knight = Beasts.ZOMBIE_KNIGHT.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (knight == null) {
				return;
			}
			knight.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, this.getYRot(), 0.0F);
			if (!level.getBlockState(at.below()).isFaceSturdy(level, at.below(), net.minecraft.core.Direction.UP) || !level.noCollision(knight)) {
				knight.discard();
				continue;
			}
			knight.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.MOB_SUMMONED, null);
			knight.setTarget(this.getTarget());
			level.addFreshEntity(knight);
			level.sendParticles(Fx.mote(LIGHT, 1.0F), knight.getX(), knight.getY() + 0.4, knight.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
			i++;
			host++;
		}
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		// (his blows are his moves)
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return (source.getEntity() == null || !ally(source.getEntity())) && super.hurtServer(level, source, amount);
	}

	/** His death takes as long as his falling is drawn, the light going out of him. */
	@Override
	protected void tickDeath() {
		this.deathTime++;
		if (this.level() instanceof ServerLevel level) {
			if (this.deathTime % 6 == 0) {
				Vec3 at = this.position();
				level.sendParticles(ParticleTypes.SOUL, at.x, at.y + 1.2, at.z, 3, 0.4, 0.6, 0.4, 0.02);
			}
			if (this.deathTime >= DEATH_TICKS && !this.isRemoved()) {
				level.broadcastEntityEvent(this, (byte) 60);
				this.remove(RemovalReason.KILLED);
			}
		}
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public float getVoicePitch() {
		return 0.5F;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.WITHER_SKELETON_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.WITHER_SKELETON_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.WITHER_SKELETON_DEATH;
	}
}
