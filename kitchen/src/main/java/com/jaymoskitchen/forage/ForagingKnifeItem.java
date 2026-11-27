package com.jaymoskitchen.forage;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The forager's knife (from Spore &amp; Stem, Apache-2.0; see licenses/spore-and-stem). It mines mushrooms fast and
 * takes a second one from each (the loot tables ask for {@code #jaymoskitchen:foraging_tools}). Used on mycelium or
 * Mycelial Soil it scrapes up Mycelial Thread: bare mycelium is spoiled by it now and then, a kept bed of soil hardly.
 */
public class ForagingKnifeItem extends Item {
	/** Thread from one scrape: this many, plus up to this many more. */
	private static final int THREAD_MIN = 1, THREAD_EXTRA = 2;
	/** The scraped mycelium turns to dirt one time in this; Mycelial Soil one time in the second. */
	private static final int MYCELIUM_SPOIL_ODDS = 3, SOIL_SPOIL_ODDS = 10;

	public ForagingKnifeItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		BlockState state = level.getBlockState(pos);
		boolean mycelium = state.is(Blocks.MYCELIUM);
		if (!mycelium && !state.is(Forage.MYCELIAL_SOIL)) {
			return super.useOn(context);
		}
		if (!level.isClientSide()) {
			Player player = context.getPlayer();
			Block.popResourceFromFace(level, pos, context.getClickedFace(),
				new ItemStack(Forage.MYCELIAL_THREAD, THREAD_MIN + level.getRandom().nextInt(THREAD_EXTRA)));
			if (level.getRandom().nextInt(mycelium ? MYCELIUM_SPOIL_ODDS : SOIL_SPOIL_ODDS) == 0) {
				level.setBlock(pos, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
			}
			level.playSound(null, pos, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.BLOCKS, 0.8F, 1.2F);
			if (player != null) {
				context.getItemInHand().hurtAndBreak(1, player, context.getHand());
			}
		}
		return InteractionResult.SUCCESS;
	}
}
