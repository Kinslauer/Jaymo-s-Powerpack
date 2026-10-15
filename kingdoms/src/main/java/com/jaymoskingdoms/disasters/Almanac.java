package com.jaymoskingdoms.disasters;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * (0.7.31) The almanac: use it to read the coming weather. It tells of every disaster foretold within
 * {@link Calamities#FORETOLD_RANGE} of you (what, where and in how many hours), or that the days ahead look fair.
 */
public final class Almanac extends Item {
	public Almanac(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
			List<Component> lines = Calamities.foretold(server, sp.blockPosition());
			if (lines.isEmpty()) {
				sp.sendSystemMessage(Component.translatable("disaster.jaymoskingdoms.almanac.fair").withStyle(ChatFormatting.GRAY));
			} else {
				for (Component line : lines) {
					sp.sendSystemMessage(Component.translatable("disaster.jaymoskingdoms.almanac", line).withStyle(ChatFormatting.GOLD));
				}
			}
			server.playSound(null, sp.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
			sp.getCooldowns().addCooldown(sp.getItemInHand(hand), 20);
		}
		return InteractionResult.SUCCESS;
	}
}
