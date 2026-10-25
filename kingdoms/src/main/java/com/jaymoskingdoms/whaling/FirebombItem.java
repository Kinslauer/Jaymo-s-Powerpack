package com.jaymoskingdoms.whaling;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** (0.7.206) A firebomb (or powder firebomb) in hand: use to light its rag and throw it ({@link Firebomb}). */
public final class FirebombItem extends Item {
	/** How hard it's thrown, and how wild. */
	static final float POWER = 1.1F, SPREAD = 1.0F;
	/** Ticks before the next. */
	static final int COOLDOWN = 20;

	public FirebombItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FLINTANDSTEEL_USE, SoundSource.PLAYERS, 0.8F, 1.0F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SPLASH_POTION_THROW, SoundSource.PLAYERS, 0.6F, 0.6F);
		if (level instanceof ServerLevel server) {
			Projectile.spawnProjectileFromRotation(Firebomb::new, server, held, player, -10.0F, POWER, SPREAD);
		}
		player.getCooldowns().addCooldown(held, COOLDOWN);
		held.consume(1, player);
		return InteractionResult.SUCCESS;
	}
}
