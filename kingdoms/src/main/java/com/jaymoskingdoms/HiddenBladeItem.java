package com.jaymoskingdoms;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** (0.7.113) A hidden blade's bracer in hand: use to strap it on that hand's wrist, else the other's (see {@link HiddenBlade}). */
public class HiddenBladeItem extends Item {
	public HiddenBladeItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (HiddenBlade.worn(player, HiddenBlade.MAIN) && HiddenBlade.worn(player, HiddenBlade.OFF)) {
			if (player instanceof ServerPlayer server) {
				HiddenBlade.putOn(server, hand);
			}
			return InteractionResult.FAIL;
		}
		if (player instanceof ServerPlayer server && HiddenBlade.putOn(server, hand)) {
			ItemStack stack = player.getItemInHand(hand);
			stack.shrink(1);
		}
		return InteractionResult.SUCCESS;
	}
}
