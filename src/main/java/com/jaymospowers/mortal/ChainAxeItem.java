package com.jaymospowers.mortal;

import com.jaymospowers.armory.Lash;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/**
 * The axe on a chain: a haft in the hand with the axe head hanging off it on a length of chain. Andy: "the chain axe
 * and lasso should operate similar to the [whip] and flail", so it is swung as the flail is ({@link Lash}: hold use
 * and the head whirls round on its chain, let go and it comes down where you look; a charged left click swipes it
 * round in front), the chain and head drawn physically by client/armory/LashRenderer. What is its own, Andy: "a throw
 * embeds the axe, and you have to remove it, either by closing the distance, or ripping it out": the head stays buried
 * in whoever it comes down on ({@code Lash.EMBED}), and the haft does nothing until it is out. Use the axe again from
 * beside them ({@link #FREE_RANGE}) to work it free, or from further off to rip it out by the chain: a second wound
 * ({@link #RIP_SHARE} of the axe's blow) and a longer wait ({@link #RIP_WAIT}). Past the chain's length ({@link #CHAIN})
 * it tears out by itself.
 * Forged at the Tool Station from a Chain Axe Head, a Tool Handle and a length of chain ({@code WeaponParts}).
 */
public final class ChainAxeItem extends Item {
	/** Blocks: within this the buried head is worked free; past the chain's length it tears out by itself. */
	public static final double FREE_RANGE = 3.0, CHAIN = 9.0;
	/** Ripping it out: this share of the axe's blow again, and this many ticks before the next swing. */
	public static final float RIP_SHARE = 0.6F;
	public static final int RIP_WAIT = 40;

	public ChainAxeItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		return Lash.use(level, player, hand, Lash.FLAIL);
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return Lash.useDuration();
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return Lash.useAnimation(Lash.FLAIL);
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingTime) {
		Lash.release(level, entity);
		return false;
	}
}
