package com.jaymospowers.alchemy.bomb;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A thrown bomb or oil (Hunter's Road, patch 7, "D5" and "T-C3"), after Kingdoms' {@code GasBottle}: a flask in flight that
 * breaks where it lands. What it is and how good it is are in the stack it was thrown as (a bomb item, or an oil flask with its
 * grade): {@link Bombs#land} does the rest. The stack is always a real, spent thing: a bomb item, a splash flask, or one charge
 * of a sash.
 */
public final class Bomb extends ThrowableItemProjectile {
	/** Ticks it may fly before it breaks in the air. */
	public static final int LIFETIME = 100;

	public Bomb(EntityType<? extends Bomb> type, Level level) {
		super(type, level);
	}

	public Bomb(ServerLevel level, LivingEntity owner, ItemStack stack) {
		super(Bombs.TYPE, owner, level, stack);
	}

	@Override
	protected Item getDefaultItem() {
		return Items.GLASS_BOTTLE;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level() instanceof ServerLevel level && !this.isRemoved() && this.tickCount > LIFETIME) {
			Bombs.land(level, this.position(), this.getItem(), this.getOwner(), BlockPos.containing(this.position()));
			this.discard();
		}
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (this.level() instanceof ServerLevel level && !this.isRemoved()) {
			Vec3 at = hit.getLocation();
			BlockPos ground = hit instanceof BlockHitResult block ? block.getBlockPos().relative(block.getDirection()) : BlockPos.containing(at);
			Bombs.land(level, at, this.getItem(), this.getOwner(), ground);
			this.discard();
		}
	}
}
