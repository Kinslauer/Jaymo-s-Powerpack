package com.jaymospowers.compat;

import com.jaymospowers.baubles.Baubles;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Traveler Tool Belt (optional; read by item id, never linked). (0.21.42, Andy: "wearing the toolbelt animates
 * everything like before. it should go in the baubles slot.") The belt is worn in the Baubles belt slot, and only there:
 * {@code mixin/toolbelt/ToolBeltHelperMixin} hands that one to the mod, for its radial menu, its tool swap and drawing it
 * on the hip. A belt carried in the inventory is just carried.
 */
public final class ToolBeltCompat {
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("travelertoolbelt");
	private static final String NAMESPACE = "travelertoolbelt";

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
