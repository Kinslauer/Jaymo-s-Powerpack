package com.jaymospowers.gear;

import com.jaymospowers.gear.belt.Throwables;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

/**
 * Anything lobbed from the belt or the hand that bursts where it lands (a pot, a powder, a vial, a trap, caltrops, a dart), drawn as
 * its item, and a thrown shield in flight (then it follows a {@link Flight} instead of falling).
 */
public class ThrownGear extends ThrowableItemProjectile {
	/** How it falls (blocks per tick squared), and how long it may fly (ticks) before it is lost. */
	public static final double GRAVITY = 0.04;
	public static final int LIFETIME = 200;

	/** A path that replaces a flight and a fall: the thrown shield's. */
	public interface Flight {
		/** One tick of the flight (server). */
		void tick(ThrownGear gear);
	}

	private @Nullable Flight flight;

	public ThrownGear(EntityType<? extends ThrownGear> type, Level level) {
		super(type, level);
	}

	public ThrownGear(Level level, LivingEntity owner, ItemStack stack) {
		super(GearPicks.THROWN, owner, level, stack.copyWithCount(1));
	}

	public void follow(Flight flight) {
		this.flight = flight;
		this.setNoGravity(true);
	}

	public boolean flying() {
		return this.flight != null;
	}

	@Override
	protected Item getDefaultItem() {
		return Items.SNOWBALL;
	}

	@Override
	protected double getDefaultGravity() {
		return GRAVITY;
	}

	@Override
	public void tick() {
		if (this.level().isClientSide()) {
			super.tick();
			return;
		}
		if (this.flight != null) {
			this.flight.tick(this);
			return;
		}
		if (this.getItem().has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS)) {
			// a shield whose flight was lost (a reload): it goes back to whoever threw it
			Throwables.giveBack(this);
			return;
		}
		super.tick();
		if (this.tickCount > LIFETIME) {
			this.discard();
		}
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (this.flight == null && this.level() instanceof ServerLevel level && !this.isRemoved()) {
			if (Throwables.land(level, this.getItem(), this.getOwner(), hit)) {
				this.discard();
			}
		}
	}
}
