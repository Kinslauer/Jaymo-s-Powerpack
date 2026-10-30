package com.jaymospowers.power.zfighter.sky;

import com.jaymospowers.power.zfighter.ZFighterSpace;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * (0.18.52) A star chart: one item, with a {@link Sky#CHART_WORLD} component naming the world it charts. Using it marks that
 * world read ({@link Charts}) and opens a book page of a few odd sentences about the world. It is not used up: it can be read
 * again, and passed on.
 */
public class StarChartItem extends Item {
	public StarChartItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public Component getName(ItemStack stack) {
		int world = Charts.worldOf(stack);
		if (world < 0) {
			return super.getName(stack);
		}
		return Component.translatable("item.jaymospowers.star_chart.of", Component.translatable("space.jaymospowers.dest." + ZFighterSpace.DESTS[world].key()));
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("tooltip.jaymospowers.star_chart").withStyle(ChatFormatting.GRAY));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		int world = Charts.worldOf(player.getItemInHand(hand));
		if (world < 0) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide() || !(player instanceof ServerPlayer server)) {
			return InteractionResult.SUCCESS;
		}
		boolean fresh = Charts.mark(server, world);
		ServerPlayNetworking.send(server, new Sky.Page(world));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 1.0F);
		if (fresh) {
			server.sendOverlayMessage(Component.translatable("message.jaymospowers.star_chart.read", Component.translatable("space.jaymospowers.dest."
				+ ZFighterSpace.DESTS[world].key())).withStyle(ChatFormatting.GOLD));
		}
		return InteractionResult.SUCCESS;
	}
}
