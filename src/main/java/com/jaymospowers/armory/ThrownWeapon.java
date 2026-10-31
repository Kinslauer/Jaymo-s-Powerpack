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

	private boolean dealtDamage;

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

	/** It tumbles end over end (until it lands). */
	public boolean spins() {
		ItemStack item = this.getItem();
		return (item.getItem() instanceof ThrowingWeaponItem weapon ? weapon.spins() : Ranged.spins(item)) && !this.isInGround();
	}

	private boolean harpoon() {
		return this.getItem().is(Armory.ITEMS.get("harpoon"));
	}

	@Override
	public void tick() {
		if (this.inGroundTime > 4) {
			this.dealtDamage = true;
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
		float damage = this.getItem().getItem() instanceof ThrowingWeaponItem weapon ? weapon.thrownDamage() : Ranged.thrownDamage(this.getItem());
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

	/** (0.18.118) It strikes nothing more: a blade left standing in a body (Lodged). */
	void spent() {
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
		this.entityData.set(DATA_ITEM, this.getPickupItemStackOrigin().copyWithCount(1));
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("DealtDamage", this.dealtDamage);
	}
}
