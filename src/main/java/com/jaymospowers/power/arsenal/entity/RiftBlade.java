package com.jaymospowers.power.arsenal.entity;

import com.jaymospowers.power.arsenal.Summoned;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A Barrage weapon: it hovers in its rift for a moment (turning to track its target), then flies straight at it, hits
 * once and dissolves back into its dimension. It carries an ItemStack only for its looks and never drops anything.
 */
public class RiftBlade extends AbstractArrow implements ItemSupplier {
	private static final EntityDataAccessor<ItemStack> DATA_ITEM = SynchedEntityData.defineId(RiftBlade.class, EntityDataSerializers.ITEM_STACK);
	private static final EntityDataAccessor<Boolean> DATA_HOVER = SynchedEntityData.defineId(RiftBlade.class, EntityDataSerializers.BOOLEAN);

	/** Barrage weapons fly at this speed (blocks per tick) and give up after this long. */
	public static final float SPEED = 2.6F;
	public static final int MAX_LIFE = 120;

	private float impactDamage = 6.0F;
	private boolean dealtDamage;
	private int hoverTicks;
	private @Nullable Vec3 aim;
	private int targetId = -1;

	public RiftBlade(EntityType<? extends RiftBlade> type, Level level) {
		super(type, level);
		this.pickup = Pickup.DISALLOWED;
	}

	/** A blade hovering at {@code pos} for {@code hover} ticks, then launched at {@code target} (or at {@code aim}). */
	public static RiftBlade create(Level level, LivingEntity owner, ItemStack look, Vec3 pos, Vec3 aim, @Nullable Entity target,
		int hover, float damage) {
		RiftBlade blade = new RiftBlade(ArsenalEntities.RIFT_BLADE, level);
		blade.setOwner(owner);
		blade.pickup = Pickup.DISALLOWED; // setOwner makes player arrows pickupable
		blade.entityData.set(DATA_ITEM, look.copyWithCount(1));
		blade.entityData.set(DATA_HOVER, true);
		blade.impactDamage = damage;
		blade.hoverTicks = hover;
		blade.aim = aim;
		blade.targetId = target == null ? -1 : target.getId();
		blade.setNoGravity(true);
		blade.setPos(pos);
		blade.face(aim.subtract(pos));
		return blade;
	}

	private void face(Vec3 dir) {
		float yRot = (float) (Mth.atan2(dir.x, dir.z) * 180.0F / (float) Math.PI);
		float xRot = (float) (Mth.atan2(dir.y, dir.horizontalDistance()) * 180.0F / (float) Math.PI);
		this.setYRot(yRot);
		this.setXRot(xRot);
		this.yRotO = yRot;
		this.xRotO = xRot;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ITEM, ItemStack.EMPTY);
		builder.define(DATA_HOVER, false);
	}

	@Override
	public ItemStack getItem() {
		ItemStack stack = this.entityData.get(DATA_ITEM);
		return stack.isEmpty() ? new ItemStack(Items.IRON_SWORD) : stack;
	}

	public boolean isHovering() {
		return this.entityData.get(DATA_HOVER);
	}

	@Override
	public void tick() {
		if (this.level() instanceof ServerLevel level) {
			if (hoverTicks > 0) {
				hoverTicks--;
				if (this.tickCount % 3 == 0) {
					level.sendParticles(ParticleTypes.PORTAL, this.getX(), this.getY(), this.getZ(), 3, 0.15, 0.15, 0.15, 0.3);
				}
				Entity target = targetId < 0 ? null : level.getEntity(targetId);
				if (target != null && target.isAlive()) {
					face(target.getBoundingBox().getCenter().subtract(this.position()));
				}
				if (hoverTicks == 0) {
					launch(level, target);
				} else {
					return;
				}
			} else if (isHovering()) {
				// loaded from a save mid-hover: nothing to aim at any more
				dissolve(level);
				return;
			}
		} else if (isHovering()) {
			return;
		}

		super.tick();

		if (this.level() instanceof ServerLevel level) {
			if (this.isInGround() || this.tickCount > MAX_LIFE) {
				dissolve(level);
			}
		} else if (this.tickCount % 2 == 0) {
			this.level().addParticle(ParticleTypes.REVERSE_PORTAL, this.getX(), this.getY(), this.getZ(), 0, 0, 0);
		}
	}

	private void launch(ServerLevel level, @Nullable Entity target) {
		Vec3 at = aim == null ? this.position().add(this.getLookAngle()) : aim;
		if (target != null && target.isAlive()) {
			at = target.getBoundingBox().getCenter();
		}
		Vec3 dir = at.subtract(this.position());
		this.entityData.set(DATA_HOVER, false);
		this.shoot(dir.x, dir.y, dir.z, SPEED, 0.4F);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 0.7F, 1.3F);
	}

	/** Back into the rift: particles, a sound, gone (nothing drops). */
	public void dissolve(ServerLevel level) {
		Summoned.dissolveEffects(level, this.position());
		this.discard();
	}

	@Override
	protected @Nullable EntityHitResult findHitEntity(Vec3 from, Vec3 to) {
		return this.dealtDamage ? null : super.findHitEntity(from, to);
	}

	@Override
	protected Collection<EntityHitResult> findHitEntities(Vec3 from, Vec3 to) {
		EntityHitResult e = this.findHitEntity(from, to);
		return e != null ? List.of(e) : List.of();
	}

	@Override
	protected boolean canHitEntity(Entity entity) {
		return super.canHitEntity(entity) && entity != this.getOwner();
	}

	@Override
	protected void onHitEntity(EntityHitResult hitResult) {
		Entity entity = hitResult.getEntity();
		Entity owner = this.getOwner();
		DamageSource source = this.damageSources().trident(this, owner == null ? this : owner);
		this.dealtDamage = true;
		boolean hurt = entity.hurtOrSimulate(source, impactDamage);
		if (hurt && entity instanceof LivingEntity living) {
			this.doKnockback(living, source);
			Vec3 push = this.getDeltaMovement().multiply(1, 0, 1).normalize().scale(0.35);
			living.push(push.x, 0.08, push.z);
		}
		if (this.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY(), this.getZ(), 6, 0.2, 0.2, 0.2, 0.2);
			this.playSound(SoundEvents.TRIDENT_HIT, 0.8F, 1.3F);
			dissolve(level);
		}
	}

	@Override
	public boolean shouldBeSaved() {
		// a few seconds of life: never written to the chunk
		return false;
	}

	@Override
	protected ItemStack getDefaultPickupItem() {
		return new ItemStack(Items.IRON_SWORD);
	}

	@Override
	protected ItemStack getPickupItem() {
		return ItemStack.EMPTY;
	}

	@Override
	protected boolean tryPickup(net.minecraft.world.entity.player.Player player) {
		return false;
	}

	@Override
	protected SoundEvent getDefaultHitGroundSoundEvent() {
		return SoundEvents.TRIDENT_HIT_GROUND;
	}

	@Override
	public boolean shouldRender(double camX, double camY, double camZ) {
		return true;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.store("Look", ItemStack.OPTIONAL_CODEC, this.entityData.get(DATA_ITEM));
		output.putBoolean("Hover", isHovering());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(DATA_ITEM, input.read("Look", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
		this.entityData.set(DATA_HOVER, input.getBooleanOr("Hover", false));
	}
}
