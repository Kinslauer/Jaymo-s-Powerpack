package com.jaymospowers.power.web;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/** A pair of web shooters in hand: use to strap them on; Sneak + use to load their empty wrists (see {@link WebShooters}). */
public class WebShooterItem extends Item {
	public WebShooterItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.isShiftKeyDown()) {
			if (player instanceof ServerPlayer server) {
				WebShooters.loadHeld(server, stack);
			}
			return InteractionResult.SUCCESS;
		}
		if (WebShooters.worn(player) != null) {
			if (!level.isClientSide()) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.web_shooter.already"));
			}
			return InteractionResult.FAIL;
		}
		if (com.jaymospowers.power.smoke.SmokeVapour.active(player)) {
			// nothing stays on smoke
			com.jaymospowers.power.smoke.SmokeVapour.refuseArmour(player);
			return InteractionResult.FAIL;
		}
		if (player instanceof ServerPlayer server) {
			WebShooters.putOn(server, stack);
			stack.shrink(1);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		WebShooters.Load load = stack.getOrDefault(WebShooters.LOAD, WebShooters.Load.EMPTY);
		builder.accept(Component.translatable("item.jaymospowers.web_shooter.wrists", wrist(load.main()), wrist(load.off())).withStyle(ChatFormatting.GRAY));
		builder.accept(Component.translatable("item.jaymospowers.web_shooter.hint").withStyle(ChatFormatting.DARK_GRAY));
	}

	static Component wrist(int fluid) {
		return fluid < 0 ? Component.translatable("item.jaymospowers.web_shooter.no_cartridge")
			: Component.translatable("item.jaymospowers.web_shooter.shots", fluid, WebShooters.CARTRIDGE_SHOTS);
	}
}
