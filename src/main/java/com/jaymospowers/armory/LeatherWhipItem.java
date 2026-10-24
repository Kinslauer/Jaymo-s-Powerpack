package com.jaymospowers.armory;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/**
 * (0.15.147) A single-tail leather whip: use cracks the lash out to where you look; hold on to wrap what it cracked,
 * let go to haul it over your head into the ground (or yank it off its feet, if it's too heavy). See {@link Lash}.
 */
public class LeatherWhipItem extends Item {
	/** How long the whip lasts (cracks). */
	static final int DURABILITY = 240;

	public LeatherWhipItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		return Lash.use(level, player, hand, Lash.WHIP);
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return Lash.useDuration();
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return Lash.useAnimation(Lash.WHIP);
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingTime) {
		Lash.release(level, entity);
		return false;
	}
}
