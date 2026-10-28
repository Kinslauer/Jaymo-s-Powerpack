package com.jaymospowers.tinkering.fletching;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TippedArrowItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** An arrow dipped in a lingering potion: on landing it leaves a small cloud of it ({@link LingeringArrow}). */
public class LingeringArrowItem extends TippedArrowItem {
	public LingeringArrowItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public ItemStack getDefaultInstance() {
		ItemStack stack = new ItemStack(this);
		stack.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.POISON));
		return stack;
	}

	@Override
	public Component getName(ItemStack stack) {
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		Component potion = contents == null ? Component.translatable("item.minecraft.arrow") : contents.getName("item.minecraft.tipped_arrow.effect.");
		return Component.translatable("item.jaymospowers.lingering_arrow.named", potion);
	}

	@Override
	public AbstractArrow createArrow(Level level, ItemStack ammo, LivingEntity shooter, @Nullable ItemStack weapon) {
		return new LingeringArrow(level, shooter, ammo.copyWithCount(1), weapon);
	}

	@Override
	public Projectile asProjectile(Level level, Position position, ItemStack stack, Direction direction) {
		LingeringArrow arrow = new LingeringArrow(level, position.x(), position.y(), position.z(), stack.copyWithCount(1), null);
		arrow.pickup = AbstractArrow.Pickup.ALLOWED;
		return arrow;
	}
}
