package com.jaymoskitchen.delight;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.level.Level;

/** (Farmer's Delight's rotten tomato, MIT; our own code.) Thrown like a snowball ({@link RottenTomatoEntity}); dispensers throw it too. */
public class RottenTomatoItem extends Item implements ProjectileItem {
	/** Throw speed and spread (as a snowball's), and the throw sound's volume. */
	private static final float THROW_SPEED = 1.5F, THROW_SPREAD = 1.0F, THROW_VOLUME = 0.5F;

	public RottenTomatoItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.NEUTRAL, THROW_VOLUME,
			0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
		if (!level.isClientSide()) {
			RottenTomatoEntity projectile = new RottenTomatoEntity(level, player);
			projectile.setItem(held);
			projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, THROW_SPEED, THROW_SPREAD);
			level.addFreshEntity(projectile);
		}
		player.awardStat(Stats.ITEM_USED.get(this));
		if (!player.getAbilities().instabuild) {
			held.shrink(1);
		}
		return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
	}

	@Override
	public Projectile asProjectile(Level level, Position position, ItemStack stack, Direction direction) {
		RottenTomatoEntity tomato = new RottenTomatoEntity(level, position.x(), position.y(), position.z());
		tomato.setItem(stack);
		return tomato;
	}
}
