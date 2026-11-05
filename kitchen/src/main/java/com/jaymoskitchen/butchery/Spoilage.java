package com.jaymoskitchen.butchery;

import com.jaymoskitchen.stomach.RawFood;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/**
 * (0.5.51) Andy: "Yes all of this" (meat from an unbled carcass spoils faster, after Alex's Butchery's bleeding step).
 * Raw meat cut from a carcass that was never bled (butchered on a table or the ground, or cut up where it lay) is marked
 * "unbled" with the day it was cut ({@link #mark}); carried for {@link #SPOIL_DAYS} days uncooked, it turns to rotten
 * flesh in your pockets. Bled on a hook, or cut by a village butcher, it keeps as meat always has. Cooking it (a new
 * item) ends the clock.
 */
public final class Spoilage {
	/** Days an unbled cut keeps, raw, in someone's pockets. */
	public static final int SPOIL_DAYS = 3;
	/** How often a player's pockets are checked (ticks). */
	static final int CHECK_EVERY = 200;
	static final String KEY = "jaymoskitchen_unbled";

	private Spoilage() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Spoilage::tick);
	}

	/** Marks the raw meat in {@code stacks} as cut from an unbled carcass on {@code day}. */
	public static void mark(List<ItemStack> stacks, long day) {
		for (ItemStack stack : stacks) {
			mark(stack, day);
		}
	}

	public static void mark(ItemStack stack, long day) {
		if (stack.isEmpty() || !stack.is(RawFood.RAW_MEATS) || stack.is(Items.COD) || stack.is(Items.SALMON)) {
			return;
		}
		CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		tag.putLong(KEY, day);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.jaymoskitchen.unbled").withStyle(ChatFormatting.DARK_RED))));
	}

	/** The day it was cut unbled, or -1. */
	static long cutOn(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? -1L : data.copyTag().getLongOr(KEY, -1L);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			long today = player.level().getOverworldClockTime() / 24000L;
			var inventory = player.getInventory();
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				ItemStack stack = inventory.getItem(i);
				if (stack.isEmpty() || !stack.has(DataComponents.CUSTOM_DATA)) {
					continue;
				}
				long cut = cutOn(stack);
				if (cut >= 0 && today - cut >= SPOIL_DAYS) {
					inventory.setItem(i, new ItemStack(Items.ROTTEN_FLESH, stack.getCount()));
				}
			}
		}
	}
}
