package com.jaymospowers.tinkering.smithing;

import com.jaymospowers.tinkering.Tinkering;
import com.jaymospowers.tinkering.forge.ForgeHearthBlock;
import com.jaymospowers.tinkering.smeltery.Molten;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * (Smithing) Hot metal, after Overgeared's heated items (Copyright (c) phuccom000, MIT). Ingots left on a lit Forge
 * Hearth come off as Hot Ingots; work on the anvil and freshly hammered heads are hot too. Anything hot carries the
 * game time it was last in the fire ({@link Smithing#HEATED_AT}) and goes cold {@link #COOL_TICKS} later. Carrying hot
 * metal without tongs in either hand sets you alight; with tongs, they wear instead. Fire Resistance, creative and the
 * Emberborn's bare hands are spared.
 */
public final class HotMetal {
	/** Ticks hot metal stays hot out of the fire (Overgeared's 60 s). */
	public static final int COOL_TICKS = 1200;
	/** Ticks a hearth (heat 2) takes to bring metal up; every heat level above that halves it. */
	public static final int HEAT_TICKS = 100;
	/** How often the burn and cooling check runs per player (ticks), and how often tongs wear a point (Overgeared's 40). */
	public static final int CHECK_TICKS = 10, TONGS_WEAR_TICKS = 40;
	/** Fire ticks a bare-handed carry gives each check. */
	public static final int BURN_TICKS = 20;

	/**
	 * Whatever turns a hot stack cold that isn't a Hot Ingot or a blank (a freshly hammered head): the first non-null
	 * answer wins; with none, the stack just loses its heat.
	 */
	public static final List<BiFunction<ItemStack, ServerLevel, @Nullable ItemStack>> COOLERS = new ArrayList<>();

	private HotMetal() {
	}

	/** The metal of an ingot that can be heated and worked, or null. */
	public static @Nullable Molten ingotMetal(ItemStack stack) {
		Item item = stack.getItem();
		if (item == Items.IRON_INGOT) {
			return Molten.IRON;
		}
		if (item == Items.GOLD_INGOT) {
			return Molten.GOLD;
		}
		if (item == Items.COPPER_INGOT) {
			return Molten.COPPER;
		}
		for (var entry : Tinkering.ALLOY_INGOTS.entrySet()) {
			if (entry.getValue() == item) {
				return entry.getKey();
			}
		}
		return null;
	}

	/** The cold ingot of this metal. */
	public static ItemStack ingot(Molten molten, int count) {
		Item item = switch (molten) {
			case IRON -> Items.IRON_INGOT;
			case GOLD -> Items.GOLD_INGOT;
			case COPPER -> Items.COPPER_INGOT;
			default -> Tinkering.ALLOY_INGOTS.get(molten);
		};
		return item == null ? ItemStack.EMPTY : new ItemStack(item, count);
	}

	/** The metal a Hot Ingot (or a cold ingot) is. */
	public static @Nullable Molten metal(ItemStack stack) {
		if (stack.is(Smithing.HOT_INGOT)) {
			return Molten.byId(stack.getOrDefault(Smithing.HOT_METAL, Molten.IRON.id));
		}
		return ingotMetal(stack);
	}

	/** A Hot Ingot of this metal, just out of the fire. */
	public static ItemStack hotIngot(Molten molten, int count, long now) {
		ItemStack stack = new ItemStack(Smithing.HOT_INGOT, count);
		stack.set(Smithing.HOT_METAL, molten.id);
		stack.set(Smithing.HEATED_AT, now);
		HotIngotItem.look(stack, molten);
		return stack;
	}

	/** Whether this stack is still hot at game time {@code now}. */
	public static boolean hot(ItemStack stack, long now) {
		Long at = stack.get(Smithing.HEATED_AT);
		return at != null && now - at < COOL_TICKS;
	}

	/** Whether this stack has heat that has run out. */
	public static boolean expired(ItemStack stack, long now) {
		Long at = stack.get(Smithing.HEATED_AT);
		return at != null && now - at >= COOL_TICKS;
	}

	/** Whether the hearth can bring this up: a workable ingot, a Hot Ingot or a forging blank. */
	public static boolean heatable(ItemStack stack) {
		return !stack.isEmpty() && (ingotMetal(stack) != null || stack.is(Smithing.HOT_INGOT) || stack.is(Smithing.FORGING_BLANK));
	}

	/** This stack fresh out of the fire. */
	public static ItemStack heated(ItemStack stack, long now) {
		Molten molten = ingotMetal(stack);
		if (molten != null) {
			return hotIngot(molten, stack.getCount(), now);
		}
		ItemStack out = stack.copy();
		out.set(Smithing.HEATED_AT, now);
		return out;
	}

	/** This stack gone cold: a Hot Ingot back to its ingot, anything else without its heat. */
	public static ItemStack cooled(ItemStack stack, ServerLevel level) {
		if (stack.is(Smithing.HOT_INGOT)) {
			Molten molten = metal(stack);
			return molten == null ? ItemStack.EMPTY : ingot(molten, stack.getCount());
		}
		if (!stack.is(Smithing.FORGING_BLANK)) {
			for (var cooler : COOLERS) {
				ItemStack out = cooler.apply(stack, level);
				if (out != null) {
					return out;
				}
			}
		}
		ItemStack out = stack.copy();
		out.remove(Smithing.HEATED_AT);
		return out;
	}

	/** The tongs in either hand, or empty. */
	public static ItemStack tongs(ServerPlayer player) {
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack held = player.getItemInHand(hand);
			if (held.getItem() instanceof TongsItem) {
				return held;
			}
		}
		return ItemStack.EMPTY;
	}

	/** Whether this player can handle hot metal bare-handed. */
	public static boolean spared(ServerPlayer player) {
		return player.isCreative() || player.isSpectator() || player.hasEffect(MobEffects.FIRE_RESISTANCE)
			|| player.entityTags().contains(ForgeHearthBlock.EMBERBORN_TAG);
	}

	/** Every {@link #CHECK_TICKS}: cools what has gone cold, then burns or wears tongs for what is still hot. */
	public static void tick(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if ((now + player.getId()) % CHECK_TICKS != 0) {
			return;
		}
		Inventory inventory = player.getInventory();
		boolean hot = false, hissed = false;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty() || !stack.has(Smithing.HEATED_AT)) {
				continue;
			}
			if (expired(stack, now)) {
				inventory.setItem(i, cooled(stack, level));
				if (!hissed) {
					level.playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.4F);
					hissed = true;
				}
			} else {
				hot = true;
			}
		}
		if (!hot || spared(player)) {
			return;
		}
		ItemStack tongs = tongs(player);
		if (tongs.isEmpty()) {
			if (player.getRemainingFireTicks() < BURN_TICKS) {
				player.setRemainingFireTicks(BURN_TICKS);
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.smithing.burns"));
			}
		} else if ((now + player.getId()) % TONGS_WEAR_TICKS < CHECK_TICKS) {
			InteractionHand hand = player.getMainHandItem() == tongs ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
			tongs.hurtAndBreak(1, player, hand);
		}
	}

	/** A tooltip line for a hot stack: "Hot (42 s)". */
	public static @Nullable Component hotLine(ItemStack stack, long now) {
		Long at = stack.get(Smithing.HEATED_AT);
		if (at == null) {
			return null;
		}
		long left = COOL_TICKS - (now - at);
		return left > 0 ? Component.translatable("tooltip.jaymospowers.smithing.hot", (left + 19) / 20)
			.withStyle(net.minecraft.ChatFormatting.RED) : null;
	}
}
