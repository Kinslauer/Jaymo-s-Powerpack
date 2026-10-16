package com.jaymospowers.power.web;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/** A Web Cartridge: web fluid for a shooter. Use it while wearing shooters to load the emptier wrist (see {@link WebShooters}). */
public class WebCartridgeItem extends Item {
	/** The fill bar under a part-used cartridge: the web fluid's milky white. */
	private static final int BAR_COLOR = 0xE8ECF4;

	public WebCartridgeItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (WebShooters.worn(player) == null) {
			if (!level.isClientSide()) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.web_shooter.none"));
			}
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer server) {
			WebShooters.loadWorn(server, player.getItemInHand(hand));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return WebShooters.fluid(stack) < WebShooters.CARTRIDGE_SHOTS;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return Mth.clamp(Math.round(13.0F * WebShooters.fluid(stack) / WebShooters.CARTRIDGE_SHOTS), 0, 13);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return BAR_COLOR;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("item.jaymospowers.web_shooter.shots", WebShooters.fluid(stack), WebShooters.CARTRIDGE_SHOTS)
			.withStyle(ChatFormatting.GRAY));
		builder.accept(Component.translatable("item.jaymospowers.web_cartridge.hint").withStyle(ChatFormatting.DARK_GRAY));
	}
}
