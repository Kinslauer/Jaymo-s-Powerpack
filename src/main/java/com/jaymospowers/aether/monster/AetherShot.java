package com.jaymospowers.aether.monster;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * What the Aether's monsters throw. One class, six kinds ({@link Kind}); the kind is decided by the entity type, so each
 * has its own sprite (the item it is drawn as), gravity and effect. Monsters' shots never hurt other monsters.
 */
public class AetherShot extends ThrowableItemProjectile {
	/** The six kinds, each with the item it is drawn as and its gravity per tick (0 flies straight). */
	public enum Kind {
		ZEPHYR_WEB("zephyr_webbing_ball", 0.0), SKEPHID_WEB("skephid_webbing_ball", 0.03), THUNDERBALL("thunderball", 0.0),
		VENOMOUS_DART("venomous_dart", 0.015), TOXIC_DART("toxic_dart", 0.03), DEBRIS("gravitite_debris_shot", 0.0);

		public final String id;
		final double gravity;

		Kind(String id, double gravity) {
			this.id = id;
			this.gravity = gravity;
		}
	}

	// ---- tuning ----
	/** How long each lives (ticks). */
	public static final int LIFETIME = 400, DART_LIFETIME = 300, TOXIC_LIFETIME = 60;
	/** Webbed for this long (ticks) from a Zephyr's ball and a Skephid's. */
	public static final int ZEPHYR_WEBBED = 160, SKEPHID_WEBBED = 190;
	/** Venom from a dart (ticks) and the dart's own sting; toxin from a toxic dart. */
	public static final int VENOM_TICKS = 160, TOXIN_TICKS = 160;
	public static final float DART_DAMAGE = 1.0F, TOXIC_DAMAGE = 1.0F;
	/** The thunderball: its blow, the field it leaves (radius, ticks, ticks before it bites), the shock and the slowing it gives. */
	public static final float THUNDER_DAMAGE = 3.0F, THUNDER_BLAST = 1.0F;
	public static final float FIELD_RADIUS = 2.5F;
	public static final int FIELD_TICKS = 80, FIELD_DELAY = 10, SHOCK_TICKS = 60, FIELD_SLOW_TICKS = 10;
	/** The gravitite debris: its blow, how it speeds up each tick and the fastest it flies. */
	public static final float DEBRIS_DAMAGE = 6.0F;
	public static final double DEBRIS_ACCEL = 1.03, DEBRIS_MAX_SPEED = 1.3;
	/** A shield that stops a webbing ball is stunned this long (ticks). */
	public static final int SHIELD_STUN = 100;

	public AetherShot(EntityType<? extends AetherShot> type, Level level) {
		super(type, level);
	}

	/** Which kind this is (its entity type tells). */
	public Kind kind() {
		return AetherMonsters.kindOf(this.getType());
	}

	@Override
	protected Item getDefaultItem() {
		return AetherMonsters.shotItem(AetherMonsters.kindOf(this.getType()));
	}

	@Override
	protected double getDefaultGravity() {
		return this.kind().gravity;
	}

	@Override
	protected float getAirDrag() {
		return this.kind().gravity == 0.0 ? 1.0F : 0.99F;
	}

	/** Fires a shot from {@code from} at {@code at}. */
	public static AetherShot fire(Kind kind, LivingEntity owner, Vec3 from, Vec3 at, float speed, float inaccuracy) {
		AetherShot shot = new AetherShot(AetherMonsters.shotType(kind), owner.level());
		shot.setOwner(owner);
		shot.setPos(from.x, from.y, from.z);
		Vec3 aim = at.subtract(from);
		shot.shoot(aim.x, aim.y, aim.z, speed, inaccuracy);
		owner.level().addFreshEntity(shot);
		return shot;
	}

	@Override
	protected boolean canHitEntity(Entity entity) {
		// monsters don't shoot each other
		return super.canHitEntity(entity) && !(entity instanceof Enemy && this.getOwner() instanceof Enemy);
	}

