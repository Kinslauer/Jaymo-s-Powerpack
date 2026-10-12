package com.jaymospowers.armory;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import org.jspecify.annotations.Nullable;

/** Antiquity's recurve bow: the same draw as a bow, but its arrows fly faster and truer (so they hit harder). */
public class RecurveBowItem extends BowItem {
	/** Arrow speed over a plain bow's, and how much of a bow's spread is left. */
	static final float POWER = 1.3F, SPREAD = 0.7F;
	static final int DURABILITY = 465;

	public RecurveBowItem(Properties properties) {
		super(properties);
	}

	@Override
	protected void shootProjectile(LivingEntity shooter, Projectile projectile, int index, float power, float uncertainty, float angle,
		@Nullable LivingEntity target) {
		super.shootProjectile(shooter, projectile, index, power * POWER, uncertainty * SPREAD, angle, target);
	}
}
