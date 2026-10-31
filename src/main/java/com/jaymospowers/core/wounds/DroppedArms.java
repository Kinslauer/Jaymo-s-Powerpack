package com.jaymospowers.core.wounds;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.phys.Vec3;

/**
 * (0.18.117, Motion patch 8) Andy: "When players or enemy mobs go down or die outright, they drop their weapons to the
 * ground, in very few cases will they keep it in their hand as they crawl, and some may die with it still in their
 * clutches." Going down, a player or mob lets go of the weapons in its hands (thrown a little way) unless it's one of the
 * few that hang on ({@link #KEEP_DOWN}); one that hung on dies still holding them. A mob killed outright (never down)
 * drops them too, but for the {@link #KEEP_DYING} that die clutching theirs. Players dying outright drop everything as
 * the game has them do anyway.
 */
public final class DroppedArms {
	/** The chance someone going down keeps hold of their weapons, and a mob dying outright dies clutching them. */
	static final float KEEP_DOWN = 0.1F, KEEP_DYING = 0.2F;
	/** How hard a dropped weapon is flung, and how long before it can be picked up (ticks). */
	static final double FLING = 0.18;
	static final int PICKUP_DELAY = 30;

	private DroppedArms() {
	}

	/** {@code entity} has just gone down. */
	static void fell(ServerLevel level, LivingEntity entity) {
		if (level.getRandom().nextFloat() < KEEP_DOWN) {
			return;
		}
		drop(level, entity);
	}

	/** {@code entity} dies (called before its loot and corpse). */
	public static void died(LivingEntity entity) {
		if (entity instanceof Player || !(entity.level() instanceof ServerLevel level) || Downed.isDowned(entity)) {
			// (a player's things all fall anyway; one that went down already let go, or is clutching them still)
			return;
		}
		if (level.getRandom().nextFloat() < KEEP_DYING) {
			return;
		}
		drop(level, entity);
	}

	private static void drop(ServerLevel level, LivingEntity entity) {
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack held = entity.getItemInHand(hand);
			if (!weapon(held)) {
				continue;
			}
			entity.setItemInHand(hand, ItemStack.EMPTY);
			fling(level, entity, held);
		}
	}

	private static void fling(ServerLevel level, LivingEntity entity, ItemStack stack) {
		RandomSource random = level.getRandom();
		Vec3 look = entity.getLookAngle().multiply(1.0, 0.0, 1.0);
		look = look.lengthSqr() < 1.0E-4 ? Vec3.ZERO : look.normalize();
		ItemEntity item = new ItemEntity(level, entity.getX(), entity.getY(0.6), entity.getZ(), stack,
			look.x * FLING + (random.nextDouble() - 0.5) * 0.1, 0.15, look.z * FLING + (random.nextDouble() - 0.5) * 0.1);
		item.setPickUpDelay(PICKUP_DELAY);
		level.addFreshEntity(item);
	}

	/** Something held to fight with: anything that adds to attack damage, a bow or crossbow, a shield, a blade or a mace. */
	static boolean weapon(ItemStack stack) {
		if (stack.isEmpty() || stack.is(Items.TOTEM_OF_UNDYING)) {
			return false;
		}
		if (stack.getItem() instanceof ProjectileWeaponItem || stack.is(Items.SHIELD) || stack.is(Items.TRIDENT) || stack.is(Items.MACE)
			|| stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES)) {
			return true;
		}
		ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
			if (entry.attribute().is(Attributes.ATTACK_DAMAGE) && entry.modifier().amount() > 0.0) {
				return true;
			}
		}
		return false;
	}
}
