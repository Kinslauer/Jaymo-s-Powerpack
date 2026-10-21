package com.jaymoskingdoms.nightfall;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A vampire bat (after Nightfall Plague's): out at night, or called up by a Night Bleeder ({@link NightBleeder}), and
 * they hunt in flocks. Each goes at its prey one of two ways: it latches on and drinks ({@link Mode#SUCK}: shaken off by a
 * blow), or it rams them and is stunned a moment after ({@link Mode#CHARGE}). By day the wild ones roost like any bat;
 * the summoned ones are gone after a while, like vexes.
 */
public class VampireBat extends Bat {
	static final double HEALTH = 8.0;
	static final float DAMAGE = 2.0F;
	/** How far it looks for prey (blocks). */
	static final double HUNT_RANGE = 16.0;
	/** Latched on: a sip this often (ticks), and at most this long (ticks). */
	static final int SIP_EVERY = 20, LATCH_TICKS = 20 * 6;
	/** A charge: its speed (blocks a tick), the knockback, the stun after (ticks). */
	static final double CHARGE_SPEED = 0.85, CHARGE_PUSH = 0.9;
	static final int STUN_TICKS = 30;
	/** A summoned bat is gone after this long (ticks). */
	static final int SUMMONED_LIFE = 20 * 50;

	enum Mode { SUCK, CHARGE }

	private @Nullable UUID preyId;
	private Mode mode = Mode.SUCK;
	private int latched = -1, stunned, lifeLeft = -1;

	public VampireBat(EntityType<? extends VampireBat> type, Level level) {
		super(type, level);
		this.xpReward = 2;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Bat.createAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.ATTACK_DAMAGE, DAMAGE);
	}

	/** Called up by a Night Bleeder against {@code target}: awake, and gone in a while. */
	void summoned(@Nullable LivingEntity target) {
		this.setResting(false);
		this.lifeLeft = SUMMONED_LIFE;
		if (target != null) {
			this.preyId = target.getUUID();
		}
		this.mode = this.random.nextBoolean() ? Mode.SUCK : Mode.CHARGE;
	}

	/** Whether this bat is latched on and drinking from something. */
	public boolean latched() {
		return this.latched >= 0;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		if (this.lifeLeft > 0 && --this.lifeLeft == 0) {
			level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(), 10, 0.2, 0.2, 0.2, 0.02);
			this.discard();
			return;
		}
		if (this.stunned > 0) {
			this.stunned--;
			this.setDeltaMovement(this.getDeltaMovement().multiply(0.5, 0.5, 0.5).add(0.0, -0.02, 0.0));
			return;
		}
		LivingEntity prey = this.prey(level);
		if (prey == null) {
			this.latched = -1;
			super.customServerAiStep(level);
			return;
		}
		this.setResting(false);
		if (this.latched >= 0) {
			this.drink(level, prey);
			return;
		}
		Vec3 to = prey.getEyePosition().add(0.0, -0.3, 0.0).subtract(this.position());
		double d = to.length();
		Vec3 dir = d < 1.0E-4 ? Vec3.ZERO : to.scale(1.0 / d);
		double speed = this.mode == Mode.CHARGE && d < 8.0 ? CHARGE_SPEED : 0.45;
		Vec3 v = this.getDeltaMovement();
		this.setDeltaMovement(v.add(dir.scale(speed).subtract(v).scale(0.25)));
		this.setYRot((float) (Math.atan2(dir.z, dir.x) * 180.0 / Math.PI) - 90.0F);
		this.zza = 0.5F;
		if (d < 1.2) {
			if (this.mode == Mode.SUCK) {
				this.latched = 0;
				level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BAT_HURT, SoundSource.HOSTILE, 0.6F, 0.6F);
			} else {
				this.ram(level, prey, dir);
			}
		}
	}

	/** Clings to its prey's neck and drinks. */
	private void drink(ServerLevel level, LivingEntity prey) {
		this.latched++;
		Vec3 back = Vec3.directionFromRotation(0.0F, prey.getYRot()).scale(-0.35);
		this.setPos(prey.getX() + back.x, prey.getEyeY() - 0.55, prey.getZ() + back.z);
		this.setDeltaMovement(Vec3.ZERO);
		if (this.latched % SIP_EVERY == 0) {
			if (prey.hurtServer(level, this.damageSources().mobAttack(this), DAMAGE * 0.5F)) {
				this.heal(1.0F);
				level.sendParticles(Nightfall.BLOOD, prey.getX(), prey.getEyeY() - 0.3, prey.getZ(), 3, 0.1, 0.1, 0.1, 0.0);
			}
		}
		if (this.latched > LATCH_TICKS || !prey.isAlive()) {
			this.letGo();
		}
	}

	/** Slams into its prey and reels. */
	private void ram(ServerLevel level, LivingEntity prey, Vec3 dir) {
		prey.hurtServer(level, this.damageSources().mobAttack(this), DAMAGE);
		prey.knockback(CHARGE_PUSH, -dir.x, -dir.z, this.damageSources().mobAttack(this), DAMAGE);
		this.stunned = STUN_TICKS;
		this.setDeltaMovement(dir.scale(-0.3));
		this.mode = this.random.nextBoolean() ? Mode.SUCK : Mode.CHARGE;
		level.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY(), this.getZ(), 6, 0.2, 0.2, 0.2, 0.1);
	}

	private void letGo() {
		this.latched = -1;
		this.stunned = STUN_TICKS / 2;
		this.mode = this.random.nextBoolean() ? Mode.SUCK : Mode.CHARGE;
	}

	/** Its prey, kept or found: a player, a villager, by night (or always, if it was called up). */
	private @Nullable LivingEntity prey(ServerLevel level) {
		if (this.preyId != null && level.getEntity(this.preyId) instanceof LivingEntity kept && kept.isAlive() && kept.distanceTo(this) < HUNT_RANGE * 2
			&& huntable(kept)) {
			return kept;
		}
		this.preyId = null;
		if (this.lifeLeft < 0 && level.isBrightOutside() || this.tickCount % 10 != 0) {
			return null;
		}
		List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, new AABB(this.blockPosition()).inflate(HUNT_RANGE),
			e -> (e instanceof Player || e instanceof AbstractVillager) && e.isAlive() && huntable(e) && this.hasLineOfSight(e));
		LivingEntity best = null;
		for (LivingEntity e : near) {
			if (best == null || e.distanceToSqr(this) < best.distanceToSqr(this)) {
				best = e;
			}
		}
		if (best != null) {
			this.preyId = best.getUUID();
			this.mode = this.random.nextBoolean() ? Mode.SUCK : Mode.CHARGE;
		}
		return best;
	}

	private static boolean huntable(LivingEntity e) {
		return !(e instanceof Player p && (p.isCreative() || p.isSpectator())) && !e.isBaby() && !Nightfall.kin(e);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && this.latched >= 0) {
			this.letGo();
		}
		return hurt;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("LifeLeft", this.lifeLeft);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.lifeLeft = input.getIntOr("LifeLeft", -1);
	}
}
