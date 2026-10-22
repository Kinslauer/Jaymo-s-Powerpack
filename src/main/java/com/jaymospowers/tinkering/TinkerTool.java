package com.jaymospowers.tinkering;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

/**
 * (0.15.x) An item built at the Tool Station: {@link TinkerToolItem} (most of them), the bow and crossbow (which are vanilla
 * {@code BowItem} and {@code CrossbowItem} underneath) and the arrow (an {@code ArrowItem}). What they share lives here as
 * static methods so each class stays a thin shell.
 */
public interface TinkerTool {
	/** Overslime: how often (ticks) it mends a point per level. */
	int OVERSLIME_TICKS = 100;

	ToolType type();

	/** A raw /give gets its defaults (or its given materials) built in; mending a broken tool brings it back to life; Overslime mends. */
	static void tick(ItemStack stack, ServerLevel level) {
		CustomModelData look = stack.get(DataComponents.CUSTOM_MODEL_DATA);
		boolean shownBroken = look != null && !look.flags().isEmpty() && look.flags().getFirst();
		if (look == null || !stack.has(Tinkering.TOOL_MATERIALS) || shownBroken != ToolStats.isBroken(stack)) {
			ToolStats.apply(stack);
			Modifier.enchant(stack, level.registryAccess());
		}
		if (level.getGameTime() % OVERSLIME_TICKS == 0 && stack.isDamaged() && !ToolStats.isBroken(stack)) {
			ToolStats.Stats stats = ToolStats.stats(stack);
			int overslime = stats == null ? 0 : stats.level(Trait.OVERSLIME);
			if (overslime > 0) {
				stack.setDamageValue(Math.max(0, stack.getDamageValue() - overslime));
			}
		}
	}

	/** The tooltip: materials, traits, modifiers, level, durability, then mining or ranged numbers. */
	static void tooltip(ItemStack stack, ToolType type, Consumer<Component> builder) {
		List<TinkerMaterial> materials = ToolStats.materials(stack);
		ToolStats.Stats stats = ToolStats.compute(type, materials);
		if (ToolStats.isBroken(stack)) {
			builder.accept(Component.translatable("tooltip.jaymospowers.tinkering.broken").withStyle(ChatFormatting.RED));
		}
		MutableComponent names = Component.empty();
		for (int i = 0; i < materials.size(); i++) {
			names.append(i > 0 ? Component.literal(", ") : Component.empty()).append(materials.get(i).displayName());
		}
		builder.accept(Component.translatable("tooltip.jaymospowers.tinkering.materials", names).withStyle(ChatFormatting.GRAY));
		boolean details = Tinkering.traitDetails.getAsBoolean();
		for (Map.Entry<Trait, Integer> trait : stats.traits().entrySet()) {
			Component name = trait.getKey().name(trait.getValue());
			builder.accept((details ? Component.translatable("tooltip.jaymospowers.tinkering.trait", name,
				trait.getKey().description().copy().withStyle(ChatFormatting.DARK_GRAY)) : name.copy()).withStyle(trait.getKey().colour));
		}
		if (!type.style.durable()) {
			rangedLines(type, materials, builder);
			if (!details) {
				builder.accept(Component.translatable("tooltip.jaymospowers.tinkering.shift").withStyle(ChatFormatting.DARK_GRAY));
			}
			return;
		}
		for (Map.Entry<Modifier, Integer> modifier : Modifier.of(stack).entrySet()) {
			Component name = modifier.getKey().name(modifier.getValue());
			builder.accept((details ? Component.translatable("tooltip.jaymospowers.tinkering.trait", name,
				modifier.getKey().description().copy().withStyle(ChatFormatting.DARK_GRAY)) : name.copy()).withStyle(modifier.getKey().colour));
		}
		if (!details) {
			builder.accept(Component.translatable("tooltip.jaymospowers.tinkering.shift").withStyle(ChatFormatting.DARK_GRAY));
		}
		int xp = Modifier.xp(stack), toolLevel = Modifier.toolLevel(xp);
		builder.accept((toolLevel >= Modifier.MAX_LEVEL ? Component.translatable("tooltip.jaymospowers.tinkering.level_max", toolLevel)
			: Component.translatable("tooltip.jaymospowers.tinkering.level", toolLevel, xp, Modifier.xpFor(toolLevel + 1)))
			.withStyle(ChatFormatting.YELLOW));
		builder.accept(Component.translatable("tooltip.jaymospowers.tinkering.slots", Modifier.slots(stack) - Modifier.used(stack),
			Modifier.slots(stack)).withStyle(ChatFormatting.GRAY));
		builder.accept(Component.translatable("tooltip.jaymospowers.tinkering.durability", stack.getMaxDamage() - stack.getDamageValue(),
			stack.getMaxDamage()).withStyle(ChatFormatting.GRAY));
		if (type.style == ToolType.Style.TOOL) {
			builder.accept(Component.translatable("tooltip.jaymospowers.tinkering.mining_speed", String.format("%.2f", stats.miningSpeed()))
				.withStyle(ChatFormatting.GRAY));
			builder.accept(Component.translatable("tooltip.jaymospowers.tinkering.mining_tier", Modifier.tier(stack, stats.tier()).displayName())
				.withStyle(ChatFormatting.GRAY));
		}
		if (type.style.shoots() || type.style == ToolType.Style.JAVELIN) {
			rangedLines(type, materials, builder);
		}
	}

	/** A bow's draw speed, velocity and accuracy, an arrow's or thrown blade's damage, a javelin's throw. */
	private static void rangedLines(ToolType type, List<TinkerMaterial> materials, Consumer<Component> builder) {
		ToolStats.Ranged ranged = ToolStats.ranged(type, materials);
		if (type.style.shoots()) {
			builder.accept(percent("draw_speed", ranged.drawSpeed()));
			builder.accept(percent("velocity", ranged.velocity()));
		}
		if (type.style == ToolType.Style.ARROW) {
			builder.accept(Component.translatable("tooltip.jaymospowers.tinkering.projectile_damage", String.format("%.2f", ranged.damage()))
				.withStyle(ChatFormatting.GRAY));
			builder.accept(percent("velocity", ranged.velocity()));
		} else if (type.style == ToolType.Style.THROWN) {
			builder.accept(Component.translatable("tooltip.jaymospowers.tinkering.projectile_damage", String.format("%.2f", ranged.damage()))
				.withStyle(ChatFormatting.GRAY));
		}
		builder.accept(Component.translatable("tooltip.jaymospowers.tinkering.accuracy", Math.round(ranged.accuracy() * 100) + "%")
			.withStyle(ChatFormatting.GRAY));
	}

	private static Component percent(String key, float multiplier) {
		return Component.translatable("tooltip.jaymospowers.tinkering." + key, Math.round(multiplier * 100) + "%").withStyle(ChatFormatting.GRAY);
	}
}
