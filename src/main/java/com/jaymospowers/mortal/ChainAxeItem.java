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
 * and lasso should operate similar to the [whip] and flail", so it is whirled as the flail is ({@link Lash}: hold use
 * and the head goes round on its chain; a charged left click swipes it round in front), the chain and head drawn
 * physically by client/armory/LashRenderer. What is its own, Andy: "a throw embeds the axe, and you have to remove it,
 * either by closing the distance, or ripping it out ... after letting go of the right click hold the axe flies toward
 * the cursor, up to 9 blocks":
 * <ul>
 * <li>let go of the whirl and the head flies at what you look at ({@code Lash.THROW}, as far as {@link #CHAIN}, at
 * {@link #THROW_SPEED}), harder the faster it was whirling;</li>
 * <li>it stays buried in whoever it hits ({@code Lash.EMBED}, and {@code Mortal.BURIED_IN} on the axe), and the haft
 * does nothing until it is out. Use the axe again from beside them ({@link #FREE_RANGE}) to work it free, or from
 * further off to rip it out by the chain: a second wound ({@link #RIP_SHARE} of the axe's blow) and a longer wait
 * ({@link #RIP_WAIT}). Past the chain's length it tears out by itself;</li>
 * <li>Andy: "swapping with the head still embedded should drop the haft on the ground, still attached and rendered in
 * world. you would have to pick it up and remove it to continue": put the axe away and the haft falls where you stand,
 * the chain still running to the head. It lies there until someone takes it up (in hand it is buried as before), and
 * is dragged along ({@link #DRAG}) when what the head is in walks off past the chain's length.</li>
 * </ul>
 * Forged at the Tool Station from a Chain Axe Head, a Tool Handle and a length of chain ({@code WeaponParts}).
 */
public final class ChainAxeItem extends Item {
	/** Blocks: within this the buried head is worked free; past the chain's length it tears out by itself. */
	public static final double FREE_RANGE = 3.0, CHAIN = 9.0;
	/** The throw: blocks a tick, and how near where it was thrown (blocks) its target must still be to be hit. */
	public static final double THROW_SPEED = 1.5, THROW_HIT = 1.5;
	/** A dropped haft: ticks before it can be picked up; how hard the chain drags it per block past its length, and the most. */
	public static final int DROP_PICKUP = 40;
	public static final double DRAG = 0.3, DRAG_MOST = 0.6;
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
