package com.jaymoskingdoms.glowwood;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.BlockTransformer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlockTransformers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Axe stripping of the fungal stems and hyphae (ported from Spore & Stem, Apache-2.0). Since 26.3 stripping is the
 * axe's data-driven block transformer and the loader hooks are gone, so this is called from the right-click-block
 * event. Anything carrying the vanilla axe transformer strips, with the vanilla sound, wear and shield rule.
 */
public final class FungusStripping {
	private static Map<Block, Block> stripped;

	private FungusStripping() {
	}

	public static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (player.isSpectator()) {
			return InteractionResult.PASS;
		}
		ItemStack stack = player.getItemInHand(hand);
		Holder<BlockTransformer> transformer = stack.get(DataComponents.BLOCK_TRANSFORMER);
		if (transformer == null || !transformer.is(BlockTransformers.AXE)) {
			return InteractionResult.PASS;
		}
		// vanilla axes leave the block alone when the off hand holds a shield the player means to raise
		if (hand == InteractionHand.MAIN_HAND && player.getOffhandItem().is(Items.SHIELD) && !player.isSecondaryUseActive()) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		Block target = strippedOf(state.getBlock());
		if (target == null) {
			return InteractionResult.PASS;
		}
		level.playSound(player, pos, sound(SoundEvents.AXE_STRIP), SoundSource.BLOCKS, 1.0F, 1.0F);
		if (!level.isClientSide()) {
			level.setBlock(pos, target.withPropertiesOf(state), 11);
			level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
			stack.hurtAndBreak(1, player, hand);
		}
		return InteractionResult.SUCCESS;
	}

	private static Block strippedOf(Block block) {
		if (stripped == null) {
			Map<Block, Block> map = new HashMap<>();
			GlowWoods.STRIPPABLES.forEach(map::put);
			stripped = map;
		}
		return stripped.get(block);
	}

	// AXE_STRIP is a plain SoundEvent or a holder depending on the version; either resolves here
	private static SoundEvent sound(SoundEvent sound) {
		return sound;
	}

	private static SoundEvent sound(Holder<SoundEvent> sound) {
		return sound.value();
	}
}
