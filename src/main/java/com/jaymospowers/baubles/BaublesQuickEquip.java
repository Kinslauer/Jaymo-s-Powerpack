package com.jaymospowers.baubles;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Shift-click a wearable item in the survival inventory (or sneak-use one in hand) to put it on: one of it goes in a
 * free bauble slot that takes it, and the bauble screen opens next tick so you see it on. With every such slot full,
 * shift-click moves it as vanilla does and a short message says so. Everything happens on the server; the client only
 * holds back its own guess of the vanilla move when it sees a free slot (the worn list syncs to its wearer).
 */
public final class BaublesQuickEquip {
	/** Players to show the bauble screen next tick, after their shift-click or sneak-use put something on. */
	private static final Set<UUID> TO_OPEN = new LinkedHashSet<>();
	/** Per item class: whether it has a use of its own (then sneak-use is left to it). */
	private static final Map<Class<?>, Boolean> OWN_USE = new ConcurrentHashMap<>();

	private BaublesQuickEquip() {
	}

	static void init() {
		UseItemCallback.EVENT.register(BaublesQuickEquip::onUse);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (TO_OPEN.isEmpty()) {
				return;
			}
			List<UUID> ids = List.copyOf(TO_OPEN);
			TO_OPEN.clear();
			for (UUID id : ids) {
				ServerPlayer player = server.getPlayerList().getPlayer(id);
				if (player != null) {
					openAfterEquip(player);
				}
			}
		});
	}

	/** Whether {@code stack} fits any bauble slot. */
	static boolean wearable(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		for (int i = 0; i < Baubles.SIZE; i++) {
			if (Baubles.fits(i, stack)) {
				return true;
			}
		}
		return false;
	}

	/** Whether this player has an empty bauble slot that takes {@code stack} (server, or the local player). */
	static boolean hasFreeSlot(Player player, ItemStack stack) {
		List<ItemStack> worn = Baubles.worn(player);
		for (int i = 0; i < Baubles.SIZE; i++) {
			if (worn.get(i).isEmpty() && Baubles.fits(i, stack)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Shift-click on an inventory slot holding something wearable ({@code InventoryMenu.quickMoveStack}): true when one
	 * went on (or, on the client, when one will), so vanilla's move is skipped. On the server it takes one from the slot
	 * itself, so a repeated or late click can only put on what is still there.
	 */
	public static boolean shiftClick(Player player, Slot slot) {
		ItemStack stack = slot.getItem();
		// placeable blocks (lanterns go on the belt) keep vanilla's shift-click, so sorting a stack of them still works;
		// sneak-using one in hand still puts it on
		if (stack.getItem() instanceof BlockItem || !wearable(stack)) {
			return false;
		}
		if (!(player instanceof ServerPlayer server)) {
			return hasFreeSlot(player, stack);
		}
		if (!Baubles.equipFromHand(server, stack)) {
			server.sendOverlayMessage(Component.translatable("message.jaymospowers.baubles.full"));
			return false;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		openNextTick(server);
		return true;
	}

	/** After a sneak-use put a bauble on from the hand: show the bauble screen next tick. */
	static void openNextTick(ServerPlayer player) {
		TO_OPEN.add(player.getUUID());
	}

	/**
	 * Sneak-use in the air with a wearable item that has no use of its own (a lantern, a totem): put it on. Items with
	 * a use of their own keep it (our baubles do it in {@link BaubleItem#use}); on a block nothing changes, so a
	 * lantern is still placed.
	 */
	private static InteractionResult onUse(Player player, Level level, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!player.isShiftKeyDown() || player.isSpectator() || !wearable(stack) || ownUse(stack.getItem())) {
			return InteractionResult.PASS;
		}
		if (!(player instanceof ServerPlayer server)) {
			return hasFreeSlot(player, stack) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		if (!Baubles.equipFromHand(server, stack)) {
			server.sendOverlayMessage(Component.translatable("message.jaymospowers.baubles.full"));
			return InteractionResult.PASS;
		}
		openNextTick(server);
		return InteractionResult.SUCCESS;
	}

	private static boolean ownUse(Item item) {
		return OWN_USE.computeIfAbsent(item.getClass(), type -> {
			try {
				return type.getMethod("use", Level.class, Player.class, InteractionHand.class).getDeclaringClass() != Item.class;
			} catch (ReflectiveOperationException | RuntimeException e) {
				return true;
			}
		});
	}

	/**
	 * Opens the bauble screen, only if the player is still just in their own inventory (or no screen) with nothing on
	 * the cursor: a cursor stack belongs to the inventory screen and would be left behind. The inventory is closed
	 * first as vanilla closes it, so its crafting grid goes back into the inventory.
	 */
	private static void openAfterEquip(ServerPlayer player) {
		if (!player.isAlive() || player.isSpectator() || player.hasDisconnected() || player.containerMenu != player.inventoryMenu
			|| !player.inventoryMenu.getCarried().isEmpty()) {
			return;
		}
		player.inventoryMenu.removed(player);
		Baubles.open(player);
	}
}
