package com.jaymospowers.alchemy.hunter;

import com.jaymospowers.alchemy.brewing.BrewGrades;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * A bottle of one hunter's brew ({@link Draught}): drunk like a potion. What it does is {@link Draughts#drink}; the bottle
 * stays in the pack as a spent phial that remembers what it held ({@link Refills}), to be filled again with strong spirit.
 */
public class DraughtItem extends Item {
	private final Draught draught;

	public DraughtItem(Item.Properties properties, Draught draught) {
		super(properties);
		this.draught = draught;
	}

	public Draught draught() {
		return this.draught;
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		ItemStack drunk = stack.copyWithCount(1);
		ItemStack left = super.finishUsingItem(stack, level, entity);
		if (level instanceof ServerLevel && entity instanceof ServerPlayer player) {
			Draughts.drink(player, this.draught, Draughts.tier(drunk), 1.0, drunk);
			ItemStack spent = Refills.spentPhial(drunk);
			if (left.isEmpty()) {
				return spent;
			}
			player.getInventory().placeItemBackInInventory(spent, Prediction.SERVER_ONLY);
		}
		return left;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("tooltip.jaymospowers.hunter." + this.draught.id).withStyle(ChatFormatting.GRAY));
		if (this.draught.monster()) {
			builder.accept(Component.translatable("tooltip.jaymospowers.hunter.heavy").withStyle(ChatFormatting.DARK_RED));
		} else if (BrewGrades.of(stack) == null) {
			builder.accept(Component.translatable("tooltip.jaymospowers.hunter.grade", Draughts.tier(stack)).withStyle(ChatFormatting.DARK_GRAY));
		}
		super.appendHoverText(stack, context, display, builder, flag);
	}
}
