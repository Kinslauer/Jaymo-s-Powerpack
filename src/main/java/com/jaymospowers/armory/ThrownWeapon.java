package com.jaymospowers.armory;

import com.jaymospowers.tinkering.ranged.Ranged;
import java.util.Collection;
import java.util.List;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A thrown Armory weapon (see {@link ThrowingWeaponItem}): it strikes the first thing it meets, once, for the weapon's
 * thrown damage, then drops and lies where it fell for its thrower to pick up, as a trident does. A harpoon flies
 * through water as easily as air and drags what it strikes back toward the thrower.
 */
public class ThrownWeapon extends AbstractArrow implements ItemSupplier {
	private static final EntityDataAccessor<ItemStack> DATA_ITEM = SynchedEntityData.defineId(ThrownWeapon.class, EntityDataSerializers.ITEM_STACK);
	/** Harpoon: how hard it drags its catch back toward the thrower. */
	static final double HARPOON_PULL = 0.9;

	/** A hurled blade that comes back: the ticks it flies out before turning, how hard it is pulled home (blocks/tick each tick), and how near the hand it is caught (blocks). */
	static final int RETURN_AFTER = 14;
	static final double RETURN_PULL = 0.3, RETURN_CATCH = 1.6;

	private boolean dealtDamage;
	/** A blade hurled by {@code BladeThrow}: what it deals (negative: this weapon's own thrown damage), whether it flies back to the hand, and how long it has flown. */
	private float hurled = -1.0F;
	private boolean comesBack;
	private int flown;

	public ThrownWeapon(EntityType<? extends ThrownWeapon> type, Level level) {
		super(type, level);
	}

	public ThrownWeapon(Level level, LivingEntity owner, ItemStack weapon) {
		super(ArmoryEntities.THROWN_WEAPON, owner, level, weapon, null);
		this.entityData.set(DATA_ITEM, weapon.copyWithCount(1));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(DATA_ITEM, ItemStack.EMPTY);
	}

	@Override
	public ItemStack getItem() {
		return this.entityData.get(DATA_ITEM);
	}

	/** It tumbles end over end (until it lands). A sword always does (a hurled blade, {@code BladeThrow}). */
	public boolean spins() {
		ItemStack item = this.getItem();
		return (item.getItem() instanceof ThrowingWeaponItem weapon ? weapon.spins() : Ranged.spins(item) || item.is(net.minecraft.tags.ItemTags.SWORDS))
			&& !this.isInGround();
	}

	/** This is a hurled blade ({@code BladeThrow}): it deals {@code damage}, and {@code comesBack} to its thrower's hand. */
	public void hurled(float damage, boolean comesBack) {
		this.hurled = damage;
		this.comesBack = comesBack;
	}

