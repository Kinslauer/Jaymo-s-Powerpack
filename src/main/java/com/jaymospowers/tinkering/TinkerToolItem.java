package com.jaymospowers.tinkering;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** A tool put together at the Tool Station. Its stats all come from its parts' materials ({@link ToolStats}). */
public class TinkerToolItem extends Item {
	public final ToolType type;

	public TinkerToolItem(Item.Properties properties, ToolType type) {
		super(properties);
		this.type = type;
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		// a raw /give gets its defaults (or its given materials) built in; mending a broken tool brings it back to life
		CustomModelData look = stack.get(DataComponents.CUSTOM_MODEL_DATA);
		boolean shownBroken = look != null && !look.flags().isEmpty() && look.flags().getFirst();
		if (look == null || !stack.has(Tinkering.TOOL_MATERIALS) || shownBroken != ToolStats.isBroken(stack)) {
			ToolStats.apply(stack);
		}
	}

	@Override
	public void postHurtEnemy(ItemStack stack, LivingEntity mob, LivingEntity attacker) {
		TraitEffects.afterHit(stack, mob, attacker);
	}

	@Override
	public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity owner) {
		boolean mined = super.mineBlock(stack, level, state, pos, owner);
		TraitEffects.afterMine(stack, level, owner);
		return mined;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		return ToolStats.isBroken(context.getItemInHand()) ? InteractionResult.PASS : super.useOn(context);
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
		TooltipFlag flag) {
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
		if (!details) {
			builder.accept(Component.translatable("tooltip.jaymospowers.tinkering.shift").withStyle(ChatFormatting.DARK_GRAY));
		}
		builder.accept(Component.translatable("tooltip.jaymospowers.tinkering.durability", stack.getMaxDamage() - stack.getDamageValue(),
			stack.getMaxDamage()).withStyle(ChatFormatting.GRAY));
		if (type.style == ToolType.Style.TOOL) {
			builder.accept(Component.translatable("tooltip.jaymospowers.tinkering.mining_speed", String.format("%.2f", stats.miningSpeed()))
				.withStyle(ChatFormatting.GRAY));
			builder.accept(Component.translatable("tooltip.jaymospowers.tinkering.mining_tier", stats.tier().displayName())
				.withStyle(ChatFormatting.GRAY));
		}
	}
}
