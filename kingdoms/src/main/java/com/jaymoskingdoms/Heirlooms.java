package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Heirlooms (Andy's pick from Chest DNA, 2026-11-24): gear carried by a forebear keeps their name. When a life goes on in
 * a child ({@link Lineage}), each piece of gear the parent carried (a single piece that wears or is worn) gets the
 * parent's name added to its line of {@link #FOREBEARS}, oldest first, whether it goes on with the child or stays with the
 * elder. Gear a forebear made shows who made it too, from its {@link Provenance} stamp.
 */
public final class Heirlooms {
	/** Forebears named on one piece at most (the oldest go first). */
	static final int MOST_NAMES = 8;

	public static DataComponentType<List<String>> FOREBEARS;

	private Heirlooms() {
	}

	static void register() {
		FOREBEARS = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosKingdoms.id("forebears"),
			DataComponentType.<List<String>>builder().persistent(Codec.STRING.listOf())
				.networkSynchronized(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list())).build());
	}

	/** A piece that can be an heirloom: one alone, that wears down or is worn. */
	static boolean gear(ItemStack stack) {
		return !stack.isEmpty() && stack.getMaxStackSize() == 1 && (stack.isDamageableItem() || stack.has(DataComponents.EQUIPPABLE));
	}

	/** {@code player}'s life goes on in their child: the parent's {@code name} goes on everything they carry that is gear. */
	static void handDown(ServerPlayer player, String name) {
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			name(inventory.getItem(i), name);
		}
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			name(player.getItemBySlot(slot), name);
		}
	}

	private static void name(ItemStack stack, String name) {
		if (!gear(stack) || name.isBlank()) {
			return;
		}
		List<String> names = new ArrayList<>(stack.getOrDefault(FOREBEARS, List.of()));
		if (!names.isEmpty() && names.getLast().equals(name)) {
			return;
		}
		names.add(name);
		while (names.size() > MOST_NAMES) {
			names.removeFirst();
		}
		stack.set(FOREBEARS, List.copyOf(names));
	}
}
