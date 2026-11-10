package com.jaymospowers.ground;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * The wooden pan (Andy's TFC Hardrock picks): used on gravel or sand that water touches, it swirls a panful and now and
 * then turns up a nugget ({@link Panning#wash}). Every {@link #USED_UP} pans or so the bank is washed away.
 */
public class PanItem extends Item {
	/** Ticks between pans, and one pan in how many washes the block away. */
	static final int COOLDOWN = 30, USED_UP = 4;

	public PanItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		if (!Panning.pannable(level.getBlockState(pos)) || !wet(level, pos)) {
			return InteractionResult.PASS;
		}
		Player player = context.getPlayer();
		if (player == null || !(level instanceof ServerLevel server)) {
			return InteractionResult.SUCCESS;
		}
		server.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 0.6F, 1.3F);
		server.sendParticles(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 10, 0.3, 0.1, 0.3, 0.0);
		ItemStack found = Panning.wash(server.getRandom(), 1.0F);
		if (!found.isEmpty()) {
			player.getInventory().placeItemBackInInventory(found, Prediction.SERVER_ONLY);
		}
		if (server.getRandom().nextInt(USED_UP) == 0) {
			server.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
		player.getCooldowns().addCooldown(context.getItemInHand(), COOLDOWN);
		context.getItemInHand().hurtAndBreak(1, player, context.getHand());
		return InteractionResult.SUCCESS;
	}

	/** Water touches it (above or on a side). */
	static boolean wet(Level level, BlockPos pos) {
		for (Direction d : Direction.values()) {
			if (d != Direction.DOWN && level.getFluidState(pos.relative(d)).is(FluidTags.WATER)) {
				return true;
			}
		}
		return false;
	}
}
