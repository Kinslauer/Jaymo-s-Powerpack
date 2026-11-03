package com.jaymoskingdoms;

import com.jaymoskingdoms.HeritageVariants.Variant;
import java.util.EnumSet;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * (0.7.485, Andy: "the ones with nonstandard heads or bodies cannot wear traditional armor") The sea-blooded Tideborn whose
 * heads or backs are their own shape (client SeaParts) can't wear a helmet or a chestplate: a piece put on slides off into
 * the pack (or drops when the pack is full), with a word on the action bar. "Traditional armour" is any piece that gives
 * armour in that slot, and on the back an elytra too; crowns, pumpkins and other headwear that give no armour still fit.
 * Players only: MCA's villagers dress themselves.
 */
public final class SeaBodies {
	/** How often each player's head and chest are checked (ticks). */
	static final int EVERY = 10;

	/** Heads too strange for a helmet: mantles, lures, bells, eyestalks, bills, manes, spines, frills and fronds. */
	public static final Set<Variant> NO_HELMET = EnumSet.of(Variant.EIGHTARM, Variant.LANTERNJAW, Variant.DRIFTBELL, Variant.SHELLCLAW,
		Variant.SWORDBILL, Variant.LIONMANE, Variant.SPINEPUFF, Variant.FRILLBACK, Variant.WEEDFROND);
	/** Backs too strange for a chestplate: dorsal fins, wings, shells, sails, spines, frills and fronds. */
	public static final Set<Variant> NO_CHEST = EnumSet.of(Variant.GREYFIN, Variant.MANTAWING, Variant.SHELLCLAW, Variant.OLDSHELL,
		Variant.LIONMANE, Variant.SWORDBILL, Variant.SPINEPUFF, Variant.FRILLBACK, Variant.WEEDFROND, Variant.MORAY);

	private SeaBodies() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(SeaBodies::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator()) {
				continue;
			}
			Variant look = HeritageVariants.of(player);
			if (look == null) {
				continue;
			}
			if (NO_HELMET.contains(look)) {
				shed(player, EquipmentSlot.HEAD, "message.jaymoskingdoms.sea_body.head");
			}
			if (NO_CHEST.contains(look)) {
				shed(player, EquipmentSlot.CHEST, "message.jaymoskingdoms.sea_body.back");
			}
		}
	}

	private static void shed(ServerPlayer player, EquipmentSlot slot, String message) {
		ItemStack stack = player.getItemBySlot(slot);
		if (stack.isEmpty() || !armour(stack, slot)) {
			return;
		}
		player.setItemSlot(slot, ItemStack.EMPTY);
		player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
		player.sendOverlayMessage(Component.translatable(message).withStyle(ChatFormatting.GRAY));
	}

	/** Whether {@code stack} is traditional armour in {@code slot}: it gives armour there (or, on the back, it's an elytra). */
	public static boolean armour(ItemStack stack, EquipmentSlot slot) {
		if (slot == EquipmentSlot.CHEST && stack.has(DataComponents.GLIDER)) {
			return true;
		}
		boolean[] gives = {false};
		stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).forEach(slot, (attribute, modifier) -> {
			if ((attribute.is(Attributes.ARMOR) || attribute.is(Attributes.ARMOR_TOUGHNESS)) && modifier.amount() > 0.0) {
				gives[0] = true;
			}
		});
		return gives[0];
	}
}
