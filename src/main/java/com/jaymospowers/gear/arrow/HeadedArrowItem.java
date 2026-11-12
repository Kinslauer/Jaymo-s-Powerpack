package com.jaymospowers.gear.arrow;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * A plain arrow with an {@link ArrowHead} fitted. One item for all fourteen heads (the head is the stack's custom model data
 * string, which also picks its icon), in the {@code minecraft:arrows} tag, so the Kitchen quiver holds it and feeds it to a bow
 * like any arrow. The arrow it shoots carries the head as an entity tag ({@link ArrowHead#tag}) for {@link HeadFx}.
 */
public class HeadedArrowItem extends ArrowItem {
	/** Entity tag prefix of a loosed headed arrow. */
	public static final String TAG = "jaymospowers.head.";
	public static Item ITEM;

	public HeadedArrowItem(Item.Properties properties) {
		super(properties);
	}

	/** {@code count} arrows with this head fitted. */
	public static ItemStack of(ArrowHead head, int count) {
		ItemStack stack = new ItemStack(ITEM, count);
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(head.id), List.of()));
		stack.set(DataComponents.ITEM_NAME, Component.translatable("item.jaymospowers.headed_arrow." + head.id));
		return stack;
	}

	public static @Nullable ArrowHead head(ItemStack stack) {
		CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
		return data == null || data.strings().isEmpty() ? null : ArrowHead.byId(data.strings().get(0));
	}

	@Override
	public AbstractArrow createArrow(Level level, ItemStack ammo, LivingEntity shooter, @Nullable ItemStack weapon) {
		return HeadFx.arm(super.createArrow(level, ammo, shooter, weapon), head(ammo));
	}

	@Override
	public Projectile asProjectile(Level level, Position position, ItemStack stack, Direction direction) {
		Projectile projectile = super.asProjectile(level, position, stack, direction);
		return projectile instanceof AbstractArrow arrow ? HeadFx.arm(arrow, head(stack)) : projectile;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
		TooltipFlag flag) {
		ArrowHead head = head(stack);
		if (head != null) {
			builder.accept(Component.translatable("tooltip.jaymospowers.head." + head.id).withStyle(ChatFormatting.GRAY));
			builder.accept(Component.translatable(head.spent ? "tooltip.jaymospowers.head.spent" : "tooltip.jaymospowers.head.kept")
				.withStyle(ChatFormatting.DARK_GRAY));
		}
	}
}
