package com.jaymospowers.power.wind;

import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Powers;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The Gale Heart, torn from a breeze. Used by a wind that has mastered everything (and hasn't ascended), it is taken in
 * and the power ascends. Anyone else gets a gust in the face and nothing more; the heart stays.
 */
public final class WindGaleHeart extends Item {
	/** After a use that nothing answered, the heart is quiet for this long. */
	static final int UNANSWERED_COOLDOWN = 40;

	public WindGaleHeart(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		if (Powers.has(serverPlayer, WindPower.INSTANCE) && Fate.canAscend(serverPlayer)) {
			stack.consume(1, serverPlayer);
			serverLevel.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, player.getX(), player.getY() + 0.5, player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
			serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_WIND_CHARGE_BURST, SoundSource.PLAYERS, 1.5F, 0.5F);
			Fate.ascend(serverPlayer);
			return InteractionResult.SUCCESS;
		}
		// nothing answers: a puff of wind, a hollow sigh
		serverLevel.sendParticles(ParticleTypes.GUST, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		serverLevel.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1.0, player.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
		serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_IDLE_GROUND, SoundSource.PLAYERS, 0.8F, 0.5F);
		serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 0.4F, 0.6F);
		player.getCooldowns().addCooldown(stack, UNANSWERED_COOLDOWN);
		return InteractionResult.SUCCESS;
	}
}
