package com.jaymospowers.tinkering.smithing;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.tinkering.TinkerTool;
import com.mojang.serialization.Codec;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;

/**
 * (Smithing) Broken, not gone: a graded weapon, tool or armour piece that would break stays in your hands at the
 * last point of durability as "Broken". It does nothing (no attack bonus, no mining, no armour, can't be used) until
 * it is repaired, which clears it. Tinkering tools already break this way ({@code ToolStats#isBroken}) and are left to
 * that. Also here: graded stacks that arrive with only the grade id (Jaymo's Kingdoms' trades) are baked the first
 * time they are in a player's pack.
 */
public final class Wear {
	/** Set on a graded item that has worn out. */
	public static DataComponentType<Boolean> BROKEN;
	/** Set once a stack's grade has been baked into its numbers. */
	public static DataComponentType<Boolean> BAKED;

	/** How often (ticks) a pack is checked for stacks to bake and mend. */
	public static final int CHECK_TICKS = 10;
	/** Ticks between the "it is broken" reminders while you keep trying. */
	public static final int REMIND_TICKS = 20;

	private Wear() {
	}

	static void register() {
		BROKEN = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("broken"),
			DataComponentType.<Boolean>builder().persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());
		BAKED = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("grade_baked"),
			DataComponentType.<Boolean>builder().persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % CHECK_TICKS == 0) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					tidy(player);
				}
			}
		});
		UseItemCallback.EVENT.register((player, level, hand) -> refuse(player, hand, level.isClientSide()));
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> refuse(player, hand, level.isClientSide()));
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> refuse(player, hand, level.isClientSide()));
	}

	/** Whether this stack is a graded item that wears out like this (not a Tinkering tool, which has its own breaking). */
	public static boolean applies(ItemStack stack) {
		return !stack.isEmpty() && stack.has(Smithing.GRADE) && !(stack.getItem() instanceof TinkerTool) && stack.isDamageableItem();
	}

	/** Broken now: marked, and still at the last point of durability (a repair frees it at once). */
	public static boolean broken(ItemStack stack) {
		return stack.has(BROKEN) && stack.isDamageableItem() && stack.getDamageValue() >= stack.getMaxDamage() - 1;
	}

	/** Worn out: it stays at the last point, marked broken and stripped of its numbers. */
	public static void breakDown(ItemStack stack) {
		stack.setDamageValue(Math.max(0, stack.getMaxDamage() - 1));
		stack.set(BROKEN, true);
		Grades.bake(stack);
	}

	/** Mended: its numbers come back. */
	public static void mend(ItemStack stack) {
		stack.remove(BROKEN);
		Grades.bake(stack);
	}

	/** (from {@link Grades#bake}) A broken item has no attack, armour or mining after the grade is baked. */
	static void restrict(ItemStack stack) {
		if (!stack.has(BROKEN)) {
			return;
		}
		stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		if (stack.has(DataComponents.TOOL)) {
			stack.set(DataComponents.TOOL, new Tool(List.of(), 1.0F, 1, false));
		}
	}

	private static InteractionResult refuse(Player player, InteractionHand hand, boolean client) {
		ItemStack held = player.getItemInHand(hand);
		if (!held.has(BROKEN) || !broken(held)) {
			return InteractionResult.PASS;
		}
		if (!client && player instanceof ServerPlayer smith && smith.tickCount % REMIND_TICKS == 0) {
			smith.sendOverlayMessage(Component.translatable("message.jaymospowers.broken", held.getHoverName()));
		}
		return InteractionResult.FAIL;
	}

	/** Bakes grades that arrived unbaked and frees what has been repaired. */
	private static void tidy(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty() || !stack.has(Smithing.GRADE) || stack.getItem() instanceof TinkerTool) {
				continue;
			}
			if (stack.has(BROKEN) && !broken(stack)) {
				mend(stack);
			}
			if (!stack.has(BAKED)) {
				stack.set(BAKED, true);
				Grades.bake(stack);
			}
		}
	}
}
