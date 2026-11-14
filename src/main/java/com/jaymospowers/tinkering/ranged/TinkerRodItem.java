package com.jaymospowers.tinkering.ranged;

import com.jaymospowers.fishing.MetalFishingRodItem;
import com.jaymospowers.tinkering.TinkerMaterial;
import com.jaymospowers.tinkering.TinkerTool;
import com.jaymospowers.tinkering.ToolStats;
import com.jaymospowers.tinkering.ToolType;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The Tinkering fishing rod (after Tinkers' Construct's: a limb for the rod, a bowstring for the line, an arrow head for the
 * hook). It casts, charges and reels as the forged metal rod does ({@link MetalFishingRodItem}); its materials set its wear,
 * and a hook of metal makes it a sea rod (the stronger line, big fish), where a flint, bone, stone or wooden hook fishes
 * like a plain rod.
 */
public class TinkerRodItem extends MetalFishingRodItem implements TinkerTool {
	/** Which of the rod's parts is the hook. */
	private static final int HOOK = 2;

	private final ToolType type;

	public TinkerRodItem(Item.Properties properties, ToolType type) {
		super(properties);
		this.type = type;
	}

	/** A Tinkering rod with a metal hook (and not broken). */
	public static boolean seaRod(ItemStack stack) {
		if (!(stack.getItem() instanceof TinkerRodItem) || ToolStats.isBroken(stack)) {
			return false;
		}
		List<TinkerMaterial> materials = ToolStats.materials(stack);
		return materials.size() > HOOK && materials.get(HOOK).cast;
	}

	@Override
	public ToolType type() {
		return type;
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		TinkerTool.tick(stack, level);
	}

	/** A broken rod can't be cast until it's mended (a hook already out can still be brought in). */
	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		return ToolStats.isBroken(player.getItemInHand(hand)) && player.fishing == null ? InteractionResult.FAIL : super.use(level, player, hand);
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
		TooltipFlag flag) {
		TinkerTool.tooltip(stack, type, builder);
		builder.accept(Component.translatable(seaRod(stack) ? "tooltip.jaymospowers.tinkering.rod_sea" : "tooltip.jaymospowers.tinkering.rod_plain")
			.withStyle(ChatFormatting.GRAY));
	}
}
