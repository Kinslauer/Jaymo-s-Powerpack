package com.jaymospowers.mortal;

import com.jaymospowers.armory.Lash;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The axe on a chain: a haft in the hand with the axe head hanging off it on a length of chain. Andy: "the chain axe
 * and lasso should operate similar to the [whip] and flail", so it is swung as the flail is ({@link Lash}: hold use
 * and the head whirls round on its chain, let go and it comes down where you look; a charged left click swipes it
 * round in front), the chain and head drawn physically by client/armory/LashRenderer. What is its own: whoever the
 * head comes down on is hooked by the beard and tugged a step toward you ({@link #TUG}); sneaking, the haul is the
 * point and they are dragged all the way in ({@link #HAUL}).
 * Forged at the Tool Station from a Chain Axe Head, a Tool Handle and a length of chain ({@code WeaponParts}).
 */
public final class ChainAxeItem extends Item {
	static final double TUG = 0.35, HAUL = 1.1;

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

	/** The head has bitten {@code target}: it is pulled toward {@code player}, all the way if they are sneaking. */
	public static void haul(ServerPlayer player, LivingEntity target) {
		if (target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1.0) {
			return;
		}
		Vec3 back = player.position().subtract(target.position());
		double far = Math.max(0.5, back.length());
		double pull = player.isShiftKeyDown() ? Math.min(HAUL, 0.25 + far * 0.12) : TUG;
		target.setDeltaMovement(back.scale(pull / far).add(0.0, 0.22, 0.0));
		target.needsSync = true;
		target.syncVelocity = true;
	}
}
