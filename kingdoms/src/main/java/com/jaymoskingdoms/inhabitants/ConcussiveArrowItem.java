package com.jaymoskingdoms.inhabitants;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** The concussive arrow: shot from any bow or crossbow (it is in {@code #minecraft:arrows}) or a dispenser. */
public class ConcussiveArrowItem extends ArrowItem {
	public ConcussiveArrowItem(Properties properties) {
		super(properties);
	}

	@Override
	public AbstractArrow createArrow(Level level, ItemStack arrow, LivingEntity shooter, @Nullable ItemStack weapon) {
		return new ConcussiveArrow(level, shooter, arrow.copyWithCount(1), weapon);
	}

	@Override
	public Projectile asProjectile(Level level, Position pos, ItemStack stack, Direction direction) {
		ConcussiveArrow arrow = new ConcussiveArrow(level, pos.x(), pos.y(), pos.z(), stack.copyWithCount(1), null);
		arrow.pickup = AbstractArrow.Pickup.ALLOWED;
		return arrow;
	}
}