	/** A blade that comes back: once it has struck, stuck or flown its length it turns and flies home through anything. True when it was caught (and is gone). */
	private boolean flyHome() {
		this.flown++;
		if (!this.dealtDamage && !this.isInGround() && !this.isNoPhysics() && this.flown <= RETURN_AFTER) {
			return false;
		}
		if (!(this.getOwner() instanceof Player owner) || !owner.isAlive() || owner.isSpectator() || owner.level() != this.level()) {
			// nobody to fly to: it falls and lies like any other
			this.comesBack = false;
			this.setNoPhysics(false);
			return false;
		}
		this.dealtDamage = true;
		this.setNoPhysics(true);
		Vec3 to = owner.getEyePosition().add(0.0, -0.3, 0.0).subtract(this.position());
		if (to.length() < RETURN_CATCH) {
			ItemStack blade = this.getPickupItemStackOrigin().copy();
			if (this.pickup == AbstractArrow.Pickup.ALLOWED) {
				if (owner.getMainHandItem().isEmpty()) {
					owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, blade);
				} else if (!owner.getInventory().add(blade)) {
					this.spawnAtLocation((net.minecraft.server.level.ServerLevel)this.level(), blade);
				}
			}
			this.playSound(SoundEvents.TRIDENT_RETURN, 0.8F, 1.3F);
			this.discard();
			return true;
		}
		this.setDeltaMovement(this.getDeltaMovement().scale(0.8).add(to.normalize().scale(RETURN_PULL)));
		this.needsSync = true;
		return false;
	}

	private boolean harpoon() {
		return this.getItem().is(Armory.ITEMS.get("harpoon"));
	}

	@Override
	public void tick() {
		if (this.inGroundTime > 4) {
			this.dealtDamage = true;
		}
		if (this.comesBack && !this.level().isClientSide() && this.flyHome()) {
			return;
		}
		super.tick();
	}

	@Override
	protected @Nullable EntityHitResult findHitEntity(Vec3 from, Vec3 to) {
		return this.dealtDamage ? null : super.findHitEntity(from, to);
	}

	@Override
	protected Collection<EntityHitResult> findHitEntities(Vec3 from, Vec3 to) {
		EntityHitResult hit = this.findHitEntity(from, to);
		return hit != null ? List.of(hit) : List.of();
	}

	@Override
	protected void onHitEntity(EntityHitResult hitResult) {
		Entity entity = hitResult.getEntity();
		Entity owner = this.getOwner();
		// (0.15.x) Tinkering's javelin, throwing axes and shuriken are thrown as this too, with their materials' damage
		float damage = this.hurled >= 0.0F ? this.hurled
			: this.getItem().getItem() instanceof ThrowingWeaponItem weapon ? weapon.thrownDamage() : Ranged.thrownDamage(this.getItem());
		DamageSource source = this.damageSources().thrown(this, owner == null ? this : owner);
		if (this.level() instanceof ServerLevel serverLevel) {
			damage = EnchantmentHelper.modifyDamage(serverLevel, this.getWeaponItem(), entity, source, damage);
		}
		this.dealtDamage = true;
		boolean hurt = entity.hurtOrSimulate(source, damage);
		if (hurt) {
			if (this.level() instanceof ServerLevel serverLevel) {
				EnchantmentHelper.doPostAttackEffectsWithItemSourceOnBreak(serverLevel, entity, source, this.getWeaponItem(), item -> this.kill(serverLevel));
			}
			if (entity instanceof LivingEntity living) {
				this.doKnockback(living, source);
				this.doPostHurtEffects(living);
				if (this.harpoon() && owner != null) {
					Vec3 pull = owner.position().subtract(living.position());
					if (pull.lengthSqr() > 1.0E-4) {
						pull = pull.normalize().scale(HARPOON_PULL);
						living.push(pull.x, Math.max(0.2, pull.y), pull.z);
						living.syncVelocity = true;
					}
				}
			}
		}
		// (0.18.118) a dagger that leaves a zombie standing stays in it (Lodged)
		if (hurt && entity instanceof LivingEntity living && Lodged.thrownIn(living, this.getPickupItemStackOrigin())) {
			this.playSound(SoundEvents.TRIDENT_HIT, 0.8F, 0.9F);
			this.discard();
			return;
		}
		if (entity.projectileReceivesSideEffectsOnHit(hurt)) {
			this.playSound(SoundEvents.TRIDENT_HIT, 0.8F, 1.2F);
			this.deflect(ProjectileDeflection.REVERSE, entity, this.owner, false, new Vec3(0.02, 0.2, 0.02));
		}
	}

	/** (0.18.118) It strikes nothing more: a blade left standing in a body (Lodged), a spear that glanced off (SpellThrows). */
	public void spent() {
		this.dealtDamage = true;
	}

	@Override
	public ItemStack getWeaponItem() {
		return this.getPickupItemStackOrigin();
	}

	@Override
	protected ItemStack getDefaultPickupItem() {
		return new ItemStack(Items.STICK);
	}

	@Override
	protected SoundEvent getDefaultHitGroundSoundEvent() {
		return SoundEvents.TRIDENT_HIT_GROUND;
	}

	@Override
	public void playerTouch(Player player) {
		if (this.ownedBy(player) || this.getOwner() == null) {
			super.playerTouch(player);
		}
	}

	@Override
	protected float getWaterInertia() {
		return this.harpoon() ? 0.99F : super.getWaterInertia();
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.dealtDamage = input.getBooleanOr("DealtDamage", false);
		this.hurled = input.getFloatOr("Hurled", -1.0F);
		this.comesBack = input.getBooleanOr("ComesBack", false);
		this.entityData.set(DATA_ITEM, this.getPickupItemStackOrigin().copyWithCount(1));
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("DealtDamage", this.dealtDamage);
		output.putFloat("Hurled", this.hurled);
		output.putBoolean("ComesBack", this.comesBack);
	}
}
