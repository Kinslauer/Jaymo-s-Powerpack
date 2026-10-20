package com.jaymospowers.aether;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * The ambrosium shard. Used on aether grass it enchants it (the enchanted grass doesn't spread, and drops aether dirt);
 * used on anything else it heals {@link #HEAL} hit points and goes on a short cooldown (not at full health).
 */
public class AmbrosiumShardItem extends Item {
	// ---- tuning ----
	/** Hit points healed (2 per heart): one heart. */
	public static final float HEAL = 2.0F;
	/** Cooldown after healing (ticks): one second. */
	public static final int COOLDOWN = 20;

	public AmbrosiumShardItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		if (!level.getBlockState(pos).is(AetherBlocks.AETHER_GRASS_BLOCK)) {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel server) {
			server.setBlockAndUpdate(pos, AetherBlocks.ENCHANTED_AETHER_GRASS_BLOCK.defaultBlockState());
			server.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 8, 0.3, 0.2, 0.3, 0.02);
			server.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.8F, 1.6F);
			if (context.getPlayer() != null && !context.getPlayer().isCreative()) {
				context.getItemInHand().shrink(1);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player.getHealth() >= player.getMaxHealth()) {
			return InteractionResult.PASS;
		}
		ItemStack stack = player.getItemInHand(hand);
		if (level instanceof ServerLevel server) {
			player.heal(HEAL);
			player.getCooldowns().addCooldown(stack, COOLDOWN);
			server.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.8F);
			server.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0, player.getZ(), 5, 0.3, 0.4, 0.3, 0.02);
			if (!player.isCreative()) {
				stack.shrink(1);
			}
		}
		return InteractionResult.SUCCESS;
	}
}
