package com.jaymospowers.aether;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Aether's blocks and items (docs/aether.md). The dimension itself is {@code jaymospowers:aether} (data), reached
 * by Wind ({@code power/wind/aether/WindAether}); the trees its saplings grow are configured features in the data.
 */
public final class Aether {
	private Aether() {
	}

	public static void init() {
		AetherBlocks.init();
		AetherItems.init();
		FloatingBlockEntity.register();
		AercloudBlock.register();
		QuicksoilSpeed.register();
		// an axe strips a skyroot log or wood (vanilla's stripping only knows vanilla logs)
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			BlockPos pos = hit.getBlockPos();
			BlockState state = level.getBlockState(pos);
			Block stripped = stripped(state.getBlock());
			ItemStack held = player.getItemInHand(hand);
			if (stripped == null || player.isShiftKeyDown() || !held.is(ItemTags.AXES)) {
				return InteractionResult.PASS;
			}
			if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
				server.setBlockAndUpdate(pos, stripped.defaultBlockState().setValue(RotatedPillarBlock.AXIS, state.getValue(RotatedPillarBlock.AXIS)));
				server.playSound(null, pos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1.0F, 1.0F);
				held.hurtAndBreak(1, sp, hand);
			}
			return InteractionResult.SUCCESS;
		});
	}

	/** What an axe turns {@code block} into, or null. */
	private static Block stripped(Block block) {
		if (block == AetherBlocks.SKYROOT_LOG) {
			return AetherBlocks.STRIPPED_SKYROOT_LOG;
		}
		return block == AetherBlocks.SKYROOT_WOOD ? AetherBlocks.STRIPPED_SKYROOT_WOOD : null;
	}
}
