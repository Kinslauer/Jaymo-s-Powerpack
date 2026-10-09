package com.jaymospowers.power.web;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * The Web Shot: a fast, flat-flying ball of web that bursts where it lands like a splash potion. Everything caught in
 * the splash gets Slowness III and a cobweb at its feet; whatever it hits directly also takes the full damage. All of
 * it grows with how long the shot was charged.
 */
public class WebBall extends ThrowableItemProjectile {
	/** Direct-hit damage at no charge and at a full charge; things in the splash take up to half that. */
	public static final float DAMAGE_MIN = 0.5F, DAMAGE_MAX = 10.0F;
	/** The suit's symbiote mass: the same, but it hits much harder the longer it's charged. */
	public static final float DARK_DAMAGE_MIN = 2.0F, DARK_DAMAGE_MAX = 18.0F;
	/**
	 * Webs left around the splash besides the ones on whoever's caught: many from a quick shot (all splash, little
	 * sting), few from a charged one.
	 */
	public static final int SCATTER_MIN = 1, SCATTER_MAX = 9;
	/** Splash radius at no charge and at a full charge (a splash potion's is 4). */
	public static final double SPLASH_MIN = 1.75, SPLASH_MAX = 4.5;
	/** Slowness III, for (at the centre) this many ticks at no charge and at a full charge. */
	public static final int SLOWNESS_AMPLIFIER = 2;
	public static final int SLOWNESS_MIN_TICKS = 60, SLOWNESS_MAX_TICKS = 220;

	/**
	 * A tapped V: a webshooter pellet, fast and light, a sting and nothing more (no splash, no webs). Damage on a direct
	 * hit: a web's, and the symbiote's.
	 */
	public static final float PELLET_DAMAGE = 1.5F, DARK_PELLET_DAMAGE = 2.5F;

	/** 0-1: how long it was charged. Server only. */
	private float charge;
	private boolean pellet;
	private boolean burst;
	/** Gravity per tick (a snowball is 0.03): it flies nearly flat. */
	private static final double GRAVITY = 0.012;
	/** Despawns after this many ticks in flight. */
	private static final int LIFETIME = 100;

	public WebBall(final EntityType<? extends WebBall> type, final Level level) {
		super(type, level);
	}

	public WebBall(final Level level, final LivingEntity owner, final ItemStack stack) {
		super(WebPower.WEB_BALL, owner, level, stack);
	}

	public WebBall charged(float charge) {
		this.charge = Math.clamp(charge, 0.0F, 1.0F);
		return this;
	}

	/** A quick webshooter pellet (see {@link #PELLET_DAMAGE}). */
	public WebBall pellet() {
		this.pellet = true;
		return this;
	}

	@Override
	protected Item getDefaultItem() {
		return WebPower.WEB_BALL_ITEM;
	}

	@Override
	protected double getDefaultGravity() {
		return GRAVITY;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide()) {
			if (this.tickCount > 1) {
				this.level().addParticle(this.dark() ? WebSymbiote.dust(1.2F) : ParticleTypes.ITEM_COBWEB, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
			}
		} else if (this.tickCount > LIFETIME) {
			this.discard();
		}
	}

	/** The suit's black mass (its item says so, on both sides). */
	public boolean dark() {
		return this.getItem().is(WebPower.SYMBIOTE_MASS_ITEM);
	}

