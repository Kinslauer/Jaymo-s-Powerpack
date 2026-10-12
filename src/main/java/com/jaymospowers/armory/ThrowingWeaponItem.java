package com.jaymospowers.armory;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/**
 * A weapon you can throw. Held ones (daggers, hatchets, spears, the throwing stick, the harpoon) are drawn back like a
 * trident and thrown on release, and lie where they land to be picked up. Kunai and throwing stars fly at a flick of
 * the wrist, one from the stack.
 */
public class ThrowingWeaponItem extends Item {
	/** Held back at least this long (ticks) to throw. */
	static final int HOLD_TICKS = 8;
	/** Throw speeds (blocks/tick): a held weapon, a flicked one. */
	static final float HELD_SPEED = 2.2F, FLICK_SPEED = 2.0F;
	/** A flicked one can't be thrown again for this long (ticks). */
	static final int FLICK_COOLDOWN = 5;

	private final float thrownDamage;
	private final boolean spins;
	private final boolean held;

	public ThrowingWeaponItem(Properties properties, float thrownDamage, boolean spins, boolean held) {
		super(properties);
		this.thrownDamage = thrownDamage;
		this.spins = spins;
		this.held = held;
	}

	/** What it does when it hits, thrown. */
	public float thrownDamage() {
		return this.thrownDamage;
	}

	/** It tumbles end over end in flight (hatchets, sticks, stars) rather than flying point first. */
	public boolean spins() {
		return this.spins;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!this.held) {
			if (level instanceof ServerLevel serverLevel) {
				ItemStack thrown = stack.consumeAndReturn(1, player);
				throwFrom(serverLevel, player, thrown, FLICK_SPEED);
			}
			player.getCooldowns().addCooldown(stack, FLICK_COOLDOWN);
			player.awardStat(Stats.ITEM_USED.get(this));
			return InteractionResult.SUCCESS;
		}
		if (stack.nextDamageWillBreak()) {
			return InteractionResult.FAIL;
		}
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return this.held ? ItemUseAnimation.TRIDENT : ItemUseAnimation.NONE;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return this.held ? 72000 : 0;
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int remainingTime) {
		if (!(user instanceof Player player) || this.getUseDuration(stack, user) - remainingTime < HOLD_TICKS || stack.nextDamageWillBreak()) {
			return false;
		}
		if (level instanceof ServerLevel serverLevel) {
			stack.hurtWithoutBreaking(1, player);
			throwFrom(serverLevel, player, stack.consumeAndReturn(1, player), HELD_SPEED);
		}
		player.awardStat(Stats.ITEM_USED.get(this));
		return true;
	}

	private static void throwFrom(ServerLevel level, Player player, ItemStack thrown, float speed) {
		ThrownWeapon weapon = Projectile.spawnProjectileFromRotation(
			(l, owner, stack) -> new ThrownWeapon(l, owner, stack), level, thrown, player, 0.0F, speed, 1.0F);
		if (player.hasInfiniteMaterials()) {
			weapon.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
		}
		level.playSound(null, weapon, SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 0.8F, 1.3F);
	}
}
