package com.jaymospowers.tinkering;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.Nullable;

/** (0.11.77) A weapon's head or blade in stone or a metal ({@link WeaponParts}): put together at the Tool Station. */
public class WeaponPartItem extends Item {
	public final WeaponParts.Kind kind;

	public WeaponPartItem(Item.Properties properties, WeaponParts.Kind kind) {
		super(properties);
		this.kind = kind;
	}

	/** A head of this kind in this metal. */
	public static ItemStack of(WeaponParts.Kind kind, WeaponParts.Metal metal, int count) {
		ItemStack stack = new ItemStack(kind.item(), count);
		stack.set(Tinkering.WEAPON_PART_METAL, metal.id);
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(metal.id), List.of()));
		stack.set(DataComponents.ITEM_NAME, Component.translatable("jaymospowers.tinkering.named", metal.displayName(),
			Component.translatable("item.jaymospowers." + kind.part())));
		return stack;
	}

	public static WeaponParts.Metal metal(ItemStack stack) {
		WeaponParts.Metal metal = WeaponParts.Metal.byId(stack.get(Tinkering.WEAPON_PART_METAL));
		return metal != null ? metal : WeaponParts.Metal.STONE;
	}

	/** The weapon this head makes at the Tool Station, or null (a stone head of an iron-only weapon). */
	public static @Nullable ItemStack weapon(ItemStack head) {
		if (!(head.getItem() instanceof WeaponPartItem part)) {
			return null;
		}
		Item item = part.kind.in(metal(head));
		return item == null ? null : new ItemStack(item, part.kind.count());
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		if (!stack.has(Tinkering.WEAPON_PART_METAL) || !stack.has(DataComponents.CUSTOM_MODEL_DATA)) {
			stack.applyComponents(of(kind, metal(stack), stack.getCount()).getComponentsPatch());
		}
	}

	/** "with a Crossguard and a Tough Handle", "on its own". */
	public static Component with(WeaponParts.Assembly assembly) {
		List<PartType> parts = assembly.parts;
		if (assembly.extra != null) {
			return Component.translatable("tooltip.jaymospowers.tinkering.weapon_part.with", new ItemStack(assembly.extra).getHoverName());
		}
		return switch (parts.size()) {
			case 0 -> Component.translatable("tooltip.jaymospowers.tinkering.weapon_part.alone");
			case 1 -> Component.translatable("tooltip.jaymospowers.tinkering.weapon_part.with", name(parts.get(0)));
			default -> Component.translatable("tooltip.jaymospowers.tinkering.weapon_part.with2", name(parts.get(0)), name(parts.get(1)));
		};
	}

	private static Component name(PartType part) {
		return Component.translatable("item.jaymospowers." + part.id);
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
		TooltipFlag flag) {
		com.jaymospowers.tinkering.smithing.Finishing.tooltip(stack, builder);
		ItemStack weapon = weapon(stack);
		if (weapon == null) {
			builder.accept(Component.translatable("tooltip.jaymospowers.tinkering.weapon_part.cast_only").withStyle(ChatFormatting.GRAY));
			return;
		}
		builder.accept(Component.translatable("tooltip.jaymospowers.tinkering.weapon_part.makes", weapon.getHoverName()).withStyle(ChatFormatting.GOLD));
		builder.accept(Component.translatable("tooltip.jaymospowers.tinkering.weapon_part", with(kind.assembly())).withStyle(ChatFormatting.GRAY));
	}
}
