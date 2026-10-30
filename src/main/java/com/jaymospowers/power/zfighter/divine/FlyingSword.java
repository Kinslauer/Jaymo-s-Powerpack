package com.jaymospowers.power.zfighter.divine;

import com.jaymospowers.core.LockOn;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.48, Weapon Forms patch 6) A Divine-born's Flying Sword: the blade in their hand leaves it and fights on its own for
 * {@link #FIGHT_TICKS}: it circles the owner's lock-on target (else what they last struck, else the nearest foe in
 * {@link #RANGE}), darting in to strike every {@link #STRIKE_EVERY} ticks, then flies back and settles in the hand. It is never
 * lost: back into its slot (or the inventory, or dropped at the owner's feet) when it is caught, when the owner logs out
 * ({@link #recall}), dies or leaves the level, when it is killed, and, if it was saved with the world, when it loads and finds
 * no owner. Server-driven; clients draw it as the item.
 */
public class FlyingSword extends Projectile implements ItemSupplier {
	private static final EntityDataAccessor<ItemStack> DATA_ITEM = SynchedEntityData.defineId(FlyingSword.class, EntityDataSerializers.ITEM_STACK);

	/** How long it fights (ticks) before it flies home, and the longest it may live in all (safety). */
	public static final int FIGHT_TICKS = 100, MAX_LIFE = 260;
	/** How far from the owner it will look for something to fight (blocks). */
	public static final double RANGE = 16.0;
	/** Flight speeds (blocks a tick): out and on the attack, and homeward. */
	public static final double SPEED = 0.9, RETURN_SPEED = 1.4;
	/** How near the target it must be to strike, the wait between strikes (ticks), and the radius it circles at. */
	public static final double STRIKE_DISTANCE = 1.9, ORBIT = 1.7;
	public static final int STRIKE_EVERY = 8;
	/** Close enough to the owner to be caught. */
	public static final double CATCH_DISTANCE = 1.3;
	/** Damage as a share of the owner's attack damage with the blade in hand, and the least it deals. */
	public static final float DAMAGE_SHARE = 0.9F, MIN_DAMAGE = 3.0F;

	/** Every sword out now, by owner (server only), so a logout can call them home. */
	private static final Map<UUID, FlyingSword> OUT = new HashMap<>();

	private ItemStack blade = ItemStack.EMPTY;
	private int slot = -1;
	private float damage = MIN_DAMAGE;
	private int life;
	private int lastStrike;
	private boolean returning;
	private boolean returned;
	/** The owner, looked up through the player list so a world change or teleport never loses them. */
	private @Nullable UUID ownerId;

	public FlyingSword(EntityType<? extends FlyingSword> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	/** Sets {@code blade} (already taken out of the owner's hand by the caller) flying. */
	public static FlyingSword launch(ServerLevel level, ServerPlayer owner, ItemStack blade, int slot, float damage) {
		FlyingSword sword = new FlyingSword(DivineEntities.FLYING_SWORD, level);
		sword.setOwner(owner);
		sword.ownerId = owner.getUUID();
		sword.blade = blade.copy();
		sword.entityData.set(DATA_ITEM, blade.copyWithCount(1));
		sword.slot = slot;
		sword.damage = Math.max(MIN_DAMAGE, damage * DAMAGE_SHARE);
		Vec3 start = owner.getEyePosition().add(owner.getLookAngle().scale(0.7)).add(0.0, -0.35, 0.0);
		sword.setPos(start);
		sword.face(owner.getLookAngle());
		level.addFreshEntity(sword);
		OUT.put(owner.getUUID(), sword);
		return sword;
	}

	/** Whether {@code owner} has a sword out now. */
	public static boolean out(ServerPlayer owner) {
		FlyingSword sword = OUT.get(owner.getUUID());
		return sword != null && !sword.isRemoved() && !sword.returned;
	}

	/** Calls {@code owner}'s sword home at once (logout, power lost): it is handed back and the entity goes. */
	public static void recall(ServerPlayer owner) {
		FlyingSword sword = OUT.remove(owner.getUUID());
		if (sword != null) {
			sword.giveBack(owner);
			sword.discard();
		}
	}

	/** Server stop: every sword out goes home first (its owner is still online), then the list is emptied. */
	public static void clear() {
		for (FlyingSword sword : new java.util.ArrayList<>(OUT.values())) {
			sword.giveBack(sword.ownerOnline());
			sword.discard();
		}
		OUT.clear();
	}

	private @Nullable ServerPlayer ownerOnline() {
		return this.ownerId != null && this.level() instanceof ServerLevel level ? level.getServer().getPlayerList().getPlayer(this.ownerId) : null;
	}

	/** Never written to the world: a sword out lives only as long as its flight, so a saved copy can't hand the blade back twice. */
	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	private void face(Vec3 dir) {
		this.setYRot((float)(Mth.atan2(dir.x, dir.z) * Mth.RAD_TO_DEG));
		this.setXRot((float)(Mth.atan2(dir.y, dir.horizontalDistance()) * Mth.RAD_TO_DEG));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_ITEM, ItemStack.EMPTY);
	}

	@Override
	public ItemStack getItem() {
		ItemStack stack = this.entityData.get(DATA_ITEM);
		return stack.isEmpty() ? new ItemStack(Items.IRON_SWORD) : stack;
	}

	@Override
	public void tick() {
		this.yRotO = this.getYRot();
		this.xRotO = this.getXRot();
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		if (this.returned) {
			this.discard();
			return;
		}
		if (this.ownerOnline() instanceof ServerPlayer owner) {
			OUT.putIfAbsent(owner.getUUID(), this);
			if (!owner.isAlive() || owner.isSpectator() || owner.level() != level || ++this.life > MAX_LIFE) {
				// dead, gone to another world, or somehow lost: it goes straight back
				this.giveBack(owner);
				this.discard();
				return;
			}
			this.fly(level, owner);
		} else {
			// no owner (logged out while it was saved): it drops where it is
			this.giveBack(null);
			this.discard();
		}
	}

	private void fly(ServerLevel level, ServerPlayer owner) {
		Vec3 pos = this.position();
		if (!this.returning && this.life > FIGHT_TICKS) {
			this.returning = true;
		}
		LivingEntity target = this.returning ? null : this.pick(level, owner);
		Vec3 want;
		double cap;
		if (this.returning) {
			want = owner.position().add(0.0, owner.getBbHeight() * 0.55, 0.0);
			cap = RETURN_SPEED;
			if (pos.distanceTo(want) <= CATCH_DISTANCE) {
				level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6F, 1.5F);
				level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 6, 0.15, 0.15, 0.15, 0.02);
				this.giveBack(owner);
				this.discard();
				return;
			}
		} else if (target != null) {
			Vec3 centre = target.getBoundingBox().getCenter();
			double angle = this.life * 0.55;
			want = centre.add(Math.cos(angle) * ORBIT, 0.3 + Math.sin(angle * 0.7) * 0.4, Math.sin(angle) * ORBIT);
			cap = SPEED;
			if (this.life - this.lastStrike >= STRIKE_EVERY && pos.distanceTo(centre) <= STRIKE_DISTANCE + target.getBbWidth() * 0.5) {
				this.strike(level, owner, target);
				// the dart: straight through the foe's centre
				want = centre.add(centre.subtract(pos).normalize().scale(0.8));
				cap = SPEED * 1.6;
			}
		} else {
			// nothing to fight: it hangs about the owner's shoulder
			Vec3 right = owner.getLookAngle().cross(new Vec3(0.0, 1.0, 0.0));
			right = right.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
			want = owner.position().add(0.0, owner.getBbHeight() * 0.95 + Math.sin(this.life * 0.2) * 0.15, 0.0).add(right.scale(0.9));
			cap = SPEED;
		}
		Vec3 to = want.subtract(pos);
		double dist = to.length();
		Vec3 move = dist < 1.0E-4 ? Vec3.ZERO : to.scale(Math.min(cap, dist) / dist);
		if (move.lengthSqr() > 1.0E-4) {
			this.face(target != null && !this.returning ? target.getBoundingBox().getCenter().subtract(pos) : move);
		}
		this.setDeltaMovement(move);
		this.setPos(pos.add(move));
		if (this.life % 2 == 0) {
			level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY(), this.getZ(), 1, 0.05, 0.05, 0.05, 0.0);
		}
	}

	/** The owner's lock-on, else what they last struck, else the nearest foe in range; null if none. */
	private @Nullable LivingEntity pick(ServerLevel level, ServerPlayer owner) {
		LivingEntity locked = LockOn.target(owner);
		if (locked != null && locked.isAlive() && locked.distanceTo(owner) <= RANGE * 1.5) {
			return locked;
		}
		LivingEntity last = owner.getLastHurtMob();
		if (last != null && last.isAlive() && last != owner && last.distanceTo(owner) <= RANGE && owner.tickCount - owner.getLastHurtMobTimestamp() < 100) {
			return last;
		}
		AABB box = owner.getBoundingBox().inflate(RANGE);
		List<LivingEntity> foes = level.getEntitiesOfClass(LivingEntity.class, box,
			e -> e != owner && e.isAlive() && !e.isSpectator() && e instanceof Enemy && !e.isAlliedTo(owner));
		LivingEntity best = null;
		double bestDist = Double.MAX_VALUE;
		for (LivingEntity foe : foes) {
			double d = foe.distanceToSqr(this);
			if (d < bestDist) {
				bestDist = d;
				best = foe;
			}
		}
		return best;
	}

	private void strike(ServerLevel level, ServerPlayer owner, LivingEntity target) {
		this.lastStrike = this.life;
		DamageSource source = owner.damageSources().playerAttack(owner);
		target.hurtServer(level, source, this.damage);
		Vec3 push = target.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
		if (push.lengthSqr() > 1.0E-4) {
			push = push.normalize().scale(0.25);
			target.push(push.x, 0.08, push.z);
		}
		Vec3 at = target.getBoundingBox().getCenter();
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.2, 0.25, 0.2, 0.2);
		level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 4, 0.2, 0.25, 0.2, 0.05);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 1.5F);
	}

	/** Hands the blade back once: into its slot, else the inventory, else dropped at {@code owner}'s feet (or here, without one). */
	private void giveBack(@Nullable ServerPlayer owner) {
		if (this.returned) {
			return;
		}
		this.returned = true;
		OUT.values().remove(this);
		ItemStack stack = this.blade.isEmpty() ? this.entityData.get(DATA_ITEM).copy() : this.blade.copy();
		if (stack.isEmpty() || !(this.level() instanceof ServerLevel level)) {
			return;
		}
		if (owner != null && owner.isAlive()) {
			if (this.slot >= 0 && this.slot < owner.getInventory().getContainerSize() && owner.getInventory().getItem(this.slot).isEmpty()) {
				owner.getInventory().setItem(this.slot, stack);
			} else {
				owner.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
			}
			return;
		}
		Vec3 at = owner != null ? owner.position() : this.position();
		ItemEntity item = new ItemEntity(level, at.x, at.y + 0.3, at.z, stack);
		item.setDefaultPickUpDelay();
		level.addFreshEntity(item);
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		// killed, discarded or unloaded (the owner went far off) without being handed back: never lose the blade
		if (!this.returned && this.level() instanceof ServerLevel) {
			this.giveBack(this.ownerOnline());
		}
		super.remove(reason);
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
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 64.0 * 64.0;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.store("Blade", ItemStack.OPTIONAL_CODEC, this.blade);
		output.putInt("Slot", this.slot);
		output.putFloat("Damage", this.damage);
		output.putInt("Life", this.life);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.blade = input.read("Blade", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
		this.entityData.set(DATA_ITEM, this.blade.copyWithCount(1));
		this.slot = input.getIntOr("Slot", -1);
		this.damage = input.getFloatOr("Damage", MIN_DAMAGE);
		this.life = input.getIntOr("Life", 0);
	}
}
