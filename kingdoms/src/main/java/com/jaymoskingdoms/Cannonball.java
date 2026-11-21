package com.jaymoskingdoms;

import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
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
import com.jaymoskingdoms.sailing.ShotKind;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.64) A {@link Cannon}'s shot: a heavy iron ball that arcs to its mark and bursts on whatever it meets, hurting
 * everything within {@link #BURST_RADIUS} and throwing it back, but never its gunner's own side (nor, from a defender,
 * the villagers and golems about them: {@link Javelins#harms}). Blocks are left whole, so a kingdom doesn't knock down
 * its own walls. Drawn as Balkon's WeaponMod cannonball when that's loaded, a fire charge otherwise. Never saved.
 * <p>
 * A {@link Musketoon}'s ball is the same thing out of a hand gun ({@link #shot}): anyone may fire it, a player too, with
 * its own blow and its own pit. A {@link FieldMortar}'s shell is the same again, lobbed ({@link #shell}).
 * <p>
 * Andy (2026-11-19): the shot kinds should work for these guns too. A wall or siege gun loads any of the ship guns' five
 * ({@link ShotKind}, {@link #fire(ServerLevel, PathfinderMob, Vec3, Vec3, ShotKind)}): the ball takes that kind's flight,
 * blow, burst, shove and pit, and is drawn as its item; grape spreads where its short flight ends.
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

	/** The {@link ShotKind} it was loaded as (-1: a plain ball, a hand gun's or a mortar's), for its look. */
	private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(Cannonball.class, EntityDataSerializers.INT);

	private static @Nullable Item look;
	private int life;
	/** Its blow on a direct hit, and the pit it leaves ({@code null}: a field gun's). */
	private float damage = DAMAGE;
	private com.jaymoskingdoms.sailing.Bombardment.@Nullable Crater pit;
	/** Out of a hand gun: its owner needn't be a gunner at a cannon. */
	private boolean hand;
	/** How long it may fly (ticks). */
	private int lifespan = MAX_LIFE;
	/** Its burst's reach, its shove and its fall per tick (a shot kind's own, else a plain ball's). */
	private double burstRadius = BURST_RADIUS, knockback = KNOCKBACK, gravity = GRAVITY;

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

	/**
	 * A wall or siege gun's shot of {@code kind}, fired by {@code gunner} from {@code muzzle} along {@code dir}. Round shot
	 * keeps a field gun's own pit ({@link com.jaymoskingdoms.sailing.Bombardment#FIELD}); the others leave their kind's.
	 */
	static void fire(ServerLevel level, PathfinderMob gunner, Vec3 muzzle, Vec3 dir, ShotKind kind) {
		Cannonball ball = new Cannonball(TYPE, level);
		ball.setOwner(gunner);
		ball.entityData.set(KIND, kind.ordinal());
		ball.damage = kind.damage;
		ball.burstRadius = kind.burst;
		ball.knockback = kind.knockback;
		ball.gravity = GRAVITY * kind.gravity;
		ball.pit = kind == ShotKind.ROUND ? null : kind.crater;
		ball.lifespan = kind == ShotKind.GRAPE ? kind.life : MAX_LIFE;
		ball.setPos(muzzle);
		ball.setDeltaMovement(dir.scale(SPEED * kind.speed));
		level.addFreshEntity(ball);
	}

	/** The shot kind it was loaded as, or null for a plain ball. */
	public @Nullable ShotKind kind() {
		int i = this.entityData.get(KIND);
		return i < 0 || i >= ShotKind.values().length ? null : ShotKind.values()[i];
	}

	/** A hand gun's ball, fired by {@code shooter} (a guard or a player) from {@code muzzle} along {@code dir}. */
	static void shot(ServerLevel level, LivingEntity shooter, Vec3 muzzle, Vec3 dir, float damage, com.jaymoskingdoms.sailing.Bombardment.Crater pit) {
		Cannonball ball = new Cannonball(TYPE, level);
		ball.setOwner(shooter);
		ball.hand = true;
		ball.damage = damage;
		ball.pit = pit;
		ball.setPos(muzzle);
		ball.setDeltaMovement(dir.scale(SPEED));
		level.addFreshEntity(ball);
	}

	/**
	 * A mortar's shell ({@link FieldMortar}): thrown from {@code muzzle} at {@code velocity}, so it can be lobbed high, by
	 * a siege crewman or a player; it may fly for {@code lifespan} ticks.
	 */
	static void shell(ServerLevel level, LivingEntity shooter, Vec3 muzzle, Vec3 velocity, float damage, com.jaymoskingdoms.sailing.Bombardment.Crater pit,
		int lifespan) {
		Cannonball ball = new Cannonball(TYPE, level);
		ball.setOwner(shooter);
		ball.hand = true;
		ball.damage = damage;
		ball.pit = pit;
		ball.lifespan = lifespan;
		ball.setPos(muzzle);
		ball.setDeltaMovement(velocity);
		level.addFreshEntity(ball);
	}

	@Override
	protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
		builder.define(KIND, -1);
	}

	@Override
	public ItemStack getItem() {
		ShotKind kind = this.kind();
		if (kind != null && kind != ShotKind.ROUND) {
			return new ItemStack(kind.item());
		}
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
		// ((0.7.295) a folk gunner or an MCA archer)
		if (!(owner instanceof LivingEntity gunner) || !(this.hand || owner instanceof PathfinderMob && owner instanceof Worker)) {
			this.discard();
			return;
		}
		if (++this.life > this.lifespan) {
			if (this.kind() == ShotKind.GRAPE) {
				// grape has flown as far as it's going: it spreads
				this.burst(level, gunner, pos, null);
			} else {
				this.discard();
			}
			return;
		}
		Vec3 next = pos.add(move);
		HitResult hit = this.life <= GRACE_TICKS && !this.hand ? net.minecraft.world.phys.BlockHitResult.miss(next, net.minecraft.core.Direction.UP, net.minecraft.core.BlockPos.containing(next))
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
			// (0.7.490) it scuffs the soil where it lands (a field gun's ball is no shell: loose ground and growth only),
			// but never on its own side's land or in a standing faction's buildings: a wall gun keeps its streets whole
			net.minecraft.core.BlockPos struck = ((BlockHitResult) hit).getBlockPos();
			// (Andy: "nothing should be safe": a siege train's field gun spares only its own realm's land, the besieged's buildings break)
			// (a player's hand gun spares nothing; a guard's spares what a wall gun does)
			if (!(gunner instanceof Worker w) || (SiegeTrain.crew(gunner) ? !ownLand(level, w, struck) : !ownGround(level, w, struck))) {
				com.jaymoskingdoms.sailing.Bombardment.crater(level, Vec3.atCenterOf(struck),
					this.pit == null ? com.jaymoskingdoms.sailing.Bombardment.FIELD : this.pit, gunner);
			}
			this.burst(level, gunner, ((BlockHitResult) hit).getLocation(), null);
			return;
		}
		this.setPos(next);
		this.setDeltaMovement(move.add(0.0, -this.gravity, 0.0));
		level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(), 2, 0.05, 0.05, 0.05, 0.0);
		if (this.kind() == ShotKind.SHELL) {
			// a shell's fuse sputters
			level.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY() + 0.1, this.getZ(), 1, 0.02, 0.02, 0.02, 0.0);
		}
	}

	/** (0.7.490) {@code pos} lies on land the gunner's own faction claims, or in a building some standing faction holds. */
	private static boolean ownGround(ServerLevel level, Worker gunner, net.minecraft.core.BlockPos pos) {
		return ownLand(level, gunner, pos) || Holds.guarded(level.getServer(), pos);
	}

	/** {@code pos} lies on land the gunner's own faction claims. */
	static boolean ownLand(ServerLevel level, Worker gunner, net.minecraft.core.BlockPos pos) {
		Faction own = Factions.get(level.getServer(), gunner.factionId());
		return own != null && own.claims(net.minecraft.world.level.ChunkPos.containing(pos));
	}

	private void burst(ServerLevel level, LivingEntity gunner, Vec3 at, @Nullable Entity direct) {
		Deafened.blast(level, at, Deafened.BLAST);
		DamageSource source = this.damageSources().explosion(this, gunner);
		double radius = this.burstRadius;
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius),
			e -> e != gunner && e.isAlive() && !e.isSpectator())) {
			double d = victim == direct ? 0.0 : victim.getBoundingBox().getCenter().distanceTo(at);
			// (a field gun at a siege hurts the besieged realm's fighters too: SiegeTrain)
			// (a player's ball spares nobody but those in creative)
			boolean harms = gunner instanceof net.minecraft.world.entity.player.Player
				? !(victim instanceof net.minecraft.world.entity.player.Player p && p.isCreative())
				: Javelins.harms(gunner, victim) || gunner instanceof PathfinderMob crew && SiegeTrain.crew(crew) && SiegeTrain.harms(crew, victim);
			if (d > radius || !harms) {
				continue;
			}
			float share = (float) (1.0 - d / radius);
			victim.hurtServer(level, source, this.damage * (BURST_SHARE + (1.0F - BURST_SHARE) * share));
			double resist = 1.0 - victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
			Vec3 away = victim.position().subtract(at);
			if (resist > 0.0 && away.lengthSqr() > 1.0E-4) {
				Vec3 push = away.normalize().scale(this.knockback * share * resist);
				victim.push(push.x, 0.3 * share * resist, push.z);
				victim.syncVelocity = true;
			}
		}
		ShotKind kind = this.kind();
		if (kind == ShotKind.SHELL) {
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 24, 1.0, 0.8, 1.0, 0.06);
			level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 16, 0.8, 0.5, 0.8, 0.08);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3.0F, 0.8F);
		} else if (kind == ShotKind.GRAPE) {
			level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 30, 1.6, 0.8, 1.6, 0.4);
			level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 14, 1.2, 0.6, 1.2, 0.05);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 1.2F, 1.5F);
		} else if (kind == ShotKind.CHAIN) {
			level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 10, 0.4, 0.4, 0.4, 0.2);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 1.5F, 0.7F);
		} else {
			level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 10, 0.4, 0.4, 0.4, 0.04);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.6F, kind == ShotKind.HEAVY ? 0.8F : 1.0F);
		}
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
