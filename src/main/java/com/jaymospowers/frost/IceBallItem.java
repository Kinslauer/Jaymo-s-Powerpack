package com.jaymospowers.frost;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** An ice ball: thrown like a snowball, but it hurts ({@link ThrownIceBall}); brewed into the Potion of Freezing. */
public class IceBallItem extends Item {
	/** How hard it's thrown (a snowball is 1.5). */
	public static final float POWER = 1.5F;

	public IceBallItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.NEUTRAL, 0.5F,
			0.3F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
		if (level instanceof ServerLevel server) {
			Projectile.spawnProjectileFromRotation(ThrownIceBall::new, server, stack, player, 0.0F, POWER, 1.0F);
		}
		player.getCooldowns().addCooldown(stack, 8);
		player.awardStat(Stats.ITEM_USED.get(this));
		stack.consume(1, player);
		return InteractionResult.SUCCESS;
	}
}
