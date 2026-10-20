package com.jaymospowers.costume;

import com.jaymospowers.power.smoke.SmokeVapour;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * A sewn costume, folded. Use it to put it on (it leaves the hand and goes on the player, {@link Costume#wear}). Its
 * item model is tinted with its two colours, kept as the custom model data's colours (index 0 primary, 1 secondary).
 */
public class CostumeItem extends Item {
	public CostumeItem(Item.Properties properties) {
		super(properties);
	}

	/** A costume item made to this design. */
	public static ItemStack of(CostumeDesign design) {
		ItemStack stack = new ItemStack(Costume.COSTUME);
		stack.set(Costume.DESIGN, design);
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(),
			List.of(design.primary().getTextureDiffuseColor(), design.secondary().getTextureDiffuseColor())));
		return stack;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		CostumeDesign design = stack.get(Costume.DESIGN);
		if (design == null) {
			return InteractionResult.PASS;
		}
		if (SmokeVapour.active(player)) {
			// nothing stays on smoke
			SmokeVapour.refuseArmour(player);
			return InteractionResult.FAIL;
		}
		if (player instanceof ServerPlayer server) {
			stack.shrink(1);
			Costume.wear(server, design);
			server.sendOverlayMessage(Component.translatable("message.jaymospowers.costume.worn", label(design)));
		}
		return InteractionResult.SUCCESS;
	}

	/** The costume's name, or its cut when it has none. */
	public static Component label(CostumeDesign design) {
		return design.name().isEmpty() ? Component.translatable("costume.jaymospowers.cut." + design.cut().getSerializedName())
			: Component.literal(design.name());
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		CostumeDesign design = stack.get(Costume.DESIGN);
		if (design == null) {
			return;
		}
		if (!design.name().isEmpty()) {
			builder.accept(Component.literal("“" + design.name() + "”").withStyle(ChatFormatting.GOLD));
		}
		builder.accept(Component.translatable("tooltip.jaymospowers.costume.cut",
			Component.translatable("costume.jaymospowers.cut." + design.cut().getSerializedName())).withStyle(ChatFormatting.GRAY));
		builder.accept(Component.translatable("tooltip.jaymospowers.costume.colours", Costume.colourName(design.primary()),
			Costume.colourName(design.secondary())).withStyle(ChatFormatting.GRAY));
		builder.accept(Component.translatable("tooltip.jaymospowers.costume.mask",
			Component.translatable("costume.jaymospowers.mask." + design.mask().getSerializedName())).withStyle(ChatFormatting.GRAY));
		builder.accept(Component.translatable("tooltip.jaymospowers.costume.cape",
			Component.translatable("costume.jaymospowers.cape." + design.cape().getSerializedName())).withStyle(ChatFormatting.GRAY));
		if (design.mask().isHood()) {
			// (0.14.3) the face shows, but the name tag can't be read from afar (CostumeClient.HOOD_NAME_RANGE)
			builder.accept(Component.translatable("tooltip.jaymospowers.costume.hooded").withStyle(ChatFormatting.GRAY));
		}
		if (design.hasEmblem()) {
			builder.accept(Component.translatable("tooltip.jaymospowers.costume.emblem").withStyle(ChatFormatting.GRAY));
		}
		String power = CostumeSets.power(design);
		if (power != null) {
			// (0.11.78) from a power's set
			builder.accept(Component.translatable("tooltip.jaymospowers.costume.set", CostumeSets.powerName(power)).withStyle(ChatFormatting.DARK_AQUA));
		}
		builder.accept(Component.translatable("tooltip.jaymospowers.costume.use").withStyle(ChatFormatting.DARK_GRAY));
	}
}