	@Override
	public void tick() {
		super.tick();
		Kind kind = this.kind();
		if (this.level().isClientSide()) {
			this.level().addParticle(trail(kind), this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
			return;
		}
		if (kind == Kind.DEBRIS) {
			Vec3 motion = this.getDeltaMovement();
			if (motion.length() < DEBRIS_MAX_SPEED) {
				this.setDeltaMovement(motion.scale(DEBRIS_ACCEL));
			}
		}
		int life = kind == Kind.VENOMOUS_DART ? DART_LIFETIME : kind == Kind.TOXIC_DART ? TOXIC_LIFETIME : LIFETIME;
		if (this.tickCount > life) {
			this.discard();
		}
	}

	private static ParticleOptions trail(Kind kind) {
		return switch (kind) {
			case ZEPHYR_WEB, SKEPHID_WEB -> ParticleTypes.ITEM_COBWEB;
			case THUNDERBALL -> ParticleTypes.ELECTRIC_SPARK;
			case VENOMOUS_DART -> ParticleTypes.WITCH;
			case TOXIC_DART -> ParticleTypes.SPORE_BLOSSOM_AIR;
			case DEBRIS -> ParticleTypes.CRIT;
		};
	}

	private DamageSource source() {
		return this.getOwner() instanceof LivingEntity owner ? this.damageSources().mobProjectile(this, owner) : this.damageSources().magic();
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		super.onHitEntity(hit);
		if (!(this.level() instanceof ServerLevel level) || !(hit.getEntity() instanceof LivingEntity target)) {
			return;
		}
		switch (this.kind()) {
			case ZEPHYR_WEB -> web(target, ZEPHYR_WEBBED);
			case SKEPHID_WEB -> web(target, SKEPHID_WEBBED);
			case VENOMOUS_DART -> {
				target.hurtServer(level, this.source(), DART_DAMAGE);
				target.addEffect(new MobEffectInstance(AetherEffects.VENOM, VENOM_TICKS), this.getOwner());
			}
			case TOXIC_DART -> {
				target.hurtServer(level, this.source(), TOXIC_DAMAGE);
				target.addEffect(new MobEffectInstance(AetherEffects.TOXIN, TOXIN_TICKS), this.getOwner());
			}
			case THUNDERBALL -> target.hurtServer(level, this.source(), THUNDER_DAMAGE);
			case DEBRIS -> target.hurtServer(level, this.source(), DEBRIS_DAMAGE);
		}
	}

	/** A webbing ball webs what it hits; a shield it hits is stunned instead. */
	private void web(LivingEntity target, int ticks) {
		if (target instanceof Player player && player.isBlocking()) {
			player.getCooldowns().addCooldown(player.getUseItem(), SHIELD_STUN);
			player.stopUsingItem();
			return;
		}
		target.addEffect(new MobEffectInstance(AetherEffects.WEBBED, ticks), this.getOwner());
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (this.level() instanceof ServerLevel level && !this.isRemoved()) {
			Kind kind = this.kind();
			if (kind == Kind.THUNDERBALL) {
				this.thunderBurst(level);
			} else if (kind == Kind.DEBRIS) {
				level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 1.0F, 0.7F);
			} else if (kind == Kind.ZEPHYR_WEB || kind == Kind.SKEPHID_WEB) {
				level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.COBWEB_PLACE, SoundSource.HOSTILE, 0.8F, 1.2F);
			}
			this.level().broadcastEntityEvent(this, (byte)3);
			this.discard();
		}
	}

	/** A thunderball bursts: a small blast that breaks nothing, and a field of crackling that lingers. */
	private void thunderBurst(ServerLevel level) {
		Entity owner = this.getOwner();
		level.explode(owner, this.getX(), this.getY(), this.getZ(), THUNDER_BLAST, Level.ExplosionInteraction.NONE);
		AreaEffectCloud field = new AreaEffectCloud(level, this.getX(), this.getY(), this.getZ());
		if (owner instanceof LivingEntity living) {
			field.setOwner(living);
		}
		field.setRadius(FIELD_RADIUS);
		field.setDuration(FIELD_TICKS);
		field.setWaitTime(FIELD_DELAY);
		field.setRadiusPerTick(-FIELD_RADIUS / (2.0F * FIELD_TICKS));
		field.setCustomParticle(ParticleTypes.ELECTRIC_SPARK);
		field.addEffect(new MobEffectInstance(AetherEffects.ELECTRIC_SHOCK, SHOCK_TICKS));
		field.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, FIELD_SLOW_TICKS, 1));
		level.addFreshEntity(field);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 0.8F, 1.4F);
	}

	@Override
	public void handleEntityEvent(@EntityEvent.Value byte id) {
		if (id == 3) {
			ParticleOptions particle = trail(this.kind());
			for (int i = 0; i < 8; i++) {
				this.level().addParticle(particle, this.getX(), this.getY(), this.getZ(),
					(this.random.nextDouble() - 0.5) * 0.2, this.random.nextDouble() * 0.1, (this.random.nextDouble() - 0.5) * 0.2);
			}
		} else {
			super.handleEntityEvent(id);
		}
	}
}
