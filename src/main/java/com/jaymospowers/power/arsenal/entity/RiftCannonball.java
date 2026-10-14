package com.jaymospowers.power.arsenal.entity;

import com.jaymospowers.core.Debris;
import com.jaymospowers.power.arsenal.ArsenalDamocles;
import com.jaymospowers.power.arsenal.ArsenalVoid;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import java.util.Optional;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A Cannon Battery's shot: a heavy ball that flies fast and drops a little, and bursts on whatever it meets, hurting
 * everything close (not its owner) and throwing it back. Blocks are left alone, except by the Void Arsenal's: each of
 * those breaks up to {@link #VOID_BLOCKS} blocks where it lands. Drawn as Balkon's WeaponMod cannonball
 * when that's in the pack, a fire charge otherwise. Server-driven; never saved.
 */
public class RiftCannonball extends Projectile implements ItemSupplier {
	public static final double SPEED = 2.6, GRAVITY = 0.02;
	public static final int MAX_LIFE = 60;
	/** The burst: full damage on a direct hit, falling off to nothing at this radius; and how hard it shoves. */
	public static final double BURST_RADIUS = 2.5;
	public static final double KNOCKBACK = 0.8;
	/** A void ball breaks up to this many blocks (nearest the impact first), within this reach, up to this hard. */
	public static final int VOID_BLOCKS = 3;
	public static final double VOID_BREAK_REACH = 1.6;
	public static final float VOID_MAX_HARDNESS = 5.0F;

	private static final EntityDataAccessor<Boolean> VOID = SynchedEntityData.defineId(RiftCannonball.class, EntityDataSerializers.BOOLEAN);
	private static @Nullable Item look;

	private float damage;
	private int life;

	public RiftCannonball(EntityType<? extends RiftCannonball> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	public static RiftCannonball create(ServerLevel level, ServerPlayer owner, Vec3 pos, Vec3 dir, float damage, boolean voidShot) {
		RiftCannonball ball = new RiftCannonball(ArsenalEntities.RIFT_CANNONBALL, level);
		ball.setOwner(owner);
		ball.setPos(pos);
		ball.setDeltaMovement(dir.scale(SPEED));
		ball.damage = damage;
		ball.entityData.set(VOID, voidShot);
		return ball;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(VOID, false);
	}

	public boolean isVoid() {
		return this.entityData.get(VOID);
	}

	@Override
	public ItemStack getItem() {
		if (look == null) {
			// Balkon's cannonball if the pack has it (looked up by name: no dependency)
			Optional<Item> ball = BuiltInRegistries.ITEM.keySet().stream()
				.filter(id -> id.getNamespace().equals("weaponmod") && id.getPath().contains("cannonball"))
				.findFirst().map(BuiltInRegistries.ITEM::getValue);
			look = ball.orElse(Items.FIRE_CHARGE);
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
		if (!(owner instanceof ServerPlayer player) || player.level() != level || ++this.life > MAX_LIFE) {
			this.discard();
			return;
		}
		Vec3 next = pos.add(move);
		HitResult hit = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		if (hit.getType() != HitResult.Type.MISS) {
			next = hit.getLocation();
		}
		EntityHitResult entityHit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(level, this, pos, next,
			new AABB(pos, next).inflate(0.6), e -> e != player && e instanceof LivingEntity && e.isAlive() && !e.isSpectator(), 0.3F);
		if (entityHit != null) {
			this.burst(level, player, entityHit.getLocation(), entityHit.getEntity());
			return;
		}
		if (hit.getType() != HitResult.Type.MISS) {
			this.burst(level, player, ((BlockHitResult) hit).getLocation(), null);
			return;
		}
		this.setPos(next);
		this.setDeltaMovement(move.add(0.0, -GRAVITY, 0.0));
		level.sendParticles(this.isVoid() ? ParticleTypes.SQUID_INK : ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(), 2, 0.05, 0.05, 0.05, 0.0);
	}

	private void burst(ServerLevel level, ServerPlayer player, Vec3 at, @Nullable Entity direct) {
		DamageSource source = this.damageSources().explosion(this, player);
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(BURST_RADIUS),
			e -> e != player && e.isAlive() && !e.isSpectator())) {
			double d = victim == direct ? 0.0 : victim.getBoundingBox().getCenter().distanceTo(at);
			if (d > BURST_RADIUS) {
				continue;
			}
			float share = (float) (1.0 - d / BURST_RADIUS);
			victim.hurtServer(level, source, this.damage * (0.4F + 0.6F * share));
			if (this.isVoid()) {
				ArsenalVoid.voidHit(victim, player, false);
			}
			double resist = 1.0 - victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
			Vec3 away = victim.position().subtract(at);
			if (resist > 0.0 && away.lengthSqr() > 1.0E-4) {
				Vec3 push = away.normalize().scale(KNOCKBACK * share * resist);
				victim.push(push.x, 0.25 * share * resist, push.z);
				victim.syncVelocity = true;
			}
		}
		if (this.isVoid()) {
			this.breakBlocks(level, player, at);
		}
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 10, 0.4, 0.4, 0.4, 0.04);
		if (this.isVoid()) {
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z, 20, 0.4, 0.4, 0.4, 0.1);
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.0F, 1.2F);
		com.jaymospowers.core.Shakes.around(level, at, BURST_RADIUS * 3.0, this.isVoid() ? 1.1F : 0.8F);
		this.discard();
	}

	/** The void ball's bite: the nearest few blocks to {@code at} it can break, where its owner may build. */
	private void breakBlocks(ServerLevel level, ServerPlayer player, Vec3 at) {
		BlockPos center = BlockPos.containing(at);
		java.util.List<BlockPos> near = new java.util.ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-2, -2, -2), center.offset(2, 2, 2))) {
			if (pos.distToCenterSqr(at) <= VOID_BREAK_REACH * VOID_BREAK_REACH) {
				near.add(pos.immutable());
			}
		}
		near.sort(java.util.Comparator.comparingDouble(pos -> pos.distToCenterSqr(at)));
		int broken = 0;
		for (BlockPos pos : near) {
			if (broken >= VOID_BLOCKS) {
				return;
			}
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity()) {
				continue;
			}
			float hardness = state.getDestroySpeed(level, pos);
			if (hardness < 0.0F || hardness > VOID_MAX_HARDNESS || !ArsenalDamocles.mayBreak(player, level, pos, state)) {
				continue;
			}
			level.destroyBlock(pos, true, player);
			PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(level, player, pos, state, null);
			Debris.puff(level, pos, state, 1.0F);
			broken++;
		}
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
		return distance < 96.0 * 96.0;
	}
}
