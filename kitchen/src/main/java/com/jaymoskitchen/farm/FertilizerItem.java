package com.jaymoskitchen.farm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Fertilizer (bone meal and manure): used on farmland, or on a crop growing in farmland, it works the soil into fertile
 * farmland ({@link FertileFarmlandBlock}). It doesn't grow anything itself.
 */
public class FertilizerItem extends Item {
	public FertilizerItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		if (!level.getBlockState(pos).is(Blocks.FARMLAND)) {
			// a crop: its soil below
			if (!level.getBlockState(pos).is(BlockTags.CROPS) || !level.getBlockState(pos.below()).is(Blocks.FARMLAND)) {
				return InteractionResult.PASS;
			}
			pos = pos.below();
		}
		if (level instanceof ServerLevel server) {
			BlockState soil = level.getBlockState(pos);
			BlockState fertile = Manure.FERTILE_FARMLAND.defaultBlockState()
				.setValue(FarmlandBlock.MOISTURE, soil.getValue(FarmlandBlock.MOISTURE));
			server.setBlock(pos, fertile, Block.UPDATE_ALL);
			Player player = context.getPlayer();
			server.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, fertile));
			server.playSound(null, pos, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 1.0F, 0.8F);
			server.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 8, 0.3, 0.1, 0.3, 0.0);
			if (player == null || !player.hasInfiniteMaterials()) {
				context.getItemInHand().shrink(1);
			}
		}
		return InteractionResult.SUCCESS;
	}
}
