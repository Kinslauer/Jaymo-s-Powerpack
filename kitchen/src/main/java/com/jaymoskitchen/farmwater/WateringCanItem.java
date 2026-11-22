package com.jaymoskitchen.farmwater;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * A watering can (Let's Do's idea, our own): use it on a crop (or its farmland) on a dry day and the crop and the ones
 * touching it take a small step of growth ({@link #ROLLS} chances at the crop's own growing, so season and soil still
 * count) and the farmland drinks. Never needed: rain and ditches do it all the same. It holds {@link #USES} waterings;
 * use it on water, or a water barrel, to fill it again.
 */
public class WateringCanItem extends Item {
	/** Waterings in a full can. */
	public static final int USES = 16;
	/** Chances each watered crop gets to grow, as a random tick would give it. */
	static final int ROLLS = 6;

	public WateringCanItem(Properties properties) {
		super(properties);
	}

	private static boolean empty(ItemStack stack) {
		return stack.getDamageValue() >= USES;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
		if (hit.getType() == HitResult.Type.BLOCK && stack.getDamageValue() > 0 && level.mayInteract(player, hit.getBlockPos())
			&& level.getFluidState(hit.getBlockPos()).is(FluidTags.WATER)) {
			level.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.BUCKET_FILL, SoundSource.NEUTRAL, 1.0F, 1.1F);
			stack.setDamageValue(0);
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof CropBlock)) {
			// farmland: the crop on it
			pos = pos.above();
			state = level.getBlockState(pos);
			if (!(state.getBlock() instanceof CropBlock) || !level.getBlockState(pos.below()).hasProperty(FarmlandBlock.MOISTURE)) {
				return InteractionResult.PASS;
			}
		}
		ItemStack stack = context.getItemInHand();
		if (empty(stack) || level.isRainingAt(pos)) {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel server) {
			for (BlockPos p : BlockPos.betweenClosed(pos.offset(-1, 0, -1), pos.offset(1, 0, 1))) {
				BlockState crop = server.getBlockState(p);
				if (!(crop.getBlock() instanceof CropBlock block)) {
					continue;
				}
				BlockState soil = server.getBlockState(p.below());
				if (soil.hasProperty(FarmlandBlock.MOISTURE)) {
					server.setBlock(p.below(), soil.setValue(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE), 2);
				}
				for (int i = 0; i < ROLLS && !block.isMaxAge(server.getBlockState(p)); i++) {
					server.getBlockState(p).randomTick(server, p, server.getRandom());
					if (!(server.getBlockState(p).getBlock() instanceof CropBlock)) {
						break;
					}
				}
				server.sendParticles(ParticleTypes.SPLASH, p.getX() + 0.5, p.getY() + 0.6, p.getZ() + 0.5, 6, 0.25, 0.1, 0.25, 0.0);
			}
			server.playSound(null, pos, SoundEvents.WEATHER_RAIN, SoundSource.BLOCKS, 0.4F, 1.4F);
			stack.setDamageValue(stack.getDamageValue() + 1);
		}
		return InteractionResult.SUCCESS;
	}
}
