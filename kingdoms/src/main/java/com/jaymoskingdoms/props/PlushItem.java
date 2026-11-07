package com.jaymoskingdoms.props;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** (0.7.440) A plush in hand squeaks when squeezed (use in the air); set down, it's a block that squeaks too. */
public class PlushItem extends BlockItem {
	public PlushItem(Block block, Item.Properties properties) {
		super(block, properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		PropBlock.squeak(level, player.position().add(0.0, player.getEyeHeight() * 0.7, 0.0), null);

		return InteractionResult.SUCCESS;
	}
}
