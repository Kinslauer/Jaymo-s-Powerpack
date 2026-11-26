package com.jaymospowers.compat;

import com.jaymospowers.baubles.Baubles;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Traveler Tool Belt (optional; read by item id, never linked). (0.21.43, Andy: "wearing the toolbelt animates
 * everything like before. it should go in the baubles slot.") The belt is worn in the Baubles belt slot, and only there:
 * {@code mixin/toolbelt/ToolBeltHelperMixin} hands that one to the mod, for its radial menu, its tool swap and drawing it
 * on the hip. A belt carried in the inventory is just carried.
 */
public final class ToolBeltCompat {
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("travelertoolbelt");
	private static final String NAMESPACE = "travelertoolbelt";
	private static final String HELPER = "com.tiviacz.travelertoolbelt.ToolBeltHelper";

	private ToolBeltCompat() {
	}

	/** Whether {@code id} is one of the mod's belts (plain, copper, iron, gold, diamond, netherite). */
	private static boolean isBelt(Identifier id) {
		return id.getNamespace().equals(NAMESPACE) && id.getPath().endsWith("belt");
	}

	/** Whether {@code stack} is one of the mod's belts. */
	public static boolean isBelt(ItemStack stack) {
		return LOADED && !stack.isEmpty() && isBelt(BuiltInRegistries.ITEM.getKey(stack.getItem()));
	}

	/**
	 * The tool belt worn in the belt slot (the live stack), or empty. Server, and the wearer's own client (the worn slots
	 * sync to their wearer).
	 */
	public static ItemStack worn(Player player) {
		ItemStack belt = LOADED ? Baubles.beltSlot(player) : ItemStack.EMPTY;
		return isBelt(belt) ? belt : ItemStack.EMPTY;
	}

	/** Whether {@code player} wears a tool belt, as everyone sees it (the worn looks sync to all; client and server). */
	public static boolean wears(Player player) {
		if (!LOADED) {
			return false;
		}
		List<Identifier> looks = Baubles.looks(player);
		return Baubles.BELT_SLOT < looks.size() && isBelt(looks.get(Baubles.BELT_SLOT));
	}

	/**
	 * The belt to draw on {@code player}'s hip: the worn stack where it is known, else a plain stack of the worn kind
	 * (other players on a client: only which belt it is is synced). Empty when none is worn.
	 */
	public static ItemStack shown(Player player) {
		ItemStack worn = worn(player);
		if (!worn.isEmpty() || !wears(player)) {
			return worn;
		}
		return new ItemStack(BuiltInRegistries.ITEM.getValue(Baubles.looks(player).get(Baubles.BELT_SLOT)));
	}

	/**
	 * Tells the wearer's client again after the server changed the worn belt's tools in place (a tool swap). The belt is
	 * put back as a copy: the attachment only syncs when the list stops being equal, and an unchanged stack object is.
	 */
	public static void resync(ServerPlayer player) {
		ItemStack belt = worn(player);
		if (!belt.isEmpty()) {
			List<ItemStack> worn = Baubles.worn(player);
			worn.set(Baubles.BELT_SLOT, belt.copy());
			Baubles.setWorn(player, worn);
		}
	}

	/**
	 * What the worn tool belt holds, slot by slot (empty where a slot is empty, and a hip item, purse or lantern, counts
	 * as empty: it works from the belt and isn't drawn); an empty list without a worn belt. Server, and the wearer's client.
	 */
	public static List<ItemStack> tools(Player player) {
		ItemStack belt = worn(player);
		if (belt.isEmpty()) {
			return List.of();
		}
		return belt.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).itemCopies()
			.map(stack -> Baubles.hipItem(stack) ? ItemStack.EMPTY : stack).toList();
	}

	/** Whether the belt takes {@code stack} (the mod's own rule, or a hip item); false without the mod. */
	public static boolean holds(ItemStack stack) {
		if (!LOADED || stack.isEmpty()) {
			return false;
		}
		try {
			return (boolean)Class.forName(HELPER).getMethod("isItemValid", ItemStack.class).invoke(null, stack);
		} catch (ReflectiveOperationException | LinkageError e) {
			return false;
		}
	}

	/**
	 * Swaps the main hand with the worn belt's slot {@code slot} by the mod's own tool swap (its sound, its rules), which
	 * the mixin then syncs back ({@code ToolBeltHelperMixin}). Nothing without the mod.
	 */
	public static void swap(ServerPlayer player, int slot) {
		if (!LOADED) {
			return;
		}
		try {
			Class.forName(HELPER).getMethod("swapTool", Player.class, int.class, int.class).invoke(null, player, slot, 0);
		} catch (ReflectiveOperationException | LinkageError e) {
			com.jaymospowers.JaymosPowers.LOGGER.warn("Couldn't swap a tool belt tool", e);
		}
	}

	/** Takes the worn tool belt off the belt slot and hands it back (for the caller to drop), or null if none. */
	public static @Nullable ItemStack takeWorn(ServerPlayer player) {
		ItemStack belt = worn(player);
		if (belt.isEmpty()) {
			return null;
		}
		Baubles.setBelt(player, ItemStack.EMPTY);
		return belt;
	}
}
