package com.jaymoskingdoms;

import net.minecraft.core.Registry;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
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
 * The lead ball out of a handgonne or an arquebus ({@link EarlyGuns}): small, fast and dropping as it flies, it hits the
 * first living thing in its way for the blow it was fired with and stops there, or flattens on the first block. Nobody
 * parries it; a shield still stops it. A guard's ball passes his own side by ({@link Javelins#harms}); a player's spares
 * only those in creative. Drawn as Balkon's WeaponMod musket ball when that's loaded, an iron nugget otherwise. Never saved.
 */
public final class GunBall extends Projectile implements ItemSupplier {
	/** How fast it falls (blocks per tick, per tick), and how long it flies at most (ticks). */
	static final double GRAVITY = 0.02;
	static final int MAX_LIFE = 60;
	/** How wide of its line it still strikes a body (blocks). */
	static final float REACH = 0.25F;

	public static EntityType<GunBall> TYPE;

	private static @Nullable Item look;
	private int life;
	private float damage;

	public GunBall(EntityType<? extends GunBall> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("gun_ball"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<GunBall>of(GunBall::new, MobCategory.MISC)
			.noLootTable()
			.sized(0.2F, 0.2F)
			.clientTrackingRange(8)
			.updateInterval(1)
			.build(key));
	}

	/** A ball fired by {@code shooter} from {@code muzzle} along {@code dir} at {@code speed} blocks a tick, to hit for {@code damage}. */
	static void shot(ServerLevel level, LivingEntity shooter, Vec3 muzzle, Vec3 dir, double speed, float damage) {
		GunBall ball = new GunBall(TYPE, level);
		ball.setOwner(shooter);
		ball.damage = damage;
		ball.setPos(muzzle);
		ball.setDeltaMovement(dir.scale(speed));
		MobCombat.unparryable(ball);
		level.addFreshEntity(ball);
	}

	@Override
	protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
	}

	@Override
	public ItemStack getItem() {
		if (look == null) {
			Item ball = Javelins.WEAPONMOD_LOADED
				? BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Javelins.WEAPONMOD, "bullet")) : Items.AIR;
			look = ball == Items.AIR ? Items.IRON_NUGGET : ball;
		}
		return new ItemStack(look);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		Entity owner = this.getOwner();
		if (!(owner instanceof LivingEntity shooter) || ++this.life > MAX_LIFE) {
			this.discard();
			return;
		}
		Vec3 pos = this.position();
		Vec3 move = this.getDeltaMovement();
		Vec3 next = pos.add(move);
		HitResult hit = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		if (hit.getType() != HitResult.Type.MISS) {
			next = hit.getLocation();
		}
		EntityHitResult struck = ProjectileUtil.getEntityHitResult(level, this, pos, next, new AABB(pos, next).inflate(0.5),
			e -> e instanceof LivingEntity victim && e != shooter && e != shooter.getVehicle() && e.isAlive() && !e.isSpectator()
				&& harms(shooter, victim), REACH);
		if (struck != null) {
			DamageSource source = this.damageSources().mobProjectile(this, shooter);
			struck.getEntity().hurtServer(level, source, this.damage);
			this.discard();
			return;
		}
		if (hit.getType() != HitResult.Type.MISS) {
			BlockState state = level.getBlockState(((BlockHitResult) hit).getBlockPos());
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), next.x, next.y, next.z, 6, 0.05, 0.05, 0.05, 0.05);
			level.playSound(null, next.x, next.y, next.z, state.getSoundType().getHitSound(), net.minecraft.sounds.SoundSource.NEUTRAL, 0.8F, 1.4F);
			this.discard();
			return;
		}
		this.setPos(next);
		this.setDeltaMovement(move.add(0.0, -GRAVITY, 0.0));
		level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
	}

	/** A player's ball spares nobody but those in creative; a guard's spares his own side and those he guards. */
	private static boolean harms(LivingEntity shooter, LivingEntity victim) {
		return shooter instanceof Player ? !(victim instanceof Player p && p.isCreative()) : Javelins.harms(shooter, victim);
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
