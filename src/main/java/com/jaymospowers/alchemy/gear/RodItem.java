package com.jaymospowers.alchemy.gear;

import com.jaymospowers.alchemy.Knowledge;
import com.jaymospowers.alchemy.infusion.Infusion;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The Artificer's Rod. It holds one {@link FocusItem focus} (the focus item's id, in the {@link AlchemyGear#FOCUS}
 * component). Sneak-use with a focus in the other hand swaps it in (the old one comes back to the inventory); sneak-use
 * with nothing in the other hand takes the focus out. Plain use casts the focus ({@link Focus#cast}), paying its essence from
 * the phials you carry. Used on an infusion matrix (or its pedestal) it wakes the altar, as a phial does
 * ({@link Infusion#start}).
 * <p>
 * Needs the {@code artifice} research, and each focus its own.
 */
public class RodItem extends Item {
	public RodItem(Item.Properties properties) {
		super(properties);
	}

	static Component text(String key, Object... args) {
		return Component.translatable("message.jaymospowers.gear." + key, args);
	}

	/** The focus the rod holds, if any. */
	public static @Nullable Focus focusOf(ItemStack rod) {
		Identifier id = rod.get(AlchemyGear.FOCUS);
		return id != null && BuiltInRegistries.ITEM.getValue(id) instanceof FocusItem item ? item.focus : null;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		var state = level.getBlockState(pos);
		if (!state.is(Infusion.MATRIX) && !state.is(Infusion.PEDESTAL)) {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel server && context.getPlayer() instanceof ServerPlayer player) {
			Infusion.start(server, pos, player);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack rod = player.getItemInHand(hand);
		ItemStack other = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
		Focus held = focusOf(rod);
		boolean swap = player.isShiftKeyDown() && (other.getItem() instanceof FocusItem || held != null && other.isEmpty());
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
			return swap || held != null ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		if (!Knowledge.knows(sp, AlchemyGear.ARTIFICE)) {
			sp.sendOverlayMessage(text("not_understood"));
			return InteractionResult.FAIL;
		}
		if (swap) {
			swapFocus(sp, rod, other);
			return InteractionResult.SUCCESS;
		}
		if (held == null) {
			sp.sendOverlayMessage(text("no_focus"));
			return InteractionResult.FAIL;
		}
		if (!Knowledge.knows(sp, held.research())) {
			sp.sendOverlayMessage(text("not_understood"));
			return InteractionResult.FAIL;
		}
		if (held.cast(sp, server)) {
			player.getCooldowns().addCooldown(rod, held.cooldown);
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.FAIL;
	}

	/** Sets the focus in {@code other} into the rod (or, with {@code other} empty, takes the rod's out); the old one goes to the inventory. */
	private static void swapFocus(ServerPlayer player, ItemStack rod, ItemStack other) {
		Identifier old = rod.get(AlchemyGear.FOCUS);
		if (other.getItem() instanceof FocusItem item) {
			rod.set(AlchemyGear.FOCUS, BuiltInRegistries.ITEM.getKey(item));
			other.shrink(1);
			player.sendOverlayMessage(text("focus_set", item.getName(new ItemStack(item))));
		} else {
			rod.remove(AlchemyGear.FOCUS);
		}
		if (old != null) {
			ItemStack back = new ItemStack(BuiltInRegistries.ITEM.getValue(old));
			player.getInventory().placeItemBackInInventory(back, Prediction.SERVER_ONLY);
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
		TooltipFlag flag) {
		Identifier id = stack.get(AlchemyGear.FOCUS);
		Item focus = id == null ? null : BuiltInRegistries.ITEM.getValue(id);
		if (focus instanceof FocusItem) {
			builder.accept(Component.translatable("tooltip.jaymospowers.artificers_rod.focus", focus.getName(new ItemStack(focus))).withStyle(ChatFormatting.GRAY));
		} else {
			builder.accept(Component.translatable("tooltip.jaymospowers.artificers_rod.empty").withStyle(ChatFormatting.GRAY));
		}
		builder.accept(Component.translatable("tooltip.jaymospowers.artificers_rod").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}
}
