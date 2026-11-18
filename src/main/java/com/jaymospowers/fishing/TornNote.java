package com.jaymospowers.fishing;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A wet fisher's scrap: a torn note from a crate or a shipwreck's chest that hints at where one rare fish bites
 * ({@link FishTackle#HINT}). Use it to read it: the hint goes into the Fisher's Journal, on that fish's page (read it on
 * the page; a hint is never spelled out in the chat), and the scrap is used up. One you already have is kept.
 */
public final class TornNote extends Item {
	TornNote(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		String hint = stack.get(FishTackle.HINT);
		if (hint == null || !FishTackle.HINTS.contains(hint)) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide() || !(player instanceof ServerPlayer reader)) {
			return InteractionResult.SUCCESS;
		}
		boolean added = FishLog.noted(reader, hint);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 0.9F);
		if (added) {
			stack.consume(1, player);
		}
		reader.sendOverlayMessage(Component.translatable(added ? "message.jaymospowers.fishing.note_read" : "message.jaymospowers.fishing.note_known")
			.withStyle(added ? ChatFormatting.DARK_AQUA : ChatFormatting.GRAY));
		return InteractionResult.SUCCESS;
	}
}
