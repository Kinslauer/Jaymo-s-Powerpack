package com.jaymospowers.armory;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;

/** The bomb glider folded up: right-click a block to set it down there, facing the way you face. */
public class BombGliderItem extends Item {
	public BombGliderItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (!(context.getLevel() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
		Player player = context.getPlayer();
		BombGlider glider = new BombGlider(ArmoryEntities.BOMB_GLIDER, level);
		glider.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player != null ? player.getYRot() : 0.0F, 0.0F);
		level.addFreshEntity(glider);
		level.playSound(null, glider.getX(), glider.getY(), glider.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.8F, 1.2F);
		if (player == null || !player.isCreative()) {
			context.getItemInHand().shrink(1);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("item.jaymospowers.bomb_glider.hint").withStyle(ChatFormatting.GRAY));
		builder.accept(Component.translatable("item.jaymospowers.bomb_glider.fuel").withStyle(ChatFormatting.DARK_GRAY));
	}
}
