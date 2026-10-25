package com.jaymoskingdoms.whaling;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/**
 * (0.7.206) A roped harpoon: hold use to draw back like a trident, let go to throw ({@link Harpoon}). Each throw spends
 * one; a harpoon that misses can be picked up where it falls, one in a whale comes back when the carcass is flensed away.
 */
public final class HarpoonItem extends Item {
	/** Held this long (ticks) before it can be thrown. */
	static final int DRAW = 10;

	public HarpoonItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.TRIDENT;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return 72000;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingTime) {
		if (!(entity instanceof Player player) || this.getUseDuration(stack, entity) - remainingTime < DRAW) {
			return false;
		}
		player.awardStat(Stats.ITEM_USED.get(this));
		if (level instanceof ServerLevel server) {
			Harpoon.thrown(server, player);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0F, 0.7F);
			stack.consume(1, player);
		}
		return true;
	}
}
