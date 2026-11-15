package com.jaymoskitchen.stomach;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** The lunch box ({@link LunchBox}): used, it opens on its nine food slots. */
public class LunchBoxItem extends Item {
	public LunchBoxItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack box = player.getItemInHand(hand);
		if (player instanceof ServerPlayer server) {
			server.openMenu(new SimpleMenuProvider((id, inventory, p) -> new LunchBoxMenu(id, inventory, new LunchBoxMenu.Contents(box, hand)),
				box.getHoverName()));
		}
		return InteractionResult.SUCCESS;
	}
}
