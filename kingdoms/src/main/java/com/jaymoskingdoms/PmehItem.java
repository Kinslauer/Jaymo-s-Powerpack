package com.jaymoskingdoms;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * (0.7.210) A pinch of Pmeh ({@link Underworld}): taken at once (use), it puts a spring in your step and quick hands for a
 * while, and the room swims as it hits, and you're starving after.
 */
public class PmehItem extends Item {
	/** How long the lift lasts, the swim as it hits, and the hunger after (ticks); how long before another (ticks). */
	static final int LIFT_TICKS = 20 * 90, SWIM_TICKS = 20 * 6, HUNGER_TICKS = 20 * 30, COOLDOWN = 20 * 3;

	public PmehItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		player.getCooldowns().addCooldown(stack, COOLDOWN);
		if (level instanceof ServerLevel server) {
			player.addEffect(new MobEffectInstance(MobEffects.SPEED, LIFT_TICKS, 0));
			player.addEffect(new MobEffectInstance(MobEffects.HASTE, LIFT_TICKS, 0));
			player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, SWIM_TICKS, 0));
			player.addEffect(new MobEffectInstance(MobEffects.HUNGER, HUNGER_TICKS, 0));
			server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 0.8F, 1.4F);
			stack.consume(1, player);
		}
		return InteractionResult.SUCCESS;
	}
}