	/** The splash: like a splash potion, the effect is strongest at the centre (and on whatever was hit directly). */
	private void burst(ServerLevel level, @org.jspecify.annotations.Nullable Entity direct) {
		this.burst = true;
		boolean dark = this.dark();
		if (this.pellet) {
			// a pellet stings what it hits, and that's all
			if (direct != null && direct != this.getOwner()) {
				direct.hurtServer(level, this.damageSources().thrown(this, this.getOwner()), dark ? DARK_PELLET_DAMAGE : PELLET_DAMAGE);
			}
			level.sendParticles(dark ? WebSymbiote.dust(0.9F) : ParticleTypes.ITEM_COBWEB, this.getX(), this.getY(), this.getZ(), 5, 0.1, 0.1, 0.1, 0.03);
			return;
		}
		float damage = dark ? net.minecraft.util.Mth.lerp(this.charge, DARK_DAMAGE_MIN, DARK_DAMAGE_MAX)
			: net.minecraft.util.Mth.lerp(this.charge, DAMAGE_MIN, DAMAGE_MAX);
		double radius = net.minecraft.util.Mth.lerp(this.charge, SPLASH_MIN, SPLASH_MAX);
		int slowTicks = net.minecraft.util.Mth.lerpInt(this.charge, SLOWNESS_MIN_TICKS, SLOWNESS_MAX_TICKS);
		Entity owner = this.getOwner();
		net.minecraft.world.phys.AABB area = this.getBoundingBox().inflate(radius, radius * 0.5, radius);
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, area, e -> e.isAlive() && !e.isSpectator() && e != owner)) {
			double distance = living == direct ? 0.0 : Math.sqrt(living.distanceToSqr(this.position()));
			if (distance > radius) {
				continue;
			}
			double strength = living == direct ? 1.0 : 1.0 - distance / radius;
			float hurt = living == direct ? damage : (float)(damage * 0.5 * strength);
			if (hurt >= 0.5F) {
				living.hurtServer(level, this.damageSources().thrown(this, owner), hurt);
			}
			int ticks = (int)(slowTicks * strength);
			if (ticks > 20 && living.isAlive()) {
				WebTraps.ensnare(level, living, SLOWNESS_AMPLIFIER, ticks, dark);
			}
		}
		if (direct != null && !(direct instanceof LivingEntity)) {
			direct.hurtServer(level, this.damageSources().thrown(this, owner), damage);
		}
		// webs strewn about the splash: more from a quick shot
		WebTraps.scatter(level, this.position(), radius, net.minecraft.util.Mth.lerpInt(this.charge, SCATTER_MAX, SCATTER_MIN), dark);
		int count = (int)(12 + 40 * this.charge);
		level.sendParticles(dark ? WebSymbiote.dust(1.5F) : ParticleTypes.ITEM_COBWEB, this.getX(), this.getY(), this.getZ(), count,
			radius * 0.4, 0.3, radius * 0.4, 0.08);
		if (dark) {
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.0F, 0.5F);
		}
		level.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY(), this.getZ(), (int)(4 + 12 * this.charge), radius * 0.3, 0.2, radius * 0.3, 0.05);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SPLASH_POTION_BREAK, SoundSource.PLAYERS, 0.6F + 0.6F * this.charge, 0.8F);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.COBWEB_PLACE, SoundSource.PLAYERS, 1.0F, 0.7F);
	}

	private ParticleOptions particle() {
		if (this.dark()) {
			return WebSymbiote.dust(1.2F);
		}
		ItemStack item = this.getItem();
		return item.isEmpty() ? ParticleTypes.ITEM_COBWEB : new ItemParticleOption(ParticleTypes.ITEM, ItemStackTemplate.fromNonEmptyStack(item));
	}

	@Override
	public void handleEntityEvent(final @EntityEvent.Value byte id) {
		if (id == 3) {
			ParticleOptions particle = this.particle();
			for (int i = 0; i < 10; i++) {
				this.level().addParticle(particle, this.getX(), this.getY(), this.getZ(),
					(this.random.nextDouble() - 0.5) * 0.15, this.random.nextDouble() * 0.1, (this.random.nextDouble() - 0.5) * 0.15);
			}
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	protected void onHitEntity(final EntityHitResult hitResult) {
		super.onHitEntity(hitResult);
		if (this.level() instanceof ServerLevel level) {
			this.burst(level, hitResult.getEntity());
		}
	}

	@Override
	protected void onHit(final HitResult hitResult) {
		super.onHit(hitResult);
		if (this.level() instanceof ServerLevel level && !this.burst) {
			this.burst(level, null);
		}
		if (!this.level().isClientSide()) {
			this.level().broadcastEntityEvent(this, (byte)3);
			this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.7F, 1.4F);
			this.discard();
		}
	}
}
