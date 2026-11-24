package com.jaymospowers.umbrella;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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

/** An umbrella or a parasol: use it to open or furl it. Whether it is open rides in its custom model data ({@link Umbrellas#isOpen}). */
public class UmbrellaItem extends Item {
	public final Umbrellas.Kind kind;

	public UmbrellaItem(Properties properties, Umbrellas.Kind kind) {
		super(properties);
		this.kind = kind;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		boolean open = !Umbrellas.isOpen(stack);
		Umbrellas.setOpen(stack, open);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), open ? SoundEvents.BUNDLE_INSERT : SoundEvents.BUNDLE_REMOVE_ONE,
			SoundSource.PLAYERS, 0.7F, open ? 1.3F : 0.9F);
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("item.jaymospowers." + this.kind.key + ".hint").withStyle(ChatFormatting.GRAY));
	}
}
