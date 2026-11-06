package com.jaymoskingdoms.client;

import com.jaymoskingdoms.TierHiding;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

/**
 * The client's side of {@link TierHiding}: the items the server keeps from this player's sight, and whoever shows items
 * (the JEI plugin, {@code compat/TierJei}; the recipe book, {@code mixin/tierhiding/ClientRecipeBookMixin}) told when it
 * changes. Off a server without Kingdoms, nothing is hidden.
 */
public final class TierHidingClient {
	private static Set<Item> hidden = Set.of();
	private static final List<Runnable> LISTENERS = new ArrayList<>();

	private TierHidingClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(TierHiding.Hidden.TYPE, (payload, context) -> {
			Set<Item> items = new java.util.HashSet<>();
			for (var id : payload.items()) {
				BuiltInRegistries.ITEM.getOptional(id).ifPresent(items::add);
			}
			set(Set.copyOf(items));
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> set(Set.of())));
	}

	private static void set(Set<Item> items) {
		if (items.equals(hidden)) {
			return;
		}
		hidden = items;
		var player = Minecraft.getInstance().player;
		if (player != null) {
			player.getRecipeBook().rebuildCollections();
		}
		for (Runnable listener : LISTENERS) {
			listener.run();
		}
	}

	/** Everything hidden now. */
	public static Set<Item> hidden() {
		return hidden;
	}

	public static boolean hidden(Item item) {
		return hidden.contains(item);
	}

	/** Called (on the client thread) whenever the hidden set changes. */
	public static void listen(Runnable listener) {
		LISTENERS.add(listener);
	}

	/** Whether a recipe book entry only makes hidden things. */
	public static boolean hidden(RecipeDisplayEntry entry) {
		if (hidden.isEmpty()) {
			return false;
		}
		var level = Minecraft.getInstance().level;
		if (level == null) {
			return false;
		}
		List<ItemStack> results;
		try {
			results = entry.resultItems(SlotDisplayContext.fromLevel(level));
		} catch (RuntimeException e) {
			return false;
		}
		if (results.isEmpty()) {
			return false;
		}
		for (ItemStack stack : results) {
			if (!hidden.contains(stack.getItem())) {
				return false;
			}
		}
		return true;
	}
}
