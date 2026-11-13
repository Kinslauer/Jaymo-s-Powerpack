package com.jaymoskingdoms;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Bonding with strong things (Jaymo's Powers' {@code Bonds}), when Powers is loaded: whether a bauble or a relic works for
 * someone, a rest taken, what a player has on them, and a thing's nature made known. Without Powers nothing needs a bond,
 * nothing is unknown and a rest bonds nothing. The bond and the unknown properties are kept on the item as plain custom data
 * keys, so this class is only the way to the bond list, which Powers keeps.
 */
public final class ItemBonds {
	private static boolean looked;
	private static @Nullable MethodHandle needs, works, rest, carried, release, unknown, reveal, use, bonded, count, resync;

	private ItemBonds() {
	}

	private static void look() {
		if (looked) {
			return;
		}
		looked = true;
		if (!FabricLoader.getInstance().isModLoaded("jaymospowers")) {
			return;
		}
		try {
			MethodHandles.Lookup l = MethodHandles.publicLookup();
			Class<?> bonds = Class.forName("com.jaymospowers.baubles.Bonds");
			Class<?> baubles = Class.forName("com.jaymospowers.baubles.Baubles");
			needs = l.findStatic(bonds, "needs", MethodType.methodType(boolean.class, ItemStack.class));
			works = l.findStatic(bonds, "works", MethodType.methodType(boolean.class, Player.class, ItemStack.class));
			bonded = l.findStatic(bonds, "bonded", MethodType.methodType(boolean.class, Player.class, ItemStack.class));
			rest = l.findStatic(bonds, "rest", MethodType.methodType(int.class, ServerPlayer.class));
			carried = l.findStatic(bonds, "carried", MethodType.methodType(List.class, ServerPlayer.class));
			release = l.findStatic(bonds, "release", MethodType.methodType(void.class, ServerPlayer.class, ItemStack.class));
			unknown = l.findStatic(bonds, "unknown", MethodType.methodType(boolean.class, ItemStack.class));
			reveal = l.findStatic(bonds, "reveal", MethodType.methodType(void.class, ItemStack.class));
			use = l.findStatic(bonds, "use", MethodType.methodType(boolean.class, ItemStack.class, int.class));
			count = l.findStatic(bonds, "bonds", MethodType.methodType(List.class, Player.class));
			resync = l.findStatic(baubles, "worn", MethodType.methodType(List.class, Player.class));
		} catch (ReflectiveOperationException | LinkageError e) {
			JaymosKingdoms.LOGGER.warn("Couldn't find Jaymo's Powers' bonds", e);
			needs = null;
		}
	}

	private static boolean flag(@Nullable MethodHandle handle, boolean otherwise, Object... args) {
		look();
		if (handle == null) {
			return otherwise;
		}
		try {
			return (boolean) handle.invokeWithArguments(args);
		} catch (Throwable e) {
			return otherwise;
		}
	}

	/** Whether {@code stack} needs a bond to work. */
	public static boolean needs(ItemStack stack) {
		return flag(needs, false, stack);
	}

	/** Whether {@code stack} does anything for {@code who}: no bond is needed, or {@code who} is bonded. Mobs are never held back. */
	public static boolean works(LivingEntity who, ItemStack stack) {
		return !(who instanceof Player player) || flag(works, true, player, stack);
	}

	/** Whether {@code player} is bonded to {@code stack}. */
	public static boolean bonded(Player player, ItemStack stack) {
		return flag(bonded, false, player, stack);
	}

	/** Whether {@code stack}'s powers are still hidden. */
	public static boolean unknown(ItemStack stack) {
		return flag(unknown, false, stack);
	}

	/** Marks {@code stack}'s nature as known. */
	public static void reveal(ItemStack stack) {
		look();
		if (reveal != null) {
			try {
				reveal.invoke(stack);
			} catch (Throwable e) {
				// nothing to reveal
			}
		}
		Relics.relabel(stack);
	}

	/** Counts {@code ticks} of use on a bonded stack; true when it just became known. */
	public static boolean use(ItemStack stack, int ticks) {
		boolean made = flag(use, false, stack, ticks);
		if (made) {
			Relics.relabel(stack);
		}
		return made;
	}

	/** A rest was taken: bonds what {@code player} has on. */
	public static int rest(ServerPlayer player) {
		look();
		if (rest == null) {
			return 0;
		}
		try {
			return (int) rest.invoke(player);
		} catch (Throwable e) {
			return 0;
		}
	}

	/** Everything {@code player} holds, wears or has in a bauble slot that can be bonded (the live stacks). */
	@SuppressWarnings("unchecked")
	public static List<ItemStack> carried(ServerPlayer player) {
		look();
		if (carried == null) {
			return List.of(player.getMainHandItem(), player.getOffhandItem());
		}
		try {
			return (List<ItemStack>) carried.invoke(player);
		} catch (Throwable e) {
			return List.of();
		}
	}

	/** Lets go of a stack's bond (a curse lifted). */
	public static void release(ServerPlayer player, ItemStack stack) {
		look();
		if (release != null) {
			try {
				release.invoke(player, stack);
			} catch (Throwable e) {
				// no bond to let go
			}
		}
	}

	/** How many things {@code player} is bonded to. */
	public static int bondCount(Player player) {
		look();
		if (count == null) {
			return 0;
		}
		try {
			return ((List<?>) count.invoke(player)).size();
		} catch (Throwable e) {
			return 0;
		}
	}

	/** The bond id a stack carries (as written in its custom data), or null. */
	public static @Nullable UUID idOf(ItemStack stack) {
		net.minecraft.world.item.component.CustomData data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		return data.copyTag().getString("jaymos_bond").map(s -> {
			try {
				return UUID.fromString(s);
			} catch (IllegalArgumentException e) {
				return null;
			}
		}).orElse(null);
	}

	/** Sends the worn list to the wearer again after a stack in it was changed in place. */
	public static void resync(ServerPlayer player) {
		look();
		if (resync == null) {
			return;
		}
		try {
			Class<?> baubles = Class.forName("com.jaymospowers.baubles.Baubles");
			baubles.getMethod("setWorn", ServerPlayer.class, List.class).invoke(null, player, resync.invoke(player));
		} catch (Throwable e) {
			// nothing worn to send
		}
	}
}
