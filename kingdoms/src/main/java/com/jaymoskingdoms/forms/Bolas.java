package com.jaymoskingdoms.forms;

import com.jaymoskingdoms.JaymosKingdoms;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.CreativeModeTabs;
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
 * (0.7.383) Bolas, the Tangle Cord's weapon: two weights on a cord (leather, string, and two stone or iron nuggets at the
 * crafting grid). Thrown by hand or by the Knife school's Tangle Cord form, it flies in an arc and wraps the legs of the first
 * living thing it meets (never its thrower, never another player): slowed like a sprained foot for {@link #TANGLE_TICKS}.
 * Whether it hits or misses, it drops back as the item where it ends ({@link #land}), also if it flies too long or is
 * unloaded; it is never lost except to the void and to lava.
 */
public final class Bolas extends Projectile implements ItemSupplier {
	/** How fast it falls (blocks per tick, per tick) and the most ticks it flies before it drops. */
	static final double GRAVITY = 0.04;
	static final int FLIGHT_TICKS = 80;
	/** A hand throw's speed (blocks per tick) and loft, and the wait between throws (ticks). */
	static final float THROW_SPEED = 1.3F, THROW_LOFT = 0.08F;
	static final int THROW_COOLDOWN = 20;
	/** The tangle: slowness level (0 is the first), how long (ticks), and a little more at each rank of the form. */
	static final int TANGLE_SLOW = 1, TANGLE_TICKS = 80, TANGLE_PER_RANK = 20;
	/** A bolas hits for this little (a wrap, not a blow). */
	static final float WRAP_DAMAGE = 1.0F;

	public static EntityType<Bolas> TYPE;
	public static Item ITEM;

	/** The form rank it was thrown with (0 for a hand throw): practice and length of the tangle. */
	private int rank;
	/** Whether the Tangle Cord form threw it (a landing counts as practice). */
	private boolean fromForm;
	private int flight;

	public Bolas(EntityType<? extends Bolas> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("bolas"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<Bolas>of(Bolas::new, MobCategory.MISC)
			.noLootTable()
			.sized(0.3F, 0.3F)
			.clientTrackingRange(8)
			.updateInterval(1)
			.build(key));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("bolas"));
		ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey, new BolasItem(new Item.Properties().setId(itemKey).stacksTo(16)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> output.insertAfter(Items.WIND_CHARGE, ITEM));
	}

	/** A bolas thrown by {@code thrower} along {@code velocity}. */
	static Bolas throwFrom(ServerLevel level, LivingEntity thrower, Vec3 velocity, int rank, boolean fromForm) {
		Bolas bolas = new Bolas(TYPE, level);
		bolas.setOwner(thrower);
		bolas.rank = rank;
		bolas.fromForm = fromForm;
		bolas.setPos(thrower.getX(), thrower.getEyeY() - 0.2, thrower.getZ());
		bolas.setDeltaMovement(velocity);
		level.addFreshEntity(bolas);
		level.playSound(null, thrower.getX(), thrower.getY(), thrower.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.8F, 0.6F);
		return bolas;
	}

	@Override
	protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
	}

	@Override
	public ItemStack getItem() {
		return new ItemStack(ITEM);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		Vec3 pos = this.position();
		if (++this.flight > FLIGHT_TICKS || !level.getFluidState(BlockPos.containing(pos)).isEmpty()) {
			this.land(level, pos);
			return;
		}
		Vec3 move = this.getDeltaMovement().add(0.0, -GRAVITY, 0.0);
		Vec3 next = pos.add(move);
		Entity owner = this.getOwner();
		EntityHitResult struck = ProjectileUtil.getEntityHitResult(level, this, pos, next, new AABB(pos, next).inflate(0.4),
			e -> e != owner && e instanceof LivingEntity && !(e instanceof Player) && e.isAlive() && !e.isSpectator()
				&& (e instanceof net.minecraft.world.entity.monster.Enemy || e instanceof net.minecraft.world.entity.Mob mob && owner != null && mob.getTarget() == owner), 0.3F);
		HitResult hit = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		if (struck != null && struck.getEntity() instanceof LivingEntity foe) {
			this.wrap(level, foe);
			return;
		}
		if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
			Direction face = block.getDirection();
			this.land(level, block.getLocation().add(face.getStepX() * 0.2, face.getStepY() * 0.2, face.getStepZ() * 0.2));
			return;
		}
		this.setPos(next);
		this.setDeltaMovement(move);
		level.sendParticles(ParticleTypes.CRIT, next.x, next.y, next.z, 1, 0.0, 0.0, 0.0, 0.0);
	}

	/** It wraps the foe's legs and falls to its feet. */
	private void wrap(ServerLevel level, LivingEntity foe) {
		int ticks = TANGLE_TICKS + TANGLE_PER_RANK * Math.max(0, this.rank - 1);
		foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, TANGLE_SLOW), this.getOwner());
		if (this.getOwner() instanceof ServerPlayer player) {
			foe.hurtServer(level, this.damageSources().thrown(this, player), WRAP_DAMAGE);
			if (this.fromForm) {
				Forms.landed(player, KnifeForms.TANGLE_CORD, java.util.List.of(foe));
			}
		}
		level.sendParticles(ParticleTypes.CRIT, foe.getX(), foe.getY(0.2), foe.getZ(), 8, 0.25, 0.1, 0.25, 0.1);
		level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.LEAD_BREAK, SoundSource.PLAYERS, 0.9F, 1.0F);
		this.land(level, foe.position());
	}

	/** It comes to rest as the item, and is gone. */
	private void land(ServerLevel level, Vec3 at) {
		if (!this.isRemoved()) {
			this.setPos(at);
			this.spawnAtLocation(level, new ItemStack(ITEM));
			this.discard();
		}
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 96.0 * 96.0;
	}

	/** A pair of bolas: use it to throw it. */
	static final class BolasItem extends Item {
		BolasItem(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (level instanceof ServerLevel server) {
				Vec3 look = player.getLookAngle();
				throwFrom(server, player, look.scale(THROW_SPEED).add(0.0, THROW_LOFT, 0.0).add(player.getDeltaMovement().multiply(1.0, 0.0, 1.0)), 0, false);
			}
			ItemStack held = player.getItemInHand(hand);
			player.getCooldowns().addCooldown(held, THROW_COOLDOWN);
			held.consume(1, player);
			return InteractionResult.SUCCESS;
		}
	}
}
