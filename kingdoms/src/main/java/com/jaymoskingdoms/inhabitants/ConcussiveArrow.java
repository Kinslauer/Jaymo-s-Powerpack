package com.jaymoskingdoms.inhabitants;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** A concussive arrow in flight: an Impaler's spike for a head. Whatever it hits is concussed ({@link ImpalerGear#concuss}). */
public class ConcussiveArrow extends AbstractArrow {
	public ConcussiveArrow(EntityType<? extends ConcussiveArrow> type, Level level) {
		super(type, level);
	}

	ConcussiveArrow(Level level, LivingEntity shooter, ItemStack pickup, @Nullable ItemStack weapon) {
		super(ImpalerGear.CONCUSSIVE_ARROW_ENTITY, shooter, level, pickup, weapon);
	}

	ConcussiveArrow(Level level, double x, double y, double z, ItemStack pickup, @Nullable ItemStack weapon) {
		super(ImpalerGear.CONCUSSIVE_ARROW_ENTITY, x, y, z, level, pickup, weapon);
	}

	@Override
	protected void doPostHurtEffects(LivingEntity hit) {
		super.doPostHurtEffects(hit);
		ImpalerGear.concuss(hit, ImpalerGear.ARROW_CONCUSSION);
	}

	@Override
	protected ItemStack getDefaultPickupItem() {
		return new ItemStack(ImpalerGear.CONCUSSIVE_ARROW);
	}
}
