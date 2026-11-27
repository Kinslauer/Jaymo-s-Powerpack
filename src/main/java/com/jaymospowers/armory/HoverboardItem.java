package com.jaymospowers.armory;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;

/** The hoverboard, folded to carry: right-click a block to set it down there, facing the way you face. Its charge and dyes ride in its custom data. */
public class HoverboardItem extends Item {
	public HoverboardItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (!(context.getLevel() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
		Player player = context.getPlayer();
		Hoverboard board = new Hoverboard(ArmoryEntities.HOVERBOARD, level);
		board.load(context.getItemInHand());
		board.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player != null ? player.getYRot() : 0.0F, 0.0F);
		level.addFreshEntity(board);
		level.playSound(null, board.getX(), board.getY(), board.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6F, 1.6F);
		if (player == null || !player.isCreative()) {
			context.getItemInHand().shrink(1);
		}
		return InteractionResult.SUCCESS;
	}

	/** The charge it carries (ticks): a new one's starting charge when it has none noted. */
	static int charge(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? Hoverboard.START_CHARGE : data.copyTag().getIntOr(Hoverboard.CHARGE_KEY, Hoverboard.START_CHARGE);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("item.jaymospowers.hoverboard.hint").withStyle(ChatFormatting.GRAY));
		builder.accept(Component.translatable("item.jaymospowers.hoverboard.charge", charge(stack) / 20).withStyle(ChatFormatting.DARK_GRAY));
	}
}
