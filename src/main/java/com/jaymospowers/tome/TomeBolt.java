package com.jaymospowers.tome;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A bolt cast from a {@link TomeItem spell tome}: a small mote of its element that flies straight (no gravity) and is
 * driven by the server like Hrunting's Frost Lance, with a particle trail instead of a model. Everything it does scales
 * with its {@link #power}: 0 is the quick bolt of a tap, 1 a fully charged one (or a stored spell).
 * <ul>
 * <li>Fire sets what it hits alight; frost slows (and chills) it; lightning arcs on to the nearest ones around the first;
 * arcane pierces through several.</li>
 * <li>All of it is magic damage (armour doesn't count) from the caster.</li>
 * </ul>
 */
public class TomeBolt extends Projectile {
	// ---- tuning: each is its value for a quick bolt (power 0), then for a full one (power 1) ----
	public static final float QUICK_DAMAGE = 4.0F, FULL_DAMAGE = 14.0F;
	/** Blocks a tick, and how far it flies at most. */
	public static final double QUICK_SPEED = 1.5, FULL_SPEED = 2.4;
	public static final double QUICK_RANGE = 20.0, FULL_RANGE = 48.0;
	/** Fire: seconds alight. */
	public static final float QUICK_FIRE = 3.0F, FULL_FIRE = 8.0F;
	/** Frost: ticks slowed (Slowness I, up to III on a full one), and ticks of freezing added. */
	public static final int QUICK_SLOW = 40, FULL_SLOW = 160, QUICK_FREEZE = 60, FULL_FREEZE = 160;
	/** Lightning: how many more it arcs to, how far each arc reaches (blocks), and the share of the damage an arc carries. */
	public static final int QUICK_CHAIN = 1, FULL_CHAIN = 4;
	public static final double QUICK_CHAIN_RANGE = 3.0, FULL_CHAIN_RANGE = 5.5;
	public static final float CHAIN_DAMAGE = 0.6F;
	/** Arcane: how many more it passes through after the first. */
	public static final int QUICK_PIERCE = 1, FULL_PIERCE = 5;
	/** How close to the aimed point the bolt counts as touching something (blocks of margin around the target). */
	private static final double HIT_MARGIN = 0.3;

	private TomeElement element = TomeElement.FIRE;
	private float power;
	private Vec3 dir = Vec3.ZERO;
	private double speed, range, travelled;
	private int pierced;
	private final Set<Integer> hit = new HashSet<>();

	public TomeBolt(EntityType<? extends TomeBolt> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	/** Casts a bolt from {@code owner}'s hand down their sight line. */
	public static void cast(ServerLevel level, LivingEntity owner, TomeElement element, float power) {
		TomeBolt bolt = new TomeBolt(Tome.BOLT, level);
		bolt.setOwner(owner);
		bolt.element = element;
		bolt.power = Mth.clamp(power, 0.0F, 1.0F);
		bolt.dir = owner.getLookAngle().normalize();
		bolt.speed = lerp(QUICK_SPEED, FULL_SPEED, bolt.power);
		bolt.range = lerp(QUICK_RANGE, FULL_RANGE, bolt.power);
		Vec3 from = owner.getEyePosition().add(0.0, -0.15, 0.0).add(bolt.dir.scale(0.6));
		bolt.setPos(from);
		bolt.setDeltaMovement(bolt.dir.scale(bolt.speed));
		float yRot = (float)(Mth.atan2(bolt.dir.x, bolt.dir.z) * Mth.RAD_TO_DEG);
		float xRot = (float)(Mth.atan2(bolt.dir.y, bolt.dir.horizontalDistance()) * Mth.RAD_TO_DEG);
		bolt.setYRot(yRot);
		bolt.setXRot(xRot);
		level.addFreshEntity(bolt);
		level.playSound(null, from.x, from.y, from.z, element.castSound, SoundSource.PLAYERS, element.castVolume * (0.7F + 0.5F * bolt.power),
			element.castPitch - 0.35F * bolt.power);
	}

	private static double lerp(double quick, double full, float power) {
		return quick + (full - quick) * power;
	}

	private float damage() {
		return (float) lerp(QUICK_DAMAGE, FULL_DAMAGE, this.power);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		Entity owner = this.getOwner();
		if (!(owner instanceof LivingEntity caster) || !caster.isAlive() || caster.level() != level) {
			this.discard();
			return;
		}
		Vec3 pos = this.position();
		Vec3 next = pos.add(this.dir.scale(this.speed));
		BlockHitResult block = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		boolean wall = block.getType() != HitResult.Type.MISS;
		if (wall) {
			next = block.getLocation();
		}
		boolean stopped = this.strike(level, caster, pos, next);
		this.trail(level, pos, next);
		this.travelled += pos.distanceTo(next);
		this.setPos(next);
		if (stopped || wall || this.travelled >= this.range) {
			this.impact(level, next);
			this.discard();
		}
	}

	/** Strikes what lies between the two points, nearest first. Whether the bolt is spent. */
	private boolean strike(ServerLevel level, LivingEntity caster, Vec3 from, Vec3 to) {
		List<LivingEntity> targets = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(HIT_MARGIN + 0.5),
			e -> this.canStrike(caster, e) && !this.hit.contains(e.getId()) && this.touches(e, from, to)));
		targets.sort(Comparator.comparingDouble(e -> e.position().distanceToSqr(from)));
		int allowed = this.element == TomeElement.ARCANE ? (int) Math.round(lerp(QUICK_PIERCE, FULL_PIERCE, this.power)) : 0;
		for (LivingEntity target : targets) {
			this.hit.add(target.getId());
			this.hurt(level, caster, target, this.damage());
			this.effect(level, caster, target);
			if (this.element == TomeElement.LIGHTNING) {
				this.arc(level, caster, target);
				return true;
			}
			if (this.pierced++ >= allowed) {
				return true;
			}
		}
		return false;
	}

	private boolean touches(LivingEntity e, Vec3 from, Vec3 to) {
		AABB box = e.getBoundingBox().inflate(HIT_MARGIN);
		return box.contains(from) || box.clip(from, to).isPresent();
	}

	private boolean canStrike(LivingEntity caster, LivingEntity e) {
		return e != caster && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && !caster.isAlliedTo(e)
			&& !caster.isPassengerOfSameVehicle(e) && !(e instanceof OwnableEntity owned && owned.getOwner() == caster);
	}

	private void hurt(ServerLevel level, LivingEntity caster, LivingEntity target, float damage) {
		DamageSource source = this.damageSources().indirectMagic(this, caster);
		target.hurtServer(level, source, damage);
	}

	/** What the element does besides the damage. */
	private void effect(ServerLevel level, LivingEntity caster, LivingEntity target) {
		Vec3 at = target.position().add(0.0, target.getBbHeight() * 0.6, 0.0);
		switch (this.element) {
			case FIRE -> {
				target.igniteForSeconds((float) lerp(QUICK_FIRE, FULL_FIRE, this.power));
				level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 8 + (int) (10 * this.power), 0.25, 0.3, 0.25, 0.04);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.5F, 1.4F);
			}
			case FROST -> {
				int slow = (int) lerp(QUICK_SLOW, FULL_SLOW, this.power);
				int amplifier = Math.round(2.0F * this.power);
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, slow, amplifier), caster);
				if (target.canFreeze()) {
					int add = (int) lerp(QUICK_FREEZE, FULL_FREEZE, this.power);
					target.setTicksFrozen(Math.min(target.getTicksFrozen() + add, target.getTicksRequiredToFreeze() + add / 2));
				}
				level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, 10 + (int) (14 * this.power), 0.3, 0.35, 0.3, 0.05);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 0.7F, 1.2F);
			}
			case LIGHTNING -> {
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 10, 0.3, 0.35, 0.3, 0.3);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.3F, 2.0F);
			}
			case ARCANE -> {
				level.sendParticles(ParticleTypes.WITCH, at.x, at.y, at.z, 10, 0.3, 0.35, 0.3, 0.1);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.0F);
			}
		}
	}

	/** Lightning: from the first target it jumps to the nearest ones around it, a sparking line to each. */
	private void arc(ServerLevel level, LivingEntity caster, LivingEntity first) {
		int jumps = (int) Math.round(lerp(QUICK_CHAIN, FULL_CHAIN, this.power));
		double reach = lerp(QUICK_CHAIN_RANGE, FULL_CHAIN_RANGE, this.power);
		LivingEntity from = first;
		for (int i = 0; i < jumps; i++) {
			LivingEntity last = from;
			LivingEntity best = null;
			double bestDist = reach * reach;
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, last.getBoundingBox().inflate(reach),
				e -> this.canStrike(caster, e) && !this.hit.contains(e.getId()))) {
				double d = e.position().distanceToSqr(last.position());
				if (d < bestDist) {
					best = e;
					bestDist = d;
				}
			}
			if (best == null) {
				return;
			}
			this.hit.add(best.getId());
			this.spark(level, last.position().add(0.0, last.getBbHeight() * 0.6, 0.0), best.position().add(0.0, best.getBbHeight() * 0.6, 0.0));
			this.hurt(level, caster, best, this.damage() * CHAIN_DAMAGE);
			level.playSound(null, best.getX(), best.getY(), best.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.25F, 2.0F);
			from = best;
		}
	}

	/** A line of sparks. */
	private void spark(ServerLevel level, Vec3 a, Vec3 b) {
		Vec3 line = b.subtract(a);
		int steps = Math.max(2, (int) (line.length() / 0.35));
		for (int i = 0; i <= steps; i++) {
			Vec3 p = a.add(line.scale(i / (double) steps));
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.08, 0.08, 0.08, 0.02);
		}
	}

	/** The element's trail along the step, denser and brighter the stronger the bolt. */
	private void trail(ServerLevel level, Vec3 from, Vec3 to) {
		Vec3 step = to.subtract(from);
		int count = Math.max(1, (int) (step.length() / 0.5));
		for (int i = 0; i < count; i++) {
			Vec3 p = from.add(step.scale(i / (double) count));
			level.sendParticles(this.element.trail, p.x, p.y, p.z, 1 + (this.power > 0.5F ? 1 : 0), 0.04, 0.04, 0.04, 0.005);
		}
		level.sendParticles(ParticleTypes.END_ROD, to.x, to.y, to.z, 1, 0.0, 0.0, 0.0, 0.0);
		if (this.element == TomeElement.FIRE) {
			level.sendParticles(ParticleTypes.SMALL_FLAME, to.x, to.y, to.z, 1, 0.05, 0.05, 0.05, 0.01);
		} else if (this.element == TomeElement.ARCANE) {
			level.sendParticles(ParticleTypes.ENCHANT, to.x, to.y, to.z, 1, 0.1, 0.1, 0.1, 0.3);
		}
	}

	/** Where it ends: a puff of its element. */
	private void impact(ServerLevel level, Vec3 at) {
		level.sendParticles(this.element.trail, at.x, at.y, at.z, 6 + (int) (10 * this.power), 0.15, 0.15, 0.15, 0.06);
		level.sendParticles(ParticleTypes.POOF, at.x, at.y, at.z, 2, 0.1, 0.1, 0.1, 0.02);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 64.0 * 64.0;
	}
}
