package com.jaymospowers.tinkering;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.Nullable;

/** A tool part of some material, shaped at the Part Builder. */
public class ToolPartItem extends Item {
	public final PartType part;

	public ToolPartItem(Item.Properties properties, PartType part) {
		super(properties);
		this.part = part;
	}

	/** A part of this material. */
	public static ItemStack of(PartType part, TinkerMaterial material, int count) {
		ItemStack stack = new ItemStack(Tinkering.PARTS.get(part), count);
		stack.set(Tinkering.PART_MATERIAL, material.id);
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(material.id), List.of()));
		stack.set(DataComponents.ITEM_NAME, Component.translatable("jaymospowers.tinkering.named", material.displayName(),
			Component.translatable("item.jaymospowers." + part.id)));
		return stack;
	}

	public static @Nullable TinkerMaterial material(ItemStack stack) {
		String id = stack.get(Tinkering.PART_MATERIAL);
		return id != null ? TinkerMaterial.byId(id) : null;
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		TinkerMaterial material = material(stack);
		if (material == null || !stack.has(DataComponents.CUSTOM_MODEL_DATA)) {
			TinkerMaterial fallback = part.kind == PartType.Kind.HEAD || part.kind == PartType.Kind.HANDLE ? TinkerMaterial.WOOD
				: ToolStats.defaultFor(part.kind);
			ItemStack fixed = of(part, material != null ? material : fallback, stack.getCount());
			stack.applyComponents(fixed.getComponentsPatch());
		}
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
		TooltipFlag flag) {
		TinkerMaterial material = material(stack);
		if (material == null) {
			return;
		}
		for (Component line : lines(part, material)) {
			builder.accept(line);
		}
		List<Component> tools = new ArrayList<>();
		for (ToolType type : ToolType.values()) {
			if (type.parts.stream().anyMatch(s -> s.part() == part)) {
				tools.add(Component.translatable("item.jaymospowers." + type.itemId()));
			}
		}
		if (tools.isEmpty()) {
			return;
		}
		MutableComponent used = Component.empty();
		for (int i = 0; i < tools.size(); i++) {
			used.append(i > 0 ? Component.literal(", ") : Component.empty()).append(tools.get(i));
		}
		builder.accept(Component.translatable("tooltip.jaymospowers.tinkering.part_of", used).withStyle(ChatFormatting.DARK_GRAY));
	}

	/** What a part of this material brings: its stats for the part's kind, then its trait. */
	public static List<Component> lines(PartType part, TinkerMaterial material) {
		List<Component> out = new ArrayList<>();
		TinkerMaterial.Stats s = material.stats;
		TinkerMaterial.Limb limb = material.limb();
		TinkerMaterial.Grip grip = material.grip();
		if (part.kind == PartType.Kind.LIMB && limb != null) {
			out.add(durability(limb.durability()));
			out.add(percent("draw_speed", limb.drawSpeed()));
			out.add(percent("velocity", limb.velocity()));
			out.add(percent("accuracy", limb.accuracy()));
		} else if (part.kind == PartType.Kind.GRIP && grip != null) {
			out.add(percent("handle_durability", grip.durability()));
			out.add(percent("accuracy", grip.accuracy()));
			out.add(Component.translatable("tooltip.jaymospowers.tinkering.attack", String.format("%.2f", grip.meleeDamage())).withStyle(ChatFormatting.GRAY));
		} else if (part.kind == PartType.Kind.ARROW_HEAD && s != null) {
			out.add(Component.translatable("tooltip.jaymospowers.tinkering.attack", String.format("%.2f", s.attack())).withStyle(ChatFormatting.GRAY));
		} else if (part.kind == PartType.Kind.BOWSTRING || part.kind == PartType.Kind.ARROW_SHAFT || part.kind == PartType.Kind.FLETCHING) {
			if (part.kind == PartType.Kind.ARROW_SHAFT) {
				out.add(percent("velocity", material.shaftVelocity()));
			}
			out.add(Component.translatable("tooltip.jaymospowers.tinkering.part_trait_only").withStyle(ChatFormatting.GRAY));
		} else if (s == null || part.kind == PartType.Kind.BINDING) {
			out.add(Component.translatable("tooltip.jaymospowers.tinkering.binding_only").withStyle(ChatFormatting.GRAY));
		} else if (part.kind == PartType.Kind.HEAD) {
			out.add(durability(s.durability()));
			out.add(Component.translatable("tooltip.jaymospowers.tinkering.mining_speed", String.format("%.2f", s.miningSpeed()))
				.withStyle(ChatFormatting.GRAY));
			out.add(Component.translatable("tooltip.jaymospowers.tinkering.mining_tier", s.tier().displayName()).withStyle(ChatFormatting.GRAY));
			out.add(Component.translatable("tooltip.jaymospowers.tinkering.attack", String.format("%.2f", s.attack())).withStyle(ChatFormatting.GRAY));
		} else {
			out.add(percent("handle_durability", s.handleDurability()));
			out.add(percent("handle_speed", s.handleSpeed()));
			out.add(percent("handle_attack", s.handleAttack()));
			out.add(percent("handle_attack_speed", s.handleAttackSpeed()));
		}
		out.add(Component.translatable("tooltip.jaymospowers.tinkering.trait", material.trait.name(1),
			material.trait.description().copy().withStyle(ChatFormatting.DARK_GRAY)).withStyle(material.trait.colour));
		return out;
	}

	private static Component durability(int value) {
		return Component.translatable("tooltip.jaymospowers.tinkering.handle_durability", value).withStyle(ChatFormatting.GRAY);
	}

	private static Component percent(String key, float value) {
		String shown = (value >= 0 ? "+" : "") + Math.round(value * 100) + "%";
		return Component.translatable("tooltip.jaymospowers.tinkering." + key, shown)
			.withStyle(value > 0 ? ChatFormatting.GREEN : value < 0 ? ChatFormatting.RED : ChatFormatting.GRAY);
	}
}
