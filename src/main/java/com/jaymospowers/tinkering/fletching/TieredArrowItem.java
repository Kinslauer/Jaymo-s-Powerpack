package com.jaymospowers.tinkering.fletching;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** An arrow with a metal head, fired by any bow, crossbow or dispenser: it hits harder than the flint-headed vanilla arrow (2). */
public class TieredArrowItem extends ArrowItem {
	/** The head's damage (a vanilla arrow's base damage is 2). Tune the tiers here. */
	public enum Tier {
		IRON(2.6), STEEL(3.2);

		public final double damage;

		Tier(double damage) {
			this.damage = damage;
		}
	}

	public final Tier tier;

	public TieredArrowItem(Item.Properties properties, Tier tier) {
		super(properties);
		this.tier = tier;
	}

	@Override
	public AbstractArrow createArrow(Level level, ItemStack ammo, LivingEntity shooter, @Nullable ItemStack weapon) {
		AbstractArrow arrow = super.createArrow(level, ammo, shooter, weapon);
		arrow.setBaseDamage(tier.damage);
		return arrow;
	}

	@Override
	public Projectile asProjectile(Level level, Position position, ItemStack stack, Direction direction) {
		Projectile projectile = super.asProjectile(level, position, stack, direction);
		if (projectile instanceof AbstractArrow arrow) {
			arrow.setBaseDamage(tier.damage);
		}
		return projectile;
	}
}
