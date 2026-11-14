package com.jaymoskingdoms;

import com.jaymoskingdoms.sailing.Bombardment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A {@link Mangonel}'s stone: lobbed high, it comes down on whatever is under it, hurting the thrower's foes within
 * {@link #BURST_RADIUS} ({@link SiegeTrain#harms}) and knocking them back. It is a stone and not a shell: where it lands
 * it only tears up loose ground and growth, never on its own side's land or in a standing faction's buildings. Drawn as
 * a cobblestone. Never saved.
 */
public final class SiegeStone extends Projectile implements ItemSupplier {
	/** How fast it leaves the cup and how fast it falls (blocks per tick, per tick). */
	static final double SPEED = 1.45, GRAVITY = 0.04;
	static final int MAX_LIFE = 200;
	/** For its first ticks it clears its own engine's frame (blocks don't stop it). */
	static final int GRACE_TICKS = 4;
	/** Its blow on a direct hit; the burst falls off to {@link #BURST_SHARE} of it at this radius. */
	static final float DAMAGE = 12.0F, BURST_SHARE = 0.3F;
	static final double BURST_RADIUS = 2.5, KNOCKBACK = 0.8;
	/** What it does to the ground: soil, sand, plants and leaves in a small dent. */
	static final Bombardment.Crater DENT = new Bombardment.Crater(1.6, 1, 1, 3.0F, Bombardment.Strip.SOFT, 16, false);

	public static EntityType<SiegeStone> TYPE;

	private int life;

	public SiegeStone(EntityType<? extends SiegeStone> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("siege_stone"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<SiegeStone>of(SiegeStone::new, MobCategory.MISC)
			.noLootTable()
			.sized(0.5F, 0.5F)
			.clientTrackingRange(8)
			.updateInterval(1)
			.build(key));
	}

	/** A stone thrown by {@code thrower} from {@code cup} at {@code velocity}. */
	static void loose(ServerLevel level, PathfinderMob thrower, Vec3 cup, Vec3 velocity) {
		SiegeStone stone = new SiegeStone(TYPE, level);
		stone.setOwner(thrower);
		stone.setPos(cup);
		stone.setDeltaMovement(velocity);
		level.addFreshEntity(stone);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	public ItemStack getItem() {
		return new ItemStack(Items.COBBLESTONE);
	}

	@Override
	public void tick() {
		super.tick();
		Vec3 pos = this.position();
		Vec3 move = this.getDeltaMovement();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		if (!(this.getOwner() instanceof PathfinderMob thrower) || !(thrower instanceof Worker) || ++this.life > MAX_LIFE) {
			this.discard();
			return;
		}
		Vec3 next = pos.add(move);
		HitResult hit = this.life <= GRACE_TICKS ? BlockHitResult.miss(next, Direction.UP, BlockPos.containing(next))
			: level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		if (hit.getType() != HitResult.Type.MISS) {
			next = hit.getLocation();
		}
		EntityHitResult entityHit = this.life <= GRACE_TICKS ? null : ProjectileUtil.getEntityHitResult(level, this, pos, next, new AABB(pos, next).inflate(0.6),
			e -> e instanceof LivingEntity living && e.isAlive() && !e.isSpectator() && SiegeTrain.harms(thrower, living), 0.3F);
		if (entityHit != null) {
			this.burst(level, thrower, entityHit.getLocation(), entityHit.getEntity());
			return;
		}
		if (hit.getType() != HitResult.Type.MISS) {
			BlockPos struck = ((BlockHitResult) hit).getBlockPos();
			if (!Cannonball.ownGround(level, (Worker) thrower, struck)) {
				Bombardment.crater(level, Vec3.atCenterOf(struck), DENT, thrower);
			}
			this.burst(level, thrower, hit.getLocation(), null);
			return;
		}
		this.setPos(next);
		this.setDeltaMovement(move.add(0.0, -GRAVITY, 0.0));
	}

	private void burst(ServerLevel level, PathfinderMob thrower, Vec3 at, @Nullable Entity direct) {
		DamageSource source = this.damageSources().thrown(this, thrower);
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(BURST_RADIUS),
			e -> e.isAlive() && !e.isSpectator())) {
			double d = victim == direct ? 0.0 : victim.getBoundingBox().getCenter().distanceTo(at);
			if (d > BURST_RADIUS || !SiegeTrain.harms(thrower, victim)) {
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
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBBLESTONE.defaultBlockState()), at.x, at.y + 0.2, at.z, 40, 0.5, 0.3, 0.5, 0.2);
		level.sendParticles(ParticleTypes.POOF, at.x, at.y + 0.2, at.z, 10, 0.5, 0.2, 0.5, 0.03);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 3.0F, 0.5F);
		PowersCompat.shakeAround(level, at, 8.0, 0.8F);
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
