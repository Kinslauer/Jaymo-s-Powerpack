package com.jaymoskingdoms;

import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.61) A {@link Cannon}'s shot: a heavy iron ball that arcs to its mark and bursts on whatever it meets, hurting
 * everything within {@link #BURST_RADIUS} and throwing it back, but never its gunner's own side (nor, from a defender,
 * the villagers and golems about them: {@link Javelins#harms}). Blocks are left whole, so a kingdom doesn't knock down
 * its own walls. Drawn as Balkon's WeaponMod cannonball when that's loaded, a fire charge otherwise. Never saved.
 */
public final class Cannonball extends Projectile implements ItemSupplier {
	/** How fast it leaves the barrel and how fast it falls (blocks per tick, per tick). */
	static final double SPEED = 1.8, GRAVITY = 0.03;
	static final int MAX_LIFE = 100;
	/** For its first ticks it clears the wall's parapet in front of its barrel (blocks don't stop it). */
	static final int GRACE_TICKS = 2;
	/** Its blow on a direct hit; the burst falls off to {@link #BURST_SHARE} of it at this radius. */
	static final float DAMAGE = 14.0F, BURST_SHARE = 0.3F;
	static final double BURST_RADIUS = 2.5, KNOCKBACK = 0.9;

	public static EntityType<Cannonball> TYPE;

	private static @Nullable Item look;
	private int life;

	public Cannonball(EntityType<? extends Cannonball> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("cannonball"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<Cannonball>of(Cannonball::new, MobCategory.MISC)
			.noLootTable()
			.sized(0.4F, 0.4F)
			.clientTrackingRange(8)
			.updateInterval(1)
			.build(key));
	}

	/** A ball fired by {@code gunner} from {@code muzzle} along {@code dir}. */
	static void fire(ServerLevel level, Folk gunner, Vec3 muzzle, Vec3 dir) {
		Cannonball ball = new Cannonball(TYPE, level);
		ball.setOwner(gunner);
		ball.setPos(muzzle);
		ball.setDeltaMovement(dir.scale(SPEED));
		level.addFreshEntity(ball);
	}

	@Override
	protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
	}

	@Override
	public ItemStack getItem() {
		if (look == null) {
			Item ball = Javelins.WEAPONMOD_LOADED
				? BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Javelins.WEAPONMOD, "cannonball")) : Items.AIR;
			look = ball == Items.AIR ? Items.FIRE_CHARGE : ball;
		}
		return new ItemStack(look);
	}

	@Override
	public void tick() {
		super.tick();
		Vec3 pos = this.position();
		Vec3 move = this.getDeltaMovement();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		Entity owner = this.getOwner();
		if (!(owner instanceof Folk gunner) || ++this.life > MAX_LIFE) {
			this.discard();
			return;
		}
		Vec3 next = pos.add(move);
		HitResult hit = this.life <= GRACE_TICKS ? net.minecraft.world.phys.BlockHitResult.miss(next, net.minecraft.core.Direction.UP, net.minecraft.core.BlockPos.containing(next))
			: level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		if (hit.getType() != HitResult.Type.MISS) {
			next = hit.getLocation();
		}
		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level, this, pos, next, new AABB(pos, next).inflate(0.6),
			e -> e != gunner && e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && !(e instanceof Cannon) && !(e == gunner.getVehicle()), 0.3F);
		if (entityHit != null) {
			this.burst(level, gunner, entityHit.getLocation(), entityHit.getEntity());
			return;
		}
		if (hit.getType() != HitResult.Type.MISS) {
			this.burst(level, gunner, ((BlockHitResult) hit).getLocation(), null);
			return;
		}
		this.setPos(next);
		this.setDeltaMovement(move.add(0.0, -GRAVITY, 0.0));
		level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(), 2, 0.05, 0.05, 0.05, 0.0);
	}

	private void burst(ServerLevel level, Folk gunner, Vec3 at, @Nullable Entity direct) {
		DamageSource source = this.damageSources().explosion(this, gunner);
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(BURST_RADIUS),
			e -> e != gunner && e.isAlive() && !e.isSpectator())) {
			double d = victim == direct ? 0.0 : victim.getBoundingBox().getCenter().distanceTo(at);
			if (d > BURST_RADIUS || !Javelins.harms(gunner, victim)) {
				continue;
			}
			float share = (float) (1.0 - d / BURST_RADIUS);
			victim.hurtServer(level, source, DAMAGE * (BURST_SHARE + (1.0F - BURST_SHARE) * share));
			double resist = 1.0 - victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
			Vec3 away = victim.position().subtract(at);
			if (resist > 0.0 && away.lengthSqr() > 1.0E-4) {
				Vec3 push = away.normalize().scale(KNOCKBACK * share * resist);
				victim.push(push.x, 0.3 * share * resist, push.z);
				victim.syncVelocity = true;
			}
		}
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 10, 0.4, 0.4, 0.4, 0.04);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.6F, 1.0F);
		this.discard();
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
		return distance < 128.0 * 128.0;
	}
}
