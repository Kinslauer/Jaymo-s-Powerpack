package com.jaymospowers.aether;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Tools on aether soil (vanilla's tilling and flattening only know vanilla blocks): a shovel flattens aether grass or
 * aether dirt (or coarse aether dirt) into a path, a hoe tills them into farmland (coarse dirt becomes plain aether
 * dirt first, as in vanilla). The space above must be air.
 */
public final class AetherSoilTools {
	private AetherSoilTools() {
	}

	static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (player.isSpectator() || hit.getDirection() == Direction.DOWN) {
				return InteractionResult.PASS;
			}
			BlockPos pos = hit.getBlockPos();
			BlockState state = level.getBlockState(pos);
			ItemStack held = player.getItemInHand(hand);
			boolean soil = state.is(AetherBlocks.AETHER_GRASS_BLOCK) || state.is(AetherBlocks.AETHER_DIRT) || state.is(AetherBlocks.COARSE_AETHER_DIRT);
			if (!soil || !level.getBlockState(pos.above()).isAir()) {
				return InteractionResult.PASS;
			}
			Block result;
			if (held.is(ItemTags.SHOVELS) && !player.isShiftKeyDown()) {
				result = AetherBlocks.AETHER_DIRT_PATH;
			} else if (held.is(ItemTags.HOES)) {
				result = state.is(AetherBlocks.COARSE_AETHER_DIRT) ? AetherBlocks.AETHER_DIRT : AetherBlocks.AETHER_FARMLAND;
			} else {
				return InteractionResult.PASS;
			}
			if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
				server.setBlockAndUpdate(pos, result.defaultBlockState());
				if (result == AetherBlocks.AETHER_DIRT_PATH) {
					server.playSound(null, pos, SoundEvents.SHOVEL_FLATTEN.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
				} else {
					server.playSound(null, pos, SoundEvents.HOE_TILL.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
				}
				held.hurtAndBreak(1, sp, hand);
			}
			return InteractionResult.SUCCESS;
		});
	}
}
