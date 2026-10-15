package com.jaymospowers.tinkering.smeltery;

import com.jaymospowers.tinkering.PartType;
import com.jaymospowers.tinkering.Tinkering;
import com.jaymospowers.tinkering.ToolPartItem;
import com.jaymospowers.tinkering.WeaponPartItem;
import com.jaymospowers.tinkering.WeaponParts;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.Nullable;

/**
 * A gold cast: set it on a casting table and pour metal over it to make an ingot, a nugget or a tool part. It's
 * reusable. Pour gold over an ingot, a nugget, any tool part or weapon head ({@link WeaponParts}) on the table to make
 * the cast of its shape.
 */
public class CastItem extends Item {
	/** The shapes there are casts for: ingots, nuggets and each tool part. */
	public enum Shape {
		INGOT("ingot", null), NUGGET("nugget", null),
		PICK_HEAD(PartType.PICK_HEAD), SMALL_AXE_HEAD(PartType.SMALL_AXE_HEAD), ADZE_HEAD(PartType.ADZE_HEAD),
		SMALL_BLADE(PartType.SMALL_BLADE), HAMMER_HEAD(PartType.HAMMER_HEAD), BROAD_BLADE(PartType.BROAD_BLADE),
		LARGE_PLATE(PartType.LARGE_PLATE), TOOL_HANDLE(PartType.TOOL_HANDLE), TOUGH_HANDLE(PartType.TOUGH_HANDLE),
		TOOL_BINDING(PartType.TOOL_BINDING),
		/** (0.11.70) A weapon head of the kind it was made from ({@link WeaponParts}). */
		WEAPON("weapon", null);

		public final String id;
		public final @Nullable PartType part;

		Shape(String id, @Nullable PartType part) {
			this.id = id;
			this.part = part;
		}

		Shape(PartType part) {
			this(part.id, part);
		}

		/** The shape of an item a cast can be made from, or null. */
		public static @Nullable Shape of(ItemStack stack) {
			if (stack.getItem() instanceof ToolPartItem part) {
				for (Shape shape : values()) {
					if (shape.part == part.part) {
						return shape;
					}
				}
			}
			if (stack.is(Items.IRON_INGOT) || stack.is(Items.GOLD_INGOT) || stack.is(Items.COPPER_INGOT) || stack.is(Items.NETHERITE_INGOT)
				|| stack.is(Items.BRICK) || stack.is(Items.NETHER_BRICK) || Tinkering.ALLOY_INGOTS.containsValue(stack.getItem())) {
				return INGOT;
			}
			if (stack.is(Items.IRON_NUGGET) || stack.is(Items.GOLD_NUGGET) || stack.is(Items.COPPER_NUGGET) || stack.is(Tinkering.SILVER_NUGGET)) {
				return NUGGET;
			}
			if (stack.getItem() instanceof WeaponPartItem) {
				return WEAPON;
			}
			return null;
		}
	}

	public final Shape shape;

	public CastItem(Item.Properties properties, Shape shape) {
		super(properties);
		this.shape = shape;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
		TooltipFlag flag) {
		if (shape == Shape.WEAPON) {
			WeaponParts.Kind kind = WeaponParts.ofCast(stack);
			if (kind == null) {
				builder.accept(Component.translatable("tooltip.jaymospowers.tinkering.weapon_cast.blank").withStyle(ChatFormatting.GRAY));
				return;
			}
			for (ItemStack result : WeaponParts.castResults(kind)) {
				builder.accept(Component.translatable("tooltip.jaymospowers.tinkering.weapon_cast.makes", result.getHoverName())
					.withStyle(ChatFormatting.GOLD));
			}
		}
		builder.accept(Component.translatable("tooltip.jaymospowers.tinkering.cast").withStyle(ChatFormatting.GRAY));
	}
}
