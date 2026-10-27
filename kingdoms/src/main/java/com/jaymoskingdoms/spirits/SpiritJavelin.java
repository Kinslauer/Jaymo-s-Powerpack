package com.jaymoskingdoms.spirits;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Mana;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
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
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.238) The energy javelin of Old Varro's. Andy: "a collected energy javelin attack". A bolt of gathered mana that flies
 * straight for {@link #FLIGHT_TICKS} and then drops, passing through up to {@code pierce} foes (never a friend: {@link
 * SpiritFoes}) and stopping at a wall. The same entity is Varro's heavy orb (the bombard): slow, arcing, and bursting on
 * what it meets. It has no model, so the client draws nothing for it: the look is its trail, a faint end-rod glimmer for
 * everyone and a mote trail the attuned see. <b>The client must register a renderer that draws nothing for
 * {@link #TYPE}</b> (a no-op entity renderer). Never saved.
 */
public final class SpiritJavelin extends Projectile {
	/** The javelin's speed, how long it flies straight, and its whole life (ticks); a dropped javelin falls at {@link #GRAVITY}. */
	static final double SPEED = 2.2, GRAVITY = 0.05;
	static final int FLIGHT_TICKS = 40, MAX_LIFE = 80;
	/** The bombard orb: speed, gravity, life, burst radius and knockback. */
	static final double ORB_SPEED = 0.8, ORB_GRAVITY = 0.04, BURST_RADIUS = 3.0, BURST_KNOCK = 1.0;
	static final int ORB_LIFE = 100;
	/** How much of the burst's damage lands at its very edge. */
	static final float BURST_EDGE = 0.5F;
	/** The javelin's trail colour (amber). */
	static final DustParticleOptions AMBER = new DustParticleOptions(0xFFB347, 0.9F);

	public static EntityType<SpiritJavelin> TYPE;

	private float damage = 8.0F;
	private int pierce = 3;
	private boolean orb;
	private @Nullable UUID aim;
	private int life;
	private final List<UUID> struck = new ArrayList<>();

	public SpiritJavelin(EntityType<? extends SpiritJavelin> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	/** Registers the entity type (called from {@link Varro#register}). */
	static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("spirit_javelin"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<SpiritJavelin>of(SpiritJavelin::new, MobCategory.MISC)
			.noLootTable()
			.sized(0.4F, 0.4F)
			.clientTrackingRange(8)
			.updateInterval(1)
			.build(key));
	}

	/**
	 * Throws a javelin of {@code damage} from {@code from} along {@code dir} for {@code owner}, piercing {@code pierce} foes;
	 * {@code aim} is the one thing it may also hit though it is not hostile (what the keeper looked at).
	 */
	static SpiritJavelin throwFrom(ServerLevel level, LivingEntity owner, Vec3 from, Vec3 dir, float damage, int pierce, @Nullable LivingEntity aim) {
		SpiritJavelin javelin = new SpiritJavelin(TYPE, level);
		javelin.setOwner(owner);
		javelin.damage = damage;
		javelin.pierce = pierce;
		javelin.aim = aim == null ? null : aim.getUUID();
		javelin.setPos(from);
		javelin.setDeltaMovement(dir.normalize().scale(SPEED));
		level.addFreshEntity(javelin);
		return javelin;
	}

	/** Lobs a heavy orb of {@code damage} from {@code from} along {@code dir}: it arcs and bursts. */
	static SpiritJavelin lob(ServerLevel level, LivingEntity owner, Vec3 from, Vec3 dir, float damage) {
		SpiritJavelin orb = new SpiritJavelin(TYPE, level);
		orb.setOwner(owner);
		orb.damage = damage;
		orb.orb = true;
		orb.setPos(from);
		orb.setDeltaMovement(dir.normalize().scale(ORB_SPEED));
		level.addFreshEntity(orb);
		return orb;
	}

	@Override
	protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		Entity owner = this.getOwner();
		int limit = this.orb ? ORB_LIFE : MAX_LIFE;
		if (!(owner instanceof LivingEntity caster) || !owner.isAlive() || ++this.life > limit) {
			this.fizzle(level);
			return;
		}
		Vec3 pos = this.position();
		Vec3 move = this.getDeltaMovement();
		Vec3 next = pos.add(move);
		HitResult wall = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		if (wall.getType() != HitResult.Type.MISS) {
			next = wall.getLocation();
		}
		AABB sweep = new AABB(pos, next).inflate(0.6);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, this, pos, next, sweep, e -> this.hittable(caster, e), 0.3F);
		while (hit != null) {
			LivingEntity victim = (LivingEntity)hit.getEntity();
			if (this.orb) {
				this.burst(level, caster, hit.getLocation());
				return;
			}
			this.struck.add(victim.getUUID());
			victim.setInvulnerableTime(0);
			victim.hurtServer(level, this.damageSource(level, caster), this.damage);
			Vec3 at = hit.getLocation();
			level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.2, 0.2, 0.2, 0.3);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.8F, 1.3F);
			if (this.struck.size() >= this.pierce) {
				this.setPos(at);
				this.fizzle(level);
				return;
			}
			hit = ProjectileUtil.getEntityHitResult(level, this, pos, next, sweep, e -> this.hittable(caster, e), 0.3F);
		}
		if (wall.getType() != HitResult.Type.MISS) {
			if (this.orb) {
				this.burst(level, caster, ((BlockHitResult)wall).getLocation());
			} else {
				this.setPos(next);
				this.fizzle(level);
			}
			return;
		}
		this.trail(level, pos, next);
		this.setPos(next);
		boolean falls = this.orb || this.life > FLIGHT_TICKS;
		this.setDeltaMovement(falls ? move.add(0.0, -(this.orb ? ORB_GRAVITY : GRAVITY), 0.0) : move);
	}

	private boolean hittable(LivingEntity caster, Entity e) {
		return e instanceof LivingEntity living && !this.struck.contains(e.getUUID()) && SpiritFoes.foe(caster, living, e.getUUID().equals(this.aim));
	}

	private DamageSource damageSource(ServerLevel level, LivingEntity caster) {
		return level.damageSources().indirectMagic(this, caster);
	}

	private void trail(ServerLevel level, Vec3 from, Vec3 to) {
		int steps = this.orb ? 1 : 3;
		for (int i = 0; i < steps; i++) {
			Vec3 p = from.lerp(to, i / (double)steps);
			level.sendParticles(this.orb ? AMBER : ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
			Mana.sendSeen(level, Mana.MOTE, p.x, p.y, p.z, this.orb ? 2 : 1, 0.05, 0.05, 0.05, 0.0);
		}
	}

	/** The orb bursts at {@code at}: damage with falloff to every foe in {@link #BURST_RADIUS}, thrown back; blocks are left whole. */
	private void burst(ServerLevel level, LivingEntity caster, Vec3 at) {
		DamageSource source = this.damageSource(level, caster);
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(BURST_RADIUS),
			e -> SpiritFoes.foe(caster, e, e.getUUID().equals(this.aim)))) {
			double d = victim.getBoundingBox().getCenter().distanceTo(at);
			if (d > BURST_RADIUS) {
				continue;
			}
			float share = (float)(1.0 - d / BURST_RADIUS);
			victim.setInvulnerableTime(0);
			victim.hurtServer(level, source, this.damage * (BURST_EDGE + (1.0F - BURST_EDGE) * share));
			double resist = 1.0 - victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
			Vec3 away = victim.position().subtract(at);
			if (resist > 0.0 && away.lengthSqr() > 1.0E-4) {
				Vec3 push = away.normalize().scale(BURST_KNOCK * (0.4 + share) * resist);
				victim.push(push.x, 0.35 * (0.4 + share) * resist, push.z);
				victim.syncVelocity = true;
			}
		}
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(AMBER, at.x, at.y, at.z, 30, 0.8, 0.6, 0.8, 0.1);
		Mana.sendSeen(level, Mana.MOTE, at.x, at.y, at.z, 24, 1.0, 0.7, 1.0, 0.1);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2F, 1.2F);
		this.discard();
	}

	/** The javelin spends itself: a puff of light where it ends. */
	private void fizzle(ServerLevel level) {
		level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY(), this.getZ(), 5, 0.1, 0.1, 0.1, 0.05);
		Mana.sendSeen(level, Mana.MOTE, this.getX(), this.getY(), this.getZ(), 6, 0.15, 0.15, 0.15, 0.03);
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
